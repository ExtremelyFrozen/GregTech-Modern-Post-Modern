package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTabsElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTooltipsPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2AutoStockingFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2CircuitFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2DirectionalFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2WorkingEnabledFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.datacomponents.AEInputConfigCopyData;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.integration.ae2.gui.element.AEFluidConfigElement;
import com.gregtechceu.gtceu.integration.ae2.gui.fancy.LDLib2MEFluidAutoPullFancyConfigurator;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.AEFluidConfigWidget;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidSlot;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAESlot;
import com.gregtechceu.gtceu.utils.GTMath;

import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import lombok.Getter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * ME fluid import hatch with a holder-scoped LDLib2 configuration page and immutable client snapshot.
 */
public class MEInputHatchPartMachine extends MEHatchPartMachine
                                     implements IDataStickInteractable, IHasCircuitSlot,
                                     LDLib2FancyPartUIProvider, MEFluidConfigActionTarget {

    static {
        MEFluidConfigActions.initialize();
    }

    /** Authoritative sixteen-slot fluid request and stock inventory. */
    protected ExportOnlyAEFluidList aeFluidHandler;

    /** Lossless client-facing page state rebuilt only after an authoritative value change. */
    @SyncToClient
    @Getter
    private AEFluidConfigSnapshot fluidConfigSnapshot;

    /** Creates the ordinary ME fluid input hatch and connects slot changes to one snapshot path. */
    public MEInputHatchPartMachine(BlockEntityCreationInfo info) {
        super(info, IO.IN);
        this.fluidConfigSnapshot = AEFluidConfigSnapshot.empty(isOnline(), isMEFluidStocking(),
                isMEFluidConfigAutoPull());
        this.aeFluidHandler.setSnapshotChangeListener(this::onFluidConfigContentsChanged);
    }

    /////////////////////////////////
    // ***** Machine LifeCycle ****//
    /////////////////////////////////

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            scheduleForNextServerTick(this::refreshFluidConfigSnapshot);
        }
    }

    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        flushInventory();
    }

    @Override
    protected NotifiableFluidTank createTank(int initialCapacity, int slots) {
        this.aeFluidHandler = new ExportOnlyAEFluidList(this, slots);
        return aeFluidHandler;
    }

    /////////////////////////////////
    // ********** Sync ME *********//
    /////////////////////////////////

    @Override
    public void setOnline(boolean online) {
        boolean changed = isOnline != online;
        super.setOnline(online);
        if (changed && !isRemote()) {
            refreshFluidConfigSnapshot();
        }
    }

    @Override
    protected void autoIO() {
        if (!this.isWorkingEnabled()) return;
        if (!this.shouldSyncME()) return;

        if (this.updateMEStatus()) {
            this.syncME();
            this.updateTankSubscription();
        }
    }

    protected void syncME() {
        MEStorage networkInv = this.getMainNode().getGrid().getStorageService().getInventory();
        for (ExportOnlyAEFluidSlot aeTank : this.aeFluidHandler.getInventory()) {
            // Try to clear the wrong fluid
            GenericStack exceedFluid = aeTank.exceedStack();
            if (exceedFluid != null) {
                int total = GTMath.saturatedCast(exceedFluid.amount());
                int inserted = GTMath.saturatedCast(networkInv.insert(exceedFluid.what(), exceedFluid.amount(),
                        Actionable.MODULATE, this.actionSource));
                if (inserted > 0) {
                    aeTank.drain(inserted, IFluidHandler.FluidAction.EXECUTE);
                    continue;
                } else {
                    aeTank.drain(total, IFluidHandler.FluidAction.EXECUTE);
                }
            }
            // Fill it
            GenericStack reqFluid = aeTank.requestStack();
            if (reqFluid != null) {
                long extracted = networkInv.extract(reqFluid.what(), reqFluid.amount(), Actionable.MODULATE,
                        this.actionSource);
                if (extracted > 0) {
                    aeTank.addStack(new GenericStack(reqFluid.what(), extracted));
                }
            }
        }
    }

    protected void flushInventory() {
        var grid = getMainNode().getGrid();
        if (grid != null) {
            for (var aeSlot : aeFluidHandler.getInventory()) {
                GenericStack stock = aeSlot.getStock();
                if (stock != null) {
                    grid.getStorageService().getInventory().insert(stock.what(), stock.amount(), Actionable.MODULATE,
                            actionSource);
                }
            }
        }
    }

    ///////////////////////////////
    // ********** GUI ***********//
    ///////////////////////////////

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(new Position(0, 0));
        // ME Network status
        group.addWidget(new LabelWidget(3, 0, () -> this.isOnline ?
                "gtpm.gui.me_network.online" :
                "gtpm.gui.me_network.offline"));

        // Config slots
        group.addWidget(new AEFluidConfigWidget(3, 10, this.aeFluidHandler));

        return group;
    }

    /** Returns one authoritative slot for action execution and direct behavior verification. */
    public ExportOnlyAEFluidSlot getMEFluidConfigSlot(int index) {
        validateSlotIndex(index);
        return aeFluidHandler.getInventory()[index];
    }

    private void onFluidConfigContentsChanged() {
        if (!isRemote()) {
            refreshFluidConfigSnapshot();
        }
    }

    /** Rebuilds and publishes the page snapshot only when one observable value actually changed. */
    protected final void refreshFluidConfigSnapshot() {
        AEFluidConfigSnapshot refreshed = AEFluidConfigSnapshot.capture(isOnline(), isMEFluidStocking(),
                isMEFluidConfigAutoPull(), aeFluidHandler.getInventory());
        if (!refreshed.equals(fluidConfigSnapshot)) {
            fluidConfigSnapshot = refreshed;
            syncDataHolder.markClientSyncFieldDirty("fluidConfigSnapshot");
        }
    }

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this && supportsMEFluidConfigActions();
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        LDLib2FancyUIProvider page = createLDLib2Page(player, holder);
        return UI.of(new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight()));
    }

    /** Creates one standalone opening-scoped page after validating the exact holder and definition. */
    LDLib2FancyUIProvider createLDLib2Page(Player player, MachineUIHolder holder) {
        requireMatchingHolder(holder);
        if (!supportsMEFluidConfigActions()) {
            throw new IllegalStateException("ME fluid configuration page requires an exact input hatch definition.");
        }
        return new MEFluidHatchFancyPage(player, holder);
    }

    @Override
    public LDLib2FancyUIProvider createLDLib2FancyPage(Player player, MachineUIHolder holder) {
        return createLDLib2Page(player, holder);
    }

    private void requireMatchingHolder(MachineUIHolder holder) {
        if (holder.getMachine() != this) {
            throw new IllegalArgumentException("ME fluid configuration page holder must resolve the opened hatch.");
        }
    }

    private UIElement createLDLib2MainElement(Player player, MachineUIHolder holder) {
        return createLDLib2MainElement(player, holder, MachineUIHelper::sendAction,
                () -> player.level().isClientSide() && holder.getMachine() == this,
                UIEvent::isCtrlDown, UIEvent::isShiftDown);
    }

    /**
     * Builds a directly testable page body with injected action transport and modifier state.
     */
    UIElement createLDLib2MainElement(Player player, MachineUIHolder holder,
                                      BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                      BooleanSupplier canSendAction, Predicate<UIEvent> modifierDown) {
        return createLDLib2MainElement(player, holder, actionSender, canSendAction, modifierDown, modifierDown);
    }

    private UIElement createLDLib2MainElement(Player player, MachineUIHolder holder,
                                              BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                              BooleanSupplier canSendAction, Predicate<UIEvent> ctrlDown,
                                              Predicate<UIEvent> shiftDown) {
        requireMatchingHolder(holder);
        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, 150, 88);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));
        root.addChild(new AEFluidConfigElement(this::getFluidConfigSnapshot, player, holder,
                actionSender, canSendAction, ctrlDown, shiftDown));
        return root;
    }

    ////////////////////////////////
    // ******* Interaction *******//
    ////////////////////////////////

    @Override
    public final InteractionResult onDataStickShiftUse(Player player, ItemStack dataStick) {
        if (!isRemote()) {
            dataStick.set(GTDataComponents.AE_INPUT_CONFIG_COPY_DATA, writeConfigData());
            dataStick.set(DataComponents.ITEM_NAME,
                    Component.translatable("gtpm.machine.me.fluid_import.data_stick.name"));
            player.sendSystemMessage(Component.translatable("gtpm.machine.me.import_copy_settings"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public final InteractionResult onDataStickUse(Player player, ItemStack dataStick) {
        AEInputConfigCopyData data = dataStick.get(GTDataComponents.AE_INPUT_CONFIG_COPY_DATA);
        if (data == null) {
            return InteractionResult.PASS;
        }

        if (!isRemote()) {
            readConfigData(data);
            this.updateTankSubscription();
            player.sendSystemMessage(Component.translatable("gtpm.machine.me.import_paste_settings"));
        }
        return InteractionResult.sidedSuccess(isRemote());
    }

    ////////////////////////////////
    // ****** Configuration ******//
    ////////////////////////////////

    protected AEInputConfigCopyData writeConfigData() {
        List<@Nullable GenericStack> stacks = new ArrayList<>(CONFIG_SIZE);
        for (int i = 0; i < CONFIG_SIZE; i++) {
            stacks.add(this.aeFluidHandler.getInventory()[i].getConfig());
        }
        byte ghostCircuit = (byte) IntCircuitBehaviour.getCircuitConfiguration(circuitInventory.getStackInSlot(0));
        return new AEInputConfigCopyData(stacks, ghostCircuit, false, false);
    }

    /** Validates a complete fluid-only data-stick payload before applying any slot mutation. */
    protected void readConfigData(AEInputConfigCopyData data) {
        List<@Nullable GenericStack> stacks = data.stacks();
        for (int index = 0; index < Math.min(stacks.size(), CONFIG_SIZE); index++) {
            GenericStack stack = stacks.get(index);
            if (stack != null && (!(stack.what() instanceof AEFluidKey) ||
                    stack.amount() <= 0 || stack.amount() > Integer.MAX_VALUE)) {
                throw new IllegalArgumentException("ME fluid input data stick contains an invalid stack at slot " +
                        index + '.');
            }
        }
        for (int i = 0; i < CONFIG_SIZE; i++) {
            this.aeFluidHandler.getInventory()[i].setConfig(i < stacks.size() ? stacks.get(i) : null);
        }
        circuitInventory.setStackInSlot(0, IntCircuitBehaviour.stack(data.ghostCircuit()));
    }

    @Override
    @ApiStatus.Internal
    public boolean supportsFluidHatchActions() {
        return getDefinition() == GTAEMachines.FLUID_IMPORT_HATCH_ME;
    }

    @Override
    @ApiStatus.Internal
    public boolean supportsMEFluidConfigActions() {
        return getDefinition() == GTAEMachines.FLUID_IMPORT_HATCH_ME ||
                getDefinition() == GTAEMachines.STOCKING_IMPORT_HATCH_ME;
    }

    @Override
    @ApiStatus.Internal
    public int getMEFluidConfigSlotCount() {
        return CONFIG_SIZE;
    }

    @Override
    @ApiStatus.Internal
    public boolean isMEFluidConfigAutoPull() {
        return false;
    }

    @Override
    @ApiStatus.Internal
    public boolean isMEFluidStocking() {
        return false;
    }

    @Override
    @ApiStatus.Internal
    public boolean canSetMEFluidConfig(int slot, @NotNull FluidStack fluid) {
        validateSlotIndex(slot);
        if (isMEFluidConfigAutoPull()) {
            return false;
        }
        if (fluid.isEmpty() || !isMEFluidStocking()) {
            return true;
        }
        AEFluidKey key = AEFluidKey.of(fluid);
        for (int index = 0; index < CONFIG_SIZE; index++) {
            GenericStack configured = aeFluidHandler.getInventory()[index].getConfig();
            if (index != slot && configured != null && configured.what().equals(key)) {
                return false;
            }
        }
        return !isConfiguredInOtherStockingPart(new GenericStack(key, fluid.getAmount()));
    }

    /** Returns whether another formed stocking part already owns the candidate fluid key. */
    protected boolean isConfiguredInOtherStockingPart(@NotNull GenericStack stack) {
        return false;
    }

    @Override
    @ApiStatus.Internal
    public void setMEFluidConfig(int slot, @NotNull FluidStack fluid) {
        if (!supportsMEFluidConfigActions() || !canSetMEFluidConfig(slot, fluid)) {
            throw new IllegalStateException("ME fluid configuration mutation is not permitted.");
        }
        getMEFluidConfigSlot(slot).setConfig(fluid.isEmpty() ? null :
                new GenericStack(AEFluidKey.of(fluid), fluid.getAmount()));
    }

    @Override
    @ApiStatus.Internal
    public boolean canSetMEFluidConfigAmount(int slot, @NotNull FluidStack expectedFluid, int amount) {
        validateSlotIndex(slot);
        if (!supportsMEFluidConfigActions() || isMEFluidStocking() || isMEFluidConfigAutoPull() ||
                expectedFluid.isEmpty() || amount <= 0) {
            return false;
        }
        GenericStack config = getMEFluidConfigSlot(slot).getConfig();
        return config != null && config.what().equals(AEFluidKey.of(expectedFluid));
    }

    @Override
    @ApiStatus.Internal
    public void setMEFluidConfigAmount(int slot, @NotNull FluidStack expectedFluid, int amount) {
        validateSlotIndex(slot);
        ExportOnlyAEFluidSlot configSlot = getMEFluidConfigSlot(slot);
        GenericStack config = configSlot.getConfig();
        if (!supportsMEFluidConfigActions() || isMEFluidStocking() || isMEFluidConfigAutoPull() ||
                expectedFluid.isEmpty() || amount <= 0 || config == null ||
                !config.what().equals(AEFluidKey.of(expectedFluid))) {
            throw new IllegalStateException("ME fluid configuration amount mutation is not permitted.");
        }
        configSlot.setConfig(ExportOnlyAESlot.copy(config, amount));
    }

    @Override
    @ApiStatus.Internal
    public void setMEFluidAutoPull(boolean autoPull) {
        throw new IllegalStateException("Ordinary ME fluid input hatches do not support auto-pull.");
    }

    private static void validateSlotIndex(int slot) {
        if (slot < 0 || slot >= CONFIG_SIZE) {
            throw new IllegalArgumentException("ME fluid configuration slot is out of range: " + slot);
        }
    }

    /** One opening-scoped Fancy home page shared by standalone and contextual part navigation. */
    private final class MEFluidHatchFancyPage implements LDLib2FancyUIProvider {

        private final Player player;
        private final MachineUIHolder holder;
        @Nullable
        private final LDLib2DirectionalFancyConfigurator directionalPage;

        private MEFluidHatchFancyPage(Player player, MachineUIHolder holder) {
            requireMatchingHolder(holder);
            this.player = player;
            this.holder = holder;
            this.directionalPage = isMEFluidStocking() ? null :
                    new LDLib2DirectionalFancyConfigurator(MEInputHatchPartMachine.this, player, holder);
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            if (holder.getMachine() != MEInputHatchPartMachine.this) {
                throw new IllegalStateException("ME fluid configuration page holder no longer resolves its hatch.");
            }
            return createLDLib2MainElement(player, holder);
        }

        @Override
        public IGuiTexture getTabIcon() {
            return GuiTextures.itemStack(getDefinition().getItem());
        }

        @Override
        public Component getTitle() {
            return Component.translatable(getDefinition().getDescriptionId());
        }

        @Override
        public int getLDLib2PageWidth() {
            return 150;
        }

        @Override
        public int getLDLib2PageHeight() {
            return 88;
        }

        @Override
        public void attachSideTabs(LDLib2FancyTabsElement tabs) {
            if (directionalPage != null) {
                tabs.attachSubTab(directionalPage);
            }
        }

        @Override
        public void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel) {
            configuratorPanel.attachConfigurators(new LDLib2WorkingEnabledFancyConfigurator(
                    MEInputHatchPartMachine.this, holder));
            if (isCircuitSlotEnabled()) {
                configuratorPanel.attachConfigurators(new LDLib2CircuitFancyConfigurator(
                        MEInputHatchPartMachine.this, holder));
            }
            if (MEInputHatchPartMachine.this instanceof MEStockingHatchPartMachine stockingHatch) {
                configuratorPanel.attachConfigurators(
                        new LDLib2MEFluidAutoPullFancyConfigurator(stockingHatch, holder),
                        new LDLib2AutoStockingFancyConfigurator(stockingHatch, holder));
            }
        }

        @Override
        public void attachTooltips(LDLib2FancyTooltipsPanelElement tooltipsPanel) {
            tooltipsPanel.attachTooltips(MEInputHatchPartMachine.this);
            for (var trait : getTraitHolder().getAllTraits()) {
                if (trait instanceof IFancyTooltip tooltip) {
                    tooltipsPanel.attachTooltips(tooltip);
                }
            }
        }

        @Override
        public List<Component> getTabTooltips() {
            return List.of(Component.translatable(getDefinition().getDescriptionId()));
        }

        @Override
        public PageGroupingData getPageGroupingData() {
            return new PageGroupingData("gtpm.multiblock.page_switcher.io.import", 1);
        }
    }
}

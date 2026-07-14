package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTabsElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTooltipsPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.util.FluidContainerSlotInteraction;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.ButtonConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.CircuitFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.FancyInvConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.FancyTankConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2ButtonConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2CircuitFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2DirectionalFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2FancyInvConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2FancyTankConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerList;
import com.gregtechceu.gtceu.api.recipe.ingredient.SizedIngredientExtensions;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.integration.ae2.gui.element.MEPatternBufferPageElement;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.AETextInputButtonWidget;
import com.gregtechceu.gtceu.integration.ae2.gui.widget.slot.AEPatternViewSlotWidget;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.InternalSlotRecipeHandler;
import com.gregtechceu.gtceu.utils.FluidStackHashStrategy;
import com.gregtechceu.gtceu.utils.GTMath;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;

import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.util.ClickData;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.crafting.pattern.EncodedPatternItem;
import appeng.helpers.patternprovider.PatternContainer;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import it.unimi.dsi.fastutil.objects.*;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.ExtensionMethod;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@ExtensionMethod(SizedIngredientExtensions.class)
public class MEPatternBufferPartMachine extends MEBusPartMachine
                                        implements ICraftingProvider, PatternContainer, IDataStickInteractable,
                                        LDLib2FancyPartUIProvider, MEPatternBufferActionTarget {

    static {
        MEPatternBufferActions.initialize();
    }

    protected static final int MAX_PATTERN_COUNT = 27;
    private final InternalInventory internalPatternInventory = new InternalInventory() {

        @Override
        public int size() {
            return MAX_PATTERN_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            return patternInventory.getStackInSlot(slotIndex);
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            patternInventory.setStackInSlot(slotIndex, stack);
            patternInventory.onContentsChanged(slotIndex);
            onPatternChange(slotIndex);
        }
    };

    @Getter
    @SaveField
    @SyncToClient
    // Maybe an Expansion Option in the future? a bit redundant for rn. Maybe Packdevs want to add their own
    // version.
    private final CustomItemStackHandler patternInventory = new CustomItemStackHandler(MAX_PATTERN_COUNT);

    @Getter
    @SaveField
    protected final NotifiableItemStackHandler shareInventory;

    @Getter
    @SaveField
    protected final NotifiableFluidTank shareTank;

    @Getter
    @SaveField
    protected final InternalSlot[] internalInventory = new InternalSlot[MAX_PATTERN_COUNT];

    private final BiMap<IPatternDetails, InternalSlot> detailsSlotMap = HashBiMap.create(MAX_PATTERN_COUNT);

    @SyncToClient
    @SaveField
    @Getter
    private String customName = "";

    private boolean needPatternSync;

    @SaveField
    private final Set<BlockPos> proxies = new ObjectOpenHashSet<>();
    private final Set<MEPatternBufferProxyPartMachine> proxyMachines = new ReferenceOpenHashSet<>();

    @Getter
    protected final InternalSlotRecipeHandler internalRecipeHandler;

    @Nullable
    protected TickableSubscription updateSubs;

    public MEPatternBufferPartMachine(BlockEntityCreationInfo info) {
        super(info, IO.IN);
        patternInventory.setOnContentsChanged(() -> getSyncDataHolder().markClientSyncFieldDirty("patternInventory"));
        this.patternInventory.setFilter(stack -> stack.getItem() instanceof EncodedPatternItem<?>);
        for (int i = 0; i < this.internalInventory.length; i++) {
            this.internalInventory[i] = new InternalSlot();
        }
        getMainNode().addService(ICraftingProvider.class, this);
        this.shareInventory = attachTrait(new NotifiableItemStackHandler(9, IO.IN, IO.NONE));
        this.shareTank = attachTrait(new NotifiableFluidTank(9, 8 * FluidType.BUCKET_VOLUME, IO.IN, IO.NONE));
        this.internalRecipeHandler = new InternalSlotRecipeHandler(this, internalInventory);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            for (int i = 0; i < patternInventory.getSlots(); i++) {
                var pattern = patternInventory.getStackInSlot(i);
                var patternDetails = PatternDetailsHelper.decodePattern(pattern, getLevel());
                if (patternDetails != null) {
                    this.detailsSlotMap.put(patternDetails, this.internalInventory[i]);
                }
            }
            needPatternSync = true;
        }
    }

    @Override
    public List<RecipeHandlerList> getRecipeHandlers() {
        return internalRecipeHandler.getSlotHandlers();
    }

    @Override
    public boolean isWorkingEnabled() {
        return true;
    }

    public void setCustomName(String newName) {
        if (customName.equals(newName)) {
            return;
        }
        customName = newName;
        syncDataHolder.markClientSyncFieldDirty("customName");
    }

    @Override
    public void setWorkingEnabled(boolean ignored) {}

    @Override
    public boolean isDistinct() {
        return true;
    }

    @Override
    public void setDistinct(boolean ignored) {}

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        this.updateSubscription();
    }

    protected void updateSubscription() {
        if (getMainNode().isOnline()) {
            updateSubs = subscribeServerTick(updateSubs, this::update);
        } else if (updateSubs != null) {
            updateSubs.unsubscribe();
            updateSubs = null;
        }
    }

    protected void update() {
        if (needPatternSync) {
            ICraftingProvider.requestUpdate(getMainNode());
            this.needPatternSync = false;
        }
    }

    public void addProxy(MEPatternBufferProxyPartMachine proxy) {
        proxies.add(proxy.getBlockPos());
        proxyMachines.add(proxy);
    }

    public void removeProxy(MEPatternBufferProxyPartMachine proxy) {
        proxies.remove(proxy.getBlockPos());
        proxyMachines.remove(proxy);
    }

    @UnmodifiableView
    public Set<MEPatternBufferProxyPartMachine> getProxies() {
        if (proxyMachines.size() != proxies.size()) {
            proxyMachines.clear();
            for (var pos : proxies) {
                if (MetaMachine.getMachine(getLevel(), pos) instanceof MEPatternBufferProxyPartMachine proxy) {
                    proxyMachines.add(proxy);
                }
            }
        }
        return Collections.unmodifiableSet(proxyMachines);
    }

    private void refundAll(ClickData clickData) {
        if (!clickData.isRemote) {
            refundAllContents();
        }
    }

    private void refundAllContents() {
        for (InternalSlot internalSlot : internalInventory) {
            internalSlot.refund();
        }
    }

    @VisibleForTesting
    public void onPatternChange(int index) {
        if (isRemote()) return;

        // remove old if applicable
        var internalInv = internalInventory[index];
        var newPattern = patternInventory.getStackInSlot(index);
        var newPatternDetails = PatternDetailsHelper.decodePattern(newPattern, getLevel());
        var oldPatternDetails = detailsSlotMap.inverse().remove(internalInv);
        if (newPatternDetails != null) {
            detailsSlotMap.forcePut(newPatternDetails, internalInv);
        }
        if (oldPatternDetails != null && !oldPatternDetails.equals(newPatternDetails)) {
            internalInv.refund();
        }

        needPatternSync = true;
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////
    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this &&
                holder.getMachineDefinitionId().equals(GTAEMachines.ME_PATTERN_BUFFER.getId()) &&
                supportsMEPatternBufferActions();
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        LDLib2FancyUIProvider page = createLDLib2Page(player, holder);
        return UI.of(new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight()));
    }

    /**
     * Creates one standalone opening-scoped page after validating the holder and exact machine definition.
     */
    LDLib2FancyUIProvider createLDLib2Page(Player player, MachineUIHolder holder) {
        return createLDLib2Page(player, holder, MachineUIHelper::sendAction,
                () -> player.level().isClientSide() && holder.getMachine() == this &&
                        holder.getMachineDefinitionId().equals(GTAEMachines.ME_PATTERN_BUFFER.getId()),
                UIEvent::isShiftDown);
    }

    /**
     * Builds an opening-scoped page with injectable action transport for direct interaction tests.
     */
    LDLib2FancyUIProvider createLDLib2Page(Player player, MachineUIHolder holder,
                                           BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                           BooleanSupplier canSendAction, Predicate<UIEvent> shiftDown) {
        requireMatchingLDLib2Holder(holder);
        requirePatternBufferDefinition();
        return new MEPatternBufferFancyPage(player, holder, actionSender, canSendAction, shiftDown);
    }

    @Override
    public LDLib2FancyUIProvider createLDLib2FancyPage(Player player, MachineUIHolder holder) {
        return createLDLib2Page(player, holder);
    }

    /**
     * Builds the directly testable Pattern Buffer body for one validated opening.
     */
    MEPatternBufferPageElement createLDLib2MainElement(Player player, MachineUIHolder holder,
                                                       BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                                       BooleanSupplier canSendAction) {
        requireMatchingLDLib2Holder(holder);
        requirePatternBufferDefinition();
        return new MEPatternBufferPageElement(this, player.level(), holder, actionSender, canSendAction);
    }

    public int getLDLib2PageWidth() {
        return MEPatternBufferPageElement.WIDTH;
    }

    public int getLDLib2PageHeight() {
        return MEPatternBufferPageElement.HEIGHT;
    }

    private void requireMatchingLDLib2Holder(MachineUIHolder holder) {
        if (holder.getMachine() != this ||
                !holder.getMachineDefinitionId().equals(GTAEMachines.ME_PATTERN_BUFFER.getId())) {
            throw new IllegalArgumentException("Pattern Buffer page holder must resolve the opened definition.");
        }
    }

    private void requirePatternBufferDefinition() {
        if (!supportsMEPatternBufferActions()) {
            throw new IllegalStateException("Pattern Buffer LDLib2 UI requires the exact Pattern Buffer definition.");
        }
    }

    @Override
    public boolean supportsMEPatternBufferActions() {
        return getDefinition() == GTAEMachines.ME_PATTERN_BUFFER;
    }

    @Override
    public void setMEPatternBufferName(String name) {
        requirePatternBufferDefinition();
        setCustomName(name);
    }

    @Override
    public void refundMEPatternBufferContents() {
        requirePatternBufferDefinition();
        refundAllContents();
    }

    @Override
    public int getMEPatternBufferShareTankCount() {
        return shareTank.getStorages().length;
    }

    @Override
    public void clickMEPatternBufferShareTank(ServerPlayer player, int tankIndex, boolean shiftDown) {
        requirePatternBufferDefinition();
        if (tankIndex < 0 || tankIndex >= getMEPatternBufferShareTankCount()) {
            throw new IllegalArgumentException("Invalid Pattern Buffer shared tank index: " + tankIndex);
        }
        new FluidContainerSlotInteraction(shareTank.getStorages()[tankIndex], true, true)
                .click(player, shiftDown);
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        configuratorPanel.attachConfigurators(new ButtonConfigurator(
                GuiTextures.group(GuiTextures.BUTTON, GuiTextures.REFUND_OVERLAY), this::refundAll)
                .setTooltips(List.of(Component.translatable("gui.gtpm.refund_all.desc"))));
        if (isHasCircuitSlot() && isCircuitSlotEnabled()) {
            configuratorPanel.attachConfigurators(new CircuitFancyConfigurator(circuitInventory.storage));
        }
        configuratorPanel.attachConfigurators(new FancyInvConfigurator(
                shareInventory.storage, Component.translatable("gui.gtpm.share_inventory.title"))
                .setTooltips(List.of(
                        Component.translatable("gui.gtpm.share_inventory.desc.0"),
                        Component.translatable("gui.gtpm.share_inventory.desc.1"))));
        configuratorPanel.attachConfigurators(new FancyTankConfigurator(
                shareTank.getStorages(), Component.translatable("gui.gtpm.share_tank.title"))
                .setTooltips(List.of(
                        Component.translatable("gui.gtpm.share_tank.desc.0"),
                        Component.translatable("gui.gtpm.share_inventory.desc.1"))));
    }

    @Override
    public Widget createUIWidget() {
        int rowSize = 9;
        int colSize = 3;
        var group = new WidgetGroup(0, 0, 18 * rowSize + 16, 18 * colSize + 16);
        int index = 0;
        for (int y = 0; y < colSize; ++y) {
            for (int x = 0; x < rowSize; ++x) {
                int finalI = index;
                var slot = new AEPatternViewSlotWidget(patternInventory, index++, 8 + x * 18, 14 + y * 18)
                        .setOccupiedTexture(GuiTextures.SLOT)
                        .setItemHook(stack -> {
                            if (!stack.isEmpty() && stack.getItem() instanceof EncodedPatternItem<?> iep) {
                                final ItemStack out = iep.getOutput(stack);
                                if (!out.isEmpty()) {
                                    return out;
                                }
                            }
                            return stack;
                        })
                        .setChangeListener(() -> onPatternChange(finalI))
                        .setBackground(GuiTextures.SLOT, GuiTextures.PATTERN_OVERLAY);
                group.addWidget(slot);
            }
        }
        // ME Network status
        group.addWidget(new LabelWidget(
                8,
                2,
                () -> this.isOnline ? "gtpm.gui.me_network.online" : "gtpm.gui.me_network.offline"));

        group.addWidget(new AETextInputButtonWidget(18 * rowSize + 8 - 70, 2, 70, 10)
                .setText(customName)
                .setOnConfirm(this::setCustomName)
                .setButtonTooltips(Component.translatable("gui.gtpm.rename.desc")));

        return group;
    }

    /**
     * Owns one menu opening's page-local state, holder, actions, and directional subpage.
     */
    private final class MEPatternBufferFancyPage implements LDLib2FancyUIProvider {

        private final Player player;
        private final MachineUIHolder holder;
        private final BiConsumer<MachineUIHolder, SyncActionData> actionSender;
        private final BooleanSupplier canSendAction;
        private final Predicate<UIEvent> shiftDown;
        private final LDLib2DirectionalFancyConfigurator directionalPage;

        private MEPatternBufferFancyPage(Player player, MachineUIHolder holder,
                                         BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                         BooleanSupplier canSendAction, Predicate<UIEvent> shiftDown) {
            requireMatchingLDLib2Holder(holder);
            this.player = player;
            this.holder = holder;
            this.actionSender = actionSender;
            this.canSendAction = canSendAction;
            this.shiftDown = shiftDown;
            directionalPage = new LDLib2DirectionalFancyConfigurator(
                    MEPatternBufferPartMachine.this, player, holder);
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            if (holder.getMachine() != MEPatternBufferPartMachine.this ||
                    !holder.getMachineDefinitionId().equals(GTAEMachines.ME_PATTERN_BUFFER.getId())) {
                throw new IllegalStateException("Pattern Buffer page holder no longer resolves its opened machine.");
            }
            return createLDLib2MainElement(player, holder, actionSender, canSendAction);
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
            return MEPatternBufferPartMachine.this.getLDLib2PageWidth();
        }

        @Override
        public int getLDLib2PageHeight() {
            return MEPatternBufferPartMachine.this.getLDLib2PageHeight();
        }

        @Override
        public void attachSideTabs(LDLib2FancyTabsElement tabs) {
            tabs.attachSubTab(directionalPage);
        }

        @Override
        public void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel) {
            configuratorPanel.attachConfigurators(new LDLib2ButtonConfigurator(
                    GuiTextures.group(GuiTextures.BUTTON, GuiTextures.REFUND_OVERLAY), event -> {
                        if (canSendAction.getAsBoolean()) {
                            actionSender.accept(holder, MEPatternBufferActions.createRefundAllAction());
                        }
                    }).setTooltips(List.of(Component.translatable("gui.gtpm.refund_all.desc"))));
            if (isHasCircuitSlot() && isCircuitSlotEnabled()) {
                configuratorPanel.attachConfigurators(new LDLib2CircuitFancyConfigurator(
                        MEPatternBufferPartMachine.this, holder));
            }
            configuratorPanel.attachConfigurators(new LDLib2FancyInvConfigurator(
                    shareInventory.storage, Component.translatable("gui.gtpm.share_inventory.title"))
                    .setTooltips(List.of(
                            Component.translatable("gui.gtpm.share_inventory.desc.0"),
                            Component.translatable("gui.gtpm.share_inventory.desc.1"))));
            configuratorPanel.attachConfigurators(new LDLib2FancyTankConfigurator(
                    shareTank.getStorages(), Component.translatable("gui.gtpm.share_tank.title"))
                    .setTankClickHandler((tankIndex, event) -> {
                        if (!canSendAction.getAsBoolean() ||
                                FluidUtil.getFluidHandler(player.containerMenu.getCarried()).isEmpty()) {
                            return false;
                        }
                        actionSender.accept(holder, MEPatternBufferActions.createClickShareTankAction(
                                tankIndex, shiftDown.test(event)));
                        return true;
                    })
                    .setTooltips(List.of(
                            Component.translatable("gui.gtpm.share_tank.desc.0"),
                            Component.translatable("gui.gtpm.share_inventory.desc.1"))));
        }

        @Override
        public void attachTooltips(LDLib2FancyTooltipsPanelElement tooltipsPanel) {
            tooltipsPanel.attachTooltips(MEPatternBufferPartMachine.this);
            getTraitHolder().getAllTraits().stream()
                    .filter(IFancyTooltip.class::isInstance)
                    .map(IFancyTooltip.class::cast)
                    .forEach(tooltipsPanel::attachTooltips);
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

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return detailsSlotMap.keySet().stream().toList();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (!isFormed() || !getMainNode().isActive() || !detailsSlotMap.containsKey(patternDetails) ||
                !checkInput(inputHolder)) {
            return false;
        }

        var slot = detailsSlotMap.get(patternDetails);
        if (slot != null) {
            slot.pushPattern(patternDetails, inputHolder);
            return true;
        }
        return false;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    private boolean checkInput(KeyCounter[] inputHolder) {
        for (KeyCounter input : inputHolder) {
            var illegal = input.keySet().stream()
                    .map(AEKey::getType)
                    .map(AEKeyType::getId)
                    .anyMatch(id -> !id.equals(AEKeyType.items().getId()) && !id.equals(AEKeyType.fluids().getId()));
            if (illegal) return false;
        }
        return true;
    }

    @Override
    public @Nullable IGrid getGrid() {
        return getMainNode().getGrid();
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return internalPatternInventory;
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        // Has controller
        if (isFormed()) {
            MultiblockControllerMachine controller = getControllers().first();
            MultiblockMachineDefinition controllerDefinition = controller.getDefinition();
            // has customName
            if (!customName.isEmpty()) {
                return new PatternContainerGroup(
                        AEItemKey.of(controllerDefinition.asStack()),
                        Component.literal(customName),
                        Collections.emptyList());
            } else {
                ItemStack circuitStack = isHasCircuitSlot() ? circuitInventory.storage.getStackInSlot(0) :
                        ItemStack.EMPTY;
                int circuitConfiguration = circuitStack.isEmpty() ? -1 :
                        IntCircuitBehaviour.getCircuitConfiguration(circuitStack);

                Component groupName = circuitConfiguration != -1 ?
                        Component.translatable(controllerDefinition.getDescriptionId())
                                .append(" - " + circuitConfiguration) :
                        Component.translatable(controllerDefinition.getDescriptionId());

                return new PatternContainerGroup(
                        AEItemKey.of(controllerDefinition.asStack()), groupName, Collections.emptyList());
            }
        } else {
            if (!customName.isEmpty()) {
                return new PatternContainerGroup(
                        AEItemKey.of(GTAEMachines.ME_PATTERN_BUFFER.getItem()),
                        Component.literal(customName),
                        Collections.emptyList());
            } else {
                return new PatternContainerGroup(
                        AEItemKey.of(GTAEMachines.ME_PATTERN_BUFFER.getItem()),
                        GTAEMachines.ME_PATTERN_BUFFER.get().getDefinition().getItem().getDescription(),
                        Collections.emptyList());
            }
        }
    }

    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        patternInventory.dropInventoryInWorld(getLevel(), getBlockPos());
    }

    @Override
    public InteractionResult onDataStickShiftUse(Player player, ItemStack dataStick) {
        dataStick.set(GTDataComponents.DATA_COPY_POS, getBlockPos());
        return InteractionResult.SUCCESS;
    }

    public record BufferData(Object2LongMap<ItemStack> items, Object2LongMap<FluidStack> fluids) {}

    public BufferData mergeInternalSlots() {
        var items = new Object2LongOpenCustomHashMap<>(ItemStackHashStrategy.comparingAllButCount());
        var fluids = new Object2LongOpenHashMap<FluidStack>();
        for (InternalSlot slot : internalInventory) {
            slot.itemInventory.object2LongEntrySet().fastForEach(e -> items.addTo(e.getKey(), e.getLongValue()));
            slot.fluidInventory.object2LongEntrySet().fastForEach(e -> fluids.addTo(e.getKey(), e.getLongValue()));
        }
        return new BufferData(items, fluids);
    }

    public class InternalSlot {

        @Getter
        @Setter
        private Runnable onContentsChanged = () -> {};

        private final Object2LongOpenCustomHashMap<ItemStack> itemInventory = new Object2LongOpenCustomHashMap<>(
                ItemStackHashStrategy.comparingAllButCount());
        private final Object2LongOpenCustomHashMap<FluidStack> fluidInventory = new Object2LongOpenCustomHashMap<>(
                FluidStackHashStrategy.comparingAllButAmount());
        private @Nullable List<ItemStack> itemStacks = null;
        private @Nullable List<FluidStack> fluidStacks = null;

        public InternalSlot() {}

        public boolean isItemEmpty() {
            return itemInventory.isEmpty();
        }

        public boolean isFluidEmpty() {
            return fluidInventory.isEmpty();
        }

        public Object2LongOpenCustomHashMap<ItemStack> getItemInventoryForSerialization() {
            return itemInventory;
        }

        public Object2LongOpenCustomHashMap<FluidStack> getFluidInventoryForSerialization() {
            return fluidInventory;
        }

        public void onContentsChanged() {
            itemStacks = null;
            fluidStacks = null;
            onContentsChanged.run();
        }

        private void add(AEKey what, long amount) {
            if (amount <= 0L) return;
            if (what instanceof AEItemKey itemKey) {
                var stack = itemKey.toStack();
                itemInventory.addTo(stack, amount);
            } else if (what instanceof AEFluidKey fluidKey) {
                var stack = fluidKey.toStack(1);
                fluidInventory.addTo(stack, amount);
            }
        }

        public List<ItemStack> getItems() {
            if (itemStacks == null) {
                itemStacks = new ArrayList<>();
                itemInventory.object2LongEntrySet().stream()
                        .map(e -> GTMath.splitStacks(e.getKey(), e.getLongValue()))
                        .forEach(itemStacks::addAll);
            }
            return itemStacks;
        }

        public List<FluidStack> getFluids() {
            if (fluidStacks == null) {
                fluidStacks = fluidInventory.object2LongEntrySet().stream()
                        .flatMap(e -> GTMath.splitFluidStacks(e.getKey(), e.getLongValue()).stream())
                        .collect(Collectors.toList());
            }
            return fluidStacks;
        }

        public void refund() {
            var network = getMainNode().getGrid();
            if (network != null) {
                MEStorage networkInv = network.getStorageService().getInventory();
                var energy = network.getEnergyService();

                for (var it = itemInventory.object2LongEntrySet().iterator(); it.hasNext();) {
                    var entry = it.next();
                    var stack = entry.getKey();
                    var count = entry.getLongValue();
                    if (stack.isEmpty() || count == 0) {
                        it.remove();
                        continue;
                    }

                    var key = AEItemKey.of(stack);
                    if (key == null) continue;

                    long inserted = StorageHelper.poweredInsert(energy, networkInv, key, count, actionSource);
                    if (inserted > 0) {
                        count -= inserted;
                        if (count == 0) it.remove();
                        else entry.setValue(count);
                    }
                }

                for (var it = fluidInventory.object2LongEntrySet().iterator(); it.hasNext();) {
                    var entry = it.next();
                    var stack = entry.getKey();
                    var amount = entry.getLongValue();
                    if (stack.isEmpty() || amount == 0) {
                        it.remove();
                        continue;
                    }

                    var key = AEFluidKey.of(stack);
                    if (key == null) continue;

                    long inserted = StorageHelper.poweredInsert(energy, networkInv, key, amount, actionSource);
                    if (inserted > 0) {
                        amount -= inserted;
                        if (amount == 0) it.remove();
                        else entry.setValue(amount);
                    }
                }
                onContentsChanged();
            }
        }

        public void pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
            patternDetails.pushInputsToExternalInventory(inputHolder, this::add);
            onContentsChanged();
        }

        public List<SizedIngredient> handleItemInternal(List<SizedIngredient> left, boolean simulate) {
            boolean changed = false;
            for (var it = left.listIterator(); it.hasNext();) {
                var ingredient = it.next();
                if (ingredient.ingredient().hasNoItems()) {
                    it.remove();
                    continue;
                }

                var items = ingredient.getItems();
                if (items.length == 0 || items[0].isEmpty()) {
                    it.remove();
                    continue;
                }

                int amount = items[0].getCount();
                for (var it2 = itemInventory.object2LongEntrySet().iterator(); it2.hasNext();) {
                    var entry = it2.next();
                    var stack = entry.getKey();
                    var count = entry.getLongValue();
                    if (stack.isEmpty() || count == 0) {
                        it2.remove();
                        continue;
                    }
                    if (!ingredient.test(stack)) continue;
                    int extracted = Math.min(GTMath.saturatedCast(count), amount);
                    if (!simulate && extracted > 0) {
                        changed = true;
                        count -= extracted;
                        if (count == 0) it2.remove();
                        else entry.setValue(count);
                    }
                    amount -= extracted;

                    if (amount <= 0) {
                        it.remove();
                        break;
                    }
                }

                if (amount > 0) {
                    it.set(ingredient.copyWithCount(amount));
                }
            }
            if (changed) onContentsChanged();
            return left;
        }

        public List<SizedFluidIngredient> handleFluidInternal(List<SizedFluidIngredient> left,
                                                              boolean simulate) {
            boolean changed = false;
            for (var it = left.listIterator(); it.hasNext();) {
                var ingredient = it.next();
                if (ingredient.ingredient().hasNoFluids()) {
                    it.remove();
                    continue;
                }

                var fluids = ingredient.getFluids();
                if (fluids.length == 0 || fluids[0].isEmpty()) {
                    it.remove();
                    continue;
                }

                int amount = fluids[0].getAmount();
                for (var it2 = fluidInventory.object2LongEntrySet().iterator(); it2.hasNext();) {
                    var entry = it2.next();
                    var stack = entry.getKey();
                    var count = entry.getLongValue();
                    if (stack.isEmpty() || count == 0) {
                        it2.remove();
                        continue;
                    }
                    if (!ingredient.test(stack)) continue;
                    int extracted = Math.min(GTMath.saturatedCast(count), amount);
                    if (!simulate && extracted > 0) {
                        changed = true;
                        count -= extracted;
                        if (count == 0) it2.remove();
                        else entry.setValue(count);
                    }
                    amount -= extracted;

                    if (amount <= 0) {
                        it.remove();
                        break;
                    }
                }

                if (amount > 0) {
                    it.set(ingredient.copyWithAmount(amount));
                }
            }

            if (changed) onContentsChanged();
            return left;
        }
    }
}

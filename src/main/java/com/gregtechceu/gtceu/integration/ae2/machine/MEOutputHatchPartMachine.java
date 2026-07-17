package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTabsElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTooltipsPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2DirectionalFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2WorkingEnabledFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.integration.ae2.gui.element.MEFluidOutputWaitingListElement;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;
import com.gregtechceu.gtceu.utils.GTMath;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class MEOutputHatchPartMachine extends MEHatchPartMachine
                                      implements LDLib2FancyPartUIProvider, MEOutputWaitingListActionTarget {

    static {
        MEOutputWaitingListActions.initialize();
    }

    private static final int PAGE_WIDTH = 170;
    private static final int PAGE_HEIGHT = 74;

    @SaveField
    private KeyStorage internalBuffer; // Do not use KeyCounter, use our simple implementation

    /** Distinguishes a replacement hatch at the same position from queued packets for its predecessor. */
    @SaveField
    @SyncToClient
    private UUID waitingListIncarnation = UUID.randomUUID();

    /** Publishes ordered changes from the virtual output tank to active waiting-list openings. */
    @Getter
    private final MEOutputWaitingListPublisher waitingListPublisher;

    public MEOutputHatchPartMachine(BlockEntityCreationInfo info) {
        super(info, IO.OUT);
        waitingListPublisher = new MEOutputWaitingListPublisher(this, internalBuffer);
    }

    @Override
    public MEOutputWaitingListTarget getWaitingListTarget() {
        return new MEOutputWaitingListTarget(getBlockPos(), getDefinition().getId(), waitingListIncarnation);
    }

    /////////////////////////////////
    // ***** Machine LifeCycle ****//
    /////////////////////////////////

    @Override
    protected NotifiableFluidTank createTank(int initialCapacity, int slots) {
        this.internalBuffer = new KeyStorage();
        return new InaccessibleInfiniteTank(this);
    }

    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        var grid = getMainNode().getGrid();
        if (grid != null && !internalBuffer.isEmpty()) {
            for (var entry : internalBuffer) {
                grid.getStorageService().getInventory().insert(entry.getKey(), entry.getLongValue(),
                        Actionable.MODULATE, actionSource);
            }
        }
    }

    /////////////////////////////////
    // ********** Sync ME *********//
    /////////////////////////////////

    @Override
    protected boolean shouldSubscribe() {
        return super.shouldSubscribe() && !internalBuffer.storage.isEmpty();
    }

    @Override
    protected void autoIO() {
        if (!this.shouldSyncME()) return;
        if (this.updateMEStatus()) {
            var grid = getMainNode().getGrid();
            if (grid != null && !internalBuffer.isEmpty()) {
                internalBuffer.insertInventory(grid.getStorageService().getInventory(), actionSource);
            }
            this.updateTankSubscription();
        }
    }

    ///////////////////////////////
    // ********** GUI ***********//
    ///////////////////////////////

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this &&
                holder.getMachineDefinitionId().equals(GTAEMachines.FLUID_EXPORT_HATCH_ME.getId()) &&
                getDefinition() == GTAEMachines.FLUID_EXPORT_HATCH_ME;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        LDLib2FancyUIProvider page = createLDLib2Page(player, holder);
        return UI.of(new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight()));
    }

    /** Creates one opening-scoped page after validating the exact fluid output hatch holder and definition. */
    LDLib2FancyUIProvider createLDLib2Page(Player player, MachineUIHolder holder) {
        requireMatchingHolder(holder);
        return new MEFluidOutputHatchFancyPage(player, holder);
    }

    @Override
    public LDLib2FancyUIProvider createLDLib2FancyPage(Player player, MachineUIHolder holder) {
        return createLDLib2Page(player, holder);
    }

    private void requireMatchingHolder(MachineUIHolder holder) {
        if (holder.getMachine() != this) {
            throw new IllegalArgumentException("ME fluid output page holder must resolve the opened hatch.");
        }
        if (getDefinition() != GTAEMachines.FLUID_EXPORT_HATCH_ME ||
                !holder.getMachineDefinitionId().equals(GTAEMachines.FLUID_EXPORT_HATCH_ME.getId())) {
            throw new IllegalStateException("ME fluid output page requires the exact output hatch definition.");
        }
    }

    private UIElement createLDLib2MainElement(Player player, MachineUIHolder holder,
                                              MachineUIHolder actionHolder) {
        return createLDLib2MainElement(player, holder, actionHolder, MachineUIHelper::sendAction,
                () -> player.level().isClientSide() && holder.getMachine() == this &&
                        actionHolder.getMachine() != null);
    }

    /** Builds the directly testable waiting-list body around an injected GT action transport. */
    UIElement createLDLib2MainElement(Player player, MachineUIHolder holder,
                                      BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                      BooleanSupplier canSendAction) {
        return createLDLib2MainElement(player, holder, holder, actionSender, canSendAction);
    }

    /** Builds the waiting-list body with a hatch target holder and the real menu-root action holder. */
    UIElement createLDLib2MainElement(Player player, MachineUIHolder holder, MachineUIHolder actionHolder,
                                      BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                      BooleanSupplier canSendAction) {
        requireMatchingHolder(holder);
        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, PAGE_WIDTH, PAGE_HEIGHT);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));

        GTLabelElement status = new GTLabelElement(5, 0, 160, 10, networkStatus()) {

            @Override
            public void screenTick() {
                setValue(MEOutputHatchPartMachine.this.networkStatus());
                super.screenTick();
            }
        };
        status.setId("me_network_status");
        root.addChild(status);
        GTLabelElement title = new GTLabelElement(5, 10, 160, 10,
                Component.translatable("gtpm.gui.waiting_list"));
        title.setId("me_output_waiting_list_label");
        root.addChild(title);
        root.addChild(new MEFluidOutputWaitingListElement(holder, actionHolder, actionSender, canSendAction));
        return root;
    }

    private Component networkStatus() {
        return Component.translatable(isOnline ?
                "gtpm.gui.me_network.online" : "gtpm.gui.me_network.offline");
    }

    private class InaccessibleInfiniteTank extends NotifiableFluidTank {

        FluidStorageDelegate storage;

        public InaccessibleInfiniteTank(MetaMachine holder) {
            super(List.of(new FluidStorageDelegate()), IO.OUT, IO.NONE);
            internalBuffer.setOnContentsChanged(this::onContentsChanged);
            storage = (FluidStorageDelegate) getStorages()[0];
            allowSameFluids = true;
        }

        @Override
        public int getTanks() {
            return 128;
        }

        @Override
        public List<Object> getContents() {
            return Collections.emptyList();
        }

        @Override
        public double getTotalContentAmount() {
            return 0;
        }

        @Override
        public boolean isEmpty() {
            return true;
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public void setFluidInTank(int tank, FluidStack fluidStack) {
            // NO-OP
        }

        @Override
        public int getTankCapacity(int tank) {
            return storage.getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return true;
        }

        @Override
        public List<SizedFluidIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedFluidIngredient> left,
                                                            boolean simulate) {
            if (io != IO.OUT) return left;
            FluidAction action = simulate ? FluidAction.SIMULATE : FluidAction.EXECUTE;
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
                FluidStack output = fluids[0];
                int remainingAmount = ingredient.amount() - storage.fill(output, action);

                if (remainingAmount > 0) it.set(new SizedFluidIngredient(ingredient.ingredient(), remainingAmount));
                else it.remove();
            }
            return left;
        }
    }

    private class FluidStorageDelegate extends CustomFluidTank {

        public FluidStorageDelegate() {
            super(0);
        }

        @Override
        public int getCapacity() {
            return Integer.MAX_VALUE;
        }

        @Override
        public void setFluid(FluidStack fluid) {
            // NO-OP
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            var key = AEFluidKey.of(resource);
            int amount = resource.getAmount();
            int oldValue = GTMath.saturatedCast(internalBuffer.storage.getOrDefault(key, 0));
            int changeValue = Math.min(Integer.MAX_VALUE - oldValue, amount);
            if (changeValue > 0 && action.execute()) {
                internalBuffer.put(key, (long) oldValue + changeValue);
            }
            return changeValue;
        }

        @Override
        public boolean supportsFill(int tank) {
            return false;
        }

        @Override
        public boolean supportsDrain(int tank) {
            return false;
        }
    }

    /** One opening-scoped Fancy page shared by standalone and contextual part navigation. */
    private final class MEFluidOutputHatchFancyPage implements LDLib2FancyUIProvider {

        private final Player player;
        private final MachineUIHolder holder;
        private final LDLib2DirectionalFancyConfigurator directionalPage;

        private MEFluidOutputHatchFancyPage(Player player, MachineUIHolder holder) {
            requireMatchingHolder(holder);
            this.player = player;
            this.holder = holder;
            directionalPage = new LDLib2DirectionalFancyConfigurator(
                    MEOutputHatchPartMachine.this, player, holder);
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            if (holder.getMachine() != MEOutputHatchPartMachine.this) {
                throw new IllegalStateException("ME fluid output page holder no longer resolves its hatch.");
            }
            return createLDLib2MainElement(player, holder, shell.getHolder());
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
            return PAGE_WIDTH;
        }

        @Override
        public int getLDLib2PageHeight() {
            return PAGE_HEIGHT;
        }

        @Override
        public void attachSideTabs(LDLib2FancyTabsElement tabs) {
            tabs.attachSubTab(directionalPage);
        }

        @Override
        public void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel) {
            configuratorPanel.attachConfigurators(new LDLib2WorkingEnabledFancyConfigurator(
                    MEOutputHatchPartMachine.this, holder));
        }

        @Override
        public void attachTooltips(LDLib2FancyTooltipsPanelElement tooltipsPanel) {
            tooltipsPanel.attachTooltips(MEOutputHatchPartMachine.this);
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
            return new PageGroupingData("gtpm.multiblock.page_switcher.io.export", 2);
        }
    }
}

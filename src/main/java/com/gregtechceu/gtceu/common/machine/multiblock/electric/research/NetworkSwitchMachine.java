package com.gregtechceu.gtceu.common.machine.multiblock.electric.research;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolderContext;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTabsElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTooltipsPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2BatchModeFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2DirectionalFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2VoidingModeFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2WorkingEnabledFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.WorkLogic;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.computation.ComputationNetworkManager;
import com.gregtechceu.gtceu.common.machine.multiblock.part.OpticalComputationHatchMachine;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class NetworkSwitchMachine extends WorkableElectricMultiblockMachine
                                  implements IControllable, LDLib2MachineUIProvider, LDLib2FancyActionMachine {

    public static final int EUT_PER_HATCH = GTValues.VA[GTValues.IV];

    private int energyUsage = 0;
    private boolean computationBridgeActive;

    @Nullable
    protected TickableSubscription tickSubs;
    private final ConditionalSubscriptionHandler displaySnapshotSubscription;
    @Getter(AccessLevel.PACKAGE)
    @SyncToClient
    private List<Component> displaySnapshot = List.of();

    public NetworkSwitchMachine(BlockEntityCreationInfo info) {
        super(info);
        this.displaySnapshotSubscription = new ConditionalSubscriptionHandler(this, this::refreshDisplaySnapshot,
                this::isFormed);
    }

    protected int calculateEnergyUsage() {
        int receivers = 0;
        int transmitters = 0;
        for (var part : this.getParts()) {
            var block = part.self().getBlockState().getBlock();
            if (PartAbility.COMPUTATION_DATA_RECEPTION.isApplicable(block)) {
                ++receivers;
            }
            if (PartAbility.COMPUTATION_DATA_TRANSMISSION.isApplicable(block)) {
                ++transmitters;
            }
        }
        return EUT_PER_HATCH * (receivers + transmitters);
    }

    @Override
    public void formStructure(String structureName) {
        super.formStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        energyUsage = calculateEnergyUsage();
        updateTickSubscription();
        markComputationTopologyDirty();
        if (!isRemote()) {
            refreshDisplaySnapshot();
            displaySnapshotSubscription.updateSubscription();
        }
    }

    @Override
    public void invalidateStructure(String structureName) {
        super.invalidateStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        energyUsage = 0;
        updateComputationBridgeActive(false);
        updateTickSubscription();
        if (!isRemote()) {
            refreshDisplaySnapshot();
            displaySnapshotSubscription.updateSubscription();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            refreshDisplaySnapshot();
            displaySnapshotSubscription.initialize(getLevel());
        }
        scheduleForNextServerTick(this::updateTickSubscription);
    }

    @Override
    public void onUnload() {
        super.onUnload();
        clearRuntimeState();
    }

    @Override
    public void onPartUnload() {
        super.onPartUnload();
        clearRuntimeState();
    }

    private void clearRuntimeState() {
        energyUsage = 0;
        updateComputationBridgeActive(false);
        if (tickSubs != null) {
            tickSubs.unsubscribe();
            tickSubs = null;
        }
        displaySnapshot = List.of();
        displaySnapshotSubscription.unsubscribe();
    }

    protected void updateTickSubscription() {
        if (isFormed() && isWorkingEnabled()) {
            tickSubs = subscribeServerTick(tickSubs, this::tick);
        } else if (tickSubs != null) {
            tickSubs.unsubscribe();
            tickSubs = null;
        }
    }

    public void tick() {
        if (energyContainer == null) {
            updateComputationBridgeActive(false);
            getWorkLogic().setWaiting(Component.translatable("gtpm.recipe_logic.insufficient_in").append(": ")
                    .append(EURecipeCapability.CAP.getName()));
            refreshDisplaySnapshot();
            updateTickSubscription();
            return;
        }

        int energyToConsume = getEnergyUsage();
        if (energyContainer.getEnergyStored() >= energyToConsume &&
                energyContainer.removeEnergy(energyToConsume) >= energyToConsume) {
            getWorkLogic().setStatus(WorkLogic.Status.WORKING);
            updateComputationBridgeActive(true);
        } else {
            getWorkLogic().setWaiting(Component.translatable("gtpm.recipe_logic.insufficient_in").append(": ")
                    .append(EURecipeCapability.CAP.getName()));
            updateComputationBridgeActive(false);
        }
        refreshDisplaySnapshot();
        updateTickSubscription();
    }

    private void updateComputationBridgeActive(boolean active) {
        if (computationBridgeActive == active) return;
        computationBridgeActive = active;
        markComputationTopologyDirty();
    }

    private void markComputationTopologyDirty() {
        if (getLevel() instanceof ServerLevel serverLevel) {
            ComputationNetworkManager.get(serverLevel).markTopologyDirty();
        }
    }

    private int getMaxCWUt() {
        if (!isFormed() || !isWorkingEnabled() || !computationBridgeActive) {
            return 0;
        }
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return 0;
        }
        for (IMultiPart part : getParts()) {
            if (part instanceof OpticalComputationHatchMachine opticalHatch) {
                return ComputationNetworkManager.get(serverLevel)
                        .getNetWorkMaxCWUt(opticalHatch.getComputationPort());
            }
        }
        return 0;
    }

    private int getUsedCWUt() {
        if (!isFormed() || !isWorkingEnabled() || !computationBridgeActive) {
            return 0;
        }
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return 0;
        }
        for (IMultiPart part : getParts()) {
            if (part instanceof OpticalComputationHatchMachine opticalHatch) {
                return ComputationNetworkManager.get(serverLevel)
                        .getNetWorkUsedCWUt(opticalHatch.getComputationPort());
            }
        }
        return 0;
    }

    public int getEnergyUsage() {
        return isFormed() ? energyUsage : 0;
    }

    @Override
    public boolean isWorkingEnabled() {
        return !getWorkLogic().isSuspend();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        if (isWorkingAllowed) {
            getWorkLogic().setStatus(WorkLogic.Status.IDLE);
        } else {
            getWorkLogic().setStatus(WorkLogic.Status.SUSPEND);
            updateComputationBridgeActive(false);
        }
        updateTickSubscription();
        if (!isRemote()) {
            refreshDisplaySnapshot();
        }
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        textList.addAll(displaySnapshot);
    }

    void refreshDisplaySnapshot() {
        DisplayState state = captureDisplayState(isFormed(), isWorkingEnabled(), isActive(),
                computationBridgeActive, getEnergyUsage(), getMaxCWUt(), getUsedCWUt());
        List<Component> nextSnapshot = createDisplaySnapshot(state);
        if (!displaySnapshot.equals(nextSnapshot)) {
            displaySnapshot = nextSnapshot;
        }
    }

    static DisplayState captureDisplayState(boolean formed, boolean workingEnabled, boolean active,
                                            boolean bridgeActive, int energyUsage, int rawMaxCWUt,
                                            int rawUsedCWUt) {
        boolean computationAvailable = formed && workingEnabled && bridgeActive;
        return new DisplayState(formed, workingEnabled, active, energyUsage,
                computationAvailable ? rawMaxCWUt : 0,
                computationAvailable ? rawUsedCWUt : 0);
    }

    static List<Component> createDisplaySnapshot(DisplayState state) {
        List<Component> text = new ArrayList<>(4);
        MultiblockDisplayText.builder(text, state.formed())
                .setWorkingStatus(true, state.active() && state.workingEnabled())
                .setWorkingStatusKeys(
                        "gtpm.multiblock.idling",
                        "gtpm.multiblock.idling",
                        "gtpm.multiblock.data_bank.providing")
                .addEnergyUsageExactLine(state.energyUsage())
                .addComputationUsageLine(state.maxCWUt())
                .addComputationUsageExactLine(state.usedCWUt())
                .addWorkingStatusLine();
        return List.copyOf(text);
    }

    record DisplayState(boolean formed, boolean workingEnabled, boolean active,
                        int energyUsage, int maxCWUt, int usedCWUt) {}

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        requireMatchingHolder(holder);
        NetworkSwitchFancyPage page = new NetworkSwitchFancyPage(player, holder);
        return UI.of(new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight()));
    }

    private void requireMatchingHolder(MachineUIHolder holder) {
        if (holder.getMachine() != this) {
            throw new IllegalArgumentException("Network Switch UI holder must resolve the opened controller.");
        }
    }

    private final class NetworkSwitchFancyPage implements LDLib2FancyUIProvider {

        private static final int PAGE_WIDTH = 190;
        private static final int PAGE_HEIGHT = 125;

        private final MachineUIHolder holder;
        private final LDLib2DirectionalFancyConfigurator directionalPage;
        private final List<LDLib2FancyUIProvider> partPages;

        private NetworkSwitchFancyPage(Player player, MachineUIHolder holder) {
            requireMatchingHolder(holder);
            this.holder = holder;
            this.directionalPage = new LDLib2DirectionalFancyConfigurator(NetworkSwitchMachine.this, player, holder);

            List<LDLib2FancyUIProvider> pages = new ArrayList<>();
            for (IMultiPart part : getParts()) {
                if (!(part instanceof LDLib2FancyPartUIProvider pageProvider)) {
                    throw new IllegalStateException("Network Switch part has no LDLib2 Fancy page: " +
                            part.self().getDefinition().getId());
                }
                MachineUIHolder partHolder = new MachineUIHolderContext(player, part.self());
                pages.add(pageProvider.createLDLib2FancyPage(player, partHolder));
            }
            this.partPages = List.copyOf(pages);
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            if (holder.getMachine() != NetworkSwitchMachine.this) {
                throw new IllegalStateException("Network Switch page holder no longer resolves its controller.");
            }

            UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, PAGE_WIDTH, PAGE_HEIGHT);
            root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));

            GTScrollerViewElement screen = new GTScrollerViewElement(4, 4, 182, 117);
            screen.style(style -> style.backgroundTexture(getScreenTexture()));
            screen.viewPort(viewPort -> viewPort
                    .layout(layout -> layout.paddingAll(0))
                    .style(style -> style.backgroundTexture(getScreenTexture())));
            screen.scrollerStyle(style -> style
                    .mode(ScrollerMode.VERTICAL)
                    .verticalScrollDisplay(ScrollDisplay.AUTO)
                    .horizontalScrollDisplay(ScrollDisplay.NEVER));

            GTLabelElement title = new GTLabelElement(4, 5, 174, 10,
                    getBlockState().getBlock().getDescriptionId(), true);
            title.textStyle(style -> style
                    .textColor(0x404040)
                    .textShadow(false)
                    .textAlignHorizontal(Horizontal.LEFT)
                    .textAlignVertical(Vertical.CENTER));
            screen.addScrollViewChild(title);
            screen.addScrollViewChild(new GTComponentPanelElement(4, 17,
                    NetworkSwitchMachine.this::addDisplayText)
                    .setMaxWidthLimit(200)
                    .clickHandler(NetworkSwitchMachine.this::handleDisplayClick));
            root.addChild(screen);
            return root;
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
            LDLib2VoidingModeFancyConfigurator.attachConfigurators(configuratorPanel, NetworkSwitchMachine.this);
            LDLib2BatchModeFancyConfigurator.attachConfigurators(configuratorPanel, NetworkSwitchMachine.this);
            configuratorPanel.attachConfigurators(new LDLib2WorkingEnabledFancyConfigurator(
                    NetworkSwitchMachine.this, holder));
        }

        @Override
        public void attachTooltips(LDLib2FancyTooltipsPanelElement tooltipsPanel) {
            for (IMultiPart part : getParts()) {
                if (part instanceof IMaintenanceMachine maintenanceMachine) {
                    maintenanceMachine.attachLDLib2MaintenanceTooltips(tooltipsPanel);
                }
            }
        }

        @Override
        public List<LDLib2FancyUIProvider> getSubTabs() {
            return partPages;
        }

        @Override
        public List<Component> getTabTooltips() {
            return List.of(Component.translatable(getDefinition().getDescriptionId()));
        }
    }
}

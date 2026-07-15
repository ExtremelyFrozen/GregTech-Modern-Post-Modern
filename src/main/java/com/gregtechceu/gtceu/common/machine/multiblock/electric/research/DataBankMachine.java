package com.gregtechceu.gtceu.common.machine.multiblock.electric.research;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.IDataAccessMachine;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
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
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.machine.multiblock.part.DataAccessHatchMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DataBankMachine extends WorkableElectricMultiblockMachine
                             implements IControllable, IDataAccessMachine, LDLib2MachineUIProvider,
                             LDLib2FancyActionMachine {

    public static final int EUT_PER_HATCH = GTValues.VA[GTValues.EV];
    public static final int EUT_PER_HATCH_CHAINED = GTValues.VA[GTValues.LuV];

    private IMaintenanceMachine maintenance;
    private EnergyContainerList energyContainer;
    private final List<IDataAccessMachine> dataAccesses = new ArrayList<>();
    private final List<IDataAccessMachine> receivers = new ArrayList<>();
    private final List<IDataAccessMachine> transmitters = new ArrayList<>();
    private boolean isQuerying;

    @Getter
    private int energyUsage = 0;

    @Nullable
    protected TickableSubscription tickSubs;
    private final ConditionalSubscriptionHandler displaySnapshotSubscription;
    @Getter(AccessLevel.PACKAGE)
    @SyncToClient
    private List<Component> displaySnapshot = List.of();

    public DataBankMachine(BlockEntityCreationInfo info) {
        super(info);
        this.energyContainer = EnergyContainerList.EMPTY;
        this.displaySnapshotSubscription = new ConditionalSubscriptionHandler(this, this::refreshDisplaySnapshot,
                this::isFormed);
    }

    @Override
    public void formStructure(String structureName) {
        super.formStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        dataAccesses.clear();
        receivers.clear();
        transmitters.clear();
        List<IEnergyContainer> energyContainers = new ArrayList<>();
        Long2ObjectMap<IO> ioMap = getMultiblockState(DEFAULT_STRUCTURE).getMatchContext().getOrDefault("ioMap",
                Long2ObjectMaps.emptyMap());
        for (IMultiPart part : getParts()) {
            IO io = ioMap.getOrDefault(part.self().getBlockPos().asLong(), IO.BOTH);
            var block = part.self().getBlockState().getBlock();
            if (part instanceof IDataAccessMachine dataAccessMachine) {
                if (PartAbility.DATA_ACCESS.isApplicable(block)) {
                    dataAccesses.add(dataAccessMachine);
                } else if (PartAbility.OPTICAL_DATA_RECEPTION.isApplicable(block)) {
                    receivers.add(dataAccessMachine);
                } else if (PartAbility.OPTICAL_DATA_TRANSMISSION.isApplicable(block)) {
                    transmitters.add(dataAccessMachine);
                }
            }
            if (part instanceof IMaintenanceMachine maintenanceMachine) {
                this.maintenance = maintenanceMachine;
            }
            if (io == IO.NONE || io == IO.OUT) continue;
            var handlerLists = part.getRecipeHandlers();
            for (var handlerList : handlerLists) {
                if (!handlerList.isValid(io)) continue;
                handlerList.getCapability(EURecipeCapability.CAP).stream()
                        .filter(IEnergyContainer.class::isInstance)
                        .map(IEnergyContainer.class::cast)
                        .forEach(energyContainers::add);
            }
        }
        this.energyContainer = new EnergyContainerList(energyContainers);
        this.energyUsage = calculateEnergyUsage();

        if (this.maintenance == null) {
            invalidateStructure(structureName);
            return;
        }
        updateTickSubscription();
        notifyListeners();
        if (!isRemote()) {
            refreshDisplaySnapshot();
            displaySnapshotSubscription.updateSubscription();
        }
    }

    protected int calculateEnergyUsage() {
        int receivers = 0;
        int transmitters = 0;
        int regulars = 0;
        for (var part : this.getParts()) {
            var block = part.self().getBlockState().getBlock();
            if (PartAbility.OPTICAL_DATA_RECEPTION.isApplicable(block)) {
                ++receivers;
            }
            if (PartAbility.OPTICAL_DATA_TRANSMISSION.isApplicable(block)) {
                ++transmitters;
            }
            if (PartAbility.DATA_ACCESS.isApplicable(block)) {
                ++regulars;
            }
        }

        int dataHatches = receivers + transmitters + regulars;
        int eutPerHatch = receivers > 0 ? EUT_PER_HATCH_CHAINED : EUT_PER_HATCH;
        return eutPerHatch * dataHatches;
    }

    @Override
    public void invalidateStructure(String structureName) {
        super.invalidateStructure(structureName);
        if (DEFAULT_STRUCTURE.equals(structureName)) {
            notifyListeners();
            this.energyContainer = EnergyContainerList.EMPTY;
            this.energyUsage = 0;
            this.maintenance = null;
            this.dataAccesses.clear();
            this.receivers.clear();
            this.transmitters.clear();
            updateTickSubscription();
            if (!isRemote()) {
                refreshDisplaySnapshot();
                displaySnapshotSubscription.updateSubscription();
            }
        }
    }

    @Override
    public boolean isRecipeAvailable(@NotNull GTRecipeType recipeType, @NotNull ResourceLocation recipeId) {
        if (isQuerying) return false;
        isQuerying = true;
        try {
            return queryRecipe(recipeType, recipeId);
        } finally {
            isQuerying = false;
        }
    }

    private boolean queryRecipe(@NotNull GTRecipeType recipeType, @NotNull ResourceLocation recipeId) {
        if (!getWorkLogic().isWorking()) {
            return false;
        }
        for (IDataAccessMachine dataAccess : dataAccesses) {
            if (dataAccess.isRecipeAvailable(recipeType, recipeId)) {
                return true;
            }
        }
        for (IDataAccessMachine receiver : receivers) {
            if (receiver.isRecipeAvailable(recipeType, recipeId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void notifyListeners() {
        if (isQuerying) return;
        isQuerying = true;
        try {
            for (IDataAccessMachine transmitter : transmitters) {
                transmitter.notifyListeners();
            }
        } finally {
            isQuerying = false;
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
        clearDisplayRuntimeState();
    }

    @Override
    public void onPartUnload() {
        super.onPartUnload();
        clearDisplayRuntimeState();
    }

    private void clearDisplayRuntimeState() {
        if (tickSubs != null) {
            tickSubs.unsubscribe();
            tickSubs = null;
        }
        displaySnapshot = List.of();
        displaySnapshotSubscription.unsubscribe();
    }

    protected void updateTickSubscription() {
        if (isFormed) {
            tickSubs = subscribeServerTick(tickSubs, this::tick);
        } else if (tickSubs != null) {
            tickSubs.unsubscribe();
            tickSubs = null;
        }
    }

    public void tick() {
        boolean wasProviding = getWorkLogic().isWorking();
        int energyToConsume = this.getEnergyUsage();
        boolean hasMaintenance = ConfigHolder.INSTANCE.machines.enableMaintenance && this.maintenance != null;
        if (hasMaintenance) {
            // 10% more energy per maintenance problem
            energyToConsume += maintenance.getNumMaintenanceProblems() * energyToConsume / 10;
        }

        if (getWorkLogic().isWaiting() && energyContainer.getInputPerSec() > 19L * energyToConsume) {
            getWorkLogic().setStatus(WorkLogic.Status.IDLE);
        }

        if (this.energyContainer.getEnergyStored() >= energyToConsume) {
            if (!getWorkLogic().isWaiting()) {
                long consumed = this.energyContainer.removeEnergy(energyToConsume);
                if (consumed == energyToConsume) {
                    getWorkLogic().setStatus(WorkLogic.Status.WORKING);
                } else {
                    getWorkLogic().setWaiting(Component.translatable("gtpm.recipe_logic.insufficient_in")
                            .append(": ").append(EURecipeCapability.CAP.getName()));
                }
            }
        } else {
            getWorkLogic().setWaiting(Component.translatable("gtpm.recipe_logic.insufficient_in").append(": ")
                    .append(EURecipeCapability.CAP.getName()));
        }
        if (wasProviding != getWorkLogic().isWorking()) {
            notifyListeners();
        }
        refreshDisplaySnapshot();
        updateTickSubscription();
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        super.setWorkingEnabled(isWorkingAllowed);
        if (!isRemote()) {
            refreshDisplaySnapshot();
        }
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        textList.addAll(displaySnapshot);
    }

    void refreshDisplaySnapshot() {
        DisplayState state = captureDisplayState(isFormed(), isWorkingEnabled(), isActive(), getEnergyUsage());
        List<Component> nextSnapshot = createDisplaySnapshot(state);
        if (!displaySnapshot.equals(nextSnapshot)) {
            displaySnapshot = nextSnapshot;
        }
    }

    static DisplayState captureDisplayState(boolean formed, boolean workingEnabled, boolean active, int energyUsage) {
        return new DisplayState(formed, workingEnabled, active, energyUsage);
    }

    static List<Component> createDisplaySnapshot(DisplayState state) {
        List<Component> text = new ArrayList<>(2);
        MultiblockDisplayText.builder(text, state.formed())
                .setWorkingStatus(true, state.active() && state.workingEnabled())
                .setWorkingStatusKeys(
                        "gtpm.multiblock.idling",
                        "gtpm.multiblock.idling",
                        "gtpm.multiblock.data_bank.providing")
                .addEnergyUsageExactLine(state.energyUsage())
                .addWorkingStatusLine();
        return List.copyOf(text);
    }

    record DisplayState(boolean formed, boolean workingEnabled, boolean active, int energyUsage) {}

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        LDLib2FancyUIProvider page = createLDLib2Page(player, holder);
        return UI.of(new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight()));
    }

    LDLib2FancyUIProvider createLDLib2Page(Player player, MachineUIHolder holder) {
        requireMatchingHolder(holder);
        return new DataBankFancyPage(player, holder);
    }

    private void requireMatchingHolder(MachineUIHolder holder) {
        if (holder.getMachine() != this) {
            throw new IllegalArgumentException("Data Bank UI holder must resolve the opened controller.");
        }
    }

    private final class DataBankFancyPage implements LDLib2FancyUIProvider {

        private static final int PAGE_WIDTH = 190;
        private static final int PAGE_HEIGHT = 125;

        private final MachineUIHolder holder;
        private final LDLib2DirectionalFancyConfigurator directionalPage;
        private final List<LDLib2FancyUIProvider> partPages;

        private DataBankFancyPage(Player player, MachineUIHolder holder) {
            requireMatchingHolder(holder);
            this.holder = holder;
            this.directionalPage = new LDLib2DirectionalFancyConfigurator(DataBankMachine.this, player, holder);

            List<LDLib2FancyUIProvider> pages = new ArrayList<>();
            for (IMultiPart part : getParts()) {
                if (part instanceof DataAccessHatchMachine hatch && hatch.isCreative()) {
                    continue;
                }
                var block = part.self().getBlockState().getBlock();
                boolean isDataPart = PartAbility.DATA_ACCESS.isApplicable(block) ||
                        PartAbility.OPTICAL_DATA_RECEPTION.isApplicable(block) ||
                        PartAbility.OPTICAL_DATA_TRANSMISSION.isApplicable(block);
                if (!isDataPart) {
                    continue;
                }
                if (!(part instanceof LDLib2FancyPartUIProvider pageProvider)) {
                    throw new IllegalStateException("Data Bank data part has no LDLib2 Fancy page: " +
                            part.self().getDefinition().getId());
                }
                MachineUIHolder partHolder = new MachineUIHolderContext(player, part.self());
                pages.add(pageProvider.createLDLib2FancyPage(player, partHolder));
            }
            this.partPages = List.copyOf(pages);
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            if (holder.getMachine() != DataBankMachine.this) {
                throw new IllegalStateException("Data Bank page holder no longer resolves its controller.");
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
            screen.addScrollViewChild(new GTComponentPanelElement(4, 17, DataBankMachine.this::addDisplayText)
                    .setMaxWidthLimit(150)
                    .clickHandler(DataBankMachine.this::handleDisplayClick));
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
            LDLib2VoidingModeFancyConfigurator.attachConfigurators(configuratorPanel, DataBankMachine.this);
            LDLib2BatchModeFancyConfigurator.attachConfigurators(configuratorPanel, DataBankMachine.this);
            configuratorPanel.attachConfigurators(new LDLib2WorkingEnabledFancyConfigurator(
                    DataBankMachine.this, holder));
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

    /*
     * @Override
     * protected void addWarningText(List<Component> textList) {
     * MultiblockDisplayText.builder(textList, isFormed(), false)
     * .addLowPowerLine(hasNotEnoughEnergy)
     * .addMaintenanceProblemLines(maintenance.getMaintenanceProblems());
     * }
     */

    @Override
    public int getProgress() {
        return 0;
    }

    @Override
    public int getMaxProgress() {
        return 0;
    }
}

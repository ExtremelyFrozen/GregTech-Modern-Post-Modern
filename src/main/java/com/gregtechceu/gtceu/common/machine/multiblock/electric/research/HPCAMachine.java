package com.gregtechceu.gtceu.common.machine.multiblock.electric.research;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.*;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.computation.ComputationProducer;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTProgressBarElement;
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
import com.gregtechceu.gtceu.api.gui.texture.ProgressTexture;
import com.gregtechceu.gtceu.api.gui.util.TimedProgressSupplier;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
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
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.WorkLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import com.gregtechceu.gtceu.api.sync_system.SyncDataHolder;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.sync_system.managed.ISyncManaged;
import com.gregtechceu.gtceu.api.transfer.fluid.FluidHandlerList;
import com.gregtechceu.gtceu.common.machine.multiblock.part.hpca.HPCAComponentPartMachine;
import com.gregtechceu.gtceu.common.machine.trait.hpca.HPCAComponentTrait;
import com.gregtechceu.gtceu.common.machine.trait.hpca.HPCAComputationProviderTrait;
import com.gregtechceu.gtceu.common.machine.trait.hpca.HPCACoolantProviderTrait;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.DoubleSupplier;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

import static com.gregtechceu.gtceu.data.recipe.CustomTags.HPCA_COOLANTS;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class HPCAMachine extends WorkableElectricMultiblockMachine
                         implements IOpticalComputationProvider, IControllable, ComputationProducer,
                         LDLib2MachineUIProvider, LDLib2FancyActionMachine {

    private static final double IDLE_TEMPERATURE = 200;
    private static final double DAMAGE_TEMPERATURE = 1000;

    private IMaintenanceMachine maintenance;
    private EnergyContainerList energyContainer;
    private IFluidHandler coolantHandler;
    @Getter(AccessLevel.PACKAGE)
    @SaveField
    @SyncToClient
    private final HPCAGridHandler hpcaHandler;
    @Getter(AccessLevel.PACKAGE)
    @SyncToClient
    private List<Component> hpcaDisplaySnapshot = List.of();
    @Getter(AccessLevel.PACKAGE)
    @SyncToClient
    private List<Component> hpcaInfoSnapshot = List.of();

    private boolean hasNotEnoughEnergy;

    @SaveField
    private double temperature = IDLE_TEMPERATURE; // start at idle temperature

    private final TimedProgressSupplier progressSupplier;

    @Nullable
    protected TickableSubscription tickSubs;

    public HPCAMachine(BlockEntityCreationInfo info) {
        super(info);
        this.energyContainer = EnergyContainerList.EMPTY;
        this.progressSupplier = new TimedProgressSupplier(200, 47, false);
        this.hpcaHandler = new HPCAGridHandler(this);
    }

    @Override
    public void formStructure(String structureName) {
        super.formStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        List<IEnergyContainer> energyContainers = new ArrayList<>();
        List<IFluidHandler> coolantContainers = new ArrayList<>();
        List<HPCAComponentTrait> componentTraits = new ArrayList<>();
        Long2ObjectMap<IO> ioMap = getMultiblockState(DEFAULT_STRUCTURE).getMatchContext().getOrDefault("ioMap",
                Long2ObjectMaps.emptyMap());
        for (IMultiPart part : getParts()) {
            IO io = ioMap.getOrDefault(part.self().getBlockPos().asLong(), IO.BOTH);

            componentTraits.addAll(part.self().getTraits(HPCAComponentTrait.TYPE));

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
                handlerList.getCapability(FluidRecipeCapability.CAP).stream()
                        .filter(IFluidHandler.class::isInstance)
                        .map(IFluidHandler.class::cast)
                        .forEach(coolantContainers::add);
            }
        }
        this.energyContainer = new EnergyContainerList(energyContainers);
        this.coolantHandler = new FluidHandlerList(coolantContainers);
        this.hpcaHandler.onStructureForm(componentTraits);
        if (!isRemote()) {
            refreshHPCASnapshots();
        }

        scheduleForNextServerTick(this::updateTickSubscription);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            refreshHPCASnapshots();
        }
        scheduleForNextServerTick(this::updateTickSubscription);
    }

    @Override
    public void onUnload() {
        super.onUnload();
        clearHPCASnapshots();
        if (tickSubs != null) {
            tickSubs.unsubscribe();
            tickSubs = null;
        }
    }

    protected void updateTickSubscription() {
        if (isFormed) {
            tickSubs = subscribeServerTick(tickSubs, this::tick);
        } else if (tickSubs != null) {
            tickSubs.unsubscribe();
            tickSubs = null;
        }
    }

    @Override
    public void invalidateStructure(String structureName) {
        super.invalidateStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        this.updateActive(false);
        this.energyContainer = EnergyContainerList.EMPTY;
        this.hpcaHandler.onStructureInvalidate();
        if (!isRemote()) {
            refreshHPCASnapshots();
        }
    }

    @Override
    public int requestCWUt(int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        return isActive() && isWorkingEnabled() && !hasNotEnoughEnergy ? hpcaHandler.allocateCWUt(cwut, simulate) : 0;
    }

    @Override
    public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        return isActive() && isWorkingEnabled() ? hpcaHandler.getMaxCWUt() : 0;
    }

    @Override
    public boolean canBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        // don't show a problem if the structure is not yet formed
        return !isFormed() || hpcaHandler.hasHPCABridge();
    }

    @Override
    public int getOfferedCWUt() {
        return isActive() && isWorkingEnabled() && !hasNotEnoughEnergy ? hpcaHandler.getMaxCWUt() : 0;
    }

    @Override
    public void applyProducedCWUt(int allocatedCWUt) {
        if (allocatedCWUt > 0) {
            hpcaHandler.setAllocatedCWUt(allocatedCWUt);
        }
    }

    @Override
    public boolean canBridgeComputation() {
        return hpcaHandler.hasHPCABridge();
    }

    public void tick() {
        if (isWorkingEnabled()) consumeEnergy();
        if (isActive()) {
            // forcibly use active coolers at full rate if temperature is half-way to damaging temperature
            double midpoint = (DAMAGE_TEMPERATURE - IDLE_TEMPERATURE) / 2;
            double temperatureChange = hpcaHandler.calculateTemperatureChange(coolantHandler, temperature >= midpoint) /
                    2.0;
            if (temperature + temperatureChange <= IDLE_TEMPERATURE) {
                temperature = IDLE_TEMPERATURE;
            } else {
                temperature += temperatureChange;
            }
            if (temperature >= DAMAGE_TEMPERATURE) {
                hpcaHandler.attemptDamageHPCA();
            }
            hpcaHandler.tick();
        } else {
            hpcaHandler.clearComputationCache();
            // passively cool (slowly) if not active
            temperature = Math.max(IDLE_TEMPERATURE, temperature - 0.25);
        }
        refreshHPCASnapshots();
        this.updateActive(this.getEnergyContainer().getEnergyStored() > 0);
    }

    /** Refreshes the server-owned text snapshots consumed by the LDLib2 controller page. */
    void refreshHPCASnapshots() {
        List<Component> nextDisplaySnapshot = createHPCADisplaySnapshot();
        if (!hpcaDisplaySnapshot.equals(nextDisplaySnapshot)) {
            hpcaDisplaySnapshot = nextDisplaySnapshot;
        }
        List<Component> nextSnapshot = hpcaHandler.createInfoSnapshot();
        if (!hpcaInfoSnapshot.equals(nextSnapshot)) {
            hpcaInfoSnapshot = nextSnapshot;
        }
    }

    private void clearHPCASnapshots() {
        hpcaDisplaySnapshot = List.of();
        hpcaInfoSnapshot = List.of();
    }

    private void updateActive(boolean active) {
        for (var part : getParts()) {
            part.self().getTraitOptional(HPCAComponentTrait.TYPE).ifPresent(t -> t.setActive(active));
        }
    }

    private void consumeEnergy() {
        long energyToConsume = hpcaHandler.getCurrentEUt();
        boolean hasMaintenance = ConfigHolder.INSTANCE.machines.enableMaintenance && this.maintenance != null;
        if (hasMaintenance) {
            // 10% more energy per maintenance problem
            energyToConsume += maintenance.getNumMaintenanceProblems() * energyToConsume / 10;
        }

        if (this.hasNotEnoughEnergy && energyContainer.getInputPerSec() > 19L * energyToConsume) {
            this.hasNotEnoughEnergy = false;
        }

        if (this.energyContainer.getEnergyStored() >= energyToConsume) {
            if (!hasNotEnoughEnergy) {
                long consumed = this.energyContainer.removeEnergy(energyToConsume);
                if (consumed == energyToConsume) {
                    getWorkLogic().setStatus(WorkLogic.Status.WORKING);
                } else {
                    this.hasNotEnoughEnergy = true;
                    getWorkLogic().setWaiting(Component.translatable("gtpm.recipe_logic.insufficient_in")
                            .append(": ").append(EURecipeCapability.CAP.getName()));
                }
            }
        } else {
            this.hasNotEnoughEnergy = true;
            getWorkLogic().setWaiting(Component.translatable("gtpm.recipe_logic.insufficient_in")
                    .append(": ").append(EURecipeCapability.CAP.getName()));
        }
    }

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        requireMatchingHolder(holder);
        HPCAControllerFancyPage page = new HPCAControllerFancyPage(player, holder);
        return UI.of(new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight()));
    }

    private void requireMatchingHolder(MachineUIHolder holder) {
        if (holder.getMachine() != this) {
            throw new IllegalArgumentException("HPCA UI holder must resolve the opened controller.");
        }
    }

    private void gatherClientComponentGrid() {
        if (!isRemote()) {
            return;
        }
        if (isFormed) {
            hpcaHandler.tryGatherClientComponents(getLevel(), getBlockPos(), getFrontFacing(), getUpwardsFacing(),
                    isFlipped);
        } else {
            hpcaHandler.clearClientComponents();
        }
    }

    private final class HPCAControllerFancyPage implements LDLib2FancyUIProvider {

        private static final int PAGE_WIDTH = 190;
        private static final int PAGE_HEIGHT = 125;

        private final MachineUIHolder holder;
        private final LDLib2DirectionalFancyConfigurator directionalPage;
        private final List<LDLib2FancyUIProvider> partPages;

        private HPCAControllerFancyPage(Player player, MachineUIHolder holder) {
            requireMatchingHolder(holder);
            this.holder = holder;
            this.directionalPage = new LDLib2DirectionalFancyConfigurator(HPCAMachine.this, player, holder);

            List<LDLib2FancyUIProvider> pages = new ArrayList<>();
            for (IMultiPart part : getParts()) {
                if (!(part instanceof LDLib2FancyPartUIProvider pageProvider)) {
                    throw new IllegalStateException("HPCA part has no LDLib2 Fancy page: " +
                            part.self().getDefinition().getId());
                }
                MachineUIHolder partHolder = new MachineUIHolderContext(player, part.self());
                pages.add(pageProvider.createLDLib2FancyPage(player, partHolder));
            }
            this.partPages = List.copyOf(pages);
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            if (holder.getMachine() != HPCAMachine.this) {
                throw new IllegalStateException("HPCA page holder no longer resolves its controller.");
            }
            gatherClientComponentGrid();

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
            screen.addScrollViewChild(new GTComponentPanelElement(4, 17, HPCAMachine.this::addDisplayText)
                    .setMaxWidthLimit(150)
                    .clickHandler(HPCAMachine.this::handleDisplayClick));
            root.addChild(screen);

            ProgressTexture progressTexture = GuiTextures.progressBar(GuiTextures.HPCA_COMPONENT_OUTLINE);
            root.addChild(new HPCAStatusGridElement(
                    () -> hpcaHandler.cachedCWUt > 0 ? progressSupplier.getAsDouble() : 0,
                    () -> hpcaInfoSnapshot,
                    hpcaHandler::getComponentTexture,
                    progressTexture));
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
            LDLib2VoidingModeFancyConfigurator.attachConfigurators(configuratorPanel, HPCAMachine.this);
            LDLib2BatchModeFancyConfigurator.attachConfigurators(configuratorPanel, HPCAMachine.this);
            configuratorPanel.attachConfigurators(new LDLib2WorkingEnabledFancyConfigurator(
                    HPCAMachine.this, holder));
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

    private static final class HPCAStatusGridElement extends UIElement {

        private static final int GRID_SIZE = 3;
        private static final int ICON_SIZE = 13;
        private static final int ICON_STEP = 15;

        private final Supplier<List<Component>> tooltipSupplier;
        private final IntFunction<IGuiTexture> componentTextureSupplier;
        private final List<GTImageElement> componentImages = new ArrayList<>(GRID_SIZE * GRID_SIZE);

        private HPCAStatusGridElement(DoubleSupplier progressSupplier,
                                      Supplier<List<Component>> tooltipSupplier,
                                      IntFunction<IGuiTexture> componentTextureSupplier,
                                      ProgressTexture progressTexture) {
            this.tooltipSupplier = tooltipSupplier;
            this.componentTextureSupplier = componentTextureSupplier;
            UITemplate.setLDLib2Bounds(this, 74, 57, 47, 47);
            setId("hpca_status_grid");
            addEventListener(UIEvents.HOVER_TOOLTIPS, this::addInfoTooltip);

            GTProgressBarElement progress = new GTProgressBarElement(progressSupplier);
            progress.setProgressTexture(progressTexture.getEmptyBarArea(), progressTexture.getFilledBarArea());
            progress.setFillDirection(FillDirection.LEFT_TO_RIGHT);
            UITemplate.setLDLib2Bounds(progress, 0, 0, 47, 47);
            addChild(progress);

            for (int row = 0; row < GRID_SIZE; row++) {
                for (int column = 0; column < GRID_SIZE; column++) {
                    int index = row * GRID_SIZE + column;
                    GTImageElement image = new GTImageElement(
                            2 + ICON_STEP * column,
                            2 + ICON_STEP * row,
                            ICON_SIZE,
                            ICON_SIZE,
                            componentTextureSupplier.apply(index));
                    componentImages.add(image);
                    addChild(image);
                }
            }
        }

        @Override
        public void screenTick() {
            for (int index = 0; index < componentImages.size(); index++) {
                componentImages.get(index).setTexture(componentTextureSupplier.apply(index));
            }
            super.screenTick();
        }

        private void addInfoTooltip(UIEvent event) {
            List<Component> tooltips = tooltipSupplier.get();
            if (!tooltips.isEmpty()) {
                event.hoverTooltips = new HoverTooltips(tooltips, null, null, null);
            }
        }
    }

    @Override
    public void addDisplayText(List<Component> textList) {
        textList.addAll(hpcaDisplaySnapshot);
    }

    private List<Component> createHPCADisplaySnapshot() {
        List<Component> snapshot = new ArrayList<>();
        MultiblockDisplayText.builder(snapshot, isFormed())
                .setWorkingStatus(true, hpcaHandler.cachedCWUt > 0) // transform into two-state system for display
                .setWorkingStatusKeys(
                        "gtpm.multiblock.idling",
                        "gtpm.multiblock.idling",
                        "gtpm.multiblock.data_bank.providing")
                .addCustom(tl -> {
                    if (isFormed()) {
                        // Energy Usage
                        tl.add(Component.translatable(
                                "gtpm.multiblock.hpca.energy",
                                FormattingUtil.formatNumbers(hpcaHandler.cachedEUt),
                                FormattingUtil.formatNumbers(hpcaHandler.getMaxEUt()),
                                GTValues.VNF[GTUtil.getTierByVoltage(hpcaHandler.getMaxEUt())])
                                .withStyle(ChatFormatting.GRAY));

                        // Provided Computation
                        Component cwutInfo = Component.literal(
                                hpcaHandler.cachedCWUt + " / " + hpcaHandler.getMaxCWUt() + " CWU/t")
                                .withStyle(ChatFormatting.AQUA);
                        tl.add(Component.translatable(
                                "gtpm.multiblock.hpca.computation",
                                cwutInfo).withStyle(ChatFormatting.GRAY));
                    }
                })
                .addWorkingStatusLine();
        return List.copyOf(snapshot);
    }

    private ChatFormatting getDisplayTemperatureColor() {
        if (temperature < 500) {
            return ChatFormatting.GREEN;
        } else if (temperature < 750) {
            return ChatFormatting.YELLOW;
        }
        return ChatFormatting.RED;
    }

    /*
     * @Override
     * protected void addWarningText(List<Component> textList) {
     * MultiblockDisplayText.builder(textList, isFormed(), false)
     * .addLowPowerLine(hasNotEnoughEnergy)
     * .addCustom(tl -> {
     * if (isStructureFormed()) {
     * if (temperature > 500) {
     * // Temperature warning
     * tl.add(TextComponentUtil.translationWithColor(
     * TextFormatting.YELLOW,
     * "gtceu.multiblock.hpca.warning_temperature"));
     *
     * // Active cooler overdrive warning
     * tl.add(TextComponentUtil.translationWithColor(
     * TextFormatting.GRAY,
     * "gtceu.multiblock.hpca.warning_temperature_active_cool"));
     * }
     *
     * // Structure warnings
     * hpcaHandler.addWarnings(tl);
     * }
     * })
     * .addMaintenanceProblemLines(getMaintenanceProblems());
     * }
     *
     * @Override
     * protected void addErrorText(List<Component> textList) {
     * super.addErrorText(textList);
     * if (isFormed()) {
     * if (temperature > 1000) {
     * textList.add(Component.translatable("gtceu.multiblock.hpca.error_temperature").withStyle(ChatFormatting.RED));
     * }
     * hpcaHandler.addErrors(textList);
     * }
     * }
     *
     * @Override
     * public void addBarHoverText(List<Component> hoverList, int index) {
     * if (index == 0) {
     * Component cwutInfo = Component.literal(
     * hpcaHandler.cachedCWUt + " / " + hpcaHandler.getMaxCWUt() + " CWU/t").withStyle(ChatFormatting.AQUA);
     * hoverList.add(Component.translatable(
     * "gtceu.multiblock.hpca.computation",
     * cwutInfo).withStyle(ChatFormatting.GRAY));
     * } else {
     * Component tempInfo = Component.literal(,
     * Math.round(temperature / 10.0D) + "°C").withStyle(getDisplayTemperatureColor());
     * hoverList.add(TextComponentUtil.translationWithColor(
     * TextFormatting.GRAY,
     * "gtceu.multiblock.hpca.temperature",
     * tempInfo));
     * }
     * }
     */

    // Handles the logic of this structure's specific HPCA component grid
    public static class HPCAGridHandler implements ISyncManaged {

        @Getter
        private final SyncDataHolder syncDataHolder = new SyncDataHolder(this);

        @Nullable // for testing
        private final HPCAMachine controller;

        // structure info
        private final List<HPCAComponentTrait> components = new ObjectArrayList<>();
        private final Set<HPCACoolantProviderTrait> coolantProviders = new ObjectOpenHashSet<>();
        private final Set<HPCAComputationProviderTrait> computationProviders = new ObjectOpenHashSet<>();
        private int numBridges;

        // transaction info
        /** How much CWU/t is currently allocated for this tick. */
        @Getter
        private int allocatedCWUt;

        // cached gui info
        // holding these values past the computation clear because GUI is too "late" to read the state in time
        @SyncToClient
        private long cachedEUt;
        @SyncToClient
        private int cachedCWUt;

        public HPCAGridHandler(@Nullable HPCAMachine controller) {
            this.controller = controller;
        }

        @Override
        public @Nullable ISyncManaged getParentSyncObject() {
            return controller;
        }

        public void onStructureForm(Collection<HPCAComponentTrait> components) {
            reset();
            for (var component : components) {
                this.components.add(component);
                if (component instanceof HPCACoolantProviderTrait coolantProvider) {
                    this.coolantProviders.add(coolantProvider);
                }
                if (component instanceof HPCAComputationProviderTrait computationProvider) {
                    this.computationProviders.add(computationProvider);
                }
                if (component.allowBridging()) {
                    this.numBridges++;
                }
            }
        }

        private void onStructureInvalidate() {
            reset();
        }

        private void reset() {
            clearComputationCache();
            components.clear();
            coolantProviders.clear();
            computationProviders.clear();
            numBridges = 0;
        }

        private void clearComputationCache() {
            allocatedCWUt = 0;
            if (cachedCWUt != 0) {
                cachedCWUt = 0;
                syncDataHolder.markClientSyncFieldDirty("cachedCWUt");
            }
        }

        public void tick() {
            if (cachedCWUt != allocatedCWUt) {
                cachedCWUt = allocatedCWUt;
                syncDataHolder.markClientSyncFieldDirty("cachedCWUt");
            }
            cachedEUt = getCurrentEUt();
            syncDataHolder.markClientSyncFieldDirty("cachedEUt");
            if (allocatedCWUt != 0) {
                allocatedCWUt = 0;
            }
        }

        /**
         * Calculate the temperature differential this tick given active computation and consume coolant.
         *
         * @param coolantTank         The tank to drain coolant from.
         * @param forceCoolWithActive Whether active coolers should forcibly cool even if temperature is already
         *                            decreasing due to passive coolers. Used when the HPCA is running very hot.
         * @return The temperature change, can be positive or negative.
         */
        public double calculateTemperatureChange(IFluidHandler coolantTank, boolean forceCoolWithActive) {
            // calculate temperature increase
            int maxCWUt = Math.max(1, getMaxCWUt()); // avoids dividing by 0 and the behavior is no different
            int maxCoolingDemand = getMaxCoolingDemand();

            // temperature increase is proportional to the amount of actively used computation
            // a * (b / c)
            int temperatureIncrease = (int) Math.round(1.0 * maxCoolingDemand * allocatedCWUt / maxCWUt);

            // calculate temperature decrease
            long maxPassiveCooling = 0;
            long maxActiveCooling = 0;
            int maxCoolantDrain = 0;

            for (var coolantProvider : coolantProviders) {
                if (coolantProvider.isActiveCooler()) {
                    maxActiveCooling += coolantProvider.getCoolingAmount();
                    maxCoolantDrain += coolantProvider.getMaxCoolantPerTick();
                } else {
                    maxPassiveCooling += coolantProvider.getCoolingAmount();
                }
            }

            double temperatureChange = temperatureIncrease - maxPassiveCooling;
            // quick exit if no active cooling/coolant drain is present
            if (maxActiveCooling == 0 && maxCoolantDrain == 0) {
                return temperatureChange;
            }
            if (forceCoolWithActive || maxActiveCooling <= temperatureChange) {
                // try to fully utilize active coolers
                int remainingCoolant = maxCoolantDrain;
                for (var fluid : BuiltInRegistries.FLUID.getTagOrEmpty(HPCA_COOLANTS)) {
                    FluidStack drained = GTTransferUtils.drainFluidAccountNotifiableList(coolantTank,
                            new FluidStack(fluid, remainingCoolant), IFluidHandler.FluidAction.EXECUTE);
                    remainingCoolant -= drained.getAmount();
                    if (remainingCoolant <= 0) break;
                }
                if (remainingCoolant <= 0) {
                    // coolant requirement was fully met
                    temperatureChange -= maxActiveCooling;
                } else {
                    // coolant requirement was only partially met, cool proportional to fluid amount drained
                    // a * (b / c)
                    int coolantDrained = maxCoolantDrain - remainingCoolant;
                    temperatureChange -= maxActiveCooling * (1.0 * coolantDrained / maxCoolantDrain);
                }
            } else if (temperatureChange > 0) {
                // try to partially utilize active coolers to stabilize to zero
                double temperatureToDecrease = Math.min(temperatureChange, maxActiveCooling);
                int coolantToDrain = Math.max(1, (int) (maxCoolantDrain * (temperatureToDecrease / maxActiveCooling)));
                int remainingCoolant = coolantToDrain;
                for (var fluid : BuiltInRegistries.FLUID.getTagOrEmpty(HPCA_COOLANTS)) {
                    FluidStack drained = GTTransferUtils.drainFluidAccountNotifiableList(coolantTank,
                            new FluidStack(fluid, remainingCoolant), IFluidHandler.FluidAction.EXECUTE);
                    remainingCoolant -= drained.getAmount();
                    if (remainingCoolant <= 0) break;
                }
                if (remainingCoolant <= 0) {
                    // successfully stabilized to zero
                    return 0;
                } else {
                    // coolant requirement was only partially met, cool proportional to fluid amount drained
                    // a * (b / c)
                    int coolantDrained = (coolantToDrain - remainingCoolant);
                    temperatureChange -= temperatureToDecrease * (1.0 * coolantDrained / coolantToDrain);
                }
            }
            return temperatureChange;
        }

        /**
         * Roll a 1/200 chance to damage a HPCA component marked as damageable. Randomly selects the component.
         * If called every tick, this succeeds on average once every 10 seconds.
         */
        public void attemptDamageHPCA() {
            // 1% chance each tick to damage a component if running too hot
            if (GTValues.RNG.nextInt(200) == 0) {
                // randomize which component is actually damaged
                List<HPCAComponentTrait> candidates = new ArrayList<>();
                for (var component : components) {
                    if (component.canBeDamaged()) {
                        candidates.add(component);
                    }
                }
                if (!candidates.isEmpty()) {
                    candidates.get(GTValues.RNG.nextInt(candidates.size())).setDamaged(true);
                }
            }
        }

        /** Allocate computation on a given request. Allocates for one tick. */
        public int allocateCWUt(int cwut, boolean simulate) {
            if (cwut == 0) return 0;
            int maxCWUt = getMaxCWUt();
            int availableCWUt = maxCWUt - this.allocatedCWUt;
            int toAllocate = Math.min(cwut, availableCWUt);
            if (!simulate) {
                this.allocatedCWUt += toAllocate;
            }
            return toAllocate;
        }

        public void setAllocatedCWUt(int allocatedCWUt) {
            this.allocatedCWUt = Math.max(0, Math.min(allocatedCWUt, getMaxCWUt()));
        }

        /** The maximum amount of CWUs (Compute Work Units) created per tick. */
        public int getMaxCWUt() {
            int maxCWUt = 0;
            for (var computationProvider : computationProviders) {
                maxCWUt += computationProvider.getCWUPerTick();
            }
            return maxCWUt;
        }

        /** The current EU/t this HPCA should use, considering passive drain, current computation, etc.. */
        public long getCurrentEUt() {
            long maximumCWUt = Math.max(1, getMaxCWUt()); // behavior is no different setting this to 1 if it is 0
            long maximumEUt = getMaxEUt();
            long upkeepEUt = getUpkeepEUt();

            if (maximumEUt == upkeepEUt) {
                return maximumEUt;
            }

            // energy draw is proportional to the amount of actively used computation
            // a + c(b - a) / d
            return upkeepEUt + ((maximumEUt - upkeepEUt) * allocatedCWUt / maximumCWUt);
        }

        /** The amount of EU/t this HPCA uses just to stay on with 0 output computation. */
        public long getUpkeepEUt() {
            long upkeepEUt = 0;
            for (var component : components) {
                upkeepEUt += component.upkeepEUt();
            }
            return upkeepEUt;
        }

        /** The maximum EU/t that this HPCA could ever use with the given configuration. */
        public long getMaxEUt() {
            long maximumEUt = 0;
            for (var component : components) {
                maximumEUt += component.maxEUt();
            }
            return maximumEUt;
        }

        /** Whether this HPCA has a Bridge to allow connecting to other HPCA's */
        public boolean hasHPCABridge() {
            return numBridges > 0;
        }

        /** Whether this HPCA has any cooling providers which are actively cooled. */
        public boolean hasActiveCoolers() {
            for (var coolantProvider : coolantProviders) {
                if (coolantProvider.isActiveCooler()) return true;
            }
            return false;
        }

        /** How much cooling this HPCA can provide. NOT related to coolant fluid consumption. */
        public int getMaxCoolingAmount() {
            int maxCooling = 0;
            for (var coolantProvider : coolantProviders) {
                maxCooling += coolantProvider.getCoolingAmount();
            }
            return maxCooling;
        }

        /** How much cooling this HPCA can require. NOT related to coolant fluid consumption. */
        public int getMaxCoolingDemand() {
            int maxCooling = 0;
            for (var computationProvider : computationProviders) {
                maxCooling += computationProvider.getCoolingPerTick();
            }
            return maxCooling;
        }

        /** How much coolant this HPCA can consume in a tick, in mB/t. */
        public int getMaxCoolantDemand() {
            int maxCoolant = 0;
            for (var coolantProvider : coolantProviders) {
                maxCoolant += coolantProvider.getMaxCoolantPerTick();
            }
            return maxCoolant;
        }

        public void addInfo(List<Component> textList) {
            // Max Computation
            MutableComponent data = Component.literal(Integer.toString(getMaxCWUt())).withStyle(ChatFormatting.AQUA);
            textList.add(Component.translatable("gtpm.multiblock.hpca.info_max_computation", data)
                    .withStyle(ChatFormatting.GRAY));

            // Cooling
            ChatFormatting coolingColor = getMaxCoolingAmount() < getMaxCoolingDemand() ? ChatFormatting.RED :
                    ChatFormatting.GREEN;
            data = Component.literal(Integer.toString(getMaxCoolingDemand())).withStyle(coolingColor);
            textList.add(Component.translatable("gtpm.multiblock.hpca.info_max_cooling_demand", data)
                    .withStyle(ChatFormatting.GRAY));

            data = Component.literal(Integer.toString(getMaxCoolingAmount())).withStyle(coolingColor);
            textList.add(Component.translatable("gtpm.multiblock.hpca.info_max_cooling_available", data)
                    .withStyle(ChatFormatting.GRAY));

            // Coolant Required
            if (getMaxCoolantDemand() > 0) {
                data = Component.translatable("gtpm.universal.liters", getMaxCoolantDemand())
                        .withStyle(ChatFormatting.YELLOW).append(" ");
                Component coolantName = Component.translatable("gtpm.multiblock.hpca.info_coolant_name")
                        .withStyle(ChatFormatting.YELLOW);
                data.append(coolantName);
            } else {
                data = Component.literal("0").withStyle(ChatFormatting.GREEN);
            }
            textList.add(Component.translatable("gtpm.multiblock.hpca.info_max_coolant_required", data)
                    .withStyle(ChatFormatting.GRAY));

            // Bridging
            if (numBridges > 0) {
                textList.add(Component.translatable("gtpm.multiblock.hpca.info_bridging_enabled")
                        .withStyle(ChatFormatting.GREEN));
            } else {
                textList.add(Component.translatable("gtpm.multiblock.hpca.info_bridging_disabled")
                        .withStyle(ChatFormatting.RED));
            }
        }

        /** Captures the authoritative structural statistics sent to the LDLib2 status-grid tooltip. */
        List<Component> createInfoSnapshot() {
            List<Component> snapshot = new ArrayList<>();
            addInfo(snapshot);
            return List.copyOf(snapshot);
        }

        public void addWarnings(List<Component> textList) {
            List<Component> warnings = new ArrayList<>();
            if (numBridges > 1) {
                warnings.add(Component.translatable("gtpm.multiblock.hpca.warning_multiple_bridges")
                        .withStyle(ChatFormatting.GRAY));
            }
            if (computationProviders.isEmpty()) {
                warnings.add(Component.translatable("gtpm.multiblock.hpca.warning_no_computation")
                        .withStyle(ChatFormatting.GRAY));
            }
            if (getMaxCoolingDemand() > getMaxCoolingAmount()) {
                warnings.add(Component.translatable("gtpm.multiblock.hpca.warning_low_cooling")
                        .withStyle(ChatFormatting.GRAY));
            }
            if (!warnings.isEmpty()) {
                textList.add(Component.translatable("gtpm.multiblock.hpca.warning_structure_header")
                        .withStyle(ChatFormatting.YELLOW));
                textList.addAll(warnings);
            }
        }

        public void addErrors(List<Component> textList) {
            if (components.stream().anyMatch(HPCAComponentTrait::isDamaged)) {
                textList.add(
                        Component.translatable("gtpm.multiblock.hpca.error_damaged").withStyle(ChatFormatting.RED));
            }
        }

        public IGuiTexture getComponentTexture(int index) {
            if (components.size() <= index) {
                return GuiTextures.BLANK_TRANSPARENT;
            }
            if (components.get(index).getMachine() instanceof HPCAComponentPartMachine componentPartMachine)
                return componentPartMachine.getComponentIcon();
            return GuiTextures.BLANK_TRANSPARENT;
        }

        public void tryGatherClientComponents(Level world, BlockPos pos, Direction frontFacing,
                                              Direction upwardsFacing, boolean flip) {
            Direction relativeUp = RelativeDirection.UP.getRelative(frontFacing, upwardsFacing, flip);

            if (components.isEmpty()) {
                BlockPos testPos = pos
                        .relative(frontFacing.getOpposite(), 3)
                        .relative(relativeUp, 3);

                for (int i = 0; i < 3; i++) {
                    for (int j = 0; j < 3; j++) {
                        BlockPos tempPos = testPos.relative(frontFacing, j).relative(relativeUp.getOpposite(), i);
                        MetaMachine be = MetaMachine.getMachine(world, tempPos);
                        if (be == null) continue;
                        var trait = be.getTrait(HPCAComponentTrait.TYPE);
                        if (trait != null) {
                            components.add(trait);
                        }
                        // if here without a hatch, something went wrong, better to skip than add a null into the mix.
                    }
                }
            }
        }

        public void clearClientComponents() {
            components.clear();
        }
    }
}

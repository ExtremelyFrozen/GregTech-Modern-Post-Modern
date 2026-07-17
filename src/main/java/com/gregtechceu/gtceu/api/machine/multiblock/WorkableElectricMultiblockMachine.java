package com.gregtechceu.gtceu.api.machine.multiblock;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolderContext;
import com.gregtechceu.gtceu.api.gui.fancy.ConfiguratorPanel;
import com.gregtechceu.gtceu.api.gui.fancy.FancyMachineUIWidget;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTabsElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTooltipsPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.TooltipsPanel;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2BatchModeFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2DirectionalFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2MachineModeFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2VoidingModeFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2WorkingEnabledFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IVoidable;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.BatchModeMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IDisplayUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifierList;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldNormalizer;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ParallelHatchPartMachine;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class WorkableElectricMultiblockMachine extends WorkableMultiblockMachine implements IFancyUIMachine,
                                               BatchModeMachine, IDisplayUIMachine, ITieredMachine,
                                               IOverclockMachine, LDLib2MachineUIProvider,
                                               LDLib2FancyActionMachine {

    // runtime
    protected @Nullable EnergyContainerList energyContainer;
    @Getter
    protected int tier;
    @SaveField
    @SyncBoth
    @Getter
    protected boolean batchEnabled;
    private final ConditionalSubscriptionHandler ldlib2DisplaySnapshotSubscription;
    @Getter(AccessLevel.PACKAGE)
    @SyncToClient
    private List<Component> ldlib2DisplaySnapshot = List.of();
    private final Map<WorkableElectricMultiblockFancyPage, LDLib2DisplayOpening> ldlib2DisplayOpenings = new IdentityHashMap<>();

    public WorkableElectricMultiblockMachine(BlockEntityCreationInfo info, RecipeLogic recipeLogic) {
        super(info, recipeLogic);
        ldlib2DisplaySnapshotSubscription = new ConditionalSubscriptionHandler(this,
                this::tickLDLib2DisplayTracking,
                () -> !ldlib2DisplayOpenings.isEmpty());
    }

    public WorkableElectricMultiblockMachine(BlockEntityCreationInfo info) {
        super(info);
        ldlib2DisplaySnapshotSubscription = new ConditionalSubscriptionHandler(this,
                this::tickLDLib2DisplayTracking,
                () -> !ldlib2DisplayOpenings.isEmpty());
    }

    @Override
    public WorkableElectricMultiblockMachine self() {
        return this;
    }

    //////////////////////////////////////
    // *** Multiblock Lifecycle ***//
    //////////////////////////////////////
    @Override
    public void invalidateStructure(String structureName) {
        super.invalidateStructure(structureName);
        if (DEFAULT_STRUCTURE.equals(structureName)) {
            this.energyContainer = null;
            this.tier = 0;
            refreshLDLib2DisplayTracking();
        }
    }

    @Override
    public void formStructure(String structureName) {
        super.formStructure(structureName);
        if (DEFAULT_STRUCTURE.equals(structureName)) {
            this.energyContainer = getEnergyContainer();
            this.tier = energyContainer.getTier();
            refreshLDLib2DisplayTracking();
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        clearLDLib2DisplayOpenings();
    }

    @Override
    public void onPartUnload() {
        super.onPartUnload();
        this.energyContainer = null;
        this.tier = 0;
        clearLDLib2DisplayOpenings();
    }

    private void refreshLDLib2DisplayTracking() {
        if (ldlib2DisplayOpenings.isEmpty() || isRemote()) return;
        ldlib2DisplaySnapshotSubscription.updateSubscription();
        refreshLDLib2DisplaySnapshot();
    }

    private void startLDLib2DisplayTracking(WorkableElectricMultiblockFancyPage page, Player player,
                                            AbstractContainerMenu menu) {
        if (isRemote()) return;
        boolean firstAttach = !ldlib2DisplayOpenings.containsKey(page);
        ldlib2DisplayOpenings.put(page, new LDLib2DisplayOpening(player, menu));
        if (firstAttach) {
            refreshLDLib2DisplayTracking();
        }
    }

    private void tickLDLib2DisplayTracking() {
        ldlib2DisplayOpenings.values().removeIf(opening -> opening.player().containerMenu != opening.menu());
        if (ldlib2DisplayOpenings.isEmpty()) {
            clearLDLib2DisplayRuntimeState();
        } else if (isFormed()) {
            refreshLDLib2DisplaySnapshot();
        }
    }

    private void clearLDLib2DisplayOpenings() {
        ldlib2DisplayOpenings.clear();
        clearLDLib2DisplayRuntimeState();
    }

    private void clearLDLib2DisplayRuntimeState() {
        ldlib2DisplaySnapshot = List.of();
        ldlib2DisplaySnapshotSubscription.unsubscribe();
    }

    @Override
    public boolean supportsBatchMode() {
        return getDefinition().getRecipeModifier() instanceof RecipeModifierList list &&
                Arrays.stream(list.getModifiers())
                        .anyMatch(modifier -> modifier == GTRecipeModifiers.BATCH_MODE);
    }

    @Override
    public void setBatchEnabled(boolean batchEnabled) {
        this.batchEnabled = batchEnabled;
    }

    @ServerFieldNormalizer(fieldName = "batchEnabled")
    private boolean normalizeBatchEnabled(boolean candidate) {
        if (!supportsBatchMode()) {
            throw new IllegalArgumentException("Machine does not support batch mode.");
        }
        return candidate;
    }

    //////////////////////////////////////
    // ********** GUI ***********//
    //////////////////////////////////////

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        if (holder.getMachine() != this) return false;
        for (IMultiPart part : getParts()) {
            if (!(part instanceof LDLib2FancyPartUIProvider)) return false;
        }
        return true;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        requireMatchingLDLib2Holder(holder);
        WorkableElectricMultiblockFancyPage page = new WorkableElectricMultiblockFancyPage(player, holder);
        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
        shell.addEventListener(UIEvents.MUI_CHANGED, event -> {
            var modularUI = shell.getModularUI();
            if (modularUI != null) {
                var menu = modularUI.getMenu();
                if (menu != null) {
                    startLDLib2DisplayTracking(page, player, menu);
                }
            }
        });
        return UI.of(shell);
    }

    private void requireMatchingLDLib2Holder(MachineUIHolder holder) {
        if (holder.getMachine() != this) {
            GTCEu.LOGGER.error("Workable multiblock UI holder no longer resolves controller at {}", getBlockPos());
            throw new IllegalArgumentException("Workable multiblock UI holder must resolve the opened controller.");
        }
    }

    void refreshLDLib2DisplaySnapshot() {
        List<Component> nextSnapshot = new ArrayList<>();
        addDisplayText(nextSnapshot);
        nextSnapshot = List.copyOf(nextSnapshot);
        if (!ldlib2DisplaySnapshot.equals(nextSnapshot)) {
            ldlib2DisplaySnapshot = nextSnapshot;
        }
    }

    private void addLDLib2DisplayText(List<Component> textList) {
        textList.addAll(ldlib2DisplaySnapshot);
    }

    private record LDLib2DisplayOpening(Player player, AbstractContainerMenu menu) {}

    private final class WorkableElectricMultiblockFancyPage implements LDLib2FancyUIProvider {

        private static final int PAGE_WIDTH = 190;
        private static final int PAGE_HEIGHT = 125;

        private final MachineUIHolder holder;
        private final List<IMultiPart> openingParts;
        private final List<LDLib2FancyUIProvider> sidePages;
        private final List<LDLib2FancyUIProvider> partPages;

        private WorkableElectricMultiblockFancyPage(Player player, MachineUIHolder holder) {
            this.holder = holder;
            openingParts = List.copyOf(getParts());

            List<LDLib2FancyUIProvider> openingSidePages = new ArrayList<>();
            if (getRecipeTypes().length > 1) {
                openingSidePages.add(new LDLib2MachineModeFancyConfigurator(
                        WorkableElectricMultiblockMachine.this));
            }
            openingSidePages.add(new LDLib2DirectionalFancyConfigurator(
                    WorkableElectricMultiblockMachine.this, player, holder));
            sidePages = List.copyOf(openingSidePages);

            List<LDLib2FancyUIProvider> openingPartPages = new ArrayList<>();
            for (IMultiPart part : openingParts) {
                if (!(part instanceof LDLib2FancyPartUIProvider pageProvider)) {
                    GTCEu.LOGGER.error("Workable multiblock part {} has no LDLib2 Fancy page",
                            part.self().getDefinition().getId());
                    throw new IllegalStateException("Workable multiblock part has no LDLib2 Fancy page: " +
                            part.self().getDefinition().getId());
                }
                openingPartPages.add(pageProvider.createLDLib2FancyPage(
                        player, new MachineUIHolderContext(player, part.self())));
            }
            partPages = List.copyOf(openingPartPages);
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            if (holder.getMachine() != WorkableElectricMultiblockMachine.this) {
                GTCEu.LOGGER.error("Workable multiblock page holder changed after opening at {}", getBlockPos());
                throw new IllegalStateException("Workable multiblock page holder no longer resolves its controller.");
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
                    WorkableElectricMultiblockMachine.this::addLDLib2DisplayText)
                    .setMaxWidthLimit(200)
                    .clickHandler(WorkableElectricMultiblockMachine.this::handleDisplayClick));
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
            sidePages.forEach(tabs::attachSubTab);
        }

        @Override
        public void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel) {
            LDLib2VoidingModeFancyConfigurator.attachConfigurators(
                    configuratorPanel, WorkableElectricMultiblockMachine.this);
            LDLib2BatchModeFancyConfigurator.attachConfigurators(
                    configuratorPanel, WorkableElectricMultiblockMachine.this);
            configuratorPanel.attachConfigurators(new LDLib2WorkingEnabledFancyConfigurator(
                    WorkableElectricMultiblockMachine.this, holder));
        }

        @Override
        public void attachTooltips(LDLib2FancyTooltipsPanelElement tooltipsPanel) {
            for (IMultiPart part : openingParts) {
                part.attachLDLib2FancyTooltipsToController(
                        WorkableElectricMultiblockMachine.this, tooltipsPanel);
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

    @Override
    public void addDisplayText(@NotNull List<Component> textList) {
        var workLogic = getWorkLogic();
        int numParallels;
        int subtickParallels;
        int batchParallels;
        int totalRuns;
        boolean exact = false;
        if (workLogic.isActive() && recipeLogic.getLastRecipe() != null) {
            numParallels = recipeLogic.getLastRecipe().parallels;
            subtickParallels = recipeLogic.getLastRecipe().subtickParallels;
            batchParallels = recipeLogic.getLastRecipe().batchParallels;
            totalRuns = recipeLogic.getLastRecipe().getTotalRuns();
            exact = true;
        } else {
            numParallels = getParallelHatch()
                    .map(ParallelHatchPartMachine::getCurrentParallel)
                    .orElse(0);
            subtickParallels = 0;
            batchParallels = 0;
            totalRuns = 0;
        }

        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(workLogic.isWorkingEnabled(), workLogic.isActive())
                .addEnergyUsageLine(energyContainer)
                .addEnergyTierLine(tier)
                .addMachineModeLine(getRecipeType(), getRecipeTypes().length > 1)
                .addTotalRunsLine(totalRuns)
                .addParallelsLine(numParallels, exact)
                .addSubtickParallelsLine(subtickParallels)
                .addBatchModeLine(isBatchEnabled(), batchParallels)
                .addWorkingStatusLine()
                .addProgressLine(recipeLogic)
                .addRecipeFailReasonLine(recipeLogic)
                .addOutputLines(recipeLogic.getLastRecipe());
        getDefinition().getAdditionalDisplay().accept(this, textList);
        IDisplayUIMachine.super.addDisplayText(textList);
    }

    @Override
    public Widget createUIWidget() {
        var group = new WidgetGroup(0, 0, 182 + 8, 117 + 8);
        group.addWidget(new DraggableScrollableWidgetGroup(4, 4, 182, 117).setBackground(getScreenTexture())
                .addWidget(new LabelWidget(4, 5, self().getBlockState().getBlock().getDescriptionId()))
                .addWidget(new ComponentPanelWidget(4, 17, this::addDisplayText)
                        .textSupplier(this.getLevel().isClientSide ? null : this::addDisplayText)
                        .setMaxWidthLimit(200)
                        .clickHandler(this::handleDisplayClick)));
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        return group;
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        return new ModularUI(198, 208, this, entityPlayer).widget(new FancyMachineUIWidget(this, 198, 208));
    }

    @Override
    public List<IFancyUIProvider> getSubTabs() {
        return getParts().stream().map(IFancyUIProvider.class::cast).toList();
    }

    @Override
    public void attachConfigurators(ConfiguratorPanel configuratorPanel) {
        IVoidable.attachConfigurators(configuratorPanel, this);
        if (supportsBatchMode()) {
            configuratorPanel.attachConfigurators(new IFancyConfiguratorButton.Toggle(
                    GuiTextures.BUTTON_BATCH.getSubTexture(0, 0, 1, 0.5),
                    GuiTextures.BUTTON_BATCH.getSubTexture(0, 0.5, 1, 0.5),
                    this::isBatchEnabled,
                    (cd, p) -> setBatchEnabled(p))
                    .setTooltipsSupplier(
                            p -> List.of(
                                    Component.translatable("gtpm.machine.batch_" + (p ? "enabled" : "disabled")))));
        }

        IFancyUIMachine.super.attachConfigurators(configuratorPanel);
    }

    @Override
    public void attachTooltips(TooltipsPanel tooltipsPanel) {
        for (IMultiPart part : getParts()) {
            part.attachFancyTooltipsToController(this, tooltipsPanel);
        }
    }

    //////////////////////////////////////
    // ******** OVERCLOCK *********//
    //////////////////////////////////////
    public int getOverclockTier() {
        return getTier();
    }

    public int getMaxOverclockTier() {
        return getTier();
    }

    public int getMinOverclockTier() {
        return getTier();
    }

    @Override
    public void setOverclockTier(int tier) {}

    @Override
    public long getOverclockVoltage() {
        if (this.energyContainer == null) {
            this.energyContainer = getEnergyContainer();
        }
        return energyContainer.getEffectiveVoltage();
    }

    //////////////////////////////////////
    // ****** RECIPE LOGIC *******//
    //////////////////////////////////////

    public EnergyContainerList getEnergyContainer() {
        List<IEnergyContainer> containers = new ArrayList<>();
        var handlers = getCapabilitiesFlat(IO.IN, EURecipeCapability.CAP);
        if (handlers.isEmpty()) handlers = getCapabilitiesFlat(IO.OUT, EURecipeCapability.CAP);
        for (IRecipeHandler<?> handler : handlers) {
            if (handler instanceof IEnergyContainer container) {
                containers.add(container);
            }
        }
        return new EnergyContainerList(containers);
    }

    public long getMaxVoltage() {
        if (this.energyContainer == null) {
            this.energyContainer = getEnergyContainer();
        }
        if (this.isGenerator()) {
            return energyContainer.getEffectiveVoltage();
        }
        return GTValues.V[energyContainer.getTier()];
    }

    @Override
    public long getTierVoltage() {
        return getMaxVoltage();
    }

    @Override
    public long getDisplayRecipeVoltage() {
        return this.getEnergyContainer().getHighestVoltage();
    }

    /**
     * Is this multiblock a generator?
     * Used for max voltage calculations.
     */
    public boolean isGenerator() {
        return getDefinition().isGenerator();
    }
}

package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.IMiner;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolderContext;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTabsElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2DirectionalFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2VoidingModeFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2WorkingEnabledFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.IDataInfoProvider;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.fluid.FluidHandlerList;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.item.behavior.PortableScannerBehavior;
import com.gregtechceu.gtceu.common.machine.trait.miner.LargeMinerLogic;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import lombok.AccessLevel;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.gregtechceu.gtceu.common.data.GTMaterials.DrillingFluid;

public class LargeMinerMachine extends WorkableElectricMultiblockMachine
                               implements IMiner, IControllable, IDataInfoProvider, LDLib2MachineUIProvider,
                               LDLib2FancyActionMachine, LargeMinerModeActionTarget {

    public static final int CHUNK_LENGTH = 16;
    static {
        LargeMinerMachineActions.initialize();
    }

    @Getter
    private final int tier;
    @Nullable
    protected EnergyContainerList energyContainer;
    @Nullable
    protected FluidHandlerList inputFluidInventory;
    @Getter(AccessLevel.PACKAGE)
    private final int drillingFluidConsumePerTick;
    @Getter(AccessLevel.PACKAGE)
    private final ConditionalSubscriptionHandler displaySnapshotSubscription;
    @Getter(AccessLevel.PACKAGE)
    @SyncToClient
    private List<Component> displaySnapshot = List.of();

    public LargeMinerMachine(BlockEntityCreationInfo info, int tier, int speed, int maximumChunkDiameter, int fortune,
                             int drillingFluidConsumePerTick) {
        super(info, new LargeMinerLogic(fortune, speed, maximumChunkDiameter * CHUNK_LENGTH / 2));
        this.tier = tier;
        this.drillingFluidConsumePerTick = drillingFluidConsumePerTick;
        this.displaySnapshotSubscription = new ConditionalSubscriptionHandler(this, this::refreshDisplaySnapshot,
                this::isFormed);
    }

    @Override
    public LargeMinerLogic getRecipeLogic() {
        return (LargeMinerLogic) super.getRecipeLogic();
    }

    public static Material getMaterial(int tier) {
        if (tier == GTValues.EV) return GTMaterials.Steel;
        if (tier == GTValues.IV) return GTMaterials.Titanium;
        if (tier == GTValues.LuV) return GTMaterials.TungstenSteel;
        return GTMaterials.Steel;
    }

    public static Block getCasingState(int tier) {
        return GTBlocks.MATERIALS_TO_CASINGS.get(getMaterial(tier)).get();
    }

    public long getMaxVoltage() {
        return GTValues.V[getEnergyTier()];
    }

    //////////////////////////////////////
    // ******* Logic *********//
    //////////////////////////////////////
    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            refreshDisplaySnapshot();
            displaySnapshotSubscription.initialize(getLevel());
        }
    }

    @Override
    public void formStructure(String structureName) {
        super.formStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        Direction opposite = this.getUpwardsFacing().getOpposite();
        getRecipeLogic().setDir(opposite == Direction.NORTH ? Direction.UP : Direction.DOWN);
        initializeAbilities();
        if (!isRemote()) {
            refreshDisplaySnapshot();
            displaySnapshotSubscription.updateSubscription();
        }
    }

    @Override
    public void invalidateStructure(String structureName) {
        super.invalidateStructure(structureName);
        if (DEFAULT_STRUCTURE.equals(structureName) && !isRemote()) {
            refreshDisplaySnapshot();
            displaySnapshotSubscription.updateSubscription();
        }
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
        displaySnapshot = List.of();
        displaySnapshotSubscription.unsubscribe();
    }

    @Override
    public boolean checkPattern(String structureName) {
        return super.checkPattern(structureName) &&
                (this.getUpwardsFacing() == Direction.NORTH || this.getUpwardsFacing() == Direction.SOUTH);
    }

    private void initializeAbilities() {
        List<IEnergyContainer> energyContainers = new ArrayList<>();
        List<IFluidHandler> fluidTanks = new ArrayList<>();
        Long2ObjectMap<IO> ioMap = getMultiblockState(DEFAULT_STRUCTURE).getMatchContext().getOrDefault("ioMap",
                Long2ObjectMaps.emptyMap());
        for (IMultiPart part : getParts()) {
            IO io = ioMap.getOrDefault(part.self().getBlockPos().asLong(), IO.BOTH);
            if (io == IO.NONE) continue;

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
                        .forEach(fluidTanks::add);
            }
        }
        this.energyContainer = new EnergyContainerList(energyContainers);
        this.inputFluidInventory = new FluidHandlerList(fluidTanks);

        getRecipeLogic().setVoltageTier(GTUtil.getTierByVoltage(this.energyContainer.getEffectiveVoltage()));
        getRecipeLogic().setOverclockAmount(
                Math.max(1, GTUtil.getTierByVoltage(this.energyContainer.getEffectiveVoltage()) - this.tier));
        getRecipeLogic().initPos(getBlockPos(), getRecipeLogic().getCurrentRadius());
    }

    public int getEnergyTier() {
        if (energyContainer == null) return this.tier;
        return Math.min(this.tier + 1,
                Math.max(this.tier, GTUtil.getFloorTierByVoltage(energyContainer.getEffectiveVoltage())));
    }

    @Override
    public boolean drainInput(boolean simulate) {
        // drain energy
        if (energyContainer != null && energyContainer.getEnergyStored() > 0) {
            long energyToDrain = GTValues.VA[getEnergyTier()];
            long resultEnergy = energyContainer.getEnergyStored() - energyToDrain;
            if (resultEnergy >= 0L && resultEnergy <= energyContainer.getEnergyCapacity()) {
                if (!simulate) {
                    energyContainer.changeEnergy(-energyToDrain);
                }
            } else {
                return false;
            }
        } else {
            return false;
        }

        // drain fluid
        if (inputFluidInventory != null && inputFluidInventory.handlers.length > 0) {
            FluidStack drillingFluid = DrillingFluid
                    .getFluid(this.drillingFluidConsumePerTick * getRecipeLogic().getOverclockAmount());
            FluidStack fluidStack = inputFluidInventory.getFluidInTank(0);
            if (fluidStack != FluidStack.EMPTY && fluidStack.is(DrillingFluid.getFluid()) &&
                    fluidStack.getAmount() >= drillingFluid.getAmount()) {
                if (!simulate) {
                    GTTransferUtils.drainFluidAccountNotifiableList(inputFluidInventory, drillingFluid,
                            IFluidHandler.FluidAction.EXECUTE);
                }
            } else {
                return false;
            }
        }
        return true;
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////
    @Override
    public void addDisplayText(List<Component> textList) {
        textList.addAll(displaySnapshot);
    }

    void refreshDisplaySnapshot() {
        List<Component> nextSnapshot = new ArrayList<>();
        collectServerDisplayText(nextSnapshot);
        nextSnapshot = List.copyOf(nextSnapshot);
        if (!displaySnapshot.equals(nextSnapshot)) {
            displaySnapshot = nextSnapshot;
        }
    }

    protected void collectServerDisplayText(List<Component> textList) {
        super.addDisplayText(textList);
        textList.addAll(createLargeMinerDisplayText(captureDisplayState()));
    }

    protected DisplayState captureDisplayState() {
        LargeMinerLogic logic = getRecipeLogic();
        return new DisplayState(isFormed(), logic.getX(), logic.getY(), logic.getZ(), logic.isSilkTouchMode(),
                logic.isChunkMode(), logic.getCurrentRadius(), logic.isDone());
    }

    static List<Component> createLargeMinerDisplayText(DisplayState state) {
        if (!state.formed()) {
            return List.of();
        }

        List<Component> text = new ArrayList<>(7);
        text.add(Component.translatable("gtpm.machine.miner.startx", displayCoordinate(state.x())));
        text.add(Component.translatable("gtpm.machine.miner.starty", displayCoordinate(state.y())));
        text.add(Component.translatable("gtpm.machine.miner.startz", displayCoordinate(state.z())));
        text.add(Component.translatable("gtpm.universal.tooltip.silk_touch")
                .append(modeButton(state.silkTouchMode(), "silk_touch")));
        text.add(Component.translatable("gtpm.universal.tooltip.chunk_mode")
                .append(modeButton(state.chunkMode(), "chunk_mode")));
        if (state.chunkMode()) {
            int workingAreaChunks = state.currentRadius() * 2 / CHUNK_LENGTH;
            text.add(Component.translatable("gtpm.universal.tooltip.working_area_chunks", workingAreaChunks,
                    workingAreaChunks));
        } else {
            int workingArea = IMiner.getWorkingArea(state.currentRadius());
            text.add(Component.translatable("gtpm.universal.tooltip.working_area", workingArea, workingArea));
        }
        if (state.done()) {
            text.add(Component.translatable("gtpm.multiblock.large_miner.done")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)));
        }
        return List.copyOf(text);
    }

    private static int displayCoordinate(int coordinate) {
        return coordinate == Integer.MAX_VALUE ? 0 : coordinate;
    }

    private static Component modeButton(boolean enabled, String componentData) {
        return GTComponentPanelElement.withButton(Component.literal("[")
                .append(enabled ? Component.translatable("gtpm.creative.activity.on") :
                        Component.translatable("gtpm.creative.activity.off"))
                .append(Component.literal("]")), componentData);
    }

    record DisplayState(boolean formed, int x, int y, int z, boolean silkTouchMode, boolean chunkMode,
                        int currentRadius, boolean done) {}

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
        return new LargeMinerFancyPage(player, holder);
    }

    private void requireMatchingHolder(MachineUIHolder holder) {
        if (holder.getMachine() != this) {
            throw new IllegalArgumentException("Large Miner UI holder must resolve the opened controller.");
        }
    }

    SyncActionData resolveLargeMinerDisplayAction(MachineUIHolder holder, String componentData) {
        requireMatchingHolder(holder);
        return switch (componentData) {
            case "silk_touch" -> LargeMinerMachineActions.createToggleSilkTouchAction();
            case "chunk_mode" -> LargeMinerMachineActions.createToggleChunkModeAction();
            default -> throw new IllegalArgumentException("Unknown Large Miner display action: " + componentData);
        };
    }

    private void handleLDLib2DisplayClick(Player player, MachineUIHolder holder, String componentData) {
        SyncActionData action = resolveLargeMinerDisplayAction(holder, componentData);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, action);
        }
    }

    @Override
    public boolean isLargeMinerModeChangeBlocked() {
        return getRecipeLogic().isWorking();
    }

    @Override
    public void toggleLargeMinerSilkTouch() {
        LargeMinerLogic logic = getRecipeLogic();
        logic.setSilkTouchMode(!logic.isSilkTouchMode());
        refreshDisplaySnapshot();
    }

    @Override
    public void toggleLargeMinerChunkMode() {
        LargeMinerLogic logic = getRecipeLogic();
        logic.setChunkMode(!logic.isChunkMode());
        refreshDisplaySnapshot();
    }

    private final class LargeMinerFancyPage implements LDLib2FancyUIProvider {

        private static final int PAGE_WIDTH = 190;
        private static final int PAGE_HEIGHT = 125;

        private final MachineUIHolder holder;
        private final LDLib2DirectionalFancyConfigurator directionalPage;
        private final List<LDLib2FancyUIProvider> partPages;

        private LargeMinerFancyPage(Player player, MachineUIHolder holder) {
            requireMatchingHolder(holder);
            this.holder = holder;
            this.directionalPage = new LDLib2DirectionalFancyConfigurator(LargeMinerMachine.this, player, holder);

            List<LDLib2FancyUIProvider> pages = new ArrayList<>();
            for (IMultiPart part : getParts()) {
                if (!(part instanceof LDLib2FancyPartUIProvider pageProvider)) {
                    throw new IllegalStateException("Large Miner part has no LDLib2 Fancy page: " +
                            part.self().getDefinition().getId());
                }
                MachineUIHolder partHolder = new MachineUIHolderContext(player, part.self());
                pages.add(pageProvider.createLDLib2FancyPage(player, partHolder));
            }
            this.partPages = List.copyOf(pages);
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            if (holder.getMachine() != LargeMinerMachine.this) {
                throw new IllegalStateException("Large Miner page holder no longer resolves its controller.");
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
            screen.addScrollViewChild(new GTComponentPanelElement(4, 17, LargeMinerMachine.this::addDisplayText)
                    .setMaxWidthLimit(200)
                    .clickHandler(
                            (componentData, clickData) -> handleLDLib2DisplayClick(
                                    shell.getOpeningPlayer(), holder, componentData)));
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
            LDLib2VoidingModeFancyConfigurator.attachConfigurators(configuratorPanel, LargeMinerMachine.this);
            configuratorPanel.attachConfigurators(new LDLib2WorkingEnabledFancyConfigurator(
                    LargeMinerMachine.this, holder));
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

    //////////////////////////////////////
    // ******* Interaction *******//
    //////////////////////////////////////
    @Override
    public InteractionResult onScrewdriverClick(ExtendedUseOnContext context) {
        if (isRemote() || !this.isFormed())
            return InteractionResult.SUCCESS;

        if (!this.isActive()) {
            int currentRadius = getRecipeLogic().getCurrentRadius();
            if (getRecipeLogic().isChunkMode()) {
                if (currentRadius - CHUNK_LENGTH <= 0) {
                    getRecipeLogic().setCurrentRadius(getRecipeLogic().getMaximumRadius());
                } else {
                    getRecipeLogic().setCurrentRadius(currentRadius - CHUNK_LENGTH);
                }
                int workingAreaChunks = getRecipeLogic().getCurrentRadius() * 2 / CHUNK_LENGTH;
                context.getPlayer()
                        .sendSystemMessage(Component.translatable("gtpm.universal.tooltip.working_area_chunks",
                                workingAreaChunks, workingAreaChunks));
            } else {
                if (currentRadius - CHUNK_LENGTH / 2 <= 0) {
                    getRecipeLogic().setCurrentRadius(getRecipeLogic().getMaximumRadius());
                } else {
                    getRecipeLogic().setCurrentRadius(currentRadius - CHUNK_LENGTH / 2);
                }
                int workingArea = IMiner.getWorkingArea(getRecipeLogic().getCurrentRadius());
                context.getPlayer().sendSystemMessage(
                        Component.translatable("gtpm.universal.tooltip.working_area", workingArea, workingArea));
            }
            getRecipeLogic().resetArea(true);
        } else {
            context.getPlayer().sendSystemMessage(Component.translatable("gtpm.multiblock.large_miner.errorradius"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public List<Component> getDataInfo(PortableScannerBehavior.DisplayMode mode) {
        if (mode == PortableScannerBehavior.DisplayMode.SHOW_ALL ||
                mode == PortableScannerBehavior.DisplayMode.SHOW_MACHINE_INFO) {
            int workingArea = IMiner.getWorkingArea(getRecipeLogic().getCurrentRadius());
            return Collections.singletonList(
                    Component.translatable("gtpm.universal.tooltip.working_area", workingArea, workingArea));
        }
        return new ArrayList<>();
    }
}

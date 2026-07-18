package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.IFusionCasingType;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
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
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2BatchModeFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2DirectionalFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2VoidingModeFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2WorkingEnabledFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.RecipeData;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI.LDLib2RecipeUISize;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.client.bloom.BloomRenderTicket;
import com.gregtechceu.gtceu.common.block.FusionCasingBlock;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;

import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2IntAVLTreeMap;
import it.unimi.dsi.fastutil.longs.Long2IntSortedMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.gregtechceu.gtceu.api.GTValues.*;
import static com.gregtechceu.gtceu.api.recipe.OverclockingLogic.PERFECT_HALF_DURATION_FACTOR;
import static com.gregtechceu.gtceu.api.recipe.OverclockingLogic.PERFECT_HALF_VOLTAGE_FACTOR;
import static com.gregtechceu.gtceu.common.data.GTBlocks.*;

public class FusionReactorMachine extends WorkableElectricMultiblockMachine
                                  implements ITieredMachine, LDLib2MachineUIProvider, LDLib2FancyActionMachine {

    // Standard OC used for Fusion
    public static final OverclockingLogic FUSION_OC = OverclockingLogic.create(PERFECT_HALF_DURATION_FACTOR,
            PERFECT_HALF_VOLTAGE_FACTOR, false);

    // Max EU -> Tier map, used to find minimum tier needed for X EU to start
    private static final Long2IntSortedMap FUSION_ENERGY = new Long2IntAVLTreeMap();
    // Tier -> Suffix map, i.e. LuV -> MKI
    private static final Int2ObjectMap<String> FUSION_NAMES = new Int2ObjectArrayMap<>(4);
    // Minimum registered fusion reactor tier
    private static int MINIMUM_TIER = MAX;

    @Getter
    private final int tier;
    @Getter(AccessLevel.PACKAGE)
    private final ConditionalSubscriptionHandler displaySnapshotSubscription;
    @Getter(AccessLevel.PACKAGE)
    @SyncToClient
    private List<Component> displaySnapshot = List.of();
    @Nullable
    protected EnergyContainerList inputEnergyContainers;
    @SaveField
    protected long heat = 0;
    @SaveField
    protected final NotifiableEnergyContainer energyContainer;
    @Nullable
    protected TickableSubscription preHeatSubs;

    // Used for rendering
    @Getter
    @SyncToClient
    private int color = 0xFFFFFFFF;
    public float delta = 0;
    public int lastColor = -1;
    @Getter
    @Setter
    protected BloomRenderTicket registeredBloomTicket = BloomRenderTicket.INVALID;

    public FusionReactorMachine(BlockEntityCreationInfo info, int tier) {
        super(info);
        this.tier = tier;
        this.energyContainer = attachTrait(new NotifiableEnergyContainer(0, 0, 0, 0, 0));
        this.displaySnapshotSubscription = new ConditionalSubscriptionHandler(this, this::refreshDisplaySnapshot,
                this::isFormed);
        energyContainer.setCapabilityValidator(candidate -> candidate == null);
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            updatePreHeatSubscription();
            refreshDisplaySnapshot();
            displaySnapshotSubscription.initialize(getLevel());
        }
    }

    @Override
    public void formStructure(String structureName) {
        super.formStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        // capture all energy containers
        List<IEnergyContainer> energyContainers = new ArrayList<>();
        Long2ObjectMap<IO> ioMap = getMultiblockState(DEFAULT_STRUCTURE).getMatchContext().getOrDefault("ioMap",
                Long2ObjectMaps.emptyMap());
        for (IMultiPart part : getParts()) {
            IO io = ioMap.getOrDefault(part.self().getBlockPos().asLong(), IO.BOTH);
            if (io == IO.NONE || io == IO.OUT) continue;
            var handlerLists = part.getRecipeHandlers();
            for (var handlerList : handlerLists) {
                if (!handlerList.isValid(io)) continue;

                handlerList.getCapability(EURecipeCapability.CAP).stream()
                        .filter(IEnergyContainer.class::isInstance)
                        .map(IEnergyContainer.class::cast)
                        .forEach(energyContainers::add);
                traitSubscriptions.add(handlerList.subscribe(this::updatePreHeatSubscription, EURecipeCapability.CAP));
            }
        }
        this.inputEnergyContainers = new EnergyContainerList(energyContainers);
        energyContainer.resetBasicInfo(calculateEnergyStorageFactor(getTier(), energyContainers.size()), 0, 0, 0, 0);
        updatePreHeatSubscription();
        if (!isRemote()) {
            refreshDisplaySnapshot();
            displaySnapshotSubscription.updateSubscription();
        }
    }

    @Override
    public void invalidateStructure(String structureName) {
        super.invalidateStructure(structureName);
        if (!DEFAULT_STRUCTURE.equals(structureName)) return;
        this.inputEnergyContainers = null;
        heat = 0;
        energyContainer.resetBasicInfo(0, 0, 0, 0, 0);
        energyContainer.setEnergyStored(0);
        updatePreHeatSubscription();
        if (!isRemote()) {
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

    //////////////////////////////////////
    // ***** Recipe Logic ******//
    //////////////////////////////////////
    protected void updatePreHeatSubscription() {
        // do preheat logic for heat cool down and charge internal energy container
        if (heat > 0 || (inputEnergyContainers != null && inputEnergyContainers.getEnergyStored() > 0 &&
                energyContainer.getEnergyStored() < energyContainer.getEnergyCapacity())) {
            preHeatSubs = subscribeServerTick(preHeatSubs, this::updateHeat);
        } else if (preHeatSubs != null) {
            preHeatSubs.unsubscribe();
            preHeatSubs = null;
        }
    }

    /**
     * Recipe Modifier for <b>Fusion Reactors</b> - can be used as a valid {@link RecipeModifier}
     * <p>
     * If the Fusion Reactor has enough heat or can get enough heat to run the recipe based on the {@code eu_to_start}
     * data,
     * apply {@link FusionReactorMachine#FUSION_OC} to the recipe.
     * Otherwise, the recipe is rejected.
     * </p>
     *
     * @param machine a {@link FusionReactorMachine}
     * @param recipe  recipe
     * @return A {@link ModifierFunction} for the given Fusion Reactor and recipe
     */
    public static ModifierFunction recipeModifier(@NotNull MetaMachine machine, @NotNull GTRecipe recipe) {
        if (!(machine instanceof FusionReactorMachine fusionReactorMachine)) {
            return RecipeModifier.nullWrongType(FusionReactorMachine.class, machine);
        }
        if (RecipeHelper.getRecipeEUtTier(recipe) > fusionReactorMachine.getTier() ||
                !RecipeData.contains(recipe.data, "eu_to_start") ||
                RecipeData.getLong(recipe.data, "eu_to_start") >
                        fusionReactorMachine.energyContainer.getEnergyCapacity()) {
            return ModifierFunction
                    .cancel(Component.translatable("gtpm.recipe_modifier.insufficient_eu_to_start_fusion"));
        }

        long heatDiff = RecipeData.getLong(recipe.data, "eu_to_start") - fusionReactorMachine.heat;

        // if the stored heat is >= required energy, recipe is okay to run
        if (heatDiff <= 0) {
            return FUSION_OC.getModifier(machine, recipe, fusionReactorMachine.getMaxVoltage(), false);
        }
        // if the remaining energy needed is more than stored, do not run
        if (fusionReactorMachine.energyContainer.getEnergyStored() < heatDiff)
            return ModifierFunction
                    .cancel(Component.translatable("gtpm.recipe_modifier.insufficient_eu_to_start_fusion"));

        // remove the energy needed
        fusionReactorMachine.energyContainer.removeEnergy(heatDiff);
        // increase the stored heat
        fusionReactorMachine.heat += heatDiff;
        fusionReactorMachine.updatePreHeatSubscription();
        return FUSION_OC.getModifier(machine, recipe, fusionReactorMachine.getMaxVoltage(), false);
    }

    @Override
    public boolean onWorking() {
        GTRecipe recipe = recipeLogic.getLastRecipe();
        assert recipe != null;
        if (RecipeData.contains(recipe.data, "eu_to_start")) {
            long heatDiff = RecipeData.getLong(recipe.data, "eu_to_start") - this.heat;
            // if the remaining energy needed is more than stored, do not run
            if (heatDiff > 0) {
                recipeLogic.setWaiting(Component.translatable("gtpm.recipe_logic.insufficient_fuel"));

                // if the remaining energy needed is more than stored, do not run
                if (this.energyContainer.getEnergyStored() < heatDiff)
                    return super.onWorking();
                // remove the energy needed
                this.energyContainer.removeEnergy(heatDiff);
                // increase the stored heat
                this.heat += heatDiff;
                this.updatePreHeatSubscription();
            }
        }

        if (color == -1) {
            if (!recipe.getOutputContents(FluidRecipeCapability.CAP).isEmpty()) {
                var stack = FluidRecipeCapability.CAP
                        .of(recipe.getOutputContents(FluidRecipeCapability.CAP).getFirst().getContent()).getFluids()[0];
                int newColor = 0xFF000000 | GTUtil.getFluidColor(stack);
                if (color != newColor) {
                    color = newColor;
                    syncDataHolder.markClientSyncFieldDirty("color");
                }
            }
        }
        return super.onWorking();
    }

    public void updateHeat() {
        // Drain heat when the reactor is not active, is paused via soft mallet, or does not have enough energy and has
        // fully wiped recipe progress
        // Don't drain heat when there is not enough energy and there is still some recipe progress, as that makes it
        // doubly hard to complete the recipe
        // (Will have to recover heat and recipe progress)
        var workLogic = getWorkLogic();
        if ((workLogic.isIdle() || !workLogic.isWorkingEnabled() ||
                (workLogic.isWaiting() && getRecipeLogic().getProgress() == 0)) && heat > 0) {
            heat = heat <= 10000 ? 0 : (heat - 10000);
        }
        // charge the internal energy storage
        var leftStorage = energyContainer.getEnergyCapacity() - energyContainer.getEnergyStored();
        if (inputEnergyContainers != null && leftStorage > 0) {
            energyContainer.addEnergy(inputEnergyContainers.removeEnergy(leftStorage));
        }
        updatePreHeatSubscription();
    }

    @Override
    public void onWaiting() {
        super.onWaiting();
        color = -1;
        syncDataHolder.markClientSyncFieldDirty("color");
    }

    @Override
    public void afterWorking() {
        super.afterWorking();
        color = -1;
        syncDataHolder.markClientSyncFieldDirty("color");
    }

    @Override
    public long getMaxVoltage() {
        return Math.min(GTValues.V[tier], super.getMaxVoltage());
    }

    //////////////////////////////////////
    // ******** GUI *********//
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
        textList.addAll(createFusionDisplayText(captureDisplayState()));
    }

    protected DisplayState captureDisplayState() {
        return new DisplayState(isFormed(), energyContainer.getEnergyStored(), energyContainer.getEnergyCapacity(),
                heat);
    }

    static List<Component> createFusionDisplayText(DisplayState state) {
        if (!state.formed()) {
            return List.of();
        }
        return List.of(
                Component.translatable("gtpm.multiblock.fusion_reactor.energy",
                        state.energyStored(), state.energyCapacity()),
                Component.translatable("gtpm.multiblock.fusion_reactor.heat", state.heat()));
    }

    record DisplayState(boolean formed, long energyStored, long energyCapacity, long heat) {}

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
        return new FusionReactorFancyPage(player, holder);
    }

    private void requireMatchingHolder(MachineUIHolder holder) {
        if (holder.getMachine() != this) {
            throw new IllegalArgumentException("Fusion Reactor UI holder must resolve the opened controller.");
        }
    }

    private final class FusionReactorFancyPage implements LDLib2FancyUIProvider {

        private static final int PAGE_WIDTH = 190;
        private static final int PAGE_HEIGHT = 125;

        private final MachineUIHolder holder;
        private final LDLib2DirectionalFancyConfigurator directionalPage;
        private final List<LDLib2FancyUIProvider> partPages;

        private FusionReactorFancyPage(Player player, MachineUIHolder holder) {
            requireMatchingHolder(holder);
            this.holder = holder;
            this.directionalPage = new LDLib2DirectionalFancyConfigurator(FusionReactorMachine.this, player, holder);

            List<LDLib2FancyUIProvider> pages = new ArrayList<>();
            for (IMultiPart part : getParts()) {
                if (!(part instanceof LDLib2FancyPartUIProvider pageProvider)) {
                    throw new IllegalStateException("Fusion Reactor part has no LDLib2 Fancy page: " +
                            part.self().getDefinition().getId());
                }
                MachineUIHolder partHolder = new MachineUIHolderContext(player, part.self());
                pages.add(pageProvider.createLDLib2FancyPage(player, partHolder));
            }
            this.partPages = List.copyOf(pages);
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            if (holder.getMachine() != FusionReactorMachine.this) {
                throw new IllegalStateException("Fusion Reactor page holder no longer resolves its controller.");
            }

            UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, PAGE_WIDTH, PAGE_HEIGHT);
            UITemplate.setLDLib2BackgroundTexture(root, GuiTextures.BACKGROUND_INVERSE);

            GTScrollerViewElement screen = new GTScrollerViewElement(4, 4, 182, 117);
            UITemplate.setLDLib2BackgroundTexture(screen, getScreenTexture());
            screen.viewPort.layout(layout -> layout.paddingAll(0));
            UITemplate.setLDLib2BackgroundTexture(screen.viewPort, getScreenTexture());
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
            screen.addScrollViewChild(new GTComponentPanelElement(4, 17, FusionReactorMachine.this::addDisplayText)
                    .setMaxWidthLimit(200)
                    .clickHandler(FusionReactorMachine.this::handleDisplayClick));
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
            LDLib2VoidingModeFancyConfigurator.attachConfigurators(configuratorPanel, FusionReactorMachine.this);
            LDLib2BatchModeFancyConfigurator.attachConfigurators(configuratorPanel, FusionReactorMachine.this);
            configuratorPanel.attachConfigurators(new LDLib2WorkingEnabledFancyConfigurator(
                    FusionReactorMachine.this, holder));
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

    public static void addLDLib2EUToStartLabel(GTRecipeDefinition recipe, UIElement root,
                                               LDLib2RecipeUISize rootSize) {
        long euToStart = RecipeData.getLong(recipe.data, "eu_to_start");
        if (euToStart <= 0) return;
        int recipeTier = RecipeHelper.getPreOCRecipeEuTier(recipe);
        addLDLib2EUToStartLabel(root, euToStart, recipeTier, rootSize);
    }

    private static void addLDLib2EUToStartLabel(UIElement root, long euToStart, int recipeTier,
                                                LDLib2RecipeUISize rootSize) {
        int fusionTier = findCeilingTier(euToStart);
        int tier = Math.max(MINIMUM_TIER, Math.max(recipeTier, fusionTier));
        GTLabelElement label = new GTLabelElement(-8, rootSize.height() - 10, rootSize.width() + 8, 10);
        label.setValue(Component.translatable("gtpm.recipe.eu_to_start",
                FormattingUtil.formatNumberReadable2F(euToStart, false),
                FUSION_NAMES.get(tier)));
        label.textStyle(textStyle -> textStyle.textWrap(TextWrap.NONE));
        root.addChild(label);
    }

    //////////////////////////////////////
    // ******** MISC *********//
    //////////////////////////////////////
    public static void registerFusionTier(int tier, @NotNull String name) {
        long maxEU = calculateEnergyStorageFactor(tier, 16);
        FUSION_ENERGY.put(maxEU, tier);
        FUSION_NAMES.put(tier, name);
        MINIMUM_TIER = Math.min(tier, MINIMUM_TIER);
    }

    private static int findCeilingTier(long euToStart) {
        long key;
        // tail = submap where all keys are >= EU to start
        // if tail is empty, then EU is greater than all the EU values, so we choose the last key
        // otherwise we want the first key in the tail map
        var tail = FUSION_ENERGY.tailMap(euToStart);
        if (tail.isEmpty()) key = FUSION_ENERGY.lastLongKey();
        else key = tail.firstLongKey();
        return FUSION_ENERGY.get(key);
    }

    public static long calculateEnergyStorageFactor(int tier, int energyInputAmount) {
        return energyInputAmount * (long) Math.pow(2, tier - LuV) * 10000000L;
    }

    public static Block getCasingState(int tier) {
        return switch (tier) {
            case LuV -> FUSION_CASING.get();
            case ZPM -> FUSION_CASING_MK2.get();
            default -> FUSION_CASING_MK3.get();
        };
    }

    public static Block getCoilState(int tier) {
        if (tier == GTValues.LuV)
            return SUPERCONDUCTING_COIL.get();

        return FUSION_COIL.get();
    }

    public static IFusionCasingType getCasingType(int tier) {
        return switch (tier) {
            case LuV -> FusionCasingBlock.CasingType.FUSION_CASING;
            case ZPM -> FusionCasingBlock.CasingType.FUSION_CASING_MK2;
            case UV -> FusionCasingBlock.CasingType.FUSION_CASING_MK3;
            default -> FusionCasingBlock.CasingType.FUSION_CASING;
        };
    }
}

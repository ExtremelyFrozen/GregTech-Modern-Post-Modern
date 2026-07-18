package com.gregtechceu.gtceu.common.machine.multiblock.electric.gcym;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.machines.GCYMMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.CokeOvenHatch;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ParallelHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.StandardFluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.StandardItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.trait.multiblock.MultiblockFluidRendererTrait;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.gregtechceu.gtceu.api.GTValues.IV;
import static com.gregtechceu.gtceu.api.GTValues.LV;
import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.CHEMICAL_BATH_RECIPES;
import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.ORE_WASHER_RECIPES;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LargeChemicalBathMachineLDLib2UITest {

    private static final Component FIRST_DISPLAY_LINE = Component.literal("first server display line");
    private static final Component SECOND_DISPLAY_LINE = Component.literal("second server display line");
    private static final Set<BlockPos> DEFAULT_FLUID_RENDER_OFFSETS = Set.of(
            new BlockPos(-1, 1, 1), new BlockPos(0, 1, 1), new BlockPos(1, 1, 1),
            new BlockPos(-1, 1, 2), new BlockPos(0, 1, 2), new BlockPos(1, 1, 2),
            new BlockPos(-1, 1, 3), new BlockPos(0, 1, 3), new BlockPos(1, 1, 3),
            new BlockPos(-1, 1, 4), new BlockPos(0, 1, 4), new BlockPos(1, 1, 4),
            new BlockPos(-1, 1, 5), new BlockPos(0, 1, 5), new BlockPos(1, 1, 5));

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LargeChemicalBathMachineLDLib2UI")
    public static void registeredDefinitionUsesConcreteLDLib2ControllerAndKeepsFluidRenderer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MetaMachine registeredMachine = createMachine(GCYMMachines.LARGE_CHEMICAL_BATH);
        helper.assertTrue(registeredMachine.getClass() == LargeChemicalBathMachine.class,
                "Large Chemical Bath definition did not create its concrete controller type");
        helper.assertTrue(registeredMachine instanceof LDLib2MachineUIProvider,
                "Large Chemical Bath definition did not expose the LDLib2 machine UI route");
        helper.assertTrue(registeredMachine instanceof LDLib2FancyActionMachine,
                "Large Chemical Bath definition did not expose Fancy action validation");

        LargeChemicalBathMachine bath = (LargeChemicalBathMachine) registeredMachine;
        MultiblockFluidRendererTrait fluidRenderer = bath.getFluidRendererTrait();
        helper.assertTrue(bath.getTrait(MultiblockFluidRendererTrait.TYPE) == fluidRenderer &&
                bath.getTraits(MultiblockFluidRendererTrait.TYPE).equals(List.of(fluidRenderer)),
                "Large Chemical Bath did not retain its attached fluid renderer trait");
        helper.assertTrue(bath.saveOffsets().equals(DEFAULT_FLUID_RENDER_OFFSETS),
                "Large Chemical Bath did not retain its exact default-facing fluid rendering offsets");

        MutableMachineUIHolder holder = new MutableMachineUIHolder(bath);
        helper.assertTrue(bath.canCreateLDLib2UI(player, holder),
                "Large Chemical Bath rejected its matching controller holder");
        helper.assertTrue(bath.createLDLib2UI(player, holder).getRootElement() instanceof LDLib2FancyMachineUIElement,
                "Large Chemical Bath matching holder did not open an LDLib2 Fancy shell");

        MetaMachine wrongMachine = createMachine(GTMachines.MACERATOR[LV]);
        MutableMachineUIHolder wrongHolder = new MutableMachineUIHolder(wrongMachine);
        helper.assertTrue(!bath.canCreateLDLib2UI(player, wrongHolder),
                "Large Chemical Bath accepted a holder for a different machine definition");
        helper.assertTrue(createUIFails(bath, player, wrongHolder),
                "Large Chemical Bath created an LDLib2 UI for a different machine definition");

        MetaMachine replacement = createMachine(GCYMMachines.LARGE_CHEMICAL_BATH);
        helper.assertTrue(replacement.getBlockPos().equals(bath.getBlockPos()) &&
                replacement.getDefinition() == bath.getDefinition(),
                "Large Chemical Bath stale-holder fixture did not preserve definition and position");
        holder.setMachine(replacement);
        helper.assertTrue(!bath.canCreateLDLib2UI(player, holder),
                "Large Chemical Bath accepted a replacement controller after capability probing");
        helper.assertTrue(createUIFails(bath, player, holder),
                "Large Chemical Bath created an LDLib2 UI for a stale replacement controller");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "LargeChemicalBathMachineLDLib2UI")
    public static void shellPreservesLayoutConfiguratorsTooltipsAndOpeningScopedParts(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        StandardItemBusPartMachine itemInput = requireItemBus(
                placeMachine(helper, new BlockPos(0, 1, 0), GTMachines.ITEM_IMPORT_BUS[LV]));
        StandardFluidHatchPartMachine fluidInput = requireFluidHatch(
                placeMachine(helper, new BlockPos(1, 1, 0), GTMachines.FLUID_IMPORT_HATCH[LV]));
        MaintenanceHatchPartMachine maintenance = requireMaintenanceHatch(
                placeMachine(helper, new BlockPos(2, 1, 0), GTMachines.MAINTENANCE_HATCH));
        ParallelHatchPartMachine parallel = requireParallelHatch(
                placeMachine(helper, new BlockPos(3, 1, 0), GCYMMachines.PARALLEL_HATCH[IV]));
        List<IMultiPart> parts = List.of(itemInput, fluidInput, maintenance, parallel);
        TestLargeChemicalBathMachine bath = new TestLargeChemicalBathMachine(parts);
        MachineUIHolder holder = new MutableMachineUIHolder(bath);

        boolean maintenanceEnabled = ConfigHolder.INSTANCE.machines.enableMaintenance;
        ConfigHolder.INSTANCE.machines.enableMaintenance = true;
        try {
            LDLib2FancyMachineUIElement firstShell = requireFancyShell(
                    bath.createLDLib2UI(player, holder).getRootElement());
            LDLib2FancyMachineUIElement secondShell = requireFancyShell(
                    bath.createLDLib2UI(player, holder).getRootElement());

            helper.assertTrue(firstShell.getHolder() == holder,
                    "Large Chemical Bath Fancy shell did not retain its controller holder");
            helper.assertTrue(firstShell.getConfiguratorPanel().getChildren().size() == 3,
                    "Large Chemical Bath did not retain voiding, batch, and working configurators");
            helper.assertTrue(firstShell.getSideTabsElement().getChildren().size() == 3,
                    "Large Chemical Bath did not retain its machine-mode and contextual direction pages");
            helper.assertTrue(firstShell.getTooltipsPanel().getChildren().size() == 1,
                    "Large Chemical Bath did not attach the maintenance warning tooltip");
            helper.assertTrue(bath.getTrait(MultiblockFluidRendererTrait.TYPE) == bath.getFluidRendererTrait(),
                    "Large Chemical Bath UI migration replaced its fluid renderer trait");

            UIElement firstPageContainer = firstShell.getChildren().getFirst();
            UIElement secondPageContainer = secondShell.getChildren().getFirst();
            helper.assertTrue(firstPageContainer.getChildren().size() == parts.size() + 1,
                    "Large Chemical Bath silently dropped an actual multiblock part page");
            helper.assertTrue(secondPageContainer.getChildren().size() == parts.size() + 1,
                    "Large Chemical Bath reused an incomplete part-page set on a later opening");
            for (int index = 0; index < firstPageContainer.getChildren().size(); index++) {
                helper.assertTrue(firstPageContainer.getChildren().get(index) !=
                        secondPageContainer.getChildren().get(index),
                        "Large Chemical Bath reused a page element across UI openings");
            }

            clickButton(firstShell.getSideTabsElement().getChildren().get(1));
            clickButton(secondShell.getSideTabsElement().getChildren().get(1));
            helper.assertTrue(firstPageContainer.getChildren().size() == parts.size() + 2 &&
                    secondPageContainer.getChildren().size() == parts.size() + 2,
                    "Large Chemical Bath machine-mode tab did not create one page per UI opening");
            UIElement firstModePage = firstPageContainer.getChildren().getLast();
            UIElement secondModePage = secondPageContainer.getChildren().getLast();
            helper.assertTrue(firstModePage != secondModePage,
                    "Large Chemical Bath reused its machine-mode page across UI openings");
            UITemplate.LDLib2Bounds firstModeBounds = UITemplate.getLDLib2Bounds(firstModePage);
            helper.assertTrue(firstModeBounds.width() == 140 && firstModeBounds.height() == 44 &&
                    firstModePage.getChildren().size() == 4 &&
                    firstModePage.getChildren().stream().filter(GTButtonElement.class::isInstance).count() == 2,
                    "Large Chemical Bath machine-mode page did not expose both registered recipe types");

            UIElement mainPage = firstPageContainer.getChildren().getFirst();
            UITemplate.LDLib2Bounds mainPageBounds = UITemplate.getLDLib2Bounds(mainPage);
            helper.assertTrue(mainPageBounds.width() == 190 && mainPageBounds.height() == 125,
                    "Large Chemical Bath main page did not preserve its 190x125 body");
            helper.assertTrue(mainPage.getChildren().size() == 1 &&
                    mainPage.getChildren().getFirst() instanceof GTScrollerViewElement,
                    "Large Chemical Bath main page did not create one display scroller");
            UIElement scroller = mainPage.getChildren().getFirst();
            UITemplate.LDLib2Bounds scrollerBounds = UITemplate.getLDLib2Bounds(scroller);
            helper.assertTrue(scrollerBounds.x() == 4 && scrollerBounds.y() == 4 &&
                    scrollerBounds.width() == 182 && scrollerBounds.height() == 117,
                    "Large Chemical Bath display scroller did not preserve its 4,4 182x117 bounds");
            List<UIElement> descendants = descendants(mainPage);
            helper.assertTrue(descendants.stream().filter(GTLabelElement.class::isInstance).count() == 1,
                    "Large Chemical Bath display did not preserve its title label");
            List<GTComponentPanelElement> panels = descendants.stream()
                    .filter(GTComponentPanelElement.class::isInstance)
                    .map(GTComponentPanelElement.class::cast)
                    .toList();
            helper.assertTrue(panels.size() == 1 && panels.getFirst().getMaxWidthLimit() == 200,
                    "Large Chemical Bath display panel did not preserve its maximum text width");
        } finally {
            ConfigHolder.INSTANCE.machines.enableMaintenance = maintenanceEnabled;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LargeChemicalBathMachineLDLib2UI")
    public static void unsupportedPartsFailFastAndSnapshotFollowsServerLifecycle(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        CokeOvenHatch unsupportedPart = requireCokeOvenHatch(createMachine(GTMachines.COKE_OVEN_HATCH));
        TestLargeChemicalBathMachine invalidBath = new TestLargeChemicalBathMachine(List.of(unsupportedPart));
        helper.assertTrue(createUIFails(invalidBath, player, new MutableMachineUIHolder(invalidBath)),
                "Large Chemical Bath silently omitted a part without an LDLib2 contextual page");

        TestLargeChemicalBathMachine bath = new TestLargeChemicalBathMachine(List.of());
        bath.setFormedForTest(true);
        bath.setServerDisplay(List.of(FIRST_DISPLAY_LINE));
        bath.refreshDisplaySnapshot();
        List<Component> firstSnapshot = bath.getDisplaySnapshot();
        helper.assertTrue(firstSnapshot.equals(List.of(FIRST_DISPLAY_LINE)),
                "Large Chemical Bath snapshot did not collect server display text");
        bath.refreshDisplaySnapshot();
        helper.assertTrue(bath.getDisplaySnapshot() == firstSnapshot,
                "Large Chemical Bath replaced an unchanged display snapshot instance");
        boolean immutable = false;
        try {
            firstSnapshot.add(Component.literal("mutation"));
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        helper.assertTrue(immutable, "Large Chemical Bath published a mutable display snapshot");
        List<Component> publishedDisplay = new ArrayList<>();
        bath.addDisplayText(publishedDisplay);
        helper.assertTrue(publishedDisplay.equals(firstSnapshot),
                "Large Chemical Bath client display did not read only the synced snapshot");

        bath.getDisplaySnapshotSubscription().updateSubscription();
        TickableSubscription snapshotTick = bath.getCapturedSubscription();
        helper.assertTrue(snapshotTick != null && snapshotTick.isStillSubscribed(),
                "Large Chemical Bath did not subscribe its formed server display refresh");
        bath.setServerDisplay(List.of(SECOND_DISPLAY_LINE));
        snapshotTick.run();
        helper.assertTrue(bath.getDisplaySnapshot().equals(List.of(SECOND_DISPLAY_LINE)),
                "Large Chemical Bath display subscription did not refresh server text");
        bath.setFormedForTest(false);
        bath.getDisplaySnapshotSubscription().updateSubscription();
        helper.assertTrue(!snapshotTick.isStillSubscribed(),
                "Large Chemical Bath retained its display subscription after becoming unformed");

        assertFormationStartsAndInvalidationStopsSnapshotRefresh(helper);
        assertInvalidationPreservesUnformedSnapshot(helper);
        assertPartUnloadClearsSnapshot(helper);
        assertControllerUnloadClearsSnapshot(helper);
        assertLiveMachineModeFollowsActiveRecipeType(helper);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LargeChemicalBathMachineLDLib2UI", timeoutTicks = 20)
    public static void onLoadInitializesDisplayRefreshUntilControllerUnload(GameTestHelper helper) {
        TestLargeChemicalBathMachine unformedBath = new TestLargeChemicalBathMachine(List.of());
        unformedBath.setLevel(helper.getLevel());
        unformedBath.useLiveDisplayContract();
        unformedBath.onLoad();
        helper.assertTrue(unformedBath.getDisplaySnapshot().equals(List.of(invalidStructureLine())),
                "Large Chemical Bath onLoad did not publish the legacy invalid-structure display");
        unformedBath.onUnload();

        TestLargeChemicalBathMachine formedBath = new TestLargeChemicalBathMachine(List.of());
        formedBath.setLevel(helper.getLevel());
        formedBath.setFormedForTest(true);
        formedBath.setServerDisplay(List.of(FIRST_DISPLAY_LINE));
        formedBath.onLoad();
        int refreshesAfterLoad = formedBath.getDisplayRefreshCount();

        helper.runAfterDelay(3, () -> {
            formedBath.serverTick();
            int refreshesWhileLoaded = formedBath.getDisplayRefreshCount();
            helper.assertTrue(refreshesWhileLoaded > refreshesAfterLoad,
                    "Large Chemical Bath onLoad did not initialize its display subscription");
            formedBath.onUnload();
            helper.assertTrue(formedBath.getDisplaySnapshot().isEmpty(),
                    "Large Chemical Bath retained its display snapshot after controller unload");
            helper.runAfterDelay(3, () -> {
                formedBath.serverTick();
                helper.assertTrue(formedBath.getDisplayRefreshCount() == refreshesWhileLoaded,
                        "Large Chemical Bath display subscription kept running after controller unload");
                helper.succeed();
            });
        });
    }

    private static boolean createUIFails(LargeChemicalBathMachine bath, ServerPlayer player,
                                         MachineUIHolder holder) {
        try {
            bath.createLDLib2UI(player, holder);
            return false;
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return true;
        }
    }

    private static void assertFormationStartsAndInvalidationStopsSnapshotRefresh(GameTestHelper helper) {
        TestLargeChemicalBathMachine bath = new TestLargeChemicalBathMachine(List.of());
        bath.setLevel(helper.getLevel());
        bath.setServerDisplay(List.of(FIRST_DISPLAY_LINE));
        bath.formStructure(LargeChemicalBathMachine.DEFAULT_STRUCTURE);
        TickableSubscription subscription = bath.getCapturedSubscription();
        helper.assertTrue(bath.isFormed() && bath.getDisplaySnapshot().equals(List.of(FIRST_DISPLAY_LINE)),
                "Large Chemical Bath formation did not publish its server display snapshot");
        helper.assertTrue(subscription != null && subscription.isStillSubscribed(),
                "Large Chemical Bath formation did not start its display subscription");
        bath.invalidateStructure(LargeChemicalBathMachine.DEFAULT_STRUCTURE);
        helper.assertTrue(!subscription.isStillSubscribed(),
                "Large Chemical Bath invalidation retained its display subscription");
    }

    private static void assertInvalidationPreservesUnformedSnapshot(GameTestHelper helper) {
        TestLargeChemicalBathMachine bath = subscribedBath();
        TickableSubscription subscription = bath.getCapturedSubscription();
        bath.useLiveDisplayContract();
        bath.invalidateStructure(LargeChemicalBathMachine.DEFAULT_STRUCTURE);
        helper.assertTrue(bath.getDisplaySnapshot().equals(List.of(invalidStructureLine())),
                "Large Chemical Bath lost the legacy invalid-structure display after invalidation");
        helper.assertTrue(subscription != null && !subscription.isStillSubscribed(),
                "Large Chemical Bath retained its display subscription after structure invalidation");
    }

    private static void assertPartUnloadClearsSnapshot(GameTestHelper helper) {
        TestLargeChemicalBathMachine bath = subscribedBath();
        TickableSubscription subscription = bath.getCapturedSubscription();
        bath.onPartUnload();
        assertRuntimeDisplayCleared(helper, bath, subscription, "part unload");
    }

    private static void assertControllerUnloadClearsSnapshot(GameTestHelper helper) {
        TestLargeChemicalBathMachine bath = subscribedBath();
        TickableSubscription subscription = bath.getCapturedSubscription();
        bath.onUnload();
        assertRuntimeDisplayCleared(helper, bath, subscription, "controller unload");
    }

    private static void assertLiveMachineModeFollowsActiveRecipeType(GameTestHelper helper) {
        TestLargeChemicalBathMachine bath = new TestLargeChemicalBathMachine(List.of());
        bath.setLevel(helper.getLevel());
        bath.setFormedForTest(true);
        bath.useLiveDisplayContract();
        helper.assertTrue(bath.getRecipeTypes().length == 2 &&
                bath.getRecipeTypes()[0] == CHEMICAL_BATH_RECIPES &&
                bath.getRecipeTypes()[1] == ORE_WASHER_RECIPES &&
                bath.getActiveRecipeType() == 0,
                "Large Chemical Bath did not retain its registered recipe type order");
        bath.refreshDisplaySnapshot();
        List<Component> chemicalBathSnapshot = bath.getDisplaySnapshot();
        TranslatableContents chemicalBathMode = requireTranslatedLine(chemicalBathSnapshot,
                "gtpm.gui.machinemode");

        bath.setActiveRecipeType(1);
        bath.refreshDisplaySnapshot();
        List<Component> oreWasherSnapshot = bath.getDisplaySnapshot();
        TranslatableContents oreWasherMode = requireTranslatedLine(oreWasherSnapshot, "gtpm.gui.machinemode");

        helper.assertTrue(bath.getActiveRecipeType() == 1 && bath.getRecipeType() == ORE_WASHER_RECIPES,
                "Large Chemical Bath did not select its registered Ore Washer recipe type");
        helper.assertTrue(chemicalBathMode.getArgs().length == 1 && oreWasherMode.getArgs().length == 1 &&
                !chemicalBathMode.getArgs()[0].equals(oreWasherMode.getArgs()[0]) &&
                !chemicalBathSnapshot.equals(oreWasherSnapshot),
                "Large Chemical Bath live display snapshot did not follow its active recipe type");
    }

    private static TranslatableContents requireTranslatedLine(List<Component> lines, String translationKey) {
        for (Component line : lines) {
            if (line.getContents() instanceof TranslatableContents contents &&
                    contents.getKey().equals(translationKey)) {
                return contents;
            }
        }
        throw new IllegalStateException("Display snapshot did not contain translation key: " + translationKey);
    }

    private static TestLargeChemicalBathMachine subscribedBath() {
        TestLargeChemicalBathMachine bath = new TestLargeChemicalBathMachine(List.of());
        bath.setFormedForTest(true);
        bath.setServerDisplay(List.of(FIRST_DISPLAY_LINE));
        bath.refreshDisplaySnapshot();
        bath.getDisplaySnapshotSubscription().updateSubscription();
        return bath;
    }

    private static Component invalidStructureLine() {
        Component hover = Component.translatable("gtpm.multiblock.invalid_structure.tooltip")
                .withStyle(ChatFormatting.GRAY);
        return Component.translatable("gtpm.multiblock.invalid_structure")
                .withStyle(ChatFormatting.RED)
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static void assertRuntimeDisplayCleared(GameTestHelper helper,
                                                    TestLargeChemicalBathMachine bath,
                                                    @Nullable TickableSubscription subscription,
                                                    String lifecycleEvent) {
        helper.assertTrue(bath.getDisplaySnapshot().isEmpty(),
                "Large Chemical Bath retained display text after " + lifecycleEvent);
        helper.assertTrue(subscription != null && !subscription.isStillSubscribed(),
                "Large Chemical Bath retained its display subscription after " + lifecycleEvent);
    }

    private static LDLib2FancyMachineUIElement requireFancyShell(UIElement root) {
        if (!(root instanceof LDLib2FancyMachineUIElement shell)) {
            throw new IllegalStateException("Large Chemical Bath did not create an LDLib2 Fancy shell.");
        }
        return shell;
    }

    private static MetaMachine placeMachine(GameTestHelper helper, BlockPos pos, MachineDefinition definition) {
        helper.setBlock(pos, definition.getBlock());
        if (!(helper.getBlockEntity(pos) instanceof MetaMachine machine)) {
            throw new IllegalStateException("Placed machine block did not create a MetaMachine: " +
                    definition.getId());
        }
        return machine;
    }

    private static MetaMachine createMachine(MachineDefinition definition) {
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (machine == null) {
            throw new IllegalStateException("Machine definition did not create a MetaMachine: " + definition.getId());
        }
        return machine;
    }

    private static StandardItemBusPartMachine requireItemBus(MetaMachine machine) {
        if (!(machine instanceof StandardItemBusPartMachine itemBus)) {
            throw new IllegalStateException("Item Bus definition did not create its standard concrete type.");
        }
        return itemBus;
    }

    private static StandardFluidHatchPartMachine requireFluidHatch(MetaMachine machine) {
        if (!(machine instanceof StandardFluidHatchPartMachine fluidHatch)) {
            throw new IllegalStateException("Fluid Hatch definition did not create its standard concrete type.");
        }
        return fluidHatch;
    }

    private static MaintenanceHatchPartMachine requireMaintenanceHatch(MetaMachine machine) {
        if (!(machine instanceof MaintenanceHatchPartMachine maintenanceHatch)) {
            throw new IllegalStateException("Maintenance Hatch definition did not create its expected type.");
        }
        return maintenanceHatch;
    }

    private static ParallelHatchPartMachine requireParallelHatch(MetaMachine machine) {
        if (!(machine instanceof ParallelHatchPartMachine parallelHatch)) {
            throw new IllegalStateException("Parallel Hatch definition did not create its expected type.");
        }
        return parallelHatch;
    }

    private static CokeOvenHatch requireCokeOvenHatch(MetaMachine machine) {
        if (!(machine instanceof CokeOvenHatch cokeOvenHatch)) {
            throw new IllegalStateException("Coke Oven Hatch definition did not create its expected type.");
        }
        return cokeOvenHatch;
    }

    private static List<UIElement> descendants(UIElement root) {
        List<UIElement> descendants = new ArrayList<>();
        collectDescendants(root, descendants);
        return descendants;
    }

    private static void clickButton(UIElement button) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = button;
        event.button = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
    }

    private static void collectDescendants(UIElement root, List<UIElement> descendants) {
        for (UIElement child : root.getChildren()) {
            descendants.add(child);
            collectDescendants(child, descendants);
        }
    }

    private static final class MutableMachineUIHolder implements MachineUIHolder {

        private MetaMachine machine;

        private MutableMachineUIHolder(MetaMachine machine) {
            this.machine = machine;
        }

        private void setMachine(MetaMachine machine) {
            this.machine = machine;
        }

        @Override
        public BlockPos getPos() {
            return machine.getBlockPos();
        }

        @Override
        public ResourceLocation getMachineDefinitionId() {
            return machine.getDefinition().getId();
        }

        @Override
        public MetaMachine getMachine() {
            return machine;
        }
    }

    private static final class TestLargeChemicalBathMachine extends LargeChemicalBathMachine {

        private final List<IMultiPart> parts;
        private List<Component> serverDisplay = List.of();
        private @Nullable TickableSubscription capturedSubscription;
        private boolean useLiveDisplayContract;
        private int displayRefreshCount;

        private TestLargeChemicalBathMachine(List<IMultiPart> parts) {
            super(info(GCYMMachines.LARGE_CHEMICAL_BATH));
            this.parts = List.copyOf(parts);
        }

        @Override
        public List<IMultiPart> getParts() {
            return parts;
        }

        @Override
        protected void collectServerDisplayText(List<Component> textList) {
            if (useLiveDisplayContract) {
                super.collectServerDisplayText(textList);
            } else {
                textList.addAll(serverDisplay);
            }
        }

        @Override
        void refreshDisplaySnapshot() {
            displayRefreshCount++;
            super.refreshDisplaySnapshot();
        }

        @Override
        public TickableSubscription subscribeServerTick(Runnable runnable) {
            capturedSubscription = super.subscribeServerTick(runnable);
            if (capturedSubscription == null) {
                throw new IllegalStateException("Server-side display test did not create a tick subscription.");
            }
            return capturedSubscription;
        }

        @Override
        public TickableSubscription subscribeServerTick(@Nullable TickableSubscription last, Runnable runnable) {
            capturedSubscription = super.subscribeServerTick(last, runnable);
            if (capturedSubscription == null) {
                throw new IllegalStateException("Server-side display test did not create a tick subscription.");
            }
            return capturedSubscription;
        }

        private void setServerDisplay(List<Component> serverDisplay) {
            this.serverDisplay = List.copyOf(serverDisplay);
        }

        private void useLiveDisplayContract() {
            useLiveDisplayContract = true;
        }

        private void setFormedForTest(boolean formed) {
            isFormed = formed;
        }

        private @Nullable TickableSubscription getCapturedSubscription() {
            return capturedSubscription;
        }

        private int getDisplayRefreshCount() {
            return displayRefreshCount;
        }
    }

    private static BlockEntityCreationInfo info(MachineDefinition definition) {
        return new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.defaultBlockState());
    }
}

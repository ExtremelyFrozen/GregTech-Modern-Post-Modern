package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
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
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.machines.GCYMMachines;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.CokeOvenHatch;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ParallelHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.StandardFluidHatchPartMachine;
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
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.VoidFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.gregtechceu.gtceu.api.GTValues.HV;
import static com.gregtechceu.gtceu.api.GTValues.IV;
import static com.gregtechceu.gtceu.api.GTValues.LV;
import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.DISTILLATION_RECIPES;
import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.DISTILLERY_RECIPES;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class DistillationTowerMachineLDLib2UITest {

    private static final Component FIRST_DISPLAY_LINE = Component.literal("first server display line");
    private static final Component SECOND_DISPLAY_LINE = Component.literal("second server display line");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DistillationTowerMachineLDLib2UI")
    public static void registeredDefinitionsUseConcreteLDLib2ControllerAndKeepDistillationLogic(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        DistillationTowerMachine tower = requireTower(createMachine(GTMultiMachines.DISTILLATION_TOWER));
        DistillationTowerMachine largeDistillery = requireTower(createMachine(GCYMMachines.LARGE_DISTILLERY));

        assertRegisteredController(helper, tower, 1);
        assertRegisteredController(helper, largeDistillery, 2);
        helper.assertTrue(tower.getRecipeTypes()[0] == DISTILLATION_RECIPES,
                "Distillation Tower did not retain its sole registered recipe type");
        helper.assertTrue(largeDistillery.getRecipeTypes()[0] == DISTILLATION_RECIPES &&
                largeDistillery.getRecipeTypes()[1] == DISTILLERY_RECIPES,
                "Large Distillery did not retain its registered recipe type order");

        StandardFluidHatchPartMachine lower = requireFluidHatch(
                createMachineAt(GTMachines.FLUID_EXPORT_HATCH[HV], new BlockPos(0, 1, 0)));
        StandardFluidHatchPartMachine upper = requireFluidHatch(
                createMachineAt(GTMachines.FLUID_EXPORT_HATCH[HV], new BlockPos(0, 3, 0)));
        helper.assertTrue(tower.getPartSorter().compare(lower, upper) < 0 &&
                largeDistillery.getPartSorter().compare(lower, upper) < 0,
                "A registered distillation controller lost its ascending Y part ordering");

        assertHolderIdentity(helper, tower, player, GTMultiMachines.DISTILLATION_TOWER);
        assertHolderIdentity(helper, largeDistillery, player, GCYMMachines.LARGE_DISTILLERY);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "DistillationTowerMachineLDLib2UI")
    public static void shellsPreserveDefinitionSpecificModeLayoutAndOpeningScopedPartOrder(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ParallelHatchPartMachine parallel = requireParallelHatch(
                placeMachine(helper, new BlockPos(0, 1, 0), GCYMMachines.PARALLEL_HATCH[IV]));
        StandardFluidHatchPartMachine fluidOutput = requireFluidHatch(
                placeMachine(helper, new BlockPos(1, 1, 0), GTMachines.FLUID_EXPORT_HATCH[LV]));
        MaintenanceHatchPartMachine maintenance = requireMaintenanceHatch(
                placeMachine(helper, new BlockPos(2, 1, 0), GTMachines.MAINTENANCE_HATCH));
        List<IMultiPart> parts = List.of(parallel, fluidOutput, maintenance);
        TestDistillationTowerMachine tower = new TestDistillationTowerMachine(
                GTMultiMachines.DISTILLATION_TOWER, parts);
        TestDistillationTowerMachine largeDistillery = new TestDistillationTowerMachine(
                GCYMMachines.LARGE_DISTILLERY, parts);

        boolean maintenanceEnabled = ConfigHolder.INSTANCE.machines.enableMaintenance;
        ConfigHolder.INSTANCE.machines.enableMaintenance = true;
        try {
            LDLib2FancyMachineUIElement towerShell = requireFancyShell(tower.createLDLib2UI(player,
                    new MutableMachineUIHolder(tower)).getRootElement());
            MutableMachineUIHolder largeHolder = new MutableMachineUIHolder(largeDistillery);
            LDLib2FancyMachineUIElement firstLargeShell = requireFancyShell(
                    largeDistillery.createLDLib2UI(player, largeHolder).getRootElement());
            LDLib2FancyMachineUIElement secondLargeShell = requireFancyShell(
                    largeDistillery.createLDLib2UI(player, largeHolder).getRootElement());

            helper.assertTrue(towerShell.getSideTabsElement().getChildren().size() == 2,
                    "Single-mode Distillation Tower created an unnecessary machine-mode page");
            helper.assertTrue(firstLargeShell.getSideTabsElement().getChildren().size() == 3,
                    "Large Distillery did not expose its machine-mode and directional pages");
            helper.assertTrue(towerShell.getConfiguratorPanel().getChildren().size() == 3 &&
                    firstLargeShell.getConfiguratorPanel().getChildren().size() == 3,
                    "A distillation controller lost its voiding, batch, or working configurator");
            helper.assertTrue(towerShell.getTooltipsPanel().getChildren().size() == 1 &&
                    firstLargeShell.getTooltipsPanel().getChildren().size() == 1,
                    "A distillation controller did not attach its maintenance warning tooltip");

            UIElement firstPages = firstLargeShell.getChildren().getFirst();
            UIElement secondPages = secondLargeShell.getChildren().getFirst();
            helper.assertTrue(firstPages.getChildren().size() == parts.size() + 1 &&
                    secondPages.getChildren().size() == parts.size() + 1,
                    "Large Distillery silently dropped a real multiblock part page");
            UITemplate.LDLib2Bounds firstItemBusBounds = UITemplate.getLDLib2Bounds(firstPages.getChildren().get(1));
            UITemplate.LDLib2Bounds firstFluidHatchBounds = UITemplate.getLDLib2Bounds(firstPages.getChildren().get(2));
            helper.assertTrue(firstItemBusBounds.width() == 100 && firstItemBusBounds.height() == 20 &&
                    firstFluidHatchBounds.width() == 89 && firstFluidHatchBounds.height() == 63,
                    "Large Distillery did not preserve getParts() order for contextual pages");
            for (int index = 0; index < firstPages.getChildren().size(); index++) {
                helper.assertTrue(firstPages.getChildren().get(index) != secondPages.getChildren().get(index),
                        "Large Distillery reused a page element across UI openings");
            }

            clickButton(firstLargeShell.getSideTabsElement().getChildren().get(1));
            clickButton(secondLargeShell.getSideTabsElement().getChildren().get(1));
            UIElement firstModePage = firstPages.getChildren().getLast();
            UIElement secondModePage = secondPages.getChildren().getLast();
            UITemplate.LDLib2Bounds firstModeBounds = UITemplate.getLDLib2Bounds(firstModePage);
            helper.assertTrue(firstModePage != secondModePage &&
                    firstModeBounds.width() == 140 && firstModeBounds.height() == 44 &&
                    firstModePage.getChildren().size() == 4 &&
                    firstModePage.getChildren().stream().filter(GTButtonElement.class::isInstance).count() == 2,
                    "Large Distillery machine-mode page was not opening-scoped with two registered modes");

            clickButton(firstLargeShell.getSideTabsElement().getChildren().get(2));
            clickButton(secondLargeShell.getSideTabsElement().getChildren().get(2));
            UIElement firstDirectionalPage = firstPages.getChildren().getLast();
            UIElement secondDirectionalPage = secondPages.getChildren().getLast();
            helper.assertTrue(firstDirectionalPage != secondDirectionalPage &&
                    firstDirectionalPage != firstModePage && secondDirectionalPage != secondModePage,
                    "Large Distillery reused its directional page across UI openings");

            UIElement mainPage = firstPages.getChildren().getFirst();
            assertMainPageLayout(helper, mainPage);
        } finally {
            ConfigHolder.INSTANCE.machines.enableMaintenance = maintenanceEnabled;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "DistillationTowerMachineLDLib2UI")
    public static void fluidMappingRecipeDistributionAndUiKeepExistingLogicState(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        StandardFluidHatchPartMachine lower = requireFluidHatch(
                placeMachine(helper, new BlockPos(0, 1, 0), GTMachines.FLUID_EXPORT_HATCH[HV]));
        StandardFluidHatchPartMachine upper = requireFluidHatch(
                placeMachine(helper, new BlockPos(0, 3, 0), GTMachines.FLUID_EXPORT_HATCH[HV]));
        TestDistillationTowerMachine tower = new TestDistillationTowerMachine(
                GTMultiMachines.DISTILLATION_TOWER, helper.absolutePos(BlockPos.ZERO), List.of(lower, upper));
        tower.setLevel(helper.getLevel());
        tower.setServerDisplay(List.of(FIRST_DISPLAY_LINE));
        tower.formStructure(DistillationTowerMachine.DEFAULT_STRUCTURE);

        List<IFluidHandler> mappedOutputs = tower.getFluidOutputs();
        IFluidHandler firstValid = tower.getFirstValid();
        DistillationTowerMachine.DistillationTowerLogic logic = tower.getRecipeLogic();
        TickableSubscription formedSubscription = tower.getCapturedSubscription();
        helper.assertTrue(mappedOutputs.size() == 3 && mappedOutputs.get(1) == VoidFluidHandler.INSTANCE &&
                firstValid == mappedOutputs.getFirst() && mappedOutputs.get(2) != VoidFluidHandler.INSTANCE,
                "Distillation Tower did not retain its Y-indexed output map and first valid handler");
        helper.assertTrue(tower.getDisplaySnapshot().equals(List.of(FIRST_DISPLAY_LINE)) &&
                formedSubscription != null && formedSubscription.isStillSubscribed(),
                "Successful Distillation Tower formation did not start its display snapshot refresh");

        FluidStack firstFraction = GTMaterials.Water.getFluid(11);
        FluidStack skippedFraction = GTMaterials.Hydrogen.getFluid(13);
        FluidStack thirdFraction = GTMaterials.Oxygen.getFluid(17);
        GTRecipe towerRecipe = DISTILLATION_RECIPES.recipeBuilder(GTCEu.id("test_ldlib2_distillation_distribution"))
                .outputFluids(firstFraction, skippedFraction, thirdFraction)
                .duration(1)
                .EUt(1)
                .build();
        ActionResult towerOutput = logic.handleRecipeIO(towerRecipe, IO.OUT);
        helper.assertTrue(towerOutput.isSuccess() && fluidMatches(lower.tank.getFluidInTank(0), firstFraction) &&
                fluidMatches(upper.tank.getFluidInTank(0), thirdFraction),
                "Distillation recipe no longer distributed fractions by output height");

        lower.tank.setFluidInTank(0, FluidStack.EMPTY);
        upper.tank.setFluidInTank(0, FluidStack.EMPTY);
        FluidStack distilledFraction = GTMaterials.Nitrogen.getFluid(19);
        GTRecipe distilleryRecipe = DISTILLERY_RECIPES.recipeBuilder(GTCEu.id("test_ldlib2_distillery_distribution"))
                .outputFluids(distilledFraction)
                .duration(1)
                .EUt(1)
                .build();
        ActionResult distilleryOutput = logic.handleRecipeIO(distilleryRecipe, IO.OUT);
        helper.assertTrue(distilleryOutput.isSuccess() &&
                fluidMatches(lower.tank.getFluidInTank(0), distilledFraction) && upper.tank.isEmpty(),
                "Distillery recipe no longer routed its output to firstValid");

        logic.workingRecipe = towerRecipe;
        tower.refreshDisplaySnapshot();
        tower.createLDLib2UI(player, new MutableMachineUIHolder(tower));
        tower.onLoad();
        helper.assertTrue(tower.getRecipeLogic() == logic && logic.getLastRecipe() == towerRecipe &&
                tower.getFluidOutputs() == mappedOutputs && tower.getFirstValid() == firstValid,
                "LDLib2 UI or snapshot lifecycle replaced existing DistillationTowerLogic state");

        TickableSubscription subscription = tower.getCapturedSubscription();
        tower.useLiveDisplayContract();
        tower.invalidateStructure(DistillationTowerMachine.DEFAULT_STRUCTURE);
        helper.assertTrue(tower.getFluidOutputs() == null && tower.getFirstValid() == null,
                "Distillation Tower invalidation did not clear its output routing state");
        helper.assertTrue(tower.getDisplaySnapshot().equals(List.of(invalidStructureLine())) &&
                subscription != null && !subscription.isStillSubscribed(),
                "Distillation Tower invalidation did not publish invalid_structure and stop refresh");
        tower.onUnload();
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DistillationTowerMachineLDLib2UI")
    public static void unsupportedPartsFailFastAndSnapshotFollowsFullServerLifecycle(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        CokeOvenHatch unsupportedPart = requireCokeOvenHatch(createMachine(GTMachines.COKE_OVEN_HATCH));
        TestDistillationTowerMachine invalidTower = new TestDistillationTowerMachine(
                GTMultiMachines.DISTILLATION_TOWER, List.of(unsupportedPart));
        helper.assertTrue(createUIFails(invalidTower, player, new MutableMachineUIHolder(invalidTower)),
                "Distillation Tower silently omitted a part without an LDLib2 contextual page");

        TestDistillationTowerMachine tower = new TestDistillationTowerMachine(
                GCYMMachines.LARGE_DISTILLERY, List.of());
        tower.setFormedForTest(true);
        tower.setServerDisplay(List.of(FIRST_DISPLAY_LINE));
        tower.refreshDisplaySnapshot();
        List<Component> firstSnapshot = tower.getDisplaySnapshot();
        helper.assertTrue(firstSnapshot.equals(List.of(FIRST_DISPLAY_LINE)),
                "Distillation controller snapshot did not collect server display text");
        tower.refreshDisplaySnapshot();
        helper.assertTrue(tower.getDisplaySnapshot() == firstSnapshot,
                "Distillation controller replaced an unchanged display snapshot instance");
        boolean immutable = false;
        try {
            firstSnapshot.add(Component.literal("mutation"));
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        helper.assertTrue(immutable, "Distillation controller published a mutable display snapshot");

        List<Component> publishedDisplay = new ArrayList<>();
        tower.addDisplayText(publishedDisplay);
        helper.assertTrue(publishedDisplay.equals(firstSnapshot),
                "Distillation controller client display did not read only the synced snapshot");
        tower.getDisplaySnapshotSubscription().updateSubscription();
        TickableSubscription snapshotTick = tower.getCapturedSubscription();
        helper.assertTrue(snapshotTick != null && snapshotTick.isStillSubscribed(),
                "Distillation controller did not subscribe its formed server display refresh");
        tower.setServerDisplay(List.of(SECOND_DISPLAY_LINE));
        snapshotTick.run();
        helper.assertTrue(tower.getDisplaySnapshot().equals(List.of(SECOND_DISPLAY_LINE)),
                "Distillation controller display subscription did not refresh server text");

        assertPartUnloadClearsSnapshot(helper);
        assertControllerUnloadClearsSnapshot(helper);
        assertLiveMachineModeFollowsActiveRecipeType(helper);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DistillationTowerMachineLDLib2UI", timeoutTicks = 20)
    public static void onLoadInitializesDisplayRefreshUntilControllerUnload(GameTestHelper helper) {
        TestDistillationTowerMachine unformedTower = new TestDistillationTowerMachine(
                GTMultiMachines.DISTILLATION_TOWER, List.of());
        unformedTower.setLevel(helper.getLevel());
        unformedTower.useLiveDisplayContract();
        DistillationTowerMachine.DistillationTowerLogic unformedLogic = unformedTower.getRecipeLogic();
        unformedTower.onLoad();
        helper.assertTrue(unformedTower.getDisplaySnapshot().equals(List.of(invalidStructureLine())) &&
                unformedTower.getRecipeLogic() == unformedLogic,
                "Distillation Tower onLoad did not publish invalid_structure without replacing recipe logic");
        unformedTower.onUnload();

        TestDistillationTowerMachine formedTower = new TestDistillationTowerMachine(
                GCYMMachines.LARGE_DISTILLERY, List.of());
        formedTower.setLevel(helper.getLevel());
        formedTower.setFormedForTest(true);
        formedTower.setServerDisplay(List.of(FIRST_DISPLAY_LINE));
        DistillationTowerMachine.DistillationTowerLogic formedLogic = formedTower.getRecipeLogic();
        formedTower.onLoad();
        int refreshesAfterLoad = formedTower.getDisplayRefreshCount();

        helper.runAfterDelay(3, () -> {
            formedTower.serverTick();
            int refreshesWhileLoaded = formedTower.getDisplayRefreshCount();
            helper.assertTrue(refreshesWhileLoaded > refreshesAfterLoad &&
                    formedTower.getRecipeLogic() == formedLogic,
                    "Large Distillery onLoad did not initialize refresh while preserving recipe logic");
            formedTower.onUnload();
            helper.assertTrue(formedTower.getDisplaySnapshot().isEmpty(),
                    "Large Distillery retained its display snapshot after controller unload");
            helper.runAfterDelay(3, () -> {
                formedTower.serverTick();
                helper.assertTrue(formedTower.getDisplayRefreshCount() == refreshesWhileLoaded,
                        "Large Distillery display subscription kept running after controller unload");
                helper.succeed();
            });
        });
    }

    private static void assertRegisteredController(GameTestHelper helper, DistillationTowerMachine machine,
                                                   int recipeTypeCount) {
        helper.assertTrue(machine.getClass() == DistillationTowerMachine.class,
                "Registered distillation definition did not create its concrete controller type");
        helper.assertTrue(machine instanceof LDLib2MachineUIProvider && machine instanceof LDLib2FancyActionMachine,
                "Registered distillation controller did not expose the LDLib2 Fancy route");
        helper.assertTrue(machine.getRecipeTypes().length == recipeTypeCount,
                "Registered distillation controller did not retain its recipe type count");
        helper.assertTrue(machine.getRecipeLogic().getClass() == DistillationTowerMachine.DistillationTowerLogic.class,
                "LDLib2 migration replaced the specialized DistillationTowerLogic");
    }

    private static void assertHolderIdentity(GameTestHelper helper, DistillationTowerMachine machine,
                                             ServerPlayer player, MachineDefinition definition) {
        MutableMachineUIHolder holder = new MutableMachineUIHolder(machine);
        helper.assertTrue(machine.canCreateLDLib2UI(player, holder) &&
                machine.createLDLib2UI(player, holder).getRootElement() instanceof LDLib2FancyMachineUIElement,
                "Distillation controller rejected its matching holder");

        MutableMachineUIHolder wrongHolder = new MutableMachineUIHolder(createMachine(GTMachines.MACERATOR[LV]));
        helper.assertTrue(!machine.canCreateLDLib2UI(player, wrongHolder) &&
                createUIFails(machine, player, wrongHolder),
                "Distillation controller accepted a holder for another machine");

        MetaMachine replacement = createMachine(definition);
        helper.assertTrue(replacement.getBlockPos().equals(machine.getBlockPos()) &&
                replacement.getDefinition() == machine.getDefinition(),
                "Stale-holder fixture did not preserve the distillation definition and position");
        holder.setMachine(replacement);
        helper.assertTrue(!machine.canCreateLDLib2UI(player, holder) && createUIFails(machine, player, holder),
                "Distillation controller accepted a stale replacement instance");
    }

    private static void assertMainPageLayout(GameTestHelper helper, UIElement mainPage) {
        UITemplate.LDLib2Bounds mainPageBounds = UITemplate.getLDLib2Bounds(mainPage);
        helper.assertTrue(mainPageBounds.width() == 190 && mainPageBounds.height() == 125,
                "Distillation controller main page did not preserve its 190x125 body");
        helper.assertTrue(mainPage.getChildren().size() == 1 &&
                mainPage.getChildren().getFirst() instanceof GTScrollerViewElement,
                "Distillation controller main page did not create one display scroller");
        UIElement scroller = mainPage.getChildren().getFirst();
        UITemplate.LDLib2Bounds scrollerBounds = UITemplate.getLDLib2Bounds(scroller);
        helper.assertTrue(scrollerBounds.x() == 4 && scrollerBounds.y() == 4 &&
                scrollerBounds.width() == 182 && scrollerBounds.height() == 117,
                "Distillation controller scroller did not preserve its 4,4 182x117 bounds");
        List<UIElement> descendants = descendants(mainPage);
        helper.assertTrue(descendants.stream().filter(GTLabelElement.class::isInstance).count() == 1,
                "Distillation controller display did not preserve its title label");
        List<GTComponentPanelElement> panels = descendants.stream()
                .filter(GTComponentPanelElement.class::isInstance)
                .map(GTComponentPanelElement.class::cast)
                .toList();
        helper.assertTrue(panels.size() == 1 && panels.getFirst().getMaxWidthLimit() == 200,
                "Distillation controller display panel did not preserve maximum text width 200");
    }

    private static void assertPartUnloadClearsSnapshot(GameTestHelper helper) {
        TestDistillationTowerMachine tower = subscribedTower();
        TickableSubscription subscription = tower.getCapturedSubscription();
        tower.onPartUnload();
        assertRuntimeDisplayCleared(helper, tower, subscription, "part unload");
    }

    private static void assertControllerUnloadClearsSnapshot(GameTestHelper helper) {
        TestDistillationTowerMachine tower = subscribedTower();
        TickableSubscription subscription = tower.getCapturedSubscription();
        tower.onUnload();
        assertRuntimeDisplayCleared(helper, tower, subscription, "controller unload");
    }

    private static void assertLiveMachineModeFollowsActiveRecipeType(GameTestHelper helper) {
        TestDistillationTowerMachine tower = new TestDistillationTowerMachine(
                GCYMMachines.LARGE_DISTILLERY, List.of());
        tower.setLevel(helper.getLevel());
        tower.setFormedForTest(true);
        tower.useLiveDisplayContract();
        tower.refreshDisplaySnapshot();
        List<Component> distillationSnapshot = tower.getDisplaySnapshot();
        TranslatableContents distillationMode = requireTranslatedLine(distillationSnapshot, "gtpm.gui.machinemode");

        tower.setActiveRecipeType(1);
        tower.refreshDisplaySnapshot();
        List<Component> distillerySnapshot = tower.getDisplaySnapshot();
        TranslatableContents distilleryMode = requireTranslatedLine(distillerySnapshot, "gtpm.gui.machinemode");
        helper.assertTrue(tower.getActiveRecipeType() == 1 && tower.getRecipeType() == DISTILLERY_RECIPES &&
                distillationMode.getArgs().length == 1 && distilleryMode.getArgs().length == 1 &&
                !distillationMode.getArgs()[0].equals(distilleryMode.getArgs()[0]) &&
                !distillationSnapshot.equals(distillerySnapshot),
                "Large Distillery display snapshot did not follow its active recipe type");
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

    private static TestDistillationTowerMachine subscribedTower() {
        TestDistillationTowerMachine tower = new TestDistillationTowerMachine(
                GTMultiMachines.DISTILLATION_TOWER, List.of());
        tower.setFormedForTest(true);
        tower.setServerDisplay(List.of(FIRST_DISPLAY_LINE));
        tower.refreshDisplaySnapshot();
        tower.getDisplaySnapshotSubscription().updateSubscription();
        return tower;
    }

    private static Component invalidStructureLine() {
        Component hover = Component.translatable("gtpm.multiblock.invalid_structure.tooltip")
                .withStyle(ChatFormatting.GRAY);
        return Component.translatable("gtpm.multiblock.invalid_structure")
                .withStyle(ChatFormatting.RED)
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static void assertRuntimeDisplayCleared(GameTestHelper helper, TestDistillationTowerMachine tower,
                                                    @Nullable TickableSubscription subscription,
                                                    String lifecycleEvent) {
        helper.assertTrue(tower.getDisplaySnapshot().isEmpty(),
                "Distillation controller retained display text after " + lifecycleEvent);
        helper.assertTrue(subscription != null && !subscription.isStillSubscribed(),
                "Distillation controller retained its display subscription after " + lifecycleEvent);
    }

    private static boolean fluidMatches(FluidStack actual, FluidStack expected) {
        return FluidStack.isSameFluidSameComponents(actual, expected) && actual.getAmount() == expected.getAmount();
    }

    private static boolean createUIFails(DistillationTowerMachine machine, ServerPlayer player,
                                         MachineUIHolder holder) {
        try {
            machine.createLDLib2UI(player, holder);
            return false;
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return true;
        }
    }

    private static LDLib2FancyMachineUIElement requireFancyShell(UIElement root) {
        if (!(root instanceof LDLib2FancyMachineUIElement shell)) {
            throw new IllegalStateException("Distillation controller did not create an LDLib2 Fancy shell.");
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
        return createMachineAt(definition, BlockPos.ZERO);
    }

    private static MetaMachine createMachineAt(MachineDefinition definition, BlockPos pos) {
        MetaMachine machine = definition.getBlockEntityType().create(pos, definition.defaultBlockState());
        if (machine == null) {
            throw new IllegalStateException("Machine definition did not create a MetaMachine: " + definition.getId());
        }
        return machine;
    }

    private static DistillationTowerMachine requireTower(MetaMachine machine) {
        if (!(machine instanceof DistillationTowerMachine tower)) {
            throw new IllegalStateException("Distillation definition did not create its concrete controller type.");
        }
        return tower;
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

    private static void collectDescendants(UIElement root, List<UIElement> descendants) {
        for (UIElement child : root.getChildren()) {
            descendants.add(child);
            collectDescendants(child, descendants);
        }
    }

    private static void clickButton(UIElement button) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = button;
        event.button = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
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

    private static final class TestDistillationTowerMachine extends DistillationTowerMachine {

        private final List<IMultiPart> parts;
        private List<Component> serverDisplay = List.of();
        private @Nullable TickableSubscription capturedSubscription;
        private boolean useLiveDisplayContract;
        private int displayRefreshCount;

        private TestDistillationTowerMachine(MachineDefinition definition, List<IMultiPart> parts) {
            this(definition, BlockPos.ZERO, parts);
        }

        private TestDistillationTowerMachine(MachineDefinition definition, BlockPos pos, List<IMultiPart> parts) {
            super(info(definition, pos));
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
            TickableSubscription subscription = super.subscribeServerTick(runnable);
            if (subscription == null) {
                throw new IllegalStateException("Server-side display test did not create a subscription.");
            }
            capturedSubscription = subscription;
            return subscription;
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
        return info(definition, BlockPos.ZERO);
    }

    private static BlockEntityCreationInfo info(MachineDefinition definition, BlockPos pos) {
        return new BlockEntityCreationInfo(definition.getBlockEntityType(), pos,
                definition.defaultBlockState());
    }
}

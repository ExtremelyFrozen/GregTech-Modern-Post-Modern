package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterialBlocks;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.StandardFluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.trait.FluidDrillLogic;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputHatchPartMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.gregtechceu.gtceu.api.GTValues.EV;
import static com.gregtechceu.gtceu.api.GTValues.HV;
import static com.gregtechceu.gtceu.api.GTValues.LV;
import static com.gregtechceu.gtceu.api.GTValues.MV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class FluidDrillMachineLDLib2UITest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidDrillMachineLDLib2UI")
    public static void allRegisteredTiersKeepConcreteControllerLogicAndLDLib2Contracts(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        int[] tiers = { MV, HV, EV };
        int[] depletionChances = { 1, 2, 8 };
        int[] multipliers = { 1, 16, 64 };
        for (int index = 0; index < tiers.length; index++) {
            int tier = tiers[index];
            MetaMachine registeredMachine = createMachine(GTMultiMachines.FLUID_DRILLING_RIG[tier]);
            helper.assertTrue(registeredMachine.getClass() == FluidDrillMachine.class &&
                    registeredMachine instanceof LDLib2MachineUIProvider &&
                    registeredMachine instanceof LDLib2FancyActionMachine,
                    "Fluid Drilling Rig tier did not create its concrete LDLib2 controller: " + tier);
            FluidDrillMachine rig = (FluidDrillMachine) registeredMachine;
            FluidDrillLogic logic = rig.getRecipeLogic();
            MutableMachineUIHolder holder = new MutableMachineUIHolder(rig);
            helper.assertTrue(rig.getTier() == tier && logic.getClass() == FluidDrillLogic.class &&
                    rig.getRecipeLogic() == logic,
                    "Fluid Drilling Rig tier or specialized recipe logic changed: " + tier);
            helper.assertTrue(rig.getRecipeTypes().length == 1 &&
                    rig.getRecipeType() == GTRecipeTypes.DUMMY_RECIPES && !rig.supportsBatchMode(),
                    "Fluid Drilling Rig invented a recipe mode or batch support: " + tier);
            helper.assertTrue(FluidDrillMachine.getDepletionChance(tier) == depletionChances[index] &&
                    FluidDrillMachine.getRigMultiplier(tier) == multipliers[index],
                    "Fluid Drilling Rig depletion or production tier semantics changed: " + tier);
            helper.assertTrue(rig.canCreateLDLib2UI(player, holder) &&
                    rig.createLDLib2UI(player, holder).getRootElement() instanceof LDLib2FancyMachineUIElement &&
                    rig.getRecipeLogic() == logic,
                    "Fluid Drilling Rig UI replaced its specialized recipe logic: " + tier);
        }

        helper.assertTrue(FluidDrillMachine.getCasingState(MV) == GTBlocks.CASING_STEEL_SOLID.get() &&
                FluidDrillMachine.getCasingState(HV) == GTBlocks.CASING_TITANIUM_STABLE.get() &&
                FluidDrillMachine.getCasingState(EV) == GTBlocks.CASING_TUNGSTENSTEEL_ROBUST.get(),
                "Fluid Drilling Rig casing tier mapping changed");
        helper.assertTrue(FluidDrillMachine.getFrameState(MV) ==
                GTMaterialBlocks.MATERIAL_BLOCKS.get(TagPrefix.frameGt, GTMaterials.Steel).get() &&
                FluidDrillMachine.getFrameState(HV) ==
                        GTMaterialBlocks.MATERIAL_BLOCKS.get(TagPrefix.frameGt, GTMaterials.Titanium).get() &&
                FluidDrillMachine.getFrameState(EV) ==
                        GTMaterialBlocks.MATERIAL_BLOCKS.get(TagPrefix.frameGt, GTMaterials.TungstenSteel).get(),
                "Fluid Drilling Rig frame tier mapping changed");
        helper.assertTrue(FluidDrillMachine.getBaseTexture(MV).equals(
                GTCEu.id("block/casings/solid/machine_casing_solid_steel")) &&
                FluidDrillMachine.getBaseTexture(HV).equals(
                        GTCEu.id("block/casings/solid/machine_casing_stable_titanium")) &&
                FluidDrillMachine.getBaseTexture(EV).equals(
                        GTCEu.id("block/casings/solid/machine_casing_robust_tungstensteel")),
                "Fluid Drilling Rig base texture tier mapping changed");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidDrillMachineLDLib2UI")
    public static void controllerHolderIdentityAndUnsupportedPartsFailFast(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        FluidDrillMachine rig = (FluidDrillMachine) createMachine(GTMultiMachines.FLUID_DRILLING_RIG[MV]);
        MutableMachineUIHolder holder = new MutableMachineUIHolder(rig);

        MetaMachine wrongMachine = createMachine(GTMachines.MACERATOR[LV]);
        MutableMachineUIHolder wrongHolder = new MutableMachineUIHolder(wrongMachine);
        helper.assertTrue(!rig.canCreateLDLib2UI(player, wrongHolder) && createUIFails(rig, player, wrongHolder),
                "Fluid Drilling Rig accepted a holder for a different machine");

        FluidDrillMachine replacement = (FluidDrillMachine) createMachine(
                GTMultiMachines.FLUID_DRILLING_RIG[MV]);
        MutableMachineUIHolder replacementHolder = new MutableMachineUIHolder(replacement);
        helper.assertTrue(!rig.canCreateLDLib2UI(player, replacementHolder) &&
                createUIFails(rig, player, replacementHolder),
                "Fluid Drilling Rig accepted another same-definition controller instance");

        LDLib2FancyUIProvider stalePage = rig.createLDLib2Page(player, holder);
        holder.setMachine(replacement);
        boolean stalePageRejected = false;
        try {
            createShell(player, holder, stalePage);
        } catch (IllegalStateException expected) {
            stalePageRejected = expected.getMessage().contains("page holder");
        }
        helper.assertTrue(stalePageRejected,
                "Fluid Drilling Rig page accepted a same-definition replacement after opening");

        IMultiPart unsupportedPart = requirePart(createMachine(GTMachines.COKE_OVEN_HATCH));
        TestFluidDrillMachine invalidRig = new TestFluidDrillMachine(MV, List.of(unsupportedPart));
        boolean unsupportedRejected = false;
        try {
            invalidRig.createLDLib2Page(player, new MutableMachineUIHolder(invalidRig));
        } catch (IllegalStateException expected) {
            unsupportedRejected = expected.getMessage().contains("part");
        }
        helper.assertTrue(unsupportedRejected,
                "Fluid Drilling Rig silently omitted a part without an LDLib2 Fancy page");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "FluidDrillMachineLDLib2UI")
    public static void shellPreservesLayoutAndOpeningScopedValidParts(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        EnergyHatchPartMachine energyHatch = requireEnergyHatch(placeMachine(helper, new BlockPos(0, 1, 0),
                GTMachines.ENERGY_INPUT_HATCH[MV]));
        StandardFluidHatchPartMachine fluidExport = requireFluidHatch(placeMachine(helper, new BlockPos(1, 1, 0),
                GTMachines.FLUID_EXPORT_HATCH[MV]));
        List<IMultiPart> parts = List.of(energyHatch, fluidExport);
        TestFluidDrillMachine rig = new TestFluidDrillMachine(MV, parts);
        rig.setLevel(helper.getLevel());
        rig.setFormedForTest(true);
        rig.setDisplayState(new FluidDrillMachine.DisplayState(true, MV, null, 0));
        rig.refreshDisplaySnapshot();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(rig);
        LDLib2FancyUIProvider firstPage = rig.createLDLib2Page(player, holder);
        LDLib2FancyUIProvider secondPage = rig.createLDLib2Page(player, holder);

        List<Component> expectedPartTitles = parts.stream()
                .map(IMultiPart::self)
                .map(MetaMachine::getDefinition)
                .map(MachineDefinition::getDescriptionId)
                .<Component>map(Component::translatable)
                .toList();
        helper.assertTrue(firstPage != secondPage && firstPage.getSubTabs().size() == 2 &&
                firstPage.getSubTabs().stream().map(LDLib2FancyUIProvider::getTitle).toList()
                        .equals(expectedPartTitles),
                "Fluid Drilling Rig did not preserve energy-input then fluid-export page order");
        for (int index = 0; index < parts.size(); index++) {
            helper.assertTrue(firstPage.getSubTabs().get(index) != secondPage.getSubTabs().get(index),
                    "Fluid Drilling Rig reused a part page provider across openings");
        }

        LDLib2FancyMachineUIElement firstShell = createShell(player, holder, firstPage);
        LDLib2FancyMachineUIElement secondShell = createShell(player, holder, secondPage);
        helper.assertTrue(firstShell.getConfiguratorPanel().getChildren().size() == 2 &&
                firstShell.getSideTabsElement().getChildren().size() == 2 &&
                firstShell.getTooltipsPanel().getChildren().isEmpty(),
                "Fluid Drilling Rig did not preserve 2 configurators, 2 side tabs, and 0 tooltips");
        UIElement firstContainer = firstShell.getChildren().getFirst();
        UIElement secondContainer = secondShell.getChildren().getFirst();
        helper.assertTrue(firstContainer.getChildren().size() == 3 && secondContainer.getChildren().size() == 3,
                "Fluid Drilling Rig did not create exactly two valid contextual part pages");
        for (int index = 0; index < firstContainer.getChildren().size(); index++) {
            helper.assertTrue(firstContainer.getChildren().get(index) != secondContainer.getChildren().get(index),
                    "Fluid Drilling Rig reused a controller or part element across openings");
        }

        UIElement mainPage = firstContainer.getChildren().getFirst();
        UITemplate.LDLib2Bounds mainPageBounds = UITemplate.getLDLib2Bounds(mainPage);
        helper.assertTrue(mainPageBounds.width() == 190 && mainPageBounds.height() == 125,
                "Fluid Drilling Rig main page lost its 190x125 body");
        helper.assertTrue(mainPage.getStyle().getInline(PropertyRegistry.BACKGROUND) == GuiTextures.BACKGROUND_INVERSE,
                "Fluid Drilling Rig main page lost its inverse background");
        helper.assertTrue(mainPage.getChildren().size() == 1 &&
                mainPage.getChildren().getFirst() instanceof GTScrollerViewElement,
                "Fluid Drilling Rig main page did not create one display scroller");
        UIElement scroller = mainPage.getChildren().getFirst();
        UITemplate.LDLib2Bounds scrollerBounds = UITemplate.getLDLib2Bounds(scroller);
        helper.assertTrue(scrollerBounds.x() == 4 && scrollerBounds.y() == 4 &&
                scrollerBounds.width() == 182 && scrollerBounds.height() == 117,
                "Fluid Drilling Rig display scroller lost its (4,4) 182x117 bounds");
        List<GTLabelElement> labels = descendants(mainPage).stream()
                .filter(GTLabelElement.class::isInstance)
                .map(GTLabelElement.class::cast)
                .toList();
        List<GTComponentPanelElement> panels = descendants(mainPage).stream()
                .filter(GTComponentPanelElement.class::isInstance)
                .map(GTComponentPanelElement.class::cast)
                .toList();
        UITemplate.LDLib2Bounds labelBounds = UITemplate.getLDLib2Bounds(labels.getFirst());
        UITemplate.LDLib2Bounds panelBounds = UITemplate.getLDLib2Bounds(panels.getFirst());
        helper.assertTrue(labels.size() == 1 && labelBounds.x() == 4 &&
                labelBounds.y() == 5 && panels.size() == 1 &&
                panelBounds.x() == 4 && panelBounds.y() == 17 &&
                panels.getFirst().getMaxWidthLimit() == 200 &&
                panels.getFirst().getLastText().equals(rig.getDisplaySnapshot()),
                "Fluid Drilling Rig title or snapshot panel lost its legacy bounds");

        clickButton(firstShell.getSideTabsElement().getChildren().get(1));
        clickButton(secondShell.getSideTabsElement().getChildren().get(1));
        helper.assertTrue(firstContainer.getChildren().size() == 4 && secondContainer.getChildren().size() == 4 &&
                firstContainer.getChildren().getLast() != secondContainer.getChildren().getLast(),
                "Fluid Drilling Rig reused its directional page across openings");

        for (IMultiPart part : parts) {
            if (!(part instanceof LDLib2FancyPartUIProvider pageProvider)) {
                throw new IllegalStateException("Valid Fluid Drilling Rig part has no LDLib2 Fancy page.");
            }
            MutableMachineUIHolder partHolder = new MutableMachineUIHolder(part.self());
            LDLib2FancyUIProvider partPage = pageProvider.createLDLib2FancyPage(player, partHolder);
            helper.assertTrue(createShell(player, partHolder, partPage).getHolder() == partHolder,
                    "Fluid Drilling Rig part page lost its dedicated holder");
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidDrillMachineLDLib2UI")
    public static void injectedMaintenanceRetainsLegacyTooltipForwarding(GameTestHelper helper) {
        boolean maintenanceEnabled = ConfigHolder.INSTANCE.machines.enableMaintenance;
        ConfigHolder.INSTANCE.machines.enableMaintenance = true;
        try {
            ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            MaintenanceHatchPartMachine maintenanceHatch = requireMaintenanceHatch(
                    placeMachine(helper, new BlockPos(0, 1, 0), GTMachines.MAINTENANCE_HATCH));
            maintenanceHatch.setMaintenanceProblems((byte) 0);
            TestFluidDrillMachine rig = new TestFluidDrillMachine(MV, List.of(maintenanceHatch));
            MutableMachineUIHolder holder = new MutableMachineUIHolder(rig);
            LDLib2FancyMachineUIElement shell = createShell(player, holder,
                    rig.createLDLib2Page(player, holder));
            helper.assertTrue(shell.getTooltipsPanel().getChildren().size() == 1,
                    "Fluid Drilling Rig did not forward an injected maintenance warning");
        } finally {
            ConfigHolder.INSTANCE.machines.enableMaintenance = maintenanceEnabled;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "FluidDrillMachineLDLib2UI")
    public static void aeFluidExportKeepsOpeningScopedContextualPage(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MEOutputHatchPartMachine fluidExport = requireMEFluidExport(placeMachine(helper, new BlockPos(0, 1, 0),
                GTAEMachines.FLUID_EXPORT_HATCH_ME));
        TestFluidDrillMachine rig = new TestFluidDrillMachine(MV, List.of(fluidExport));
        MutableMachineUIHolder holder = new MutableMachineUIHolder(rig);

        LDLib2FancyUIProvider firstPage = rig.createLDLib2Page(player, holder);
        LDLib2FancyUIProvider secondPage = rig.createLDLib2Page(player, holder);
        helper.assertTrue(firstPage.getSubTabs().size() == 1 && secondPage.getSubTabs().size() == 1 &&
                firstPage.getSubTabs().getFirst() != secondPage.getSubTabs().getFirst(),
                "Fluid Drilling Rig reused its AE fluid export page across openings");

        UIElement firstContainer = createShell(player, holder, firstPage).getChildren().getFirst();
        UIElement secondContainer = createShell(player, holder, secondPage).getChildren().getFirst();
        helper.assertTrue(firstContainer.getChildren().size() == 2 &&
                secondContainer.getChildren().size() == 2 &&
                firstContainer.getChildren().getLast() != secondContainer.getChildren().getLast(),
                "Fluid Drilling Rig did not build opening-scoped AE fluid export contextual pages");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidDrillMachineLDLib2UI")
    public static void displayBranchesAreExactImmutableAndVisibleAfterRefresh(GameTestHelper helper) {
        Fluid oil = GTMaterials.Oil.getFluid();
        FluidDrillMachine.DisplayState fluidState = FluidDrillMachine.captureDisplayState(
                true, HV, oil, 125, 17.5f);
        List<Component> withFluid = FluidDrillMachine.createDisplaySnapshot(fluidState);
        Component fluidInfo = oil.getFluidType().getDescription().copy().withStyle(ChatFormatting.GREEN);
        Component amountInfo = Component.literal(FormattingUtil.formatNumbers(109.0f) + " mB/s")
                .withStyle(ChatFormatting.BLUE);
        List<Component> expectedWithFluid = List.of(
                Component.translatable("gtpm.multiblock.max_energy_per_tick", GTValues.V[HV], GTValues.VNF[HV]),
                Component.translatable("gtpm.multiblock.fluid_rig.drilled_fluid", fluidInfo)
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("gtpm.multiblock.fluid_rig.fluid_amount", amountInfo)
                        .withStyle(ChatFormatting.GRAY));
        helper.assertTrue(fluidState.fluidPerSecond() == 109.0f && withFluid.equals(expectedWithFluid),
                "Fluid Drilling Rig fluid snapshot changed its tickrate floor, text, or order");

        List<Component> withoutFluid = FluidDrillMachine.createDisplaySnapshot(
                FluidDrillMachine.captureDisplayState(true, MV, null, 800, 20));
        Component noFluid = Component.translatable("gtpm.multiblock.fluid_rig.no_fluid_in_area")
                .withStyle(ChatFormatting.RED);
        helper.assertTrue(withoutFluid.equals(List.of(
                Component.translatable("gtpm.multiblock.max_energy_per_tick", GTValues.V[MV], GTValues.VNF[MV]),
                Component.translatable("gtpm.multiblock.fluid_rig.drilled_fluid", noFluid)
                        .withStyle(ChatFormatting.GRAY))),
                "Fluid Drilling Rig no-fluid snapshot changed its energy or status lines");
        List<Component> invalid = FluidDrillMachine.createDisplaySnapshot(
                FluidDrillMachine.captureDisplayState(false, EV, oil, 800, 20));
        helper.assertTrue(invalid.equals(List.of(invalidStructureLine())),
                "Unformed Fluid Drilling Rig did not publish only invalid_structure");

        boolean immutable = false;
        try {
            withFluid.add(Component.literal("mutation"));
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        helper.assertTrue(immutable, "Fluid Drilling Rig published a mutable display snapshot");

        TestFluidDrillMachine rig = new TestFluidDrillMachine(HV, List.of());
        rig.setDisplayState(fluidState);
        rig.refreshDisplaySnapshot();
        List<Component> firstSnapshot = rig.getDisplaySnapshot();
        rig.refreshDisplaySnapshot();
        helper.assertTrue(rig.getDisplaySnapshot() == firstSnapshot,
                "Fluid Drilling Rig replaced an unchanged snapshot instance");
        int capturesBeforeRead = rig.getDisplayCaptureCount();
        List<Component> panelRead = new ArrayList<>();
        rig.addDisplayText(panelRead);
        helper.assertTrue(panelRead.equals(firstSnapshot) && rig.getDisplayCaptureCount() == capturesBeforeRead,
                "Fluid Drilling Rig panel read sampled level, tickrate, or live recipe state");

        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MutableMachineUIHolder holder = new MutableMachineUIHolder(rig);
        LDLib2FancyMachineUIElement shell = createShell(player, holder, rig.createLDLib2Page(player, holder));
        GTComponentPanelElement panel = descendants(shell.getChildren().getFirst()).stream()
                .filter(GTComponentPanelElement.class::isInstance)
                .map(GTComponentPanelElement.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Fluid Drilling Rig display panel is missing."));
        FluidDrillMachine.DisplayState updated = FluidDrillMachine.captureDisplayState(
                true, HV, oil, 200, 20);
        rig.setDisplayState(updated);
        rig.refreshDisplaySnapshot();
        panel.screenTick();
        helper.assertTrue(panel.getLastText().equals(rig.getDisplaySnapshot()) &&
                panel.getLastText().equals(FluidDrillMachine.createDisplaySnapshot(updated)),
                "Opened Fluid Drilling Rig panel did not observe its refreshed snapshot");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidDrillMachineLDLib2UI")
    public static void formInvalidateAndUnloadOwnSnapshotLifecycle(GameTestHelper helper) {
        TestFluidDrillMachine rig = new TestFluidDrillMachine(MV, List.of());
        rig.setLevel(helper.getLevel());
        rig.useLiveDisplayState();
        FluidDrillLogic logic = rig.getRecipeLogic();
        rig.onLoad();
        helper.assertTrue(rig.getDisplaySnapshot().equals(List.of(invalidStructureLine())) &&
                rig.getRecipeLogic() == logic,
                "Fluid Drilling Rig onLoad changed its logic or omitted invalid_structure");

        rig.formStructure(FluidDrillMachine.DEFAULT_STRUCTURE);
        TickableSubscription formedSubscription = rig.getCapturedSubscription();
        helper.assertTrue(rig.isFormed() && formedSubscription != null && formedSubscription.isStillSubscribed() &&
                !rig.getDisplaySnapshot().equals(List.of(invalidStructureLine())) && rig.getRecipeLogic() == logic,
                "Fluid Drilling Rig formation did not publish and subscribe without replacing its logic");
        rig.invalidateStructure(FluidDrillMachine.DEFAULT_STRUCTURE);
        helper.assertTrue(rig.getDisplaySnapshot().equals(List.of(invalidStructureLine())) &&
                !formedSubscription.isStillSubscribed(),
                "Fluid Drilling Rig invalidation did not publish invalid_structure and stop refresh");

        TestFluidDrillMachine partUnloadRig = subscribedRig();
        TickableSubscription partSubscription = partUnloadRig.getCapturedSubscription();
        partUnloadRig.onPartUnload();
        assertRuntimeDisplayCleared(helper, partUnloadRig, partSubscription, "part unload");
        TestFluidDrillMachine controllerUnloadRig = subscribedRig();
        TickableSubscription controllerSubscription = controllerUnloadRig.getCapturedSubscription();
        controllerUnloadRig.onUnload();
        assertRuntimeDisplayCleared(helper, controllerUnloadRig, controllerSubscription, "controller unload");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidDrillMachineLDLib2UI")
    public static void clientLifecycleNeverCapturesServerDisplayState(GameTestHelper helper) {
        TestFluidDrillMachine rig = new TestFluidDrillMachine(MV, List.of());
        rig.setLevel(helper.getLevel());
        rig.setRemoteForTest(true);

        rig.onLoad();
        rig.formStructure(FluidDrillMachine.DEFAULT_STRUCTURE);
        rig.invalidateStructure(FluidDrillMachine.DEFAULT_STRUCTURE);
        helper.assertTrue(rig.getDisplayCaptureCount() == 0 && rig.getCapturedSubscription() == null &&
                rig.getDisplaySnapshot().isEmpty(),
                "Fluid Drilling Rig client lifecycle sampled or subscribed to server display state");
        rig.onUnload();
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidDrillMachineLDLib2UI", timeoutTicks = 20)
    public static void onLoadRefreshesFormedSnapshotUntilUnload(GameTestHelper helper) {
        TestFluidDrillMachine rig = new TestFluidDrillMachine(EV, List.of());
        rig.setLevel(helper.getLevel());
        rig.setFormedForTest(true);
        rig.setDisplayState(new FluidDrillMachine.DisplayState(true, EV, null, 0));
        rig.setWorkingEnabled(false);
        rig.onLoad();
        int refreshesAfterLoad = rig.getDisplayRefreshCount();
        helper.runAfterDelay(3, () -> {
            TickableSubscription subscription = rig.getCapturedSubscription();
            helper.assertTrue(!rig.getWorkLogic().isWorkingEnabled() && subscription != null &&
                    subscription.isStillSubscribed(),
                    "Fluid Drilling Rig did not subscribe while formed with working disabled");
            subscription.run();
            int refreshesWhileLoaded = rig.getDisplayRefreshCount();
            helper.assertTrue(refreshesWhileLoaded > refreshesAfterLoad,
                    "Fluid Drilling Rig subscription did not refresh its display snapshot");
            rig.onUnload();
            subscription.run();
            helper.assertTrue(!subscription.isStillSubscribed() &&
                    rig.getDisplayRefreshCount() == refreshesWhileLoaded && rig.getDisplaySnapshot().isEmpty(),
                    "Fluid Drilling Rig refresh or snapshot survived unload");
            helper.succeed();
        });
    }

    private static boolean createUIFails(FluidDrillMachine rig, ServerPlayer player, MachineUIHolder holder) {
        try {
            rig.createLDLib2UI(player, holder);
            return false;
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return true;
        }
    }

    private static TestFluidDrillMachine subscribedRig() {
        TestFluidDrillMachine rig = new TestFluidDrillMachine(MV, List.of());
        rig.setFormedForTest(true);
        rig.setDisplayState(new FluidDrillMachine.DisplayState(true, MV, null, 0));
        rig.refreshDisplaySnapshot();
        rig.getDisplaySnapshotSubscription().updateSubscription();
        return rig;
    }

    private static void assertRuntimeDisplayCleared(GameTestHelper helper, TestFluidDrillMachine rig,
                                                    @Nullable TickableSubscription subscription,
                                                    String lifecycleEvent) {
        helper.assertTrue(rig.getDisplaySnapshot().isEmpty(),
                "Fluid Drilling Rig retained display text after " + lifecycleEvent);
        helper.assertTrue(subscription != null && !subscription.isStillSubscribed(),
                "Fluid Drilling Rig retained its display subscription after " + lifecycleEvent);
    }

    private static Component invalidStructureLine() {
        Component tooltip = Component.translatable("gtpm.multiblock.invalid_structure.tooltip")
                .withStyle(ChatFormatting.GRAY);
        return Component.translatable("gtpm.multiblock.invalid_structure")
                .withStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, tooltip)));
    }

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player, MachineUIHolder holder,
                                                           LDLib2FancyUIProvider page) {
        return new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
    }

    private static MetaMachine placeMachine(GameTestHelper helper, BlockPos pos, MachineDefinition definition) {
        helper.setBlock(pos, definition.getBlock());
        if (!(helper.getBlockEntity(pos) instanceof MetaMachine machine)) {
            throw new IllegalStateException("Placed block did not create its expected machine: " + definition.getId());
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

    private static IMultiPart requirePart(MetaMachine machine) {
        if (!(machine instanceof IMultiPart part)) {
            throw new IllegalStateException("Expected a multiblock part machine.");
        }
        return part;
    }

    private static EnergyHatchPartMachine requireEnergyHatch(MetaMachine machine) {
        if (!(machine instanceof EnergyHatchPartMachine energyHatch)) {
            throw new IllegalStateException("Energy Input Hatch definition did not create its expected type.");
        }
        return energyHatch;
    }

    private static StandardFluidHatchPartMachine requireFluidHatch(MetaMachine machine) {
        if (!(machine instanceof StandardFluidHatchPartMachine fluidHatch)) {
            throw new IllegalStateException("Fluid Export Hatch definition did not create its expected type.");
        }
        return fluidHatch;
    }

    private static MaintenanceHatchPartMachine requireMaintenanceHatch(MetaMachine machine) {
        if (!(machine instanceof MaintenanceHatchPartMachine maintenanceHatch)) {
            throw new IllegalStateException("Maintenance Hatch definition did not create its expected type.");
        }
        return maintenanceHatch;
    }

    private static MEOutputHatchPartMachine requireMEFluidExport(MetaMachine machine) {
        if (!(machine instanceof MEOutputHatchPartMachine fluidExport)) {
            throw new IllegalStateException("ME Fluid Export Hatch definition did not create its expected type.");
        }
        return fluidExport;
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

    private static BlockEntityCreationInfo info(int tier) {
        MachineDefinition definition = GTMultiMachines.FLUID_DRILLING_RIG[tier];
        return new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.defaultBlockState());
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

    private static final class TestFluidDrillMachine extends FluidDrillMachine {

        private final List<IMultiPart> parts;
        private DisplayState displayState;
        private @Nullable TickableSubscription capturedSubscription;
        private boolean useLiveDisplayState;
        private boolean remote;
        private int displayCaptureCount;
        private int displayRefreshCount;

        private TestFluidDrillMachine(int tier, List<IMultiPart> parts) {
            super(info(tier), tier);
            this.parts = List.copyOf(parts);
            this.displayState = new DisplayState(false, tier, null, 0);
        }

        @Override
        public List<IMultiPart> getParts() {
            return parts;
        }

        @Override
        public boolean isRemote() {
            return remote || super.isRemote();
        }

        @Override
        protected DisplayState captureDisplayState() {
            displayCaptureCount++;
            return useLiveDisplayState ? super.captureDisplayState() : displayState;
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
                throw new IllegalStateException("Server-side display test did not create a tick subscription.");
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

        private void setDisplayState(DisplayState displayState) {
            this.displayState = displayState;
            useLiveDisplayState = false;
        }

        private void useLiveDisplayState() {
            useLiveDisplayState = true;
        }

        private void setFormedForTest(boolean formed) {
            isFormed = formed;
        }

        private void setRemoteForTest(boolean remote) {
            this.remote = remote;
        }

        private @Nullable TickableSubscription getCapturedSubscription() {
            return capturedSubscription;
        }

        private int getDisplayCaptureCount() {
            return displayCaptureCount;
        }

        private int getDisplayRefreshCount() {
            return displayRefreshCount;
        }
    }
}

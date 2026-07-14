package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyPreviewPage;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTooltipsPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.AutoMaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.CleaningMaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.LaserHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.gregtechceu.gtceu.api.GTValues.EV;
import static com.gregtechceu.gtceu.api.GTValues.IV;
import static com.gregtechceu.gtceu.api.GTValues.LV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class PowerSubstationMachineLDLib2UITest {

    private static final Component PART_DISPLAY = Component.literal("part display");
    private static final Component ADDITIONAL_DISPLAY = Component.literal("additional display");

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "PowerSubstationControllerLDLib2UI")
    public static void controllerShellPreservesLayoutPartsAndOpeningScope(GameTestHelper helper) {
        boolean maintenanceEnabled = ConfigHolder.INSTANCE.machines.enableMaintenance;
        ConfigHolder.INSTANCE.machines.enableMaintenance = true;
        try {
            ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            EnergyHatchPartMachine energyInput = requireEnergyHatch(
                    placeMachine(helper, new BlockPos(0, 1, 0), GTMachines.ENERGY_INPUT_HATCH[LV]));
            EnergyHatchPartMachine substationOutput = requireEnergyHatch(
                    placeMachine(helper, new BlockPos(1, 1, 0), GTMachines.SUBSTATION_ENERGY_OUTPUT_HATCH[EV]));
            LaserHatchPartMachine laserOutput = requireLaserHatch(
                    placeMachine(helper, new BlockPos(2, 1, 0), GTMachines.LASER_OUTPUT_HATCH_256[IV]));
            MaintenanceHatchPartMachine maintenance = requireMaintenanceHatch(
                    placeMachine(helper, new BlockPos(3, 1, 0), GTMachines.MAINTENANCE_HATCH));
            List<IMultiPart> parts = List.of(energyInput, substationOutput, laserOutput, maintenance);
            TestPowerSubstationMachine substation = new TestPowerSubstationMachine(parts);
            MutableMachineUIHolder holder = new MutableMachineUIHolder(substation);
            TestPowerSubstationMachine replacement = new TestPowerSubstationMachine(List.of());
            MutableMachineUIHolder replacementHolder = new MutableMachineUIHolder(replacement);

            helper.assertTrue(substation.canCreateLDLib2UI(player, holder),
                    "Power Substation rejected its matching controller holder");
            helper.assertTrue(!substation.canCreateLDLib2UI(player, replacementHolder),
                    "Power Substation accepted another controller instance with the same definition");
            boolean replacementRejected = false;
            try {
                substation.createLDLib2UI(player, replacementHolder);
            } catch (IllegalArgumentException expected) {
                replacementRejected = true;
            }
            helper.assertTrue(replacementRejected,
                    "Power Substation created an LDLib2 UI for another controller instance");

            LDLib2FancyMachineUIElement shell = requireFancyShell(
                    substation.createLDLib2UI(player, holder).getRootElement(), "Power Substation");
            helper.assertTrue(shell.getHolder() == holder,
                    "Power Substation Fancy shell did not retain its controller holder");
            helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == 1,
                    "Power Substation exposed configurators other than working-enabled");
            helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                    "Power Substation did not expose its controller cover-direction page");
            helper.assertTrue(shell.getTooltipsPanel().getChildren().size() == 1,
                    "Power Substation did not expose exactly the conditional maintenance warning");
            helper.assertTrue(
                    shell.getChildren().stream()
                            .anyMatch(child -> child.getSizeWidth() == 162 && child.getSizeHeight() == 76),
                    "Power Substation Fancy shell did not retain the player inventory");

            UIElement pageContainer = shell.getChildren().getFirst();
            helper.assertTrue(pageContainer.getChildren().size() == parts.size() + 1,
                    "Power Substation did not cache one distinct home page per actual part");
            UIElement mainPage = pageContainer.getChildren().getFirst();
            helper.assertTrue(mainPage.getSizeWidth() == 190 && mainPage.getSizeHeight() == 125,
                    "Power Substation main page did not preserve its 190x125 body");
            helper.assertTrue(mainPage.getChildren().size() == 1 &&
                    mainPage.getChildren().getFirst() instanceof GTScrollerViewElement,
                    "Power Substation main page did not create one display scroller");
            UIElement scroller = mainPage.getChildren().getFirst();
            helper.assertTrue(scroller.getLayoutX() == 4 && scroller.getLayoutY() == 4 &&
                    scroller.getSizeWidth() == 182 && scroller.getSizeHeight() == 117,
                    "Power Substation display scroller did not preserve its (4,4) 182x117 bounds");

            List<GTLabelElement> labels = descendants(mainPage).stream()
                    .filter(GTLabelElement.class::isInstance)
                    .map(GTLabelElement.class::cast)
                    .toList();
            helper.assertTrue(labels.size() == 1 && labels.getFirst().getLayoutX() == 4 &&
                    labels.getFirst().getLayoutY() == 5,
                    "Power Substation title did not preserve its (4,5) position");
            List<GTComponentPanelElement> panels = descendants(mainPage).stream()
                    .filter(GTComponentPanelElement.class::isInstance)
                    .map(GTComponentPanelElement.class::cast)
                    .toList();
            helper.assertTrue(panels.size() == 1 && panels.getFirst().getLayoutX() == 4 &&
                    panels.getFirst().getLayoutY() == 17 && panels.getFirst().getMaxWidthLimit() == 150,
                    "Power Substation display panel did not preserve its position and maximum text width");

            LDLib2FancyMachineUIElement secondShell = requireFancyShell(
                    substation.createLDLib2UI(player, holder).getRootElement(), "second Power Substation opening");
            List<UIElement> firstPages = pageContainer.getChildren();
            List<UIElement> secondPages = secondShell.getChildren().getFirst().getChildren();
            for (int index = 0; index < firstPages.size(); index++) {
                helper.assertTrue(firstPages.get(index) != secondPages.get(index),
                        "Power Substation reused a cached page across menu openings");
            }

            ItemBusPartMachine unsupportedPart = requireItemBus(createMachine(GTMachines.ITEM_IMPORT_BUS[LV]));
            TestPowerSubstationMachine invalidSubstation = new TestPowerSubstationMachine(List.of(unsupportedPart));
            boolean unsupportedPartRejected = false;
            try {
                invalidSubstation.createLDLib2UI(player, new MutableMachineUIHolder(invalidSubstation));
            } catch (IllegalStateException expected) {
                unsupportedPartRejected = true;
            }
            helper.assertTrue(unsupportedPartRejected,
                    "Power Substation silently omitted a part without an LDLib2 contextual page");
        } finally {
            ConfigHolder.INSTANCE.machines.enableMaintenance = maintenanceEnabled;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "PowerSubstationPartLDLib2UI")
    public static void everySupportedPartCreatesAnOpeningScopedPreviewWithItsOwnHolder(GameTestHelper helper) {
        boolean maintenanceEnabled = ConfigHolder.INSTANCE.machines.enableMaintenance;
        ConfigHolder.INSTANCE.machines.enableMaintenance = true;
        try {
            ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            List<PartExpectation> expectations = List.of(
                    new PartExpectation(placeMachine(helper, new BlockPos(0, 1, 0),
                            GTMachines.ENERGY_INPUT_HATCH[LV]),
                            "gtpm.multiblock.page_switcher.io.import", 1),
                    new PartExpectation(placeMachine(helper, new BlockPos(1, 1, 0),
                            GTMachines.SUBSTATION_ENERGY_OUTPUT_HATCH[EV]),
                            "gtpm.multiblock.page_switcher.io.export", 2),
                    new PartExpectation(placeMachine(helper, new BlockPos(2, 1, 0),
                            GTMachines.LASER_INPUT_HATCH_256[IV]),
                            "gtpm.multiblock.page_switcher.io.import", 1),
                    new PartExpectation(placeMachine(helper, new BlockPos(3, 1, 0),
                            GTMachines.MAINTENANCE_HATCH), null, 0),
                    new PartExpectation(placeMachine(helper, new BlockPos(0, 2, 0),
                            GTMachines.CONFIGURABLE_MAINTENANCE_HATCH), null, 0),
                    new PartExpectation(placeMachine(helper, new BlockPos(1, 2, 0),
                            GTMachines.AUTO_MAINTENANCE_HATCH), null, 0),
                    new PartExpectation(placeMachine(helper, new BlockPos(2, 2, 0),
                            GTMachines.CLEANING_MAINTENANCE_HATCH), null, 0));

            for (PartExpectation expectation : expectations) {
                MetaMachine machine = expectation.machine();
                if (!(machine instanceof LDLib2FancyPartUIProvider pageProvider)) {
                    throw new IllegalStateException("Supported Power Substation part has no LDLib2 page provider: " +
                            machine.getDefinition().getId());
                }
                MutableMachineUIHolder holder = new MutableMachineUIHolder(machine);
                LDLib2FancyUIProvider firstPage = pageProvider.createLDLib2FancyPage(player, holder);
                LDLib2FancyUIProvider secondPage = pageProvider.createLDLib2FancyPage(player, holder);
                helper.assertTrue(firstPage != secondPage,
                        "Power Substation part reused its contextual page provider across openings");
                helper.assertTrue(firstPage instanceof LDLib2FancyPreviewPage,
                        "Power Substation part did not create the common LDLib2 preview page");
                helper.assertTrue(firstPage.getLDLib2PageWidth() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                        firstPage.getLDLib2PageHeight() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                        "Power Substation part did not preserve the 100x100 contextual preview");
                assertGrouping(helper, firstPage.getPageGroupingData(), expectation);

                LDLib2FancyMachineUIElement partShell = new LDLib2FancyMachineUIElement(firstPage,
                        player.getInventory(), holder, firstPage.getLDLib2PageWidth(), firstPage.getLDLib2PageHeight());
                helper.assertTrue(partShell.getHolder() == holder,
                        "Power Substation contextual part shell did not retain the part holder");
                helper.assertTrue(partShell.getSideTabsElement().getChildren().size() == 2,
                        "Power Substation contextual part did not expose its cover-direction page");
                UIElement preview = partShell.getChildren().getFirst().getChildren().getFirst();
                helper.assertTrue(preview.getSizeWidth() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                        preview.getSizeHeight() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                        "Power Substation contextual part preview has incorrect bounds");
                helper.assertTrue(preview.getChildren().isEmpty(),
                        "Power Substation contextual part constructed a client Scene on the GameTest server");
                helper.assertTrue(firstPage.getTitle().equals(
                        Component.translatable(machine.getDefinition().getDescriptionId())),
                        "Power Substation contextual part did not use its definition title");

                holder.setMachine(createMachine(machine.getDefinition()));
                boolean replacedHolderRejected = false;
                try {
                    new LDLib2FancyMachineUIElement(firstPage, player.getInventory(), holder,
                            firstPage.getLDLib2PageWidth(), firstPage.getLDLib2PageHeight());
                } catch (IllegalStateException expected) {
                    replacedHolderRejected = true;
                }
                helper.assertTrue(replacedHolderRejected,
                        "Power Substation contextual part accepted a replacement instance with the same definition");
            }
        } finally {
            ConfigHolder.INSTANCE.machines.enableMaintenance = maintenanceEnabled;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "PowerSubstationMachineLDLib2UI")
    public static void maintenanceStandaloneBodiesAndFancyPreviewsKeepTheirDistinctContracts(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MaintenanceHatchPartMachine maintenance = requireMaintenanceHatch(
                placeMachine(helper, new BlockPos(0, 1, 0), GTMachines.MAINTENANCE_HATCH));
        MaintenanceHatchPartMachine configurable = requireMaintenanceHatch(
                placeMachine(helper, new BlockPos(1, 1, 0), GTMachines.CONFIGURABLE_MAINTENANCE_HATCH));
        AutoMaintenanceHatchPartMachine auto = requireAutoMaintenanceHatch(
                placeMachine(helper, new BlockPos(2, 1, 0), GTMachines.AUTO_MAINTENANCE_HATCH));
        CleaningMaintenanceHatchPartMachine cleaning = requireCleaningMaintenanceHatch(
                placeMachine(helper, new BlockPos(3, 1, 0), GTMachines.CLEANING_MAINTENANCE_HATCH));

        MutableMachineUIHolder maintenanceHolder = new MutableMachineUIHolder(maintenance);
        helper.assertTrue(maintenance.canCreateLDLib2UI(player, maintenanceHolder),
                "Maintenance Hatch rejected its matching standalone holder");
        UIElement maintenanceRoot = maintenance.createLDLib2UI(player, maintenanceHolder).getRootElement();
        helper.assertTrue(!(maintenanceRoot instanceof LDLib2FancyMachineUIElement) &&
                maintenanceRoot.getSizeWidth() == 26 && maintenanceRoot.getSizeHeight() == 46,
                "ordinary Maintenance Hatch did not retain its 26x46 raw standalone body");
        assertRawMaintenanceControls(helper, maintenanceRoot, false);

        MutableMachineUIHolder configurableHolder = new MutableMachineUIHolder(configurable);
        UIElement configurableRoot = configurable.createLDLib2UI(player, configurableHolder).getRootElement();
        helper.assertTrue(!(configurableRoot instanceof LDLib2FancyMachineUIElement) &&
                configurableRoot.getSizeWidth() == 150 && configurableRoot.getSizeHeight() == 70,
                "Configurable Maintenance Hatch did not retain its 150x70 raw standalone body");
        assertRawMaintenanceControls(helper, configurableRoot, true);

        maintenanceHolder.setMachine(createMachine(GTMachines.MAINTENANCE_HATCH));
        helper.assertTrue(!maintenance.canCreateLDLib2UI(player, maintenanceHolder),
                "Maintenance Hatch accepted a replacement standalone holder in capability probing");
        boolean replacementRejected = false;
        try {
            maintenance.createLDLib2UI(player, maintenanceHolder);
        } catch (IllegalArgumentException expected) {
            replacementRejected = true;
        }
        helper.assertTrue(replacementRejected,
                "Maintenance Hatch created a standalone UI for a replacement instance");

        assertStandalonePreviewShell(helper, player, auto, "Auto Maintenance Hatch");
        assertStandalonePreviewShell(helper, player, cleaning, "Cleaning Maintenance Hatch");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "PowerSubstationMaintenanceTooltipLDLib2UI")
    public static void maintenanceWarningIsConditionalAndListsEveryProblem(GameTestHelper helper) {
        boolean maintenanceEnabled = ConfigHolder.INSTANCE.machines.enableMaintenance;
        try {
            ConfigHolder.INSTANCE.machines.enableMaintenance = true;
            MaintenanceHatchPartMachine maintenance = requireMaintenanceHatch(
                    createMachine(GTMachines.MAINTENANCE_HATCH));
            maintenance.setLevel(helper.getLevel());
            maintenance.setMaintenanceProblems((byte) 0);

            TooltipCapturePanel ldlib2Panel = new TooltipCapturePanel();
            maintenance.attachLDLib2MaintenanceTooltips(ldlib2Panel);
            helper.assertTrue(ldlib2Panel.getChildren().size() == 1 &&
                    ldlib2Panel.getSizeWidth() == 20 && ldlib2Panel.getSizeHeight() == 20,
                    "LDLib2 Maintenance warning did not render exactly one conditional icon");
            helper.assertTrue(ldlib2Panel.getCapturedTooltips().size() == 1,
                    "Maintenance warning did not register one LDLib2 Fancy tooltip");
            IFancyTooltip warning = ldlib2Panel.getCapturedTooltips().getFirst();
            helper.assertTrue(warning.showFancyTooltip(),
                    "Maintenance warning was hidden while problems remained");
            List<Component> expected = List.of(
                    Component.translatable("gtpm.multiblock.universal.has_problems_header")
                            .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)),
                    Component.translatable("gtpm.multiblock.universal.problem.wrench"),
                    Component.translatable("gtpm.multiblock.universal.problem.screwdriver"),
                    Component.translatable("gtpm.multiblock.universal.problem.soft_mallet"),
                    Component.translatable("gtpm.multiblock.universal.problem.hard_hammer"),
                    Component.translatable("gtpm.multiblock.universal.problem.wire_cutter"),
                    Component.translatable("gtpm.multiblock.universal.problem.crowbar"));
            helper.assertTrue(warning.getFancyTooltip().equals(expected),
                    "Maintenance warning did not preserve the header and all six problem descriptions");

            maintenance.setMaintenanceProblems((byte) 0b111111);
            TooltipCapturePanel fixedPanel = new TooltipCapturePanel();
            maintenance.attachLDLib2MaintenanceTooltips(fixedPanel);
            helper.assertTrue(fixedPanel.getChildren().isEmpty() &&
                    fixedPanel.getCapturedTooltips().size() == 1 && !warning.showFancyTooltip(),
                    "Maintenance warning remained visible after all problems were fixed");

            ConfigHolder.INSTANCE.machines.enableMaintenance = false;
            maintenance.setMaintenanceProblems((byte) 0);
            TooltipCapturePanel disabledPanel = new TooltipCapturePanel();
            maintenance.attachLDLib2MaintenanceTooltips(disabledPanel);
            helper.assertTrue(disabledPanel.getChildren().isEmpty() &&
                    disabledPanel.getCapturedTooltips().isEmpty(),
                    "Maintenance warning registered while maintenance was disabled");
        } finally {
            ConfigHolder.INSTANCE.machines.enableMaintenance = maintenanceEnabled;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "PowerSubstationMachineLDLib2UI")
    public static void displaySnapshotPreservesStatusEnergyHoverEtaAndSurroundingText(GameTestHelper helper) {
        Style gold = Style.EMPTY.withColor(ChatFormatting.GOLD);
        Style darkRed = Style.EMPTY.withColor(ChatFormatting.DARK_RED);
        Style green = Style.EMPTY.withColor(ChatFormatting.GREEN);
        Style red = Style.EMPTY.withColor(ChatFormatting.RED);
        PowerSubstationMachine.DisplayState activeState = new PowerSubstationMachine.DisplayState(
                true, true, true, true, BigInteger.valueOf(1_000), BigInteger.valueOf(5_000),
                12, 4_000, 2_000, 20);
        List<Component> actual = PowerSubstationMachine.createDisplaySnapshot(
                activeState, List.of(PART_DISPLAY), List.of(ADDITIONAL_DISPLAY));
        List<Component> expected = List.of(
                PART_DISPLAY,
                Component.translatable("gtpm.multiblock.running"),
                Component.translatable("gtpm.multiblock.waiting").setStyle(red),
                Component.translatable("gtpm.multiblock.power_substation.stored",
                        Component.literal(FormattingUtil.formatNumbers(BigInteger.valueOf(1_000))).setStyle(gold)),
                Component.translatable("gtpm.multiblock.power_substation.capacity",
                        Component.literal(FormattingUtil.formatNumbers(BigInteger.valueOf(5_000))).setStyle(gold)),
                Component.translatable("gtpm.multiblock.power_substation.passive_drain",
                        Component.literal(FormattingUtil.formatNumbers(12)).setStyle(darkRed)),
                Component.translatable("gtpm.multiblock.power_substation.average_in",
                        Component.literal(FormattingUtil.formatNumbers(200)).setStyle(green))
                        .withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("gtpm.multiblock.power_substation.average_in_hover")))),
                Component.translatable("gtpm.multiblock.power_substation.average_out",
                        Component.literal(FormattingUtil.formatNumbers(100)).setStyle(red))
                        .withStyle(Style.EMPTY.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("gtpm.multiblock.power_substation.average_out_hover")))),
                Component.translatable("gtpm.multiblock.power_substation.time_to_fill",
                        Component.translatable("gtpm.multiblock.power_substation.time_seconds",
                                FormattingUtil.formatNumbers(2)).setStyle(green)),
                ADDITIONAL_DISPLAY);
        helper.assertTrue(actual.equals(expected),
                "Power Substation snapshot did not preserve part, status, energy, ETA, and additional text order");

        HoverEvent inputHover = actual.get(6).getStyle().getHoverEvent();
        HoverEvent outputHover = actual.get(7).getStyle().getHoverEvent();
        helper.assertTrue(inputHover != null && Component
                .translatable("gtpm.multiblock.power_substation.average_in_hover")
                .equals(inputHover.getValue(HoverEvent.Action.SHOW_TEXT)),
                "Power Substation average input lost its hover explanation");
        helper.assertTrue(outputHover != null && Component
                .translatable("gtpm.multiblock.power_substation.average_out_hover")
                .equals(outputHover.getValue(HoverEvent.Action.SHOW_TEXT)),
                "Power Substation average output lost its hover explanation");

        List<Component> paused = PowerSubstationMachine.createDisplaySnapshot(
                new PowerSubstationMachine.DisplayState(true, false, true, false,
                        BigInteger.ZERO, BigInteger.ZERO, 0, 0, 0, 20),
                List.of(), List.of());
        helper.assertTrue(paused.getFirst().equals(Component.translatable("gtpm.multiblock.work_paused")),
                "Power Substation snapshot did not preserve the paused state");
        List<Component> idling = PowerSubstationMachine.createDisplaySnapshot(
                new PowerSubstationMachine.DisplayState(true, true, false, false,
                        BigInteger.ZERO, BigInteger.ZERO, 0, 0, 0, 20),
                List.of(), List.of());
        helper.assertTrue(idling.getFirst().equals(Component.translatable("gtpm.multiblock.idling")),
                "Power Substation snapshot did not preserve the idling state");

        List<Component> draining = PowerSubstationMachine.createDisplaySnapshot(
                new PowerSubstationMachine.DisplayState(true, true, true, false,
                        BigInteger.valueOf(4_000), BigInteger.valueOf(5_000), 0, 1_000, 3_000, 20),
                List.of(), List.of());
        helper.assertTrue(draining.getLast().equals(
                Component.translatable("gtpm.multiblock.power_substation.time_to_drain",
                        Component.translatable("gtpm.multiblock.power_substation.time_seconds",
                                FormattingUtil.formatNumbers(2)).setStyle(red))),
                "Power Substation snapshot did not preserve drain ETA text");

        List<Component> unformed = PowerSubstationMachine.createDisplaySnapshot(
                new PowerSubstationMachine.DisplayState(false, true, true, true,
                        BigInteger.valueOf(1_000), BigInteger.valueOf(5_000), 12, 4_000, 2_000, 20),
                List.of(PART_DISPLAY), List.of(ADDITIONAL_DISPLAY));
        helper.assertTrue(unformed.equals(List.of(PART_DISPLAY, ADDITIONAL_DISPLAY)),
                "unformed Power Substation retained controller statistics or lost surrounding display text");

        boolean immutable = false;
        try {
            actual.add(Component.literal("mutation"));
        } catch (UnsupportedOperationException expectedException) {
            immutable = true;
        }
        helper.assertTrue(immutable, "Power Substation display snapshot was mutable");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "PowerSubstationMachineLDLib2UI")
    public static void unchangedSnapshotRetainsIdentityAndInvalidationClearsRuntimeText(GameTestHelper helper) {
        TestPowerSubstationMachine substation = createRefreshedDisplaySubstation(helper);
        List<Component> firstSnapshot = substation.getDisplaySnapshot();
        helper.assertTrue(firstSnapshot.equals(List.of(PART_DISPLAY)),
                "Power Substation refresh did not collect part addMultiText output");
        substation.refreshDisplaySnapshot();
        helper.assertTrue(substation.getDisplaySnapshot() == firstSnapshot,
                "Power Substation replaced an unchanged display snapshot instance");

        substation.invalidateStructure(PowerSubstationMachine.DEFAULT_STRUCTURE);
        helper.assertTrue(substation.getDisplaySnapshot().isEmpty(),
                "Power Substation retained display text after structure invalidation");

        TestPowerSubstationMachine partUnloadSubstation = createRefreshedDisplaySubstation(helper);
        partUnloadSubstation.onPartUnload();
        helper.assertTrue(partUnloadSubstation.getDisplaySnapshot().isEmpty(),
                "Power Substation retained display text after a part unloaded");

        TestPowerSubstationMachine unloadSubstation = createRefreshedDisplaySubstation(helper);
        unloadSubstation.onUnload();
        helper.assertTrue(unloadSubstation.getDisplaySnapshot().isEmpty(),
                "Power Substation retained display text after the controller unloaded");
        helper.succeed();
    }

    private static TestPowerSubstationMachine createRefreshedDisplaySubstation(GameTestHelper helper) {
        TestPowerSubstationMachine substation = new TestPowerSubstationMachine(
                List.of(new DisplayTextMaintenancePart()));
        substation.setLevel(helper.getLevel());
        substation.refreshDisplaySnapshot();
        return substation;
    }

    private static void assertGrouping(GameTestHelper helper,
                                       LDLib2FancyUIProvider.PageGroupingData grouping,
                                       PartExpectation expectation) {
        if (expectation.groupKey() == null) {
            helper.assertTrue(grouping == null,
                    "Maintenance page unexpectedly joined an IO page-switcher group");
            return;
        }
        helper.assertTrue(grouping != null && expectation.groupKey().equals(grouping.groupKey()) &&
                expectation.groupWeight() == grouping.groupPositionWeight(),
                "Energy transfer part exposed incorrect IO grouping metadata");
    }

    private static void assertRawMaintenanceControls(GameTestHelper helper, UIElement root, boolean configurable) {
        List<UIElement> elements = descendants(root);
        helper.assertTrue(elements.stream().filter(GTItemSlotElement.class::isInstance).count() == 1,
                "raw Maintenance body did not retain its duct tape slot");
        helper.assertTrue(elements.stream().filter(GTButtonElement.class::isInstance).count() == 1,
                "raw Maintenance body did not retain its repair button");
        long durationPanels = elements.stream().filter(GTComponentPanelElement.class::isInstance).count();
        long displayImages = elements.stream().filter(GTImageElement.class::isInstance).count();
        helper.assertTrue(durationPanels == (configurable ? 1 : 0) && displayImages == (configurable ? 1 : 0),
                "raw Maintenance body exposed incorrect configurable duration controls");
    }

    private static void assertStandalonePreviewShell(GameTestHelper helper, ServerPlayer player,
                                                     AutoMaintenanceHatchPartMachine machine,
                                                     String description) {
        MutableMachineUIHolder holder = new MutableMachineUIHolder(machine);
        helper.assertTrue(machine.canCreateLDLib2UI(player, holder),
                description + " rejected its matching standalone holder");
        LDLib2FancyMachineUIElement shell = requireFancyShell(
                machine.createLDLib2UI(player, holder).getRootElement(), description);
        helper.assertTrue(shell.getHolder() == holder,
                description + " standalone Fancy shell did not retain its machine holder");
        UIElement preview = shell.getChildren().getFirst().getChildren().getFirst();
        helper.assertTrue(preview.getSizeWidth() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                preview.getSizeHeight() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT &&
                preview.getChildren().isEmpty(),
                description + " standalone UI did not use the server-safe 100x100 LDLib2 preview");

        holder.setMachine(createMachine(machine.getDefinition()));
        helper.assertTrue(!machine.canCreateLDLib2UI(player, holder),
                description + " accepted a replacement holder in capability probing");
        boolean replacementRejected = false;
        try {
            machine.createLDLib2UI(player, holder);
        } catch (IllegalArgumentException expected) {
            replacementRejected = true;
        }
        helper.assertTrue(replacementRejected,
                description + " created a standalone UI for a replacement instance");
    }

    private static LDLib2FancyMachineUIElement requireFancyShell(UIElement root, String description) {
        if (!(root instanceof LDLib2FancyMachineUIElement shell)) {
            throw new IllegalStateException(description + " did not create an LDLib2 Fancy shell.");
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
            throw new IllegalStateException("Machine definition did not create a machine: " + definition.getId());
        }
        return machine;
    }

    private static EnergyHatchPartMachine requireEnergyHatch(MetaMachine machine) {
        if (!(machine instanceof EnergyHatchPartMachine energyHatch)) {
            throw new IllegalStateException("Expected an Energy Hatch machine");
        }
        return energyHatch;
    }

    private static LaserHatchPartMachine requireLaserHatch(MetaMachine machine) {
        if (!(machine instanceof LaserHatchPartMachine laserHatch)) {
            throw new IllegalStateException("Expected a Laser Hatch machine");
        }
        return laserHatch;
    }

    private static MaintenanceHatchPartMachine requireMaintenanceHatch(MetaMachine machine) {
        if (!(machine instanceof MaintenanceHatchPartMachine maintenanceHatch)) {
            throw new IllegalStateException("Expected a Maintenance Hatch machine");
        }
        return maintenanceHatch;
    }

    private static AutoMaintenanceHatchPartMachine requireAutoMaintenanceHatch(MetaMachine machine) {
        if (!(machine instanceof AutoMaintenanceHatchPartMachine maintenanceHatch)) {
            throw new IllegalStateException("Expected an Auto Maintenance Hatch machine");
        }
        return maintenanceHatch;
    }

    private static CleaningMaintenanceHatchPartMachine requireCleaningMaintenanceHatch(MetaMachine machine) {
        if (!(machine instanceof CleaningMaintenanceHatchPartMachine maintenanceHatch)) {
            throw new IllegalStateException("Expected a Cleaning Maintenance Hatch machine");
        }
        return maintenanceHatch;
    }

    private static ItemBusPartMachine requireItemBus(MetaMachine machine) {
        if (!(machine instanceof ItemBusPartMachine itemBus)) {
            throw new IllegalStateException("Expected an Item Bus machine");
        }
        return itemBus;
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

    private record PartExpectation(MetaMachine machine, String groupKey, int groupWeight) {}

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

    private static class TestPowerSubstationMachine extends PowerSubstationMachine {

        private final List<IMultiPart> parts;

        private TestPowerSubstationMachine(List<IMultiPart> parts) {
            super(info(GTMultiMachines.POWER_SUBSTATION));
            this.parts = List.copyOf(parts);
        }

        @Override
        public List<IMultiPart> getParts() {
            return parts;
        }
    }

    private static final class DisplayTextMaintenancePart extends MaintenanceHatchPartMachine {

        private DisplayTextMaintenancePart() {
            super(info(GTMachines.MAINTENANCE_HATCH), false);
        }

        @Override
        public void addMultiText(List<Component> textList) {
            textList.add(PART_DISPLAY);
        }
    }

    private static final class TooltipCapturePanel extends LDLib2FancyTooltipsPanelElement {

        private final List<IFancyTooltip> capturedTooltips = new ArrayList<>();

        private TooltipCapturePanel() {
            super(0, 0);
        }

        @Override
        public void attachTooltips(IFancyTooltip... tooltips) {
            for (IFancyTooltip tooltip : tooltips) {
                capturedTooltips.add(tooltip);
            }
            super.attachTooltips(tooltips);
        }

        private List<IFancyTooltip> getCapturedTooltips() {
            return List.copyOf(capturedTooltips);
        }
    }

    private static BlockEntityCreationInfo info(MachineDefinition definition) {
        return new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.defaultBlockState());
    }
}

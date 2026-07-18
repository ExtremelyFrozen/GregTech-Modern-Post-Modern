package com.gregtechceu.gtceu.common.machine.multiblock.electric.research;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.ItemStackTexture;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTResearchMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ObjectHolderMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.OpticalComputationHatchMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
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

import static com.gregtechceu.gtceu.api.GTValues.LV;
import static com.gregtechceu.gtceu.api.GTValues.LuV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class ResearchStationMachineLDLib2UITest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ResearchStationMachineLDLib2UI")
    public static void registeredControllerUsesLDLib2AndRejectsWrongOrStaleHolders(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MetaMachine registeredMachine = createMachine(GTResearchMachines.RESEARCH_STATION);
        helper.assertTrue(registeredMachine.getClass() == ResearchStationMachine.class,
                "Research Station definition did not create its concrete controller type");
        helper.assertTrue(registeredMachine instanceof LDLib2MachineUIProvider &&
                registeredMachine instanceof LDLib2FancyActionMachine,
                "Research Station definition did not expose both LDLib2 controller contracts");

        ResearchStationMachine station = (ResearchStationMachine) registeredMachine;
        helper.assertTrue(station.getRecipeTypes().length == 1 &&
                station.getRecipeType() == GTRecipeTypes.RESEARCH_STATION_RECIPES && !station.supportsBatchMode(),
                "Research Station definition changed its single recipe mode or invented batch support");
        MutableMachineUIHolder holder = new MutableMachineUIHolder(station);
        helper.assertTrue(station.canCreateLDLib2UI(player, holder) &&
                station.createLDLib2UI(player, holder).getRootElement() instanceof LDLib2FancyMachineUIElement,
                "Research Station rejected its matching controller holder");

        MetaMachine wrongMachine = createMachine(GTMachines.MACERATOR[LV]);
        MutableMachineUIHolder wrongHolder = new MutableMachineUIHolder(wrongMachine);
        helper.assertTrue(!station.canCreateLDLib2UI(player, wrongHolder) &&
                createUIFails(station, player, wrongHolder),
                "Research Station accepted a holder for a different machine");

        ResearchStationMachine replacement = (ResearchStationMachine) createMachine(
                GTResearchMachines.RESEARCH_STATION);
        MutableMachineUIHolder replacementHolder = new MutableMachineUIHolder(replacement);
        helper.assertTrue(!station.canCreateLDLib2UI(player, replacementHolder) &&
                createUIFails(station, player, replacementHolder),
                "Research Station accepted another controller instance with the same definition");

        LDLib2FancyUIProvider stalePage = station.createLDLib2Page(player, holder);
        holder.setMachine(replacement);
        boolean stalePageRejected = false;
        try {
            createShell(player, holder, stalePage);
        } catch (IllegalStateException expected) {
            stalePageRejected = expected.getMessage().contains("page holder");
        }
        helper.assertTrue(stalePageRejected,
                "Research Station page accepted a same-definition replacement after opening");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "ResearchStationMachineLDLib2UI")
    public static void shellPreservesLayoutPanelsAndOpeningScopedPartOrder(GameTestHelper helper) {
        boolean maintenanceEnabled = ConfigHolder.INSTANCE.machines.enableMaintenance;
        ConfigHolder.INSTANCE.machines.enableMaintenance = true;
        try {
            ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            ObjectHolderMachine objectHolder = requireObjectHolder(placeMachine(helper, new BlockPos(0, 1, 0),
                    GTResearchMachines.OBJECT_HOLDER));
            OpticalComputationHatchMachine opticalHatch = requireOpticalHatch(
                    placeMachine(helper, new BlockPos(1, 1, 0),
                            GTResearchMachines.COMPUTATION_HATCH_RECEIVER));
            MaintenanceHatchPartMachine maintenanceHatch = requireMaintenanceHatch(
                    placeMachine(helper, new BlockPos(2, 1, 0), GTMachines.MAINTENANCE_HATCH));
            maintenanceHatch.setMaintenanceProblems((byte) 0);
            List<IMultiPart> parts = List.of(objectHolder, opticalHatch, maintenanceHatch);

            TestResearchStationMachine station = new TestResearchStationMachine(parts);
            station.setLevel(helper.getLevel());
            station.setFormedForTest(true);
            station.setDisplayState(new ResearchStationMachine.DisplayState(
                    true, true, false, EnergyContainerList.EMPTY, 0, 0, 0));
            station.refreshDisplaySnapshot();
            MutableMachineUIHolder holder = new MutableMachineUIHolder(station);
            LDLib2FancyUIProvider firstPage = station.createLDLib2Page(player, holder);
            LDLib2FancyUIProvider secondPage = station.createLDLib2Page(player, holder);
            helper.assertTrue(firstPage != secondPage,
                    "Research Station reused its controller page provider across openings");

            Component expectedTitle = Component.translatable(station.getDefinition().getDescriptionId());
            helper.assertTrue(firstPage.getTitle().equals(expectedTitle) &&
                    firstPage.getTabTooltips().equals(List.of(expectedTitle)) &&
                    firstPage.getTabIcon() instanceof ItemStackTexture icon && icon.items.length == 1 &&
                    icon.items[0].is(station.getDefinition().getItem()),
                    "Research Station controller page lost its definition title, tooltip, or icon");
            List<Component> expectedPartTitles = parts.stream()
                    .map(IMultiPart::self)
                    .map(MetaMachine::getDefinition)
                    .map(MachineDefinition::getDescriptionId)
                    .<Component>map(Component::translatable)
                    .toList();
            helper.assertTrue(firstPage.getSubTabs().size() == parts.size() &&
                    firstPage.getSubTabs().stream().map(LDLib2FancyUIProvider::getTitle).toList()
                            .equals(expectedPartTitles),
                    "Research Station did not preserve Object Holder, optical, and maintenance page order");
            for (int index = 0; index < parts.size(); index++) {
                helper.assertTrue(firstPage.getSubTabs().get(index) != secondPage.getSubTabs().get(index),
                        "Research Station reused a part page provider across openings");
            }

            LDLib2FancyMachineUIElement firstShell = createShell(player, holder, firstPage);
            LDLib2FancyMachineUIElement secondShell = createShell(player, holder, secondPage);
            helper.assertTrue(firstShell.getHolder() == holder && secondShell.getHolder() == holder,
                    "Research Station Fancy shell did not retain its controller holder");
            helper.assertTrue(firstShell.getConfiguratorPanel().getChildren().size() == 2,
                    "Research Station did not expose exactly voiding and working configurators");
            helper.assertTrue(firstShell.getSideTabsElement().getChildren().size() == 2,
                    "Research Station exposed a mode page or omitted its directional page");
            helper.assertTrue(firstShell.getTooltipsPanel().getChildren().size() == 1,
                    "Research Station did not expose its one maintenance warning");

            UIElement firstPageContainer = firstShell.getChildren().getFirst();
            UIElement secondPageContainer = secondShell.getChildren().getFirst();
            helper.assertTrue(firstPageContainer.getChildren().size() == parts.size() + 1 &&
                    secondPageContainer.getChildren().size() == parts.size() + 1,
                    "Research Station did not create one contextual page for every actual part");
            for (int index = 0; index < firstPageContainer.getChildren().size(); index++) {
                helper.assertTrue(firstPageContainer.getChildren().get(index) !=
                        secondPageContainer.getChildren().get(index),
                        "Research Station reused a controller or part element across openings");
            }

            UIElement mainPage = firstPageContainer.getChildren().getFirst();
            UITemplate.LDLib2Bounds mainPageBounds = UITemplate.getLDLib2Bounds(mainPage);
            helper.assertTrue(mainPageBounds.width() == 190 && mainPageBounds.height() == 125,
                    "Research Station main page lost its 190x125 body");
            helper.assertTrue(mainPage.getStyle().getInline(PropertyRegistry.BACKGROUND) ==
                    GuiTextures.BACKGROUND_INVERSE,
                    "Research Station main page lost its inverse background");
            helper.assertTrue(mainPage.getChildren().size() == 1 &&
                    mainPage.getChildren().getFirst() instanceof GTScrollerViewElement,
                    "Research Station main page did not create one display scroller");
            UIElement scroller = mainPage.getChildren().getFirst();
            UITemplate.LDLib2Bounds scrollerBounds = UITemplate.getLDLib2Bounds(scroller);
            helper.assertTrue(scrollerBounds.x() == 4 && scrollerBounds.y() == 4 &&
                    scrollerBounds.width() == 182 && scrollerBounds.height() == 117,
                    "Research Station display scroller lost its (4,4) 182x117 bounds");
            List<GTLabelElement> labels = descendants(mainPage).stream()
                    .filter(GTLabelElement.class::isInstance)
                    .map(GTLabelElement.class::cast)
                    .toList();
            UITemplate.LDLib2Bounds labelBounds = UITemplate.getLDLib2Bounds(labels.getFirst());
            helper.assertTrue(labels.size() == 1 && labelBounds.x() == 4 &&
                    labelBounds.y() == 5,
                    "Research Station title lost its (4,5) position");
            List<GTComponentPanelElement> panels = descendants(mainPage).stream()
                    .filter(GTComponentPanelElement.class::isInstance)
                    .map(GTComponentPanelElement.class::cast)
                    .toList();
            UITemplate.LDLib2Bounds panelBounds = UITemplate.getLDLib2Bounds(panels.getFirst());
            helper.assertTrue(panels.size() == 1 && panelBounds.x() == 4 &&
                    panelBounds.y() == 17 && panels.getFirst().getMaxWidthLimit() == 200 &&
                    panels.getFirst().getLastText().equals(station.getDisplaySnapshot()),
                    "Research Station display panel lost its position, width, or synchronized snapshot");

            clickButton(firstShell.getSideTabsElement().getChildren().get(1));
            clickButton(secondShell.getSideTabsElement().getChildren().get(1));
            helper.assertTrue(firstPageContainer.getChildren().size() == parts.size() + 2 &&
                    secondPageContainer.getChildren().size() == parts.size() + 2 &&
                    firstPageContainer.getChildren().getLast() != secondPageContainer.getChildren().getLast(),
                    "Research Station reused its directional page across openings");

            for (IMultiPart part : parts) {
                if (!(part instanceof LDLib2FancyPartUIProvider pageProvider)) {
                    throw new IllegalStateException("Expected Research Station part has no LDLib2 Fancy page.");
                }
                MutableMachineUIHolder partHolder = new MutableMachineUIHolder(part.self());
                LDLib2FancyUIProvider firstPartPage = pageProvider.createLDLib2FancyPage(player, partHolder);
                LDLib2FancyUIProvider secondPartPage = pageProvider.createLDLib2FancyPage(player, partHolder);
                helper.assertTrue(firstPartPage != secondPartPage &&
                        createShell(player, partHolder, firstPartPage).getHolder() == partHolder,
                        "Research Station part page was reused or lost its dedicated holder");
            }
        } finally {
            ConfigHolder.INSTANCE.machines.enableMaintenance = maintenanceEnabled;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ResearchStationMachineLDLib2UI")
    public static void unsupportedPartsFailFast(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        IMultiPart unsupportedPart = requirePart(createMachine(GTMachines.COKE_OVEN_HATCH));
        TestResearchStationMachine station = new TestResearchStationMachine(List.of(unsupportedPart));
        boolean unsupportedPartRejected = false;
        try {
            station.createLDLib2Page(player, new MutableMachineUIHolder(station));
        } catch (IllegalStateException expected) {
            unsupportedPartRejected = expected.getMessage().contains("part");
        }
        helper.assertTrue(unsupportedPartRejected,
                "Research Station silently omitted a part without an LDLib2 contextual page");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ResearchStationMachineLDLib2UI")
    public static void displaySnapshotPreservesLegacyOrderImmutabilityAndRefreshVisibility(GameTestHelper helper) {
        EnergyContainerList energyContainer = new EnergyContainerList(List.of(
                new TestEnergyContainer(GTValues.V[LuV], 2, 1_000_000)));
        int maxComputation = 512;
        double progressPercent = 0.375;
        ResearchStationMachine.DisplayState researchingState = ResearchStationMachine.captureDisplayState(
                true, true, true, energyContainer, energyContainer.getTier(), maxComputation, progressPercent);
        List<Component> researching = ResearchStationMachine.createDisplaySnapshot(researchingState);
        long totalEUt = energyContainer.getTotalEUt();
        Component energyLine = Component.translatable("gtpm.multiblock.max_energy_per_tick",
                FormattingUtil.formatNumbers(totalEUt),
                Component.literal(GTValues.VNF[GTUtil.getFloorTierByVoltage(totalEUt)]))
                .withStyle(ChatFormatting.GRAY)
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("gtpm.multiblock.max_energy_per_tick_hover")
                                .withStyle(ChatFormatting.GRAY))));
        Component tierLine = Component.translatable("gtpm.multiblock.max_recipe_tier",
                Component.literal(GTValues.VNF[energyContainer.getTier()]))
                .withStyle(ChatFormatting.GRAY)
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("gtpm.multiblock.max_recipe_tier_hover")
                                .withStyle(ChatFormatting.GRAY))));
        List<Component> expectedResearching = List.of(
                energyLine,
                tierLine,
                Component.translatable("gtpm.multiblock.research_station.researching")
                        .withStyle(ChatFormatting.GREEN),
                Component.translatable("gtpm.multiblock.computation.max",
                        Component.literal(FormattingUtil.formatNumbers(maxComputation))
                                .withStyle(ChatFormatting.AQUA))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("gtpm.multiblock.progress_percent", 37));
        helper.assertTrue(researching.equals(expectedResearching),
                "Research Station snapshot lost its energy, tier, status, computation, and progress order");

        List<Component> paused = ResearchStationMachine.createDisplaySnapshot(
                ResearchStationMachine.captureDisplayState(true, false, false, EnergyContainerList.EMPTY,
                        0, 0, 1));
        List<Component> idling = ResearchStationMachine.createDisplaySnapshot(
                ResearchStationMachine.captureDisplayState(true, true, false, EnergyContainerList.EMPTY,
                        0, 0, 1));
        helper.assertTrue(paused.equals(List.of(
                Component.translatable("gtpm.multiblock.max_recipe_tier", Component.literal(GTValues.VNF[0]))
                        .withStyle(ChatFormatting.GRAY)
                        .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("gtpm.multiblock.max_recipe_tier_hover")
                                        .withStyle(ChatFormatting.GRAY)))),
                Component.translatable("gtpm.multiblock.work_paused").withStyle(ChatFormatting.GOLD))) &&
                idling.equals(List.of(
                        Component.translatable("gtpm.multiblock.max_recipe_tier", Component.literal(GTValues.VNF[0]))
                                .withStyle(ChatFormatting.GRAY)
                                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                        Component.translatable("gtpm.multiblock.max_recipe_tier_hover")
                                                .withStyle(ChatFormatting.GRAY)))),
                        Component.translatable("gtpm.multiblock.idling").withStyle(ChatFormatting.GRAY))),
                "Research Station paused or idle snapshot changed its legacy status branch");
        List<Component> invalid = ResearchStationMachine.createDisplaySnapshot(
                ResearchStationMachine.captureDisplayState(false, true, true, energyContainer,
                        energyContainer.getTier(), maxComputation, progressPercent));
        helper.assertTrue(invalid.equals(List.of(invalidStructureLine())),
                "Unformed Research Station did not publish only invalid_structure");

        boolean immutable = false;
        try {
            researching.add(Component.literal("mutation"));
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        helper.assertTrue(immutable, "Research Station published a mutable display snapshot");

        TestResearchStationMachine station = new TestResearchStationMachine(List.of());
        station.setDisplayState(researchingState);
        station.refreshDisplaySnapshot();
        List<Component> firstSnapshot = station.getDisplaySnapshot();
        station.refreshDisplaySnapshot();
        helper.assertTrue(station.getDisplaySnapshot() == firstSnapshot,
                "Research Station replaced an unchanged display snapshot instance");
        int capturesBeforePanelRead = station.getDisplayCaptureCount();
        List<Component> panelRead = new ArrayList<>();
        station.addDisplayText(panelRead);
        helper.assertTrue(panelRead.equals(firstSnapshot) &&
                station.getDisplayCaptureCount() == capturesBeforePanelRead,
                "Research Station panel read sampled live server computation instead of its snapshot");

        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MutableMachineUIHolder holder = new MutableMachineUIHolder(station);
        LDLib2FancyMachineUIElement shell = createShell(player, holder,
                station.createLDLib2Page(player, holder));
        GTComponentPanelElement panel = descendants(shell.getChildren().getFirst()).stream()
                .filter(GTComponentPanelElement.class::isInstance)
                .map(GTComponentPanelElement.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Research Station display panel is missing."));
        ResearchStationMachine.DisplayState updatedState = ResearchStationMachine.captureDisplayState(
                true, true, true, energyContainer, energyContainer.getTier(), 768, 0.5);
        station.setDisplayState(updatedState);
        station.refreshDisplaySnapshot();
        panel.screenTick();
        helper.assertTrue(panel.getLastText().equals(ResearchStationMachine.createDisplaySnapshot(updatedState)) &&
                panel.getLastText().equals(station.getDisplaySnapshot()),
                "Opened Research Station panel did not observe the refreshed synchronized snapshot");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "ResearchStationMachineLDLib2UI")
    public static void formationInvalidationAndUnloadOwnDisplaySubscription(GameTestHelper helper) {
        ObjectHolderMachine objectHolder = requireObjectHolder(placeMachine(helper, new BlockPos(0, 1, 0),
                GTResearchMachines.OBJECT_HOLDER));
        OpticalComputationHatchMachine opticalHatch = requireOpticalHatch(
                placeMachine(helper, new BlockPos(1, 1, 0),
                        GTResearchMachines.COMPUTATION_HATCH_RECEIVER));
        MaintenanceHatchPartMachine maintenanceHatch = requireMaintenanceHatch(
                placeMachine(helper, new BlockPos(2, 1, 0), GTMachines.MAINTENANCE_HATCH));
        TestResearchStationMachine station = new TestResearchStationMachine(
                List.of(objectHolder, opticalHatch, maintenanceHatch));
        station.setLevel(helper.getLevel());
        station.useLiveDisplayState();
        station.onLoad();
        helper.assertTrue(station.getDisplaySnapshot().equals(List.of(invalidStructureLine())),
                "Research Station onLoad did not initialize its invalid-structure snapshot");

        Direction expectedHolderFacing = station.getFrontFacing().getOpposite();
        objectHolder.setFrontFacing(expectedHolderFacing);
        station.formStructure(ResearchStationMachine.DEFAULT_STRUCTURE);
        TickableSubscription formedSubscription = station.getCapturedSubscription();
        helper.assertTrue(station.isFormed() && station.getObjectHolder() == objectHolder &&
                formedSubscription != null && formedSubscription.isStillSubscribed() &&
                !station.getDisplaySnapshot().isEmpty() &&
                !station.getDisplaySnapshot().equals(List.of(invalidStructureLine())),
                "Research Station formation did not retain its Object Holder and start snapshot refresh");

        int refreshesBeforeDisable = station.getDisplayRefreshCount();
        station.setWorkingEnabled(false);
        helper.assertTrue(station.getDisplayRefreshCount() > refreshesBeforeDisable &&
                formedSubscription.isStillSubscribed(),
                "Research Station working action did not refresh immediately or stopped the display subscription");

        objectHolder.setLocked(true);
        objectHolder.setFrontFacing(station.getFrontFacing());
        station.formStructure(ResearchStationMachine.DEFAULT_STRUCTURE);
        helper.assertTrue(!station.isFormed() && station.getObjectHolder() == null && !objectHolder.isLocked() &&
                station.getDisplaySnapshot().equals(List.of(invalidStructureLine())) &&
                !formedSubscription.isStillSubscribed(),
                "Wrong-facing Object Holder did not invalidate, unlock, clear, and publish invalid_structure");

        TestResearchStationMachine partUnloadStation = subscribedStation();
        TickableSubscription partSubscription = partUnloadStation.getCapturedSubscription();
        partUnloadStation.onPartUnload();
        assertRuntimeDisplayCleared(helper, partUnloadStation, partSubscription, "part unload");

        TestResearchStationMachine controllerUnloadStation = subscribedStation();
        TickableSubscription controllerSubscription = controllerUnloadStation.getCapturedSubscription();
        controllerUnloadStation.onUnload();
        assertRuntimeDisplayCleared(helper, controllerUnloadStation, controllerSubscription, "controller unload");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ResearchStationMachineLDLib2UI", timeoutTicks = 20)
    public static void displaySubscriptionRefreshesWhileDisabledAndStopsOnUnload(GameTestHelper helper) {
        TestResearchStationMachine station = new TestResearchStationMachine(List.of());
        station.setLevel(helper.getLevel());
        station.setFormedForTest(true);
        station.setDisplayState(new ResearchStationMachine.DisplayState(
                true, false, true, EnergyContainerList.EMPTY, 0, 0, 0.5));
        station.setWorkingEnabled(false);
        station.onLoad();
        int refreshesAfterLoad = station.getDisplayRefreshCount();

        helper.runAfterDelay(3, () -> {
            station.serverTick();
            int refreshesWhileDisabled = station.getDisplayRefreshCount();
            helper.assertTrue(refreshesWhileDisabled > refreshesAfterLoad,
                    "Research Station display subscription stopped while work was disabled");
            station.onUnload();
            helper.runAfterDelay(3, () -> {
                station.serverTick();
                helper.assertTrue(station.getDisplayRefreshCount() == refreshesWhileDisabled &&
                        station.getDisplaySnapshot().isEmpty(),
                        "Research Station display subscription or snapshot survived unload");
                helper.succeed();
            });
        });
    }

    private static boolean createUIFails(ResearchStationMachine station, ServerPlayer player,
                                         MachineUIHolder holder) {
        try {
            station.createLDLib2UI(player, holder);
            return false;
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return true;
        }
    }

    private static TestResearchStationMachine subscribedStation() {
        TestResearchStationMachine station = new TestResearchStationMachine(List.of());
        station.setFormedForTest(true);
        station.setDisplayState(new ResearchStationMachine.DisplayState(
                true, true, false, EnergyContainerList.EMPTY, 0, 0, 0));
        station.refreshDisplaySnapshot();
        station.getDisplaySnapshotSubscription().updateSubscription();
        return station;
    }

    private static void assertRuntimeDisplayCleared(GameTestHelper helper, TestResearchStationMachine station,
                                                    @Nullable TickableSubscription subscription,
                                                    String lifecycleEvent) {
        helper.assertTrue(station.getDisplaySnapshot().isEmpty(),
                "Research Station retained display text after " + lifecycleEvent);
        helper.assertTrue(subscription != null && !subscription.isStillSubscribed(),
                "Research Station retained its display subscription after " + lifecycleEvent);
    }

    private static Component invalidStructureLine() {
        Component hover = Component.translatable("gtpm.multiblock.invalid_structure.tooltip")
                .withStyle(ChatFormatting.GRAY);
        return Component.translatable("gtpm.multiblock.invalid_structure")
                .withStyle(ChatFormatting.RED)
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
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

    private static ObjectHolderMachine requireObjectHolder(MetaMachine machine) {
        if (!(machine instanceof ObjectHolderMachine objectHolder)) {
            throw new IllegalStateException("Object Holder definition did not create its expected type.");
        }
        return objectHolder;
    }

    private static OpticalComputationHatchMachine requireOpticalHatch(MetaMachine machine) {
        if (!(machine instanceof OpticalComputationHatchMachine opticalHatch)) {
            throw new IllegalStateException("Optical hatch definition did not create its expected type.");
        }
        return opticalHatch;
    }

    private static MaintenanceHatchPartMachine requireMaintenanceHatch(MetaMachine machine) {
        if (!(machine instanceof MaintenanceHatchPartMachine maintenanceHatch)) {
            throw new IllegalStateException("Maintenance Hatch definition did not create its expected type.");
        }
        return maintenanceHatch;
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

    private static BlockEntityCreationInfo info(MachineDefinition definition) {
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

    private static final class TestResearchStationMachine extends ResearchStationMachine {

        private final List<IMultiPart> parts;
        private DisplayState displayState = new DisplayState(false, true, false,
                EnergyContainerList.EMPTY, 0, 0, 0);
        private @Nullable TickableSubscription capturedSubscription;
        private boolean useLiveDisplayState;
        private int displayCaptureCount;
        private int displayRefreshCount;

        private TestResearchStationMachine(List<IMultiPart> parts) {
            super(info(GTResearchMachines.RESEARCH_STATION));
            this.parts = List.copyOf(parts);
        }

        @Override
        public List<IMultiPart> getParts() {
            return parts;
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
        public TickableSubscription subscribeServerTick(@Nullable TickableSubscription last, Runnable runnable) {
            capturedSubscription = super.subscribeServerTick(last, runnable);
            if (capturedSubscription == null) {
                throw new IllegalStateException("Server-side display test did not create a tick subscription.");
            }
            return capturedSubscription;
        }

        private void setDisplayState(DisplayState displayState) {
            this.displayState = displayState;
            this.useLiveDisplayState = false;
        }

        private void useLiveDisplayState() {
            useLiveDisplayState = true;
        }

        private void setFormedForTest(boolean formed) {
            isFormed = formed;
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

    private record TestEnergyContainer(long voltage, long amperage, long capacity) implements IEnergyContainer {

        @Override
        public long acceptEnergyFromNetwork(Direction side, long voltage, long amperage) {
            return 0;
        }

        @Override
        public boolean inputsEnergy(Direction side) {
            return true;
        }

        @Override
        public long changeEnergy(long differenceAmount) {
            return 0;
        }

        @Override
        public long getEnergyStored() {
            return capacity;
        }

        @Override
        public long getEnergyCapacity() {
            return capacity;
        }

        @Override
        public long getInputAmperage() {
            return amperage;
        }

        @Override
        public long getInputVoltage() {
            return voltage;
        }
    }
}

package com.gregtechceu.gtceu.common.machine.multiblock.electric.research;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTProgressBarElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.common.data.machines.GTResearchMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.hpca.HPCAComponentPartMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

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

import com.mojang.authlib.GameProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.gregtechceu.gtceu.api.GTValues.LV;
import static com.gregtechceu.gtceu.api.GTValues.LuV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class HPCAMachineLDLib2UITest {

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "HPCAMachineLDLib2UI")
    public static void controllerPreservesLayoutSnapshotAndContextualParts(GameTestHelper helper) {
        boolean maintenanceEnabled = ConfigHolder.INSTANCE.machines.enableMaintenance;
        ConfigHolder.INSTANCE.machines.enableMaintenance = true;
        try {
            ServerPlayer player = FakePlayerFactory.get(helper.getLevel(),
                    new GameProfile(UUID.randomUUID(), "hpca-controller"));
            IMultiPart energyInput = requirePart(placeMachine(helper, new BlockPos(0, 1, 0),
                    GTMachines.ENERGY_INPUT_HATCH[LuV]));
            IMultiPart fluidInput = requirePart(placeMachine(helper, new BlockPos(1, 1, 0),
                    GTMachines.FLUID_IMPORT_HATCH[LV]));
            IMultiPart computationOutput = requirePart(placeMachine(helper, new BlockPos(2, 1, 0),
                    GTResearchMachines.COMPUTATION_HATCH_TRANSMITTER));
            HPCAComponentPartMachine component = requireHPCAComponent(placeMachine(helper, new BlockPos(3, 1, 0),
                    GTResearchMachines.HPCA_COMPUTATION_COMPONENT));
            IMultiPart maintenance = requirePart(placeMachine(helper, new BlockPos(0, 2, 0),
                    GTMachines.MAINTENANCE_HATCH));
            IMultiPart dualInput = requirePart(placeMachine(helper, new BlockPos(1, 2, 0),
                    GTMachines.DUAL_IMPORT_HATCH[LuV]));
            IMultiPart patternBufferProxy = requirePart(placeMachine(helper, new BlockPos(2, 2, 0),
                    GTAEMachines.ME_PATTERN_BUFFER_PROXY));
            List<IMultiPart> parts = List.of(
                    energyInput, fluidInput, computationOutput, component, maintenance, dualInput, patternBufferProxy);

            TestHPCAMachine controller = new TestHPCAMachine(parts);
            controller.setLevel(helper.getLevel());
            controller.setFormedForTest(true);
            controller.getHpcaHandler().onStructureForm(List.of(component.getHpcaComponentTrait()));
            controller.refreshHPCASnapshots();
            MutableMachineUIHolder holder = new MutableMachineUIHolder(controller);
            MutableMachineUIHolder replacementHolder = new MutableMachineUIHolder(new TestHPCAMachine(List.of()));

            helper.assertTrue(controller.canCreateLDLib2UI(player, holder),
                    "HPCA rejected its matching controller holder");
            helper.assertTrue(!controller.canCreateLDLib2UI(player, replacementHolder),
                    "HPCA accepted another controller instance with the same definition");
            boolean replacementRejected = false;
            try {
                controller.createLDLib2UI(player, replacementHolder);
            } catch (IllegalArgumentException expected) {
                replacementRejected = expected.getMessage().contains("holder");
            }
            helper.assertTrue(replacementRejected,
                    "HPCA created an LDLib2 UI for another controller instance");

            LDLib2FancyMachineUIElement shell = requireFancyShell(
                    controller.createLDLib2UI(player, holder).getRootElement(), "HPCA");
            helper.assertTrue(shell.getHolder() == holder,
                    "HPCA Fancy shell did not retain its controller holder");
            helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == 2,
                    "HPCA did not preserve its voiding and working-enabled configurators");
            helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                    "HPCA did not expose its controller cover-direction page");
            helper.assertTrue(shell.getTooltipsPanel().getChildren().size() == 1,
                    "HPCA did not expose exactly the conditional maintenance warning");
            helper.assertTrue(shell.getChildren().stream()
                    .anyMatch(child -> UITemplate.getLDLib2Bounds(child).width() == 162 &&
                            UITemplate.getLDLib2Bounds(child).height() == 76),
                    "HPCA Fancy shell did not retain the player inventory");

            UIElement pageContainer = shell.getChildren().getFirst();
            helper.assertTrue(pageContainer.getChildren().size() == parts.size() + 1,
                    "HPCA did not cache one distinct home page per actual part");
            UIElement mainPage = pageContainer.getChildren().getFirst();
            helper.assertTrue(
                    UITemplate.getLDLib2Bounds(mainPage).width() == 190 &&
                            UITemplate.getLDLib2Bounds(mainPage).height() == 125,
                    "HPCA main page did not preserve its 190x125 body");
            helper.assertTrue(mainPage.getChildren().size() == 2 &&
                    mainPage.getChildren().getFirst() instanceof GTScrollerViewElement,
                    "HPCA main page did not preserve its display scroller and status-grid overlay");

            UIElement scroller = mainPage.getChildren().getFirst();
            helper.assertTrue(
                    UITemplate.getLDLib2Bounds(scroller).x() == 4 && UITemplate.getLDLib2Bounds(scroller).y() == 4 &&
                            UITemplate.getLDLib2Bounds(scroller).width() == 182 &&
                            UITemplate.getLDLib2Bounds(scroller).height() == 117,
                    "HPCA display scroller did not preserve its (4,4) 182x117 bounds");
            List<GTLabelElement> labels = descendants(mainPage).stream()
                    .filter(GTLabelElement.class::isInstance)
                    .map(GTLabelElement.class::cast)
                    .toList();
            helper.assertTrue(labels.size() == 1 && UITemplate.getLDLib2Bounds(labels.getFirst()).x() == 4 &&
                    UITemplate.getLDLib2Bounds(labels.getFirst()).y() == 5,
                    "HPCA title did not preserve its (4,5) position");
            List<GTComponentPanelElement> panels = descendants(mainPage).stream()
                    .filter(GTComponentPanelElement.class::isInstance)
                    .map(GTComponentPanelElement.class::cast)
                    .toList();
            helper.assertTrue(panels.size() == 1 && UITemplate.getLDLib2Bounds(panels.getFirst()).x() == 4 &&
                    UITemplate.getLDLib2Bounds(panels.getFirst()).y() == 17 &&
                    panels.getFirst().getMaxWidthLimit() == 150,
                    "HPCA display panel did not preserve its position and text width");
            GTComponentPanelElement displayPanel = panels.getFirst();
            List<Component> firstDisplaySnapshot = controller.getHpcaDisplaySnapshot();
            helper.assertTrue(firstDisplaySnapshot.equals(expectedFormedDisplaySnapshot(controller)) &&
                    displayPanel.getLastText().equals(firstDisplaySnapshot),
                    "HPCA display panel lost its energy, computation, or idling snapshot order");
            assertImmutable(helper, firstDisplaySnapshot, "HPCA display snapshot was mutable");

            UIElement statusGrid = elementById(mainPage, "hpca_status_grid");
            helper.assertTrue(
                    UITemplate.getLDLib2Bounds(statusGrid).x() == 74 &&
                            UITemplate.getLDLib2Bounds(statusGrid).y() == 57 &&
                            UITemplate.getLDLib2Bounds(statusGrid).width() == 47 &&
                            UITemplate.getLDLib2Bounds(statusGrid).height() == 47,
                    "HPCA status grid did not preserve its (74,57) 47x47 bounds");
            List<GTProgressBarElement> progressBars = statusGrid.getChildren().stream()
                    .filter(GTProgressBarElement.class::isInstance)
                    .map(GTProgressBarElement.class::cast)
                    .toList();
            helper.assertTrue(
                    progressBars.size() == 1 && UITemplate.getLDLib2Bounds(progressBars.getFirst()).x() == 0 &&
                            UITemplate.getLDLib2Bounds(progressBars.getFirst()).y() == 0 &&
                            UITemplate.getLDLib2Bounds(progressBars.getFirst()).width() == 47 &&
                            UITemplate.getLDLib2Bounds(progressBars.getFirst()).height() == 47,
                    "HPCA status grid did not retain its full-size progress outline");
            List<GTImageElement> componentImages = statusGrid.getChildren().stream()
                    .filter(GTImageElement.class::isInstance)
                    .map(GTImageElement.class::cast)
                    .toList();
            helper.assertTrue(componentImages.size() == 9,
                    "HPCA status grid did not create all nine component icons");
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 3; column++) {
                    GTImageElement image = componentImages.get(row * 3 + column);
                    helper.assertTrue(UITemplate.getLDLib2Bounds(image).x() == 2 + column * 15 &&
                            UITemplate.getLDLib2Bounds(image).y() == 2 + row * 15 &&
                            UITemplate.getLDLib2Bounds(image).width() == 13 &&
                            UITemplate.getLDLib2Bounds(image).height() == 13,
                            "HPCA component icon lost its 3x3 grid position");
                }
            }

            List<Component> firstSnapshot = controller.getHpcaInfoSnapshot();
            helper.assertTrue(!firstSnapshot.isEmpty() && tooltips(statusGrid).equals(firstSnapshot),
                    "HPCA status grid did not expose the server-owned structural snapshot");
            helper.assertTrue(tooltips(componentImages.getFirst()).equals(firstSnapshot),
                    "HPCA component icon prevented the status-grid tooltip from bubbling");
            controller.refreshHPCASnapshots();
            helper.assertTrue(controller.getHpcaDisplaySnapshot() == firstDisplaySnapshot &&
                    controller.getHpcaInfoSnapshot() == firstSnapshot,
                    "HPCA replaced unchanged server snapshot instances");

            HPCAComponentPartMachine advancedComponent = requireHPCAComponent(
                    createMachine(GTResearchMachines.HPCA_ADVANCED_COMPUTATION_COMPONENT));
            controller.getHpcaHandler().onStructureForm(List.of(advancedComponent.getHpcaComponentTrait()));
            displayPanel.screenTick();
            helper.assertTrue(controller.getHpcaDisplaySnapshot() == firstDisplaySnapshot &&
                    displayPanel.getLastText().equals(firstDisplaySnapshot),
                    "HPCA display panel read changed business state before the server snapshot refreshed");
            controller.refreshHPCASnapshots();
            displayPanel.screenTick();
            List<Component> secondDisplaySnapshot = controller.getHpcaDisplaySnapshot();
            helper.assertTrue(!secondDisplaySnapshot.equals(firstDisplaySnapshot) &&
                    secondDisplaySnapshot.equals(expectedFormedDisplaySnapshot(controller)) &&
                    displayPanel.getLastText().equals(secondDisplaySnapshot),
                    "opened HPCA display panel did not observe the refreshed server snapshot");
            List<Component> secondSnapshot = controller.getHpcaInfoSnapshot();
            helper.assertTrue(!secondSnapshot.equals(firstSnapshot) &&
                    tooltips(componentImages.getFirst()).equals(secondSnapshot),
                    "opened HPCA status grid did not observe the refreshed server snapshot");
            assertImmutable(helper, secondSnapshot, "HPCA info snapshot was mutable");

            controller.getHpcaHandler().setAllocatedCWUt(1);
            controller.getHpcaHandler().tick();
            controller.refreshHPCASnapshots();
            displayPanel.screenTick();
            List<Component> providingSnapshot = controller.getHpcaDisplaySnapshot();
            helper.assertTrue(!providingSnapshot.equals(secondDisplaySnapshot) &&
                    providingSnapshot.get(1).equals(Component.translatable(
                            "gtpm.multiblock.hpca.computation",
                            Component.literal("1 / " + controller.getHpcaHandler().getMaxCWUt() + " CWU/t")
                                    .withStyle(ChatFormatting.AQUA))
                            .withStyle(ChatFormatting.GRAY)) &&
                    providingSnapshot.getLast().equals(
                            Component.translatable("gtpm.multiblock.data_bank.providing")
                                    .withStyle(ChatFormatting.GREEN)) &&
                    displayPanel.getLastText().equals(providingSnapshot),
                    "HPCA display snapshot lost its providing branch");

            controller.tick();
            displayPanel.screenTick();
            List<Component> idlingSnapshot = controller.getHpcaDisplaySnapshot();
            helper.assertTrue(idlingSnapshot.get(1).equals(Component.translatable(
                    "gtpm.multiblock.hpca.computation",
                    Component.literal("0 / " + controller.getHpcaHandler().getMaxCWUt() + " CWU/t")
                            .withStyle(ChatFormatting.AQUA))
                    .withStyle(ChatFormatting.GRAY)) &&
                    idlingSnapshot.getLast().equals(
                            Component.translatable("gtpm.multiblock.idling").withStyle(ChatFormatting.GRAY)) &&
                    displayPanel.getLastText().equals(idlingSnapshot),
                    "inactive HPCA retained a stale providing snapshot");

            LDLib2FancyMachineUIElement secondShell = requireFancyShell(
                    controller.createLDLib2UI(player, holder).getRootElement(), "second HPCA opening");
            List<UIElement> firstPages = pageContainer.getChildren();
            List<UIElement> secondPages = secondShell.getChildren().getFirst().getChildren();
            for (int index = 0; index < firstPages.size(); index++) {
                helper.assertTrue(firstPages.get(index) != secondPages.get(index),
                        "HPCA reused a cached page across menu openings");
            }

            IMultiPart unsupportedPart = requirePart(createMachine(GTMachines.COKE_OVEN_HATCH));
            TestHPCAMachine invalidController = new TestHPCAMachine(List.of(unsupportedPart));
            boolean unsupportedPartRejected = false;
            try {
                invalidController.createLDLib2UI(player, new MutableMachineUIHolder(invalidController));
            } catch (IllegalStateException expected) {
                unsupportedPartRejected = expected.getMessage().contains("part");
            }
            helper.assertTrue(unsupportedPartRejected,
                    "HPCA silently omitted a part without an LDLib2 contextual page");

            controller.invalidateStructure(HPCAMachine.DEFAULT_STRUCTURE);
            helper.assertTrue(controller.getHpcaDisplaySnapshot().equals(invalidStructureDisplaySnapshot()),
                    "HPCA structure invalidation lost its legacy invalid-structure display text");
            helper.assertTrue(!controller.getHpcaInfoSnapshot().isEmpty() &&
                    controller.getHpcaInfoSnapshot().getLast().equals(
                            Component.translatable("gtpm.multiblock.hpca.info_bridging_disabled")
                                    .withStyle(ChatFormatting.RED)),
                    "HPCA structure invalidation did not rebuild its reset status-grid snapshot");

            controller.onUnload();
            helper.assertTrue(controller.getHpcaDisplaySnapshot().isEmpty() &&
                    controller.getHpcaInfoSnapshot().isEmpty(),
                    "HPCA retained server-owned UI snapshots after controller unload");
        } finally {
            ConfigHolder.INSTANCE.machines.enableMaintenance = maintenanceEnabled;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "HPCAMachineLDLib2UI")
    public static void freshUnformedOpeningUsesServerSnapshots(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        TestHPCAMachine controller = new TestHPCAMachine(List.of());
        controller.setLevel(helper.getLevel());
        controller.onLoad();
        try {
            MutableMachineUIHolder holder = new MutableMachineUIHolder(controller);
            LDLib2FancyMachineUIElement shell = requireFancyShell(
                    controller.createLDLib2UI(player, holder).getRootElement(), "unformed HPCA");
            UIElement mainPage = shell.getChildren().getFirst().getChildren().getFirst();
            GTComponentPanelElement displayPanel = descendants(mainPage).stream()
                    .filter(GTComponentPanelElement.class::isInstance)
                    .map(GTComponentPanelElement.class::cast)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Unformed HPCA page omitted its display panel"));
            UIElement statusGrid = elementById(mainPage, "hpca_status_grid");

            helper.assertTrue(controller.getHpcaDisplaySnapshot().equals(invalidStructureDisplaySnapshot()) &&
                    displayPanel.getLastText().equals(controller.getHpcaDisplaySnapshot()),
                    "fresh unformed HPCA opening lost its server-owned invalid-structure snapshot");
            List<Component> infoSnapshot = controller.getHpcaInfoSnapshot();
            helper.assertTrue(!infoSnapshot.isEmpty() && infoSnapshot.getLast().equals(
                    Component.translatable("gtpm.multiblock.hpca.info_bridging_disabled")
                            .withStyle(ChatFormatting.RED)) &&
                    tooltips(statusGrid).equals(infoSnapshot),
                    "fresh unformed HPCA opening lost its reset status-grid snapshot");
        } finally {
            controller.onUnload();
        }
        helper.assertTrue(controller.getHpcaDisplaySnapshot().isEmpty() &&
                controller.getHpcaInfoSnapshot().isEmpty(),
                "fresh unformed HPCA retained UI snapshots after unload");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "HPCAMachineLDLib2UI")
    public static void componentGridKeepsTheLegacySpatialOrder(GameTestHelper helper) {
        List<MachineDefinition> definitions = List.of(
                GTResearchMachines.HPCA_EMPTY_COMPONENT,
                GTResearchMachines.HPCA_COMPUTATION_COMPONENT,
                GTResearchMachines.HPCA_ADVANCED_COMPUTATION_COMPONENT,
                GTResearchMachines.HPCA_HEAT_SINK_COMPONENT,
                GTResearchMachines.HPCA_ACTIVE_COOLER_COMPONENT,
                GTResearchMachines.HPCA_BRIDGE_COMPONENT,
                GTResearchMachines.HPCA_EMPTY_COMPONENT,
                GTResearchMachines.HPCA_COMPUTATION_COMPONENT,
                GTResearchMachines.HPCA_HEAT_SINK_COMPONENT);
        List<IGuiTexture> expectedTextures = new ArrayList<>(definitions.size());
        BlockPos gridOrigin = new BlockPos(0, 3, 1);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                int index = row * 3 + column;
                BlockPos componentPos = gridOrigin.relative(Direction.EAST, column).relative(Direction.DOWN, row);
                HPCAComponentPartMachine component = requireHPCAComponent(
                        placeMachine(helper, componentPos, definitions.get(index)));
                expectedTextures.add(component.getComponentIcon());
            }
        }

        HPCAMachine.HPCAGridHandler handler = new HPCAMachine.HPCAGridHandler(null);
        BlockPos controllerPos = helper.absolutePos(new BlockPos(3, 0, 1));
        handler.tryGatherClientComponents(helper.getLevel(), controllerPos, Direction.EAST, Direction.NORTH, false);
        for (int index = 0; index < expectedTextures.size(); index++) {
            helper.assertTrue(handler.getComponentTexture(index) == expectedTextures.get(index),
                    "HPCA component grid changed its legacy spatial order at index " + index);
        }

        handler.clearClientComponents();
        for (int index = 0; index < expectedTextures.size(); index++) {
            helper.assertTrue(handler.getComponentTexture(index) == GuiTextures.BLANK_TRANSPARENT,
                    "HPCA component grid retained a stale texture after clearing index " + index);
        }
        helper.succeed();
    }

    private static List<Component> tooltips(UIElement target) {
        UIEvent event = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
        event.target = target;
        UIEventDispatcher.dispatchEvent(event, true, true, false);
        if (event.hoverTooltips == null) {
            throw new IllegalStateException("HPCA status grid omitted its hover tooltip");
        }
        return event.hoverTooltips.tooltipTexts();
    }

    private static void assertImmutable(GameTestHelper helper, List<Component> snapshot, String message) {
        boolean immutable = false;
        try {
            snapshot.add(Component.literal("mutation"));
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        helper.assertTrue(immutable, message);
    }

    private static List<Component> expectedFormedDisplaySnapshot(HPCAMachine controller) {
        HPCAMachine.HPCAGridHandler handler = controller.getHpcaHandler();
        return List.of(
                Component.translatable(
                        "gtpm.multiblock.hpca.energy",
                        FormattingUtil.formatNumbers(0),
                        FormattingUtil.formatNumbers(handler.getMaxEUt()),
                        GTValues.VNF[GTUtil.getTierByVoltage(handler.getMaxEUt())])
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable(
                        "gtpm.multiblock.hpca.computation",
                        Component.literal("0 / " + handler.getMaxCWUt() + " CWU/t")
                                .withStyle(ChatFormatting.AQUA))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("gtpm.multiblock.idling").withStyle(ChatFormatting.GRAY));
    }

    private static List<Component> invalidStructureDisplaySnapshot() {
        Component hover = Component.translatable("gtpm.multiblock.invalid_structure.tooltip")
                .withStyle(ChatFormatting.GRAY);
        Component invalid = Component.translatable("gtpm.multiblock.invalid_structure")
                .withStyle(ChatFormatting.RED)
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
        return List.of(invalid);
    }

    private static UIElement elementById(UIElement root, String id) {
        return descendants(root).stream()
                .filter(element -> id.equals(element.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("HPCA page omitted element " + id));
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

    private static LDLib2FancyMachineUIElement requireFancyShell(UIElement root, String description) {
        if (!(root instanceof LDLib2FancyMachineUIElement shell)) {
            throw new IllegalStateException(description + " did not create an LDLib2 Fancy shell");
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

    private static HPCAComponentPartMachine requireHPCAComponent(MetaMachine machine) {
        if (!(machine instanceof HPCAComponentPartMachine component)) {
            throw new IllegalStateException("Expected an HPCA component machine");
        }
        return component;
    }

    private static IMultiPart requirePart(MetaMachine machine) {
        if (!(machine instanceof IMultiPart part)) {
            throw new IllegalStateException("Expected a multiblock part machine");
        }
        return part;
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

    private static final class TestHPCAMachine extends HPCAMachine {

        private final List<IMultiPart> parts;

        private TestHPCAMachine(List<IMultiPart> parts) {
            super(info(GTResearchMachines.HIGH_PERFORMANCE_COMPUTING_ARRAY));
            this.parts = List.copyOf(parts);
        }

        private void setFormedForTest(boolean formed) {
            this.isFormed = formed;
        }

        @Override
        public List<IMultiPart> getParts() {
            return parts;
        }
    }
}

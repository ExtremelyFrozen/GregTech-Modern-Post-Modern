package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolderContext;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2DirectionalFaceClickTracker;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.electric.ItemCollectorMachine;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.utils.data.BlockPosFace;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LDLib2DirectionalFancyConfiguratorTest {

    private static final ResourceLocation CONFIGURE_ITEM_OUTPUT_SIDE_ACTION = GTCEu
            .id("configure_item_output_side");
    private static final ResourceLocation CONFIGURE_FLUID_OUTPUT_SIDE_ACTION = GTCEu
            .id("configure_fluid_output_side");
    private static final ResourceLocation SET_ITEM_INPUT_FROM_OUTPUT_SIDE_ACTION = GTCEu
            .id("set_item_input_from_output_side");
    private static final ResourceLocation SET_FLUID_INPUT_FROM_OUTPUT_SIDE_ACTION = GTCEu
            .id("set_fluid_input_from_output_side");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalFancyConfigurator")
    public static void faceClickTrackerRequiresMatchingButtonAndFace(GameTestHelper helper) {
        LDLib2DirectionalFaceClickTracker tracker = new LDLib2DirectionalFaceClickTracker();
        BlockPosFace north = new BlockPosFace(BlockPos.ZERO, Direction.NORTH);
        BlockPosFace otherNorth = new BlockPosFace(BlockPos.ZERO.east(), Direction.NORTH);
        BlockPosFace east = new BlockPosFace(BlockPos.ZERO, Direction.EAST);

        tracker.press(GLFW.GLFW_MOUSE_BUTTON_LEFT, north);
        LDLib2DirectionalFaceClickTracker.FaceClick leftClick = tracker.release(
                GLFW.GLFW_MOUSE_BUTTON_LEFT, north, true);
        helper.assertTrue(leftClick != null && leftClick.face().equals(north) &&
                leftClick.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT,
                "matching left-button face press and release should complete a click");

        tracker.press(GLFW.GLFW_MOUSE_BUTTON_LEFT, north);
        helper.assertTrue(tracker.release(GLFW.GLFW_MOUSE_BUTTON_RIGHT, north, true) == null,
                "a different release button must cancel the face click");
        helper.assertTrue(tracker.release(GLFW.GLFW_MOUSE_BUTTON_LEFT, north, true) == null,
                "a button mismatch must clear the unfinished face press");

        tracker.press(GLFW.GLFW_MOUSE_BUTTON_RIGHT, north);
        helper.assertTrue(tracker.release(GLFW.GLFW_MOUSE_BUTTON_RIGHT, east, true) == null,
                "releasing on another face must cancel the face click");
        helper.assertTrue(tracker.release(GLFW.GLFW_MOUSE_BUTTON_RIGHT, north, true) == null,
                "a face mismatch must clear the unfinished face press");

        tracker.press(GLFW.GLFW_MOUSE_BUTTON_LEFT, north);
        helper.assertTrue(tracker.release(GLFW.GLFW_MOUSE_BUTTON_LEFT, otherNorth, true) == null,
                "the same facing on a different block position must not complete a click");

        tracker.press(GLFW.GLFW_MOUSE_BUTTON_LEFT, north);
        helper.assertTrue(tracker.release(GLFW.GLFW_MOUSE_BUTTON_LEFT, north, false) == null,
                "a release targeted from another pressed element must cancel the face click");
        helper.assertTrue(tracker.release(GLFW.GLFW_MOUSE_BUTTON_LEFT, north, true) == null,
                "a press-target mismatch must clear the unfinished face press");

        tracker.press(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, north);
        helper.assertTrue(tracker.release(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, north, true) == null,
                "unsupported mouse buttons must not complete a directional face click");

        tracker.press(GLFW.GLFW_MOUSE_BUTTON_RIGHT, east);
        LDLib2DirectionalFaceClickTracker.FaceClick rightClick = tracker.release(
                GLFW.GLFW_MOUSE_BUTTON_RIGHT, east, true);
        helper.assertTrue(rightClick != null && rightClick.face().equals(east) &&
                rightClick.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT,
                "matching right-button face press and release should complete a click");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalFancyConfigurator")
    public static void itemPageUsesItsHolderAndRequiresARepeatedLeftClick(GameTestHelper helper) {
        ItemCollectorMachine machine = createItemCollector(helper);
        machine.setFrontFacing(Direction.NORTH);
        AutoOutputTrait output = requireAutoOutputTrait(machine);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MachineUIHolder pageHolder = new MachineUIHolderContext(player, machine);
        MachineUIHolder rootHolder = new MachineUIHolderContext(player, machine);
        List<CapturedAction> capturedActions = new ArrayList<>();
        LDLib2DirectionalFancyConfigurator page = new LDLib2DirectionalFancyConfigurator(
                output, pageHolder, (holder, action) -> capturedActions.add(new CapturedAction(holder, action)));
        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(page, player.getInventory(),
                rootHolder, page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
        UIElement pageRoot = shell.getChildren().getFirst().getChildren().getFirst();
        UIElement controls = pageRoot.getChildren().get(2);
        UIElement outputModeButton = controls.getChildren().getFirst();
        UIElement allowInputButton = controls.getChildren().get(1);

        helper.assertTrue(rootHolder != pageHolder, "test requires different root and page machine holders");

        helper.assertTrue(page.getItemOutputMode() == LDLib2DirectionalFancyConfigurator.OutputMode.OFF,
                "item mode should be off before selecting a side");
        clickButton(outputModeButton);
        helper.assertTrue(capturedActions.isEmpty(), "unselected item mode button sent an action");

        clickButton(allowInputButton);
        assertAction(helper, capturedActions, 0, pageHolder, SET_ITEM_INPUT_FROM_OUTPUT_SIDE_ACTION, 1);

        helper.assertTrue(page.handleSceneFaceClick(Direction.EAST, GLFW.GLFW_MOUSE_BUTTON_LEFT),
                "first item face click should select the side");
        helper.assertTrue(capturedActions.size() == 1, "first item face click must not configure output");

        output.setItemOutputDirection(Direction.EAST);
        output.setAllowAutoOutputItems(false);
        helper.assertTrue(page.getItemOutputMode() == LDLib2DirectionalFancyConfigurator.OutputMode.OUTPUT,
                "selected item output side should use output mode when auto-output is disabled");
        output.setAllowAutoOutputItems(true);
        helper.assertTrue(page.getItemOutputMode() == LDLib2DirectionalFancyConfigurator.OutputMode.AUTO,
                "selected item output side should use auto mode when auto-output is enabled");

        page.handleSceneFaceClick(Direction.EAST, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        helper.assertTrue(capturedActions.size() == 1, "right-click must not configure item output");

        page.handleSceneFaceClick(Direction.EAST, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        assertAction(helper, capturedActions, 1, pageHolder, CONFIGURE_ITEM_OUTPUT_SIDE_ACTION,
                Direction.EAST.get3DDataValue());

        clickButton(outputModeButton);
        assertAction(helper, capturedActions, 2, pageHolder, CONFIGURE_ITEM_OUTPUT_SIDE_ACTION,
                Direction.EAST.get3DDataValue());

        page.handleSceneFaceClick(Direction.SOUTH, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        helper.assertTrue(capturedActions.size() == 3, "selecting a different face must not send an action");
        helper.assertTrue(page.getItemOutputMode() == LDLib2DirectionalFancyConfigurator.OutputMode.OFF,
                "a selected side different from item output should use off mode");
        clickButton(outputModeButton);
        assertAction(helper, capturedActions, 3, pageHolder, CONFIGURE_ITEM_OUTPUT_SIDE_ACTION,
                Direction.SOUTH.get3DDataValue());

        output.setAllowItemInputFromOutputSide(true);
        clickButton(allowInputButton);
        assertAction(helper, capturedActions, 4, pageHolder, SET_ITEM_INPUT_FROM_OUTPUT_SIDE_ACTION, 0);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalFancyConfigurator")
    public static void combinedPageRoutesFluidRightClicksAndControls(GameTestHelper helper) {
        SimpleTieredMachine machine = createSimpleMachine(helper);
        machine.setFrontFacing(Direction.NORTH);
        AutoOutputTrait output = machine.autoOutput;
        helper.assertTrue(output.supportsAutoOutputItems() && output.supportsAutoOutputFluids(),
                "simple machine must expose both directional output modes for this test");

        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MachineUIHolder pageHolder = new MachineUIHolderContext(player, machine);
        MachineUIHolder rootHolder = new MachineUIHolderContext(player, machine);
        List<CapturedAction> capturedActions = new ArrayList<>();
        LDLib2DirectionalFancyConfigurator page = new LDLib2DirectionalFancyConfigurator(
                output, pageHolder, (holder, action) -> capturedActions.add(new CapturedAction(holder, action)));
        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(page, player.getInventory(),
                rootHolder, page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
        UIElement pageRoot = shell.getChildren().getFirst().getChildren().getFirst();
        UIElement fluidLabel = pageRoot.getChildren().get(2);
        UIElement fluidControls = pageRoot.getChildren().get(4);
        UIElement fluidModeButton = fluidControls.getChildren().getFirst();
        UIElement allowFluidInputButton = fluidControls.getChildren().get(1);

        helper.assertTrue(rootHolder != pageHolder, "test requires different root and page machine holders");
        helper.assertTrue(pageRoot.getChildren().size() == 5,
                "combined page should contain one scene, two labels, and two control rows");
        helper.assertFalse(fluidLabel.isAllowHitTest(),
                "fluid auto-output label must not block pointer input to the scene beneath it");
        helper.assertTrue(page.getFluidOutputMode() == LDLib2DirectionalFancyConfigurator.OutputMode.OFF,
                "fluid mode should be off before selecting a side");

        clickButton(fluidModeButton);
        helper.assertTrue(capturedActions.isEmpty(), "unselected fluid mode button sent an action");
        clickButton(allowFluidInputButton);
        assertAction(helper, capturedActions, 0, pageHolder, SET_FLUID_INPUT_FROM_OUTPUT_SIDE_ACTION, 1);

        page.handleSceneFaceClick(Direction.UP, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        helper.assertTrue(capturedActions.size() == 1, "first fluid face click must only select the side");

        output.setFluidOutputDirection(Direction.UP);
        output.setAllowAutoOutputFluids(false);
        helper.assertTrue(page.getFluidOutputMode() == LDLib2DirectionalFancyConfigurator.OutputMode.OUTPUT,
                "selected fluid output side should use output mode when auto-output is disabled");
        output.setAllowAutoOutputFluids(true);
        helper.assertTrue(page.getFluidOutputMode() == LDLib2DirectionalFancyConfigurator.OutputMode.AUTO,
                "selected fluid output side should use auto mode when auto-output is enabled");

        page.handleSceneFaceClick(Direction.UP, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        assertAction(helper, capturedActions, 1, pageHolder, CONFIGURE_ITEM_OUTPUT_SIDE_ACTION,
                Direction.UP.get3DDataValue());
        page.handleSceneFaceClick(Direction.UP, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        assertAction(helper, capturedActions, 2, pageHolder, CONFIGURE_FLUID_OUTPUT_SIDE_ACTION,
                Direction.UP.get3DDataValue());

        clickButton(fluidModeButton);
        assertAction(helper, capturedActions, 3, pageHolder, CONFIGURE_FLUID_OUTPUT_SIDE_ACTION,
                Direction.UP.get3DDataValue());

        page.handleSceneFaceClick(Direction.DOWN, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        helper.assertTrue(capturedActions.size() == 4, "selecting a different fluid face must not send an action");
        helper.assertTrue(page.getFluidOutputMode() == LDLib2DirectionalFancyConfigurator.OutputMode.OFF,
                "a selected side different from fluid output should use off mode");
        clickButton(fluidModeButton);
        assertAction(helper, capturedActions, 4, pageHolder, CONFIGURE_FLUID_OUTPUT_SIDE_ACTION,
                Direction.DOWN.get3DDataValue());

        output.setAllowFluidInputFromOutputSide(true);
        clickButton(allowFluidInputButton);
        assertAction(helper, capturedActions, 5, pageHolder, SET_FLUID_INPUT_FROM_OUTPUT_SIDE_ACTION, 0);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalFancyConfigurator")
    public static void serverPageKeepsClientSceneHostEmpty(GameTestHelper helper) {
        ItemCollectorMachine machine = createItemCollector(helper);
        AutoOutputTrait output = requireAutoOutputTrait(machine);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MachineUIHolder pageHolder = new MachineUIHolderContext(player, machine);
        LDLib2DirectionalFancyConfigurator page = new LDLib2DirectionalFancyConfigurator(output, pageHolder);

        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(page, player.getInventory(),
                pageHolder, page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
        UIElement pageContainer = shell.getChildren().getFirst();
        UIElement pageRoot = pageContainer.getChildren().getFirst();
        UIElement sceneHost = pageRoot.getChildren().getFirst();
        UIElement autoOutputLabel = pageRoot.getChildren().get(1);

        helper.assertTrue(pageRoot.getChildren().size() == 3,
                "item directional page should keep scene, label, and controls in stable order");
        helper.assertTrue(sceneHost.getChildren().isEmpty(),
                "server-side directional page must not construct a client Scene");
        helper.assertFalse(autoOutputLabel.isAllowHitTest(),
                "auto-output label must not block pointer input to the scene beneath it");
        helper.assertTrue(page.getLDLib2PageWidth() == 168 && page.getLDLib2PageHeight() == 80,
                "directional page should preserve the legacy fixed content size");
        helper.succeed();
    }

    private static ItemCollectorMachine createItemCollector(GameTestHelper helper) {
        return (ItemCollectorMachine) TestUtils.setMachine(helper, new BlockPos(1, 1, 1),
                GTMachines.ITEM_COLLECTOR[GTValues.LV]);
    }

    private static SimpleTieredMachine createSimpleMachine(GameTestHelper helper) {
        return (SimpleTieredMachine) TestUtils.setMachine(helper, new BlockPos(1, 1, 1),
                GTMachines.ARC_FURNACE[GTValues.LV]);
    }

    private static AutoOutputTrait requireAutoOutputTrait(ItemCollectorMachine machine) {
        AutoOutputTrait output = machine.getTrait(AutoOutputTrait.TYPE);
        if (output == null) {
            throw new IllegalStateException("Item collector did not expose its auto-output trait.");
        }
        return output;
    }

    private static void assertAction(GameTestHelper helper, List<CapturedAction> actions, int index,
                                     MachineUIHolder expectedHolder, ResourceLocation expectedActionId,
                                     int expectedSequence) {
        helper.assertTrue(actions.size() > index, "expected directional action " + index + " was not sent");
        CapturedAction captured = actions.get(index);
        helper.assertTrue(captured.holder() == expectedHolder,
                "directional action must use the page-specific machine holder");
        helper.assertTrue(captured.action().actionId().equals(expectedActionId),
                "directional action id did not match " + expectedActionId);
        helper.assertTrue(captured.action().sequence() == expectedSequence,
                "directional action sequence did not match the selected state");
    }

    private static void clickButton(UIElement button) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = button;
        event.button = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
    }

    private record CapturedAction(MachineUIHolder holder, SyncActionData action) {}
}

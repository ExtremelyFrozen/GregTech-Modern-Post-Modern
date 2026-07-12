package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2CoverUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolderContext;
import com.gregtechceu.gtceu.api.gui.factory.UICoverHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2DirectionalFaceClickTracker;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.common.data.GTCovers;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.electric.BatteryBufferMachine;
import com.gregtechceu.gtceu.common.machine.electric.ItemCollectorMachine;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
    private static final ResourceLocation PLACE_DIRECTIONAL_COVER_ACTION = GTCEu.id("place_directional_cover");
    private static final ResourceLocation REMOVE_DIRECTIONAL_COVER_ACTION = GTCEu.id("remove_directional_cover");
    private static final ResourceLocation OPEN_DIRECTIONAL_COVER_ACTION = GTCEu.id("open_directional_cover");

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
    public static void coverOnlyPageBuildsWithoutAutoOutputOrClientScene(GameTestHelper helper) {
        BatteryBufferMachine machine = createCoverOnlyMachine(helper);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MachineUIHolder pageHolder = new MachineUIHolderContext(player, machine);
        LDLib2DirectionalFancyConfigurator page = new LDLib2DirectionalFancyConfigurator(machine, player, pageHolder);

        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(page, player.getInventory(),
                pageHolder, page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
        UIElement pageRoot = shell.getChildren().getFirst().getChildren().getFirst();
        UIElement sceneHost = pageRoot.getChildren().getFirst();
        UIElement coverControls = pageRoot.getChildren().get(1);

        helper.assertTrue(machine.getTrait(AutoOutputTrait.TYPE) == null,
                "cover-only page test machine unexpectedly exposes auto-output");
        helper.assertTrue(pageRoot.getChildren().size() == 2,
                "cover-only directional page should contain only the scene and cover controls");
        helper.assertTrue(coverControls.getChildren().size() == 2 &&
                coverControls.getChildren().get(1) instanceof GTItemSlotElement,
                "cover-only directional page did not build its config button and local cover slot");
        helper.assertTrue(sceneHost.getChildren().isEmpty(),
                "server-side cover-only page must not construct a client Scene");
        helper.assertTrue(page.getItemOutputMode() == LDLib2DirectionalFancyConfigurator.OutputMode.OFF &&
                page.getFluidOutputMode() == LDLib2DirectionalFancyConfigurator.OutputMode.OFF,
                "cover-only directional page exposed an auto-output mode");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalFancyConfigurator")
    public static void pageRejectsHolderForDifferentMachine(GameTestHelper helper) {
        BatteryBufferMachine machine = createCoverOnlyMachine(helper);
        BatteryBufferMachine otherMachine = (BatteryBufferMachine) TestUtils.setMachine(helper,
                new BlockPos(3, 1, 1), GTMachines.BATTERY_BUFFER_4[GTValues.LV]);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MachineUIHolder wrongHolder = new MachineUIHolderContext(player, otherMachine);
        IllegalArgumentException rejection = null;

        try {
            new LDLib2DirectionalFancyConfigurator(machine, player, wrongHolder);
        } catch (IllegalArgumentException exception) {
            rejection = exception;
        }

        helper.assertTrue(rejection != null,
                "directional page accepted a holder that resolves a different machine");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalFancyConfigurator")
    public static void coverControlsUsePageHolderAndRefreshFromSyncedCoverState(GameTestHelper helper) {
        BatteryBufferMachine machine = createCoverOnlyMachine(helper);
        machine.setFrontFacing(Direction.NORTH);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MachineUIHolder pageHolder = new MachineUIHolderContext(player, machine);
        MachineUIHolder rootHolder = new MachineUIHolderContext(player, machine);
        List<CapturedAction> capturedActions = new ArrayList<>();
        LDLib2DirectionalFancyConfigurator page = new LDLib2DirectionalFancyConfigurator(
                machine, player, pageHolder,
                (holder, action) -> capturedActions.add(new CapturedAction(holder, action)));
        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(page, player.getInventory(),
                rootHolder, page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
        UIElement pageRoot = shell.getChildren().getFirst().getChildren().getFirst();
        UIElement coverControls = pageRoot.getChildren().get(1);
        UIElement configButton = coverControls.getChildren().getFirst();
        UIElement coverSlot = coverControls.getChildren().get(1);

        helper.assertTrue(rootHolder != pageHolder, "test requires different root and page machine holders");
        helper.assertFalse(coverSlot.isVisible(), "cover slot should remain hidden before selecting a face");

        helper.assertTrue(page.handleSceneFaceClick(Direction.EAST, GLFW.GLFW_MOUSE_BUTTON_LEFT),
                "first cover-only face click should select the side");
        coverSlot.screenTick();
        helper.assertTrue(capturedActions.isEmpty(), "first cover-only face click sent an action");
        helper.assertTrue(coverSlot.isVisible() && coverSlot.isActive(),
                "cover slot did not activate after selecting a face");

        page.handleSceneFaceClick(Direction.EAST, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        helper.assertTrue(capturedActions.isEmpty(),
                "repeated cover-only scene click sent an auto-output action");

        player.containerMenu.setCarried(GTItems.COVER_MACHINE_CONTROLLER.asStack());
        clickElement(coverSlot, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        assertAction(helper, capturedActions, 0, pageHolder, PLACE_DIRECTIONAL_COVER_ACTION,
                Direction.EAST.get3DDataValue());

        player.containerMenu.setCarried(ItemStack.EMPTY);
        TestUtils.placeCover(helper, machine, GTItems.COVER_MACHINE_CONTROLLER.asStack(), Direction.EAST);
        coverSlot.screenTick();
        configButton.screenTick();
        GTItemSlotElement localCoverSlot = (GTItemSlotElement) coverSlot;
        helper.assertTrue(localCoverSlot.getValue().is(GTItems.COVER_MACHINE_CONTROLLER.get()),
                "cover slot did not refresh from the machine cover container's synced state");
        helper.assertTrue(configButton.isVisible() && configButton.isActive(),
                "configurable cover did not activate its open button");

        clickElement(configButton, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        assertAction(helper, capturedActions, 1, pageHolder, OPEN_DIRECTIONAL_COVER_ACTION,
                Direction.EAST.get3DDataValue());
        clickElement(coverSlot, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        assertAction(helper, capturedActions, 2, pageHolder, REMOVE_DIRECTIONAL_COVER_ACTION,
                Direction.EAST.get3DDataValue());
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalFancyConfigurator")
    public static void deniedCoverDoesNotExposeConfigAction(GameTestHelper helper) {
        BatteryBufferMachine machine = createCoverOnlyMachine(helper);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MachineUIHolder pageHolder = new MachineUIHolderContext(player, machine);
        List<CapturedAction> capturedActions = new ArrayList<>();
        LDLib2DirectionalFancyConfigurator page = new LDLib2DirectionalFancyConfigurator(
                machine, player, pageHolder,
                (holder, action) -> capturedActions.add(new CapturedAction(holder, action)));
        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(page, player.getInventory(),
                pageHolder, page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
        UIElement pageRoot = shell.getChildren().getFirst().getChildren().getFirst();
        UIElement configButton = pageRoot.getChildren().get(1).getChildren().getFirst();

        DeniedLDLib2Cover deniedCover = new DeniedLDLib2Cover(machine, Direction.EAST);
        deniedCover.onAttached(GTItems.COVER_MACHINE_CONTROLLER.asStack(), player);
        machine.getCoverContainer().setCoverAtSide(deniedCover, Direction.EAST);
        page.handleSceneFaceClick(Direction.EAST, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        configButton.screenTick();

        helper.assertFalse(configButton.isVisible(),
                "cover that denies this player must keep its config button hidden");
        helper.assertFalse(configButton.isActive(),
                "cover that denies this player must keep its config button inactive");
        clickElement(configButton, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        helper.assertTrue(capturedActions.isEmpty(),
                "cover that denies this player must not send an open action");
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
                machine, player, pageHolder,
                (holder, action) -> capturedActions.add(new CapturedAction(holder, action)));
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
                machine, player, pageHolder,
                (holder, action) -> capturedActions.add(new CapturedAction(holder, action)));
        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(page, player.getInventory(),
                rootHolder, page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
        UIElement pageRoot = shell.getChildren().getFirst().getChildren().getFirst();
        UIElement fluidLabel = pageRoot.getChildren().get(2);
        UIElement fluidControls = pageRoot.getChildren().get(4);
        UIElement fluidModeButton = fluidControls.getChildren().getFirst();
        UIElement allowFluidInputButton = fluidControls.getChildren().get(1);

        helper.assertTrue(rootHolder != pageHolder, "test requires different root and page machine holders");
        helper.assertTrue(pageRoot.getChildren().size() == 6,
                "combined page should contain one scene, two labels, two output control rows, and cover controls");
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
        LDLib2DirectionalFancyConfigurator page = new LDLib2DirectionalFancyConfigurator(machine, player, pageHolder);

        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(page, player.getInventory(),
                pageHolder, page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
        UIElement pageContainer = shell.getChildren().getFirst();
        UIElement pageRoot = pageContainer.getChildren().getFirst();
        UIElement sceneHost = pageRoot.getChildren().getFirst();
        UIElement autoOutputLabel = pageRoot.getChildren().get(1);

        helper.assertTrue(pageRoot.getChildren().size() == 4,
                "item directional page should keep scene, label, output controls, and cover controls in stable order");
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

    private static BatteryBufferMachine createCoverOnlyMachine(GameTestHelper helper) {
        return (BatteryBufferMachine) TestUtils.setMachine(helper, new BlockPos(1, 1, 1),
                GTMachines.BATTERY_BUFFER_4[GTValues.LV]);
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
        clickElement(button, GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void clickElement(UIElement element, int button) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = element;
        event.button = button;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
    }

    private static final class DeniedLDLib2Cover extends CoverBehavior implements LDLib2CoverUIProvider {

        private DeniedLDLib2Cover(BatteryBufferMachine machine, Direction side) {
            super(GTCovers.MACHINE_CONTROLLER, machine.getCoverContainer(), side);
        }

        @Override
        public boolean canCreateLDLib2UI(Player player, UICoverHolder holder) {
            return false;
        }

        @Override
        public UI createLDLib2UI(Player player, UICoverHolder holder) {
            throw new IllegalStateException("Denied test cover must never create an LDLib2 UI.");
        }
    }

    private record CapturedAction(MachineUIHolder holder, SyncActionData action) {}
}

package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2CoverUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolderContext;
import com.gregtechceu.gtceu.api.gui.factory.UICoverHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2DirectionalFaceClickTracker;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTCovers;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
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
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LDLib2DirectionalFancyConfiguratorTest {

    private static final ResourceLocation PLACE_DIRECTIONAL_COVER_ACTION = GTCEu.id("place_directional_cover");
    private static final ResourceLocation REMOVE_DIRECTIONAL_COVER_ACTION = GTCEu.id("remove_directional_cover");
    private static final ResourceLocation OPEN_DIRECTIONAL_COVER_ACTION = GTCEu.id("open_directional_cover");
    private static final ResourceLocation ALLOW_ITEM_INPUT_FIELD = SyncFieldData.key("allowItemInputFromOutputSide");
    private static final ResourceLocation ALLOW_FLUID_INPUT_FIELD = SyncFieldData
            .key("allowFluidInputFromOutputSide");
    private static final ResourceLocation AUTO_OUTPUT_ITEMS_FIELD = SyncFieldData.key("autoOutputItems");
    private static final ResourceLocation AUTO_OUTPUT_FLUIDS_FIELD = SyncFieldData.key("autoOutputFluids");
    private static final ResourceLocation ITEM_OUTPUT_DIRECTION_FIELD = SyncFieldData.key("itemOutputDirection");
    private static final ResourceLocation FLUID_OUTPUT_DIRECTION_FIELD = SyncFieldData.key("fluidOutputDirection");

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
        helper.assertTrue(capturedActions.isEmpty() && !output.allowsItemInputFromOutputSide(),
                "server-side item input-policy callback changed state or sent an action");

        helper.assertTrue(page.handleSceneFaceClick(Direction.EAST, GLFW.GLFW_MOUSE_BUTTON_LEFT),
                "first item face click should select the side");
        helper.assertTrue(capturedActions.isEmpty(), "first item face click must not configure output");

        output.setItemOutputDirection(Direction.EAST);
        output.setAllowAutoOutputItems(false);
        helper.assertTrue(page.getItemOutputMode() == LDLib2DirectionalFancyConfigurator.OutputMode.OUTPUT,
                "selected item output side should use output mode when auto-output is disabled");
        output.setAllowAutoOutputItems(true);
        helper.assertTrue(page.getItemOutputMode() == LDLib2DirectionalFancyConfigurator.OutputMode.AUTO,
                "selected item output side should use auto mode when auto-output is enabled");

        page.handleSceneFaceClick(Direction.EAST, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        helper.assertTrue(capturedActions.isEmpty(), "right-click must not configure item output");

        page.handleSceneFaceClick(Direction.EAST, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        helper.assertTrue(capturedActions.isEmpty() && output.isAutoOutputItems(),
                "server-side scene callback changed item auto-output or sent a legacy action");

        clickButton(outputModeButton);
        helper.assertTrue(capturedActions.isEmpty() && output.isAutoOutputItems(),
                "server-side item mode button changed item auto-output or sent a legacy action");

        page.handleSceneFaceClick(Direction.SOUTH, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        helper.assertTrue(capturedActions.isEmpty(), "selecting a different face sent an action");
        helper.assertTrue(page.getItemOutputMode() == LDLib2DirectionalFancyConfigurator.OutputMode.OFF,
                "a selected side different from item output should use off mode");
        clickButton(outputModeButton);
        helper.assertTrue(capturedActions.isEmpty() && output.getItemOutputDirection() == Direction.EAST &&
                output.isAutoOutputItems(),
                "server-side item mode button changed the selected output fields");

        output.setAllowItemInputFromOutputSide(true);
        clickButton(allowInputButton);
        helper.assertTrue(capturedActions.isEmpty() && output.allowsItemInputFromOutputSide(),
                "server-side item input-policy callback changed state or sent an action");
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
        helper.assertTrue(capturedActions.isEmpty() && !output.allowsFluidInputFromOutputSide(),
                "server-side fluid input-policy callback changed state or sent an action");

        page.handleSceneFaceClick(Direction.UP, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        helper.assertTrue(capturedActions.isEmpty(), "first fluid face click must only select the side");

        output.setFluidOutputDirection(Direction.UP);
        output.setAllowAutoOutputFluids(false);
        helper.assertTrue(page.getFluidOutputMode() == LDLib2DirectionalFancyConfigurator.OutputMode.OUTPUT,
                "selected fluid output side should use output mode when auto-output is disabled");
        output.setAllowAutoOutputFluids(true);
        helper.assertTrue(page.getFluidOutputMode() == LDLib2DirectionalFancyConfigurator.OutputMode.AUTO,
                "selected fluid output side should use auto mode when auto-output is enabled");

        page.handleSceneFaceClick(Direction.UP, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        page.handleSceneFaceClick(Direction.UP, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        helper.assertTrue(capturedActions.isEmpty() && output.isAutoOutputFluids(),
                "server-side scene callback changed auto-output or sent a legacy action");

        clickButton(fluidModeButton);
        helper.assertTrue(capturedActions.isEmpty() && output.isAutoOutputFluids(),
                "server-side fluid mode button changed auto-output or sent a legacy action");

        page.handleSceneFaceClick(Direction.DOWN, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        helper.assertTrue(capturedActions.isEmpty(), "selecting a different fluid face sent an action");
        helper.assertTrue(page.getFluidOutputMode() == LDLib2DirectionalFancyConfigurator.OutputMode.OFF,
                "a selected side different from fluid output should use off mode");
        clickButton(fluidModeButton);
        helper.assertTrue(capturedActions.isEmpty() && output.getFluidOutputDirection() == Direction.UP &&
                output.isAutoOutputFluids(),
                "server-side fluid mode button changed the selected output fields");

        output.setAllowFluidInputFromOutputSide(true);
        clickButton(allowFluidInputButton);
        helper.assertTrue(capturedActions.isEmpty() && output.allowsFluidInputFromOutputSide(),
                "server-side fluid input-policy callback changed state or sent an action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalFancyConfigurator")
    public static void clientDirectionControlsFlushAtomicFieldBatches(GameTestHelper helper) {
        TestClientDirectionalMachine machine = createClientDirectionalMachine();
        machine.setFrontFacing(Direction.NORTH);
        AutoOutputTrait output = machine.autoOutput;
        output.setItemOutputDirection(Direction.SOUTH);
        output.setFluidOutputDirection(Direction.DOWN);
        output.setAllowAutoOutputItems(true);
        output.setAllowAutoOutputFluids(true);
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.beginSyncCapture(registries);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MachineUIHolder holder = new DirectMachineUIHolder(machine);
        List<CapturedAction> capturedActions = new ArrayList<>();
        LDLib2DirectionalFancyConfigurator page = new LDLib2DirectionalFancyConfigurator(
                machine, player, holder,
                (actionHolder, action) -> capturedActions.add(new CapturedAction(actionHolder, action)));
        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(page, player.getInventory(),
                holder, page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
        UIElement pageRoot = shell.getChildren().getFirst().getChildren().getFirst();
        UIElement itemModeButton = pageRoot.getChildren().get(3).getChildren().getFirst();
        UIElement fluidModeButton = pageRoot.getChildren().get(4).getChildren().getFirst();

        page.handleSceneFaceClick(Direction.EAST, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        helper.assertTrue(machine.syncRequests == 0,
                "first item scene click flushed before the side was confirmed");
        page.handleSceneFaceClick(Direction.EAST, GLFW.GLFW_MOUSE_BUTTON_LEFT);

        helper.assertTrue(output.getItemOutputDirection() == Direction.EAST && !output.isAutoOutputItems(),
                "confirmed item scene click did not change side and disable auto-output");
        helper.assertTrue(machine.syncRequests == 1 && machine.syncBatches.size() == 1,
                "item side change did not flush exactly once");
        assertDirectionChangeBatch(helper, machine.syncBatches.getFirst(), ITEM_OUTPUT_DIRECTION_FIELD,
                AUTO_OUTPUT_ITEMS_FIELD, Direction.EAST, "item side change");

        page.handleSceneFaceClick(Direction.EAST, GLFW.GLFW_MOUSE_BUTTON_LEFT);

        helper.assertTrue(output.isAutoOutputItems() && machine.syncRequests == 2,
                "same-side item scene click did not enable auto-output with one flush");
        assertBooleanOnlyBatch(helper, machine.syncBatches.get(1), AUTO_OUTPUT_ITEMS_FIELD, true,
                "same-side item enable");

        page.handleSceneFaceClick(Direction.EAST, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        helper.assertTrue(!output.isAutoOutputItems() && machine.syncRequests == 3,
                "second same-side item scene click did not disable auto-output with one flush");
        assertBooleanOnlyBatch(helper, machine.syncBatches.get(2), AUTO_OUTPUT_ITEMS_FIELD, false,
                "same-side item disable");

        page.handleSceneFaceClick(Direction.WEST, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        clickButton(itemModeButton);

        helper.assertTrue(output.getItemOutputDirection() == Direction.WEST && !output.isAutoOutputItems(),
                "item mode button did not change side while auto-output was already disabled");
        helper.assertTrue(machine.syncRequests == 4,
                "item mode button side change did not flush exactly once");
        assertDirectionOnlyBatch(helper, machine.syncBatches.get(3), ITEM_OUTPUT_DIRECTION_FIELD,
                Direction.WEST, "item side change while disabled");

        page.handleSceneFaceClick(Direction.UP, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        clickButton(fluidModeButton);

        helper.assertTrue(output.getFluidOutputDirection() == Direction.UP && !output.isAutoOutputFluids(),
                "fluid mode button did not change side and disable auto-output");
        helper.assertTrue(machine.syncRequests == 5,
                "fluid mode button side change did not flush exactly once");
        assertDirectionChangeBatch(helper, machine.syncBatches.get(4), FLUID_OUTPUT_DIRECTION_FIELD,
                AUTO_OUTPUT_FLUIDS_FIELD, Direction.UP, "fluid side change");

        clickButton(fluidModeButton);
        helper.assertTrue(output.isAutoOutputFluids() && machine.syncRequests == 6,
                "same-side fluid mode click did not enable auto-output with one flush");
        assertBooleanOnlyBatch(helper, machine.syncBatches.get(5), AUTO_OUTPUT_FLUIDS_FIELD, true,
                "same-side fluid enable");
        helper.assertTrue(capturedActions.isEmpty(),
                "client direction controls sent legacy auto-output actions");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalFancyConfigurator")
    public static void clientDirectionControlRejectsStalePageHolder(GameTestHelper helper) {
        TestClientDirectionalMachine target = createClientDirectionalMachine();
        TestClientDirectionalMachine resolved = createClientDirectionalMachine();
        target.setFrontFacing(Direction.NORTH);
        AutoOutputTrait output = target.autoOutput;
        output.setItemOutputDirection(Direction.SOUTH);
        output.setAllowAutoOutputItems(true);
        target.beginSyncCapture(helper.getLevel().registryAccess());
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MutableMachineUIHolder holder = new MutableMachineUIHolder(target);
        LDLib2DirectionalFancyConfigurator page = new LDLib2DirectionalFancyConfigurator(target, player, holder);
        page.handleSceneFaceClick(Direction.EAST, GLFW.GLFW_MOUSE_BUTTON_LEFT);

        holder.setMachine(resolved);
        boolean configured = page.configureSelectedItemOutputSide();

        helper.assertTrue(!configured, "direction control accepted a stale page holder");
        helper.assertTrue(output.getItemOutputDirection() == Direction.SOUTH && output.isAutoOutputItems(),
                "stale page holder changed item output state");
        helper.assertTrue(target.syncRequests == 0 && target.syncBatches.isEmpty(),
                "stale page holder flushed a field update");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalFancyConfigurator")
    public static void clientInputPolicyHandlersChangeFieldsAndFlushMachineSync(GameTestHelper helper) {
        TestClientDirectionalMachine machine = createClientDirectionalMachine();
        AutoOutputTrait output = machine.autoOutput;
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.beginSyncCapture(registries);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MachineUIHolder holder = new DirectMachineUIHolder(machine);
        List<CapturedAction> capturedActions = new ArrayList<>();
        LDLib2DirectionalFancyConfigurator page = new LDLib2DirectionalFancyConfigurator(
                machine, player, holder,
                (actionHolder, action) -> capturedActions.add(new CapturedAction(actionHolder, action)));

        page.setAllowItemInputFromOutputSide(true);
        page.setAllowFluidInputFromOutputSide(true);

        helper.assertTrue(output.allowsItemInputFromOutputSide() && output.allowsFluidInputFromOutputSide(),
                "client input-policy handlers did not update both local fields");
        helper.assertTrue(machine.syncRequests == 2,
                "client input-policy handlers did not flush each field update");
        helper.assertTrue(capturedActions.isEmpty(), "client input-policy handlers sent legacy actions");
        assertBooleanOnlyBatch(helper, machine.syncBatches.getFirst(), ALLOW_ITEM_INPUT_FIELD, true,
                "item input-policy enable");
        assertBooleanOnlyBatch(helper, machine.syncBatches.get(1), ALLOW_FLUID_INPUT_FIELD, true,
                "fluid input-policy enable");

        page.setAllowItemInputFromOutputSide(false);
        page.setAllowFluidInputFromOutputSide(false);

        helper.assertTrue(!output.allowsItemInputFromOutputSide() && !output.allowsFluidInputFromOutputSide(),
                "client input-policy handlers did not clear both local fields");
        helper.assertTrue(machine.syncRequests == 4,
                "client input-policy disable handlers did not flush each field update");
        assertBooleanOnlyBatch(helper, machine.syncBatches.get(2), ALLOW_ITEM_INPUT_FIELD, false,
                "item input-policy disable");
        assertBooleanOnlyBatch(helper, machine.syncBatches.get(3), ALLOW_FLUID_INPUT_FIELD, false,
                "fluid input-policy disable");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DirectionalFancyConfigurator")
    public static void fancyMachineShellAddsHolderScopedDirectionalPage(GameTestHelper helper) {
        BatteryBufferMachine machine = createCoverOnlyMachine(helper);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MachineUIHolder holder = new MachineUIHolderContext(player, machine);

        UI ui = machine.createLDLib2UI(player, holder);
        helper.assertTrue(ui.getRootElement() instanceof LDLib2FancyMachineUIElement,
                "migrated Fancy machine did not create the LDLib2 Fancy shell");
        LDLib2FancyMachineUIElement shell = (LDLib2FancyMachineUIElement) ui.getRootElement();
        UIElement pageContainer = shell.getChildren().getFirst();

        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                "migrated Fancy machine should expose its main and directional tabs");
        helper.assertTrue(pageContainer.getChildren().size() == 1,
                "holder-scoped directional page should be created lazily");

        UIElement directionalTab = shell.getSideTabsElement().getChildren().get(1);
        clickButton(directionalTab);

        helper.assertTrue(pageContainer.getChildren().size() == 2,
                "opening the directional tab should cache one holder-scoped page");
        UIElement directionalRoot = pageContainer.getChildren().get(1);
        helper.assertTrue(directionalRoot.getChildren().size() == 2,
                "cover-only directional page should contain its scene host and cover controls");
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

    private static TestClientDirectionalMachine createClientDirectionalMachine() {
        var definition = GTMachines.BUFFER[GTValues.LV];
        return new TestClientDirectionalMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()));
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

    private static void assertDirectionChangeBatch(GameTestHelper helper, DataComponentMap components,
                                                   ResourceLocation directionField,
                                                   ResourceLocation autoOutputField, Direction expectedDirection,
                                                   String description) {
        SyncFieldData fields = requireFields(components, description);
        helper.assertTrue(fields.fields().size() == 2,
                description + " did not send exactly one direction and one auto-output field");
        assertDirectionField(helper, fields, directionField, expectedDirection, description);
        assertBooleanField(helper, fields, autoOutputField, false, description);
    }

    private static void assertDirectionOnlyBatch(GameTestHelper helper, DataComponentMap components,
                                                 ResourceLocation directionField, Direction expectedDirection,
                                                 String description) {
        SyncFieldData fields = requireFields(components, description);
        helper.assertTrue(fields.fields().size() == 1,
                description + " included an unchanged auto-output field");
        assertDirectionField(helper, fields, directionField, expectedDirection, description);
    }

    private static void assertBooleanOnlyBatch(GameTestHelper helper, DataComponentMap components,
                                               ResourceLocation field, boolean expected, String description) {
        SyncFieldData fields = requireFields(components, description);
        helper.assertTrue(fields.fields().size() == 1,
                description + " included unrelated synchronized fields");
        assertBooleanField(helper, fields, field, expected, description);
    }

    private static SyncFieldData requireFields(DataComponentMap components, String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException(description + " did not collect synchronized field data.");
        }
        return fields;
    }

    private static void assertDirectionField(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                             Direction expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isString() &&
                primitive.getAsString().equals(expected.getSerializedName()),
                description + " did not encode the expected direction enum string");
    }

    private static void assertBooleanField(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                           boolean expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " did not encode the expected boolean field");
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

    private static final class TestClientDirectionalMachine extends MetaMachine {

        private final AutoOutputTrait autoOutput;
        private final List<DataComponentMap> syncBatches = new ArrayList<>();
        private int syncRequests;
        private RegistryAccess syncRegistries;

        private TestClientDirectionalMachine(BlockEntityCreationInfo info) {
            super(info);
            autoOutput = attachTrait(new TestAutoOutputTrait());
        }

        private void beginSyncCapture(RegistryAccess registries) {
            syncRegistries = registries;
            autoOutput.getSyncDataHolder().collectServerNetworkChanges(registries);
            syncBatches.clear();
            syncRequests = 0;
        }

        @Override
        public boolean isRemote() {
            return true;
        }

        @Override
        public void sendServerSyncChanges() {
            if (syncRegistries == null) {
                throw new IllegalStateException("Test client machine did not initialize sync capture.");
            }
            syncRequests++;
            syncBatches.add(autoOutput.getSyncDataHolder().collectServerNetworkChanges(syncRegistries));
        }
    }

    private static final class TestAutoOutputTrait extends AutoOutputTrait {

        private TestAutoOutputTrait() {
            super(List.<IItemHandler>of(new ItemStackHandler(1)),
                    List.<IFluidHandler>of(new FluidTank(1_000)), false);
        }

        @Override
        protected void updateItemOutputSubscription() {}

        @Override
        protected void updateFluidOutputSubscription() {}
    }

    private record DirectMachineUIHolder(TestClientDirectionalMachine machine) implements MachineUIHolder {

        @Override
        public BlockPos getPos() {
            return machine.getBlockPos();
        }

        @Override
        public ResourceLocation getMachineDefinitionId() {
            return machine.getDefinition().getId();
        }

        @Override
        public TestClientDirectionalMachine getMachine() {
            return machine;
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

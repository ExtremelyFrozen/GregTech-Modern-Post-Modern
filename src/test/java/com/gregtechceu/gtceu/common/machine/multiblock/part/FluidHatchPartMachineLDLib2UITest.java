package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTPhantomFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.gregtechceu.gtceu.api.GTValues.EV;
import static com.gregtechceu.gtceu.api.GTValues.LV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class FluidHatchPartMachineLDLib2UITest {

    private static final ResourceLocation CLICK_FLUID_SLOT_ACTION = GTCEu.id("click_fluid_hatch_fluid_slot");
    private static final ResourceLocation SET_LOCKED_FLUID_ACTION = GTCEu.id("set_fluid_hatch_locked_fluid");
    private static final ResourceLocation SET_LOCKED_ACTION = GTCEu.id("set_fluid_hatch_locked");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidHatchPartMachineLDLib2UI")
    public static void holderIdentityAndSpecializedDefinitionFallbackAreEnforced(GameTestHelper helper) {
        FluidHatchPartMachine input = createFluidHatch(GTMachines.FLUID_IMPORT_HATCH[LV]);
        FluidHatchPartMachine output = createFluidHatch(GTMachines.FLUID_EXPORT_HATCH[LV]);
        MachineUIHolder inputHolder = new TestMachineUIHolder(input);
        MachineUIHolder outputHolder = new TestMachineUIHolder(output);

        helper.assertTrue(input.canCreateLDLib2UI(FakePlayerFactory.getMinecraft(helper.getLevel()), inputHolder),
                "ordinary fluid hatch rejected its matching holder");
        helper.assertTrue(!input.canCreateLDLib2UI(FakePlayerFactory.getMinecraft(helper.getLevel()), outputHolder),
                "ordinary fluid hatch accepted another machine's holder");

        boolean mismatchedHolderRejected = false;
        try {
            input.createLDLib2UI(FakePlayerFactory.getMinecraft(helper.getLevel()), outputHolder);
        } catch (IllegalArgumentException expected) {
            mismatchedHolderRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(mismatchedHolderRejected, "direct page creation accepted a mismatched holder");

        FluidHatchPartMachine specialized = new FluidHatchPartMachine(
                info(GTMachines.ITEM_IMPORT_BUS[LV]), LV, IO.IN,
                FluidHatchPartMachine.INITIAL_TANK_CAPACITY_1X, 1);
        helper.assertTrue(!specialized.canCreateLDLib2UI(FakePlayerFactory.getMinecraft(helper.getLevel()),
                new TestMachineUIHolder(specialized)),
                "a specialized definition inherited the generic fluid hatch page instead of retaining fallback");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidHatchPartMachineLDLib2UI")
    public static void pagesPreserveSizingSlotsPermissionsConfiguratorsAndGrouping(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        FluidHatchPartMachine input = createFluidHatch(GTMachines.FLUID_IMPORT_HATCH[LV]);
        FluidHatchPartMachine output = createFluidHatch(GTMachines.FLUID_EXPORT_HATCH[LV]);
        FluidHatchPartMachine quadruple = createFluidHatch(GTMachines.FLUID_IMPORT_HATCH_4X[EV]);

        MachineUIHolder inputHolder = new TestMachineUIHolder(input);
        MachineUIHolder outputHolder = new TestMachineUIHolder(output);
        MachineUIHolder quadrupleHolder = new TestMachineUIHolder(quadruple);
        LDLib2FancyUIProvider inputPage = input.createLDLib2Page(player, inputHolder);
        LDLib2FancyUIProvider outputPage = output.createLDLib2Page(player, outputHolder);
        LDLib2FancyUIProvider quadruplePage = quadruple.createLDLib2Page(player, quadrupleHolder);

        helper.assertTrue(inputPage.getLDLib2PageWidth() == 89 && inputPage.getLDLib2PageHeight() == 63,
                "single input hatch page did not preserve the 89x63 legacy body");
        helper.assertTrue(outputPage.getLDLib2PageWidth() == 89 && outputPage.getLDLib2PageHeight() == 63,
                "single output hatch page did not preserve the 89x63 legacy body");
        helper.assertTrue(quadruplePage.getLDLib2PageWidth() == 52 && quadruplePage.getLDLib2PageHeight() == 52,
                "quadruple hatch page did not preserve the 2x2 tank-grid dimensions");
        assertGrouping(helper, inputPage, "gtpm.multiblock.page_switcher.io.import", 1, "input hatch");
        assertGrouping(helper, outputPage, "gtpm.multiblock.page_switcher.io.export", 2, "output hatch");

        LDLib2FancyMachineUIElement inputShell = createShell(player, input, inputHolder);
        LDLib2FancyMachineUIElement outputShell = createShell(player, output, outputHolder);
        LDLib2FancyMachineUIElement quadrupleShell = createShell(player, quadruple, quadrupleHolder);
        List<GTFluidSlotElement> inputSlots = normalFluidSlots(pageRoot(inputShell));
        List<GTFluidSlotElement> outputSlots = normalFluidSlots(pageRoot(outputShell));
        List<GTFluidSlotElement> quadrupleSlots = normalFluidSlots(pageRoot(quadrupleShell));

        helper.assertTrue(inputSlots.size() == 1, "single input hatch did not expose one real tank slot");
        helper.assertTrue(outputSlots.size() == 1, "single output hatch did not expose one real tank slot");
        helper.assertTrue(quadrupleSlots.size() == 4, "quadruple input hatch did not expose four real tank slots");
        helper.assertTrue(phantomFluidSlots(pageRoot(inputShell)).isEmpty(),
                "input hatch unexpectedly exposed locked-fluid selection");
        helper.assertTrue(phantomFluidSlots(pageRoot(outputShell)).size() == 1,
                "output hatch did not expose exactly one phantom locked-fluid slot");
        helper.assertTrue(inputSlots.getFirst().isAllowClickFilled() && inputSlots.getFirst().isAllowClickDrained(),
                "input hatch tank slot did not permit both filling and draining container interactions");
        helper.assertTrue(outputSlots.getFirst().isAllowClickFilled() &&
                !outputSlots.getFirst().isAllowClickDrained(),
                "output hatch tank slot permitted draining a container into the output tank");
        helper.assertTrue(inputSlots.getFirst().getCapacity() == input.tank.getTankCapacity(0),
                "input hatch slot did not expose the real tank capacity");
        helper.assertTrue(outputSlots.getFirst().getCapacity() == output.tank.getTankCapacity(0),
                "output hatch slot did not expose the real tank capacity");
        helper.assertTrue(inputShell.getConfiguratorPanel().getChildren().size() == 2,
                "input hatch did not expose working and circuit configurators");
        helper.assertTrue(outputShell.getConfiguratorPanel().getChildren().size() == 1,
                "output hatch should expose only its working configurator");
        helper.assertTrue(inputShell.getSideTabsElement().getChildren().size() == 2 &&
                outputShell.getSideTabsElement().getChildren().size() == 2,
                "fluid hatch pages did not expose their holder-scoped directional tab");
        helper.assertTrue(!inputShell.getTooltipsPanel().getChildren().isEmpty() &&
                !outputShell.getTooltipsPanel().getChildren().isEmpty(),
                "fluid hatch pages did not attach machine tooltip metadata");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidHatchPartMachineLDLib2UI")
    public static void pageControlsSendExactlyOneHolderScopedActionWithoutFieldFlush(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        TestFluidHatchPartMachine output = new TestFluidHatchPartMachine(
                info(GTMachines.FLUID_EXPORT_HATCH[LV]), LV, IO.OUT,
                FluidHatchPartMachine.INITIAL_TANK_CAPACITY_1X, 1);
        MachineUIHolder holder = new TestMachineUIHolder(output);
        List<CapturedAction> actions = new ArrayList<>();
        UIElement page = output.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)),
                () -> true,
                event -> Boolean.TRUE.equals(event.customData));
        GTFluidSlotElement tankSlot = normalFluidSlots(page).getFirst();
        GTPhantomFluidSlotElement lockedSlot = phantomFluidSlots(page).getFirst();
        GTToggleButtonElement lockToggle = toggleButtons(page).getFirst();

        UIEvent tankClick = click(tankSlot, true);
        UIEvent phantomClick = click(lockedSlot, false);
        UIEvent toggleClick = click(lockToggle, false);

        helper.assertTrue(actions.size() == 3, "three controls did not send exactly one action each");
        assertAction(helper, actions.get(0), holder, CLICK_FLUID_SLOT_ACTION, "real tank click");
        assertAction(helper, actions.get(1), holder, SET_LOCKED_FLUID_ACTION, "phantom fluid click");
        assertAction(helper, actions.get(2), holder, SET_LOCKED_ACTION, "lock toggle click");
        helper.assertTrue(tankClick.hasHandler && phantomClick.hasHandler && toggleClick.hasHandler,
                "fluid hatch controls did not mark their handled events");
        helper.assertTrue(output.getServerSyncFlushes() == 0,
                "action-backed controls also emitted an annotation field flush");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidHatchPartMachineLDLib2UI")
    public static void phantomSlotDistinguishesCursorFluidCapability(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        FluidHatchPartMachine output = createFluidHatch(GTMachines.FLUID_EXPORT_HATCH[LV]);
        MachineUIHolder holder = new TestMachineUIHolder(output);
        List<CapturedAction> actions = new ArrayList<>();
        UIElement page = output.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)),
                () -> true,
                event -> false);
        GTPhantomFluidSlotElement lockedSlot = phantomFluidSlots(page).getFirst();
        FluidStack lockedWater = new FluidStack(Fluids.WATER, 1);

        output.tank.setLocked(true, lockedWater);
        player.containerMenu.setCarried(new ItemStack(Items.STONE));
        UIEvent stoneClick = click(lockedSlot, false);
        helper.assertTrue(actions.isEmpty(), "non-fluid cursor item sent a locked-fluid action");
        helper.assertTrue(!stoneClick.hasHandler, "non-fluid cursor item marked the phantom click handled");
        helper.assertTrue(output.tank.isLocked(), "non-fluid cursor item changed the locked fluid");

        player.containerMenu.setCarried(ItemStack.EMPTY);
        UIEvent emptyCursorClick = click(lockedSlot, false);
        assertSingleLockedFluidAction(helper, actions, holder, true, "empty cursor");
        helper.assertTrue(emptyCursorClick.hasHandler, "empty cursor did not mark the phantom click handled");
        helper.assertTrue(dispatch(player, output, actions.getFirst().action()),
                "empty-cursor locked-fluid action was rejected");
        helper.assertTrue(!output.tank.isLocked(), "empty cursor did not clear the locked fluid");
        actions.clear();

        output.tank.setLocked(true, lockedWater);
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
        UIEvent emptyBucketClick = click(lockedSlot, false);
        assertSingleLockedFluidAction(helper, actions, holder, true, "empty fluid container");
        helper.assertTrue(emptyBucketClick.hasHandler,
                "empty fluid container did not mark the phantom click handled");
        helper.assertTrue(dispatch(player, output, actions.getFirst().action()),
                "empty-container locked-fluid action was rejected");
        helper.assertTrue(!output.tank.isLocked(), "empty fluid container did not clear the locked fluid");
        actions.clear();

        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        UIEvent waterBucketClick = click(lockedSlot, false);
        assertSingleLockedFluidAction(helper, actions, holder, false, "water container");
        FluidStack selectedWater = getLockedFluidPayload(actions.getFirst());
        helper.assertTrue(selectedWater.is(Fluids.WATER) && selectedWater.getAmount() == 1,
                "water container did not send one unit of water");
        helper.assertTrue(waterBucketClick.hasHandler,
                "water container did not mark the phantom click handled");
        helper.assertTrue(dispatch(player, output, actions.getFirst().action()),
                "water-container locked-fluid action was rejected");
        helper.assertTrue(output.tank.isLocked() &&
                output.tank.getLockedFluid().getFluid().is(Fluids.WATER),
                "water container did not select water as the locked fluid");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidHatchPartMachineLDLib2UI")
    public static void dispatcherExecutesTankAndLockActionsOnceAndRejectsWrongHolder(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));
        FluidHatchPartMachine input = createFluidHatch(GTMachines.FLUID_IMPORT_HATCH[LV]);
        input.tank.setFluidInTank(0, new FluidStack(Fluids.WATER, 2 * FluidType.BUCKET_VOLUME));
        SyncActionData clickAction = FluidHatchPartMachineActions.createClickFluidSlotAction(0, false);

        helper.assertTrue(dispatch(player, input, clickAction), "valid fluid hatch tank action was rejected");
        helper.assertTrue(input.tank.getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME,
                "one tank action did not transfer exactly one bucket");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "tank action did not replace the carried empty bucket");
        helper.assertTrue(!dispatch(player, new Object(), clickAction),
                "fluid hatch tank action accepted a non-fluid-hatch holder");

        FluidHatchPartMachine output = createFluidHatch(GTMachines.FLUID_EXPORT_HATCH[LV]);
        SyncActionData setLockedFluid = FluidHatchPartMachineActions.createSetLockedFluidAction(
                new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME));
        helper.assertTrue(dispatch(player, output, setLockedFluid), "valid locked-fluid action was rejected");
        helper.assertTrue(output.tank.isLocked() &&
                output.tank.getLockedFluid().getFluid().getAmount() == 1 &&
                output.tank.getLockedFluid().getFluid().is(Fluids.WATER),
                "locked-fluid action did not normalize and apply the selected fluid");
        helper.assertTrue(dispatch(player, output,
                FluidHatchPartMachineActions.createSetLockedAction(false)),
                "valid lock-toggle action was rejected");
        helper.assertTrue(!output.tank.isLocked(), "lock-toggle action did not clear the locked fluid");
        helper.succeed();
    }

    private static void assertGrouping(GameTestHelper helper, LDLib2FancyUIProvider page,
                                       String expectedKey, int expectedWeight, String description) {
        LDLib2FancyUIProvider.PageGroupingData grouping = page.getPageGroupingData();
        if (grouping == null) {
            throw new IllegalStateException(description + " omitted IO page grouping metadata");
        }
        helper.assertTrue(expectedKey.equals(grouping.groupKey()) &&
                grouping.groupPositionWeight() == expectedWeight,
                description + " exposed incorrect IO page grouping metadata");
    }

    private static void assertAction(GameTestHelper helper, CapturedAction captured,
                                     MachineUIHolder expectedHolder, ResourceLocation expectedAction,
                                     String description) {
        helper.assertTrue(captured.holder() == expectedHolder, description + " used the wrong opened holder");
        helper.assertTrue(captured.action().actionId().equals(expectedAction),
                description + " used the wrong action id");
    }

    private static void assertSingleLockedFluidAction(GameTestHelper helper, List<CapturedAction> actions,
                                                      MachineUIHolder holder, boolean expectedEmpty,
                                                      String description) {
        helper.assertTrue(actions.size() == 1, description + " did not send exactly one action");
        assertAction(helper, actions.getFirst(), holder, SET_LOCKED_FLUID_ACTION, description);
        helper.assertTrue(getLockedFluidPayload(actions.getFirst()).isEmpty() == expectedEmpty,
                description + " sent the wrong locked-fluid payload");
    }

    private static FluidStack getLockedFluidPayload(CapturedAction captured) {
        return captured.action().payload()
                .getOrDefault(GTDataComponents.FLUID_CONTENT.get(), SimpleFluidContent.EMPTY)
                .copy();
    }

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player, FluidHatchPartMachine machine,
                                                           MachineUIHolder holder) {
        UIElement root = machine.createLDLib2UI(player, holder).getRootElement();
        if (!(root instanceof LDLib2FancyMachineUIElement shell)) {
            throw new IllegalStateException("Fluid hatch LDLib2 UI did not create a Fancy shell.");
        }
        return shell;
    }

    private static UIElement pageRoot(LDLib2FancyMachineUIElement shell) {
        return shell.getChildren().getFirst().getChildren().getFirst();
    }

    private static List<GTFluidSlotElement> normalFluidSlots(UIElement root) {
        return allDescendants(root).stream()
                .filter(element -> element instanceof GTFluidSlotElement)
                .filter(element -> !(element instanceof GTPhantomFluidSlotElement))
                .map(element -> (GTFluidSlotElement) element)
                .toList();
    }

    private static List<GTPhantomFluidSlotElement> phantomFluidSlots(UIElement root) {
        return allDescendants(root).stream()
                .filter(element -> element instanceof GTPhantomFluidSlotElement)
                .map(element -> (GTPhantomFluidSlotElement) element)
                .toList();
    }

    private static List<GTToggleButtonElement> toggleButtons(UIElement root) {
        return allDescendants(root).stream()
                .filter(element -> element instanceof GTToggleButtonElement)
                .map(element -> (GTToggleButtonElement) element)
                .toList();
    }

    private static List<UIElement> allDescendants(UIElement root) {
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

    private static UIEvent click(UIElement element, boolean shiftDown) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = element;
        event.button = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        event.customData = shiftDown;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
        return event;
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO,
                null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static FluidHatchPartMachine createFluidHatch(MachineDefinition definition) {
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (!(machine instanceof FluidHatchPartMachine fluidHatch)) {
            throw new IllegalStateException("Fluid hatch definition did not create a fluid hatch machine.");
        }
        return fluidHatch;
    }

    private static BlockEntityCreationInfo info(MachineDefinition definition) {
        return new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.defaultBlockState());
    }

    private record CapturedAction(MachineUIHolder holder, SyncActionData action) {}

    private record TestMachineUIHolder(MetaMachine machine) implements MachineUIHolder {

        @Override
        public BlockPos getPos() {
            return BlockPos.ZERO;
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

    private static final class TestFluidHatchPartMachine extends FluidHatchPartMachine {

        private int serverSyncFlushes;

        private TestFluidHatchPartMachine(BlockEntityCreationInfo info, int tier, IO io, int initialCapacity,
                                          int slots) {
            super(info, tier, io, initialCapacity, slots);
        }

        @Override
        public void sendServerSyncChanges() {
            serverSyncFlushes++;
        }

        private int getServerSyncFlushes() {
            return serverSyncFlushes;
        }
    }
}

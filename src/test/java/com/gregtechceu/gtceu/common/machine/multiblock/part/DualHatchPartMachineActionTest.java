package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyPreviewPage;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTabsElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider.PageGroupingData;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMachines;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.lwjgl.glfw.GLFW;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.gregtechceu.gtceu.api.GTValues.LuV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class DualHatchPartMachineActionTest {

    private static final ResourceLocation CLICK_DUAL_HATCH_FLUID_SLOT_ACTION = GTCEu
            .id("click_dual_hatch_fluid_slot");
    private static final ResourceLocation TANK_FIELD = SyncFieldData.key("tank");
    private static final ResourceLocation SHIFT_FIELD = SyncFieldData.key("shift");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DualHatchPartMachineAction")
    public static void dispatcherExecutesValidFluidSlotActionOnce(GameTestHelper helper) {
        DualHatchPartMachine machine = createDualHatch(GTMachines.DUAL_IMPORT_HATCH[LuV]);
        machine.tank.setFluidInTank(0, new FluidStack(Fluids.WATER, 2 * FluidType.BUCKET_VOLUME));
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));

        boolean result = dispatch(player, machine, payload(new JsonPrimitive(0), new JsonPrimitive(false)));

        helper.assertTrue(result, "valid dual hatch fluid-slot action was rejected");
        helper.assertTrue(machine.tank.getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME,
                "valid non-shift action did not transfer exactly one bucket");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "valid fluid-slot action did not replace the carried empty bucket");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DualHatchPartMachineAction")
    public static void dispatcherRoutesFluidSlotActionToNonzeroTank(GameTestHelper helper) {
        DualHatchPartMachine machine = createDualHatch(GTMachines.DUAL_IMPORT_HATCH[LuV]);
        machine.tank.setFluidInTank(0, new FluidStack(Fluids.WATER, 3 * FluidType.BUCKET_VOLUME));
        machine.tank.setFluidInTank(1, new FluidStack(Fluids.WATER, 2 * FluidType.BUCKET_VOLUME));
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));

        boolean result = dispatch(player, machine, payload(new JsonPrimitive(1), new JsonPrimitive(false)));

        helper.assertTrue(result, "valid nonzero dual hatch tank index was rejected");
        helper.assertTrue(machine.tank.getFluidInTank(0).getAmount() == 3 * FluidType.BUCKET_VOLUME,
                "nonzero tank action changed tank zero");
        helper.assertTrue(machine.tank.getFluidInTank(1).getAmount() == FluidType.BUCKET_VOLUME,
                "nonzero tank action did not drain the selected tank");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "nonzero tank action did not fill the carried container");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DualHatchPartMachineAction")
    public static void nonShiftActionTransfersOnlyOneOfThreeCarriedContainers(GameTestHelper helper) {
        int initialFluid = 2 * FluidType.BUCKET_VOLUME;
        DualHatchPartMachine machine = createDualHatch(GTMachines.DUAL_IMPORT_HATCH[LuV]);
        machine.tank.setFluidInTank(0, new FluidStack(Fluids.WATER, initialFluid));
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET, 3));

        boolean result = dispatch(player, machine, payload(new JsonPrimitive(0), new JsonPrimitive(false)));

        int emptyContainers = player.getInventory().countItem(Items.BUCKET) +
                (player.containerMenu.getCarried().is(Items.BUCKET) ?
                        player.containerMenu.getCarried().getCount() : 0);
        int filledContainers = player.getInventory().countItem(Items.WATER_BUCKET) +
                (player.containerMenu.getCarried().is(Items.WATER_BUCKET) ?
                        player.containerMenu.getCarried().getCount() : 0);
        int remainingFluid = machine.tank.getFluidInTank(0).getAmount();

        helper.assertTrue(result, "valid non-shift dual hatch action was rejected");
        helper.assertTrue(emptyContainers == 2, "non-shift action consumed more than one empty container");
        helper.assertTrue(filledContainers == 1, "non-shift action did not produce exactly one filled container");
        helper.assertTrue(emptyContainers + filledContainers == 3,
                "non-shift action did not conserve the carried container count");
        helper.assertTrue(remainingFluid + filledContainers * FluidType.BUCKET_VOLUME == initialFluid,
                "non-shift action did not conserve fluid");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DualHatchPartMachineAction")
    public static void dispatcherExecutesShiftFluidSlotActionForEveryCarriedContainer(GameTestHelper helper) {
        DualHatchPartMachine machine = createDualHatch(GTMachines.DUAL_IMPORT_HATCH[LuV]);
        machine.tank.setFluidInTank(0, new FluidStack(Fluids.WATER, 4 * FluidType.BUCKET_VOLUME));
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET, 3));

        boolean result = dispatch(player, machine, payload(new JsonPrimitive(0), new JsonPrimitive(true)));

        helper.assertTrue(result, "valid shifted dual hatch fluid-slot action was rejected");
        helper.assertTrue(machine.tank.getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME,
                "shift action did not transfer one bucket for every carried container");
        int filledContainers = player.getInventory().countItem(Items.WATER_BUCKET) +
                (player.containerMenu.getCarried().is(Items.WATER_BUCKET) ?
                        player.containerMenu.getCarried().getCount() : 0);
        int emptyContainers = player.getInventory().countItem(Items.BUCKET) +
                (player.containerMenu.getCarried().is(Items.BUCKET) ?
                        player.containerMenu.getCarried().getCount() : 0);
        helper.assertTrue(filledContainers == 3,
                "shift action did not return all three filled containers to the player");
        helper.assertTrue(emptyContainers == 0,
                "shift action left empty buckets after filling every carried container");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DualHatchPartMachineAction")
    public static void fluidSlotMouseDownSendsSelectedTankAndShiftStateThroughPageHolder(GameTestHelper helper) {
        DualHatchPartMachine machine = createDualHatch(GTMachines.DUAL_IMPORT_HATCH[LuV]);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MachineUIHolder pageHolder = new TestMachineUIHolder(machine);
        List<CapturedAction> capturedActions = new ArrayList<>();
        UIElement page = machine.createLDLib2MainElement(
                player,
                pageHolder,
                (holder, action) -> capturedActions.add(new CapturedAction(holder, action)),
                event -> true,
                event -> Boolean.TRUE.equals(event.customData));
        List<GTFluidSlotElement> fluidSlots = page.getChildren().getFirst().getChildren().stream()
                .filter(GTFluidSlotElement.class::isInstance)
                .map(GTFluidSlotElement.class::cast)
                .toList();

        helper.assertTrue(fluidSlots.size() == 2, "LuV dual hatch did not create two clickable fluid slots");
        UIEvent unshifted = clickElement(fluidSlots.get(1), GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        UIEvent shifted = clickElement(fluidSlots.get(1), GLFW.GLFW_MOUSE_BUTTON_LEFT, true);

        assertCapturedFluidSlotAction(helper, capturedActions, 0, pageHolder, 1, false);
        assertCapturedFluidSlotAction(helper, capturedActions, 1, pageHolder, 1, true);
        helper.assertTrue(unshifted.hasHandler && unshifted.propagationStopped && unshifted.laterPropagationStopped,
                "unshifted fluid-slot event was not marked handled");
        helper.assertTrue(shifted.hasHandler && shifted.propagationStopped && shifted.laterPropagationStopped,
                "shifted fluid-slot event was not marked handled");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DualHatchPartMachineAction")
    public static void fluidContainerRemaindersMergeOnlyWhenTheWholeStackFits(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();

        ItemStack merged = DualHatchPartMachine.mergeOrStoreFluidContainerResult(
                player, new ItemStack(Items.GLASS_BOTTLE, 2), new ItemStack(Items.GLASS_BOTTLE, 2));

        helper.assertTrue(merged.getCount() == 4, "two count-two remainders did not merge to four items");
        helper.assertTrue(player.getInventory().countItem(Items.GLASS_BOTTLE) == 0,
                "fully merged remainder unexpectedly entered the player inventory");

        ItemStack retained = DualHatchPartMachine.mergeOrStoreFluidContainerResult(
                player, new ItemStack(Items.GLASS_BOTTLE, 63), new ItemStack(Items.GLASS_BOTTLE, 2));

        helper.assertTrue(retained.getCount() == 63, "overflowing remainder partially grew the retained stack");
        helper.assertTrue(player.getInventory().countItem(Items.GLASS_BOTTLE) == 2,
                "overflowing remainder was not returned to the player in full");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DualHatchPartMachineAction")
    public static void shiftedUniversalCellsPreserveTheFinalPartialFill(GameTestHelper helper) {
        DualHatchPartMachine machine = createDualHatch(GTMachines.DUAL_EXPORT_HATCH[LuV]);
        machine.tank.setFluidInTank(0, new FluidStack(Fluids.WATER, 2500));
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        ItemStack emptyCells = GTItems.FLUID_CELL_UNIVERSAL.asStack(3);
        helper.assertTrue(FluidUtil.getFluidHandler(emptyCells).isPresent(),
                "universal fluid cell did not expose its registered fluid capability");
        player.containerMenu.setCarried(emptyCells);

        machine.clickFluidSlot(player, 0, true);

        ItemStack partialCell = player.containerMenu.getCarried();
        int fullCells = countInventoryFluidContainers(player, GTItems.FLUID_CELL_UNIVERSAL.asStack(),
                FluidType.BUCKET_VOLUME);
        int totalCellFluid = countInventoryFluid(player, GTItems.FLUID_CELL_UNIVERSAL.asStack()) +
                getContainedFluidAmount(partialCell) * partialCell.getCount();
        helper.assertTrue(machine.tank.getFluidInTank(0).isEmpty(),
                "shifted universal cells did not drain the selected tank");
        helper.assertTrue(fullCells == 2, "shifted universal cells did not produce two full cells");
        helper.assertTrue(partialCell.is(GTItems.FLUID_CELL_UNIVERSAL.get()) && partialCell.getCount() == 1,
                "final partial universal cell was not left on the cursor");
        helper.assertTrue(getContainedFluidAmount(partialCell) == 500,
                "final universal cell did not preserve its 500 mB partial fill");
        helper.assertTrue(totalCellFluid == 2500, "universal cell shift fill did not conserve fluid");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DualHatchPartMachineAction")
    public static void largeFluidCellDrainsOnlyTheRemainingTankCapacity(GameTestHelper helper) {
        DualHatchPartMachine machine = createDualHatch(GTMachines.DUAL_IMPORT_HATCH[LuV]);
        int tankCapacity = machine.tank.getTankCapacity(0);
        machine.tank.setFluidInTank(0, new FluidStack(Fluids.WATER, tankCapacity - 2500));
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        ItemStack largeCell = fillFluidContainer(helper, GTItems.FLUID_CELL_LARGE_STEEL.asStack(),
                new FluidStack(Fluids.WATER, 8 * FluidType.BUCKET_VOLUME));
        player.containerMenu.setCarried(largeCell);

        machine.clickFluidSlot(player, 0, false);

        ItemStack partialCell = player.containerMenu.getCarried();
        helper.assertTrue(machine.tank.getFluidInTank(0).getAmount() == tankCapacity,
                "large cell did not fill exactly the remaining tank capacity");
        helper.assertTrue(partialCell.is(GTItems.FLUID_CELL_LARGE_STEEL.get()),
                "partial large cell was not returned to the cursor");
        helper.assertTrue(getContainedFluidAmount(partialCell) == 5500,
                "large cell did not retain the untransferred 5500 mB");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DualHatchPartMachineAction")
    public static void fullInventoryDropsEveryOverflowWaterBucket(GameTestHelper helper) {
        DualHatchPartMachine machine = createDualHatch(GTMachines.DUAL_EXPORT_HATCH[LuV]);
        machine.tank.setFluidInTank(0, new FluidStack(Fluids.WATER, 2 * FluidType.BUCKET_VOLUME));
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        fillInventory(player, new ItemStack(Items.STONE, 64));
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET, 3));
        int waterBucketsBefore = countNearbyDroppedItems(player, Items.WATER_BUCKET.getDefaultInstance());
        int emptyBucketsBefore = countNearbyDroppedItems(player, Items.BUCKET.getDefaultInstance());

        machine.clickFluidSlot(player, 0, true);

        int droppedWaterBuckets = countNearbyDroppedItems(player, Items.WATER_BUCKET.getDefaultInstance()) -
                waterBucketsBefore;
        int droppedEmptyBuckets = countNearbyDroppedItems(player, Items.BUCKET.getDefaultInstance()) -
                emptyBucketsBefore;
        int inventoryWaterBuckets = player.getInventory().countItem(Items.WATER_BUCKET);
        int cursorWaterBuckets = player.containerMenu.getCarried().is(Items.WATER_BUCKET) ?
                player.containerMenu.getCarried().getCount() : 0;
        int cursorEmptyBuckets = player.containerMenu.getCarried().is(Items.BUCKET) ?
                player.containerMenu.getCarried().getCount() : 0;
        helper.assertTrue(machine.tank.getFluidInTank(0).isEmpty(),
                "full-inventory shift fill did not transfer both available buckets");
        helper.assertTrue(droppedWaterBuckets == 2,
                "full inventory did not drop the complete two-bucket result stack");
        helper.assertTrue(inventoryWaterBuckets + cursorWaterBuckets + droppedWaterBuckets == 2,
                "full-inventory shift fill did not conserve filled buckets");
        helper.assertTrue(cursorEmptyBuckets == 1 && droppedEmptyBuckets == 0,
                "full-inventory shift fill did not preserve exactly one unfilled cursor bucket");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DualHatchPartMachineAction")
    public static void outputHatchActionDoesNotDrainCarriedContainerIntoTank(GameTestHelper helper) {
        DualHatchPartMachine machine = createDualHatch(GTMachines.DUAL_EXPORT_HATCH[LuV]);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));

        boolean result = dispatch(player, machine, payload(new JsonPrimitive(0), new JsonPrimitive(false)));

        helper.assertTrue(result, "valid output dual hatch fluid-slot action was rejected");
        helper.assertTrue(machine.tank.getFluidInTank(0).isEmpty(),
                "output dual hatch accepted fluid from the carried container");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "output dual hatch consumed or replaced the carried fluid container");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DualHatchPartMachineAction")
    public static void inputHatchActionDrainsCarriedContainerIntoTank(GameTestHelper helper) {
        DualHatchPartMachine machine = createDualHatch(GTMachines.DUAL_IMPORT_HATCH[LuV]);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));

        boolean result = dispatch(player, machine, payload(new JsonPrimitive(0), new JsonPrimitive(false)));

        helper.assertTrue(result, "valid input dual hatch fluid-slot action was rejected");
        helper.assertTrue(machine.tank.getFluidInTank(0).is(Fluids.WATER),
                "input dual hatch did not transfer the carried fluid into its tank");
        helper.assertTrue(machine.tank.getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME,
                "input dual hatch did not transfer exactly one bucket");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.BUCKET),
                "input dual hatch did not replace the carried fluid container");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DualHatchPartMachineAction")
    public static void dispatcherRejectsMissingAndWrongTypedPayload(GameTestHelper helper) {
        DualHatchPartMachine machine = createDualHatch(GTMachines.DUAL_IMPORT_HATCH[LuV]);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());

        boolean missingShift = dispatch(player, machine, payloadWithOnlyTank(new JsonPrimitive(0)));
        boolean stringTank = dispatch(player, machine,
                payload(new JsonPrimitive("0"), new JsonPrimitive(false)));
        boolean numericShift = dispatch(player, machine,
                payload(new JsonPrimitive(0), new JsonPrimitive(0)));

        helper.assertTrue(!missingShift, "dual hatch action without shift field was accepted");
        helper.assertTrue(!stringTank, "dual hatch action with string tank index was accepted");
        helper.assertTrue(!numericShift, "dual hatch action with numeric shift state was accepted");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DualHatchPartMachineAction")
    public static void dispatcherRejectsInvalidNumericTankIndices(GameTestHelper helper) {
        DualHatchPartMachine machine = createDualHatch(GTMachines.DUAL_IMPORT_HATCH[LuV]);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());

        boolean negative = dispatch(player, machine,
                payload(new JsonPrimitive(-1), new JsonPrimitive(false)));
        boolean fractional = dispatch(player, machine,
                payload(new JsonPrimitive(new BigDecimal("0.5")), new JsonPrimitive(false)));
        boolean longOverflow = dispatch(player, machine,
                payload(new JsonPrimitive(new BigInteger("18446744073709551616")), new JsonPrimitive(false)));
        boolean overflow = dispatch(player, machine,
                payload(new JsonPrimitive(machine.tank.getTanks()), new JsonPrimitive(false)));

        helper.assertTrue(!negative, "dual hatch action with negative tank index was accepted");
        helper.assertTrue(!fractional, "dual hatch action with fractional tank index was accepted");
        helper.assertTrue(!longOverflow, "dual hatch action with an overflowing tank index was accepted");
        helper.assertTrue(!overflow, "dual hatch action beyond the machine's actual tank count was accepted");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DualHatchPartMachineAction")
    public static void dispatcherRejectsWrongHolder(GameTestHelper helper) {
        createDualHatch(GTMachines.DUAL_IMPORT_HATCH[LuV]);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());

        boolean result = dispatch(player, new Object(),
                payload(new JsonPrimitive(0), new JsonPrimitive(false)));

        helper.assertTrue(!result, "dual hatch action accepted a non-dual-hatch holder");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DualHatchPartMachineAction")
    public static void dispatcherRejectsSpectator(GameTestHelper helper) {
        DualHatchPartMachine machine = createDualHatch(GTMachines.DUAL_IMPORT_HATCH[LuV]);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SPECTATOR);

        boolean result = dispatch(player, machine,
                payload(new JsonPrimitive(0), new JsonPrimitive(false)));
        player.setGameMode(GameType.SURVIVAL);

        helper.assertTrue(!result, "dual hatch fluid-slot action accepted a spectator");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DualHatchPartMachineAction")
    public static void ldlib2PagePreservesInputAndOutputSlotAndConfiguratorSemantics(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        DualHatchPartMachine input = createDualHatch(GTMachines.DUAL_IMPORT_HATCH[LuV]);
        DualHatchPartMachine output = createDualHatch(GTMachines.DUAL_EXPORT_HATCH[LuV]);
        MachineUIHolder inputHolder = new TestMachineUIHolder(input);
        MachineUIHolder outputHolder = new TestMachineUIHolder(output);

        LDLib2FancyMachineUIElement inputShell = createShell(player, input, inputHolder);
        LDLib2FancyMachineUIElement outputShell = createShell(player, output, outputHolder);

        assertPageSemantics(helper, inputShell, IngredientIO.INPUT, true, 3, "input dual hatch");
        assertPageSemantics(helper, outputShell, IngredientIO.OUTPUT, false, 1, "output dual hatch");
        assertDirectionalTabReachable(helper, inputShell, inputHolder, "input dual hatch");
        assertDirectionalTabReachable(helper, outputShell, outputHolder, "output dual hatch");

        input.setCircuitSlotEnabled(false);
        LDLib2FancyMachineUIElement inputWithoutCircuit = createShell(player, input);
        helper.assertTrue(inputWithoutCircuit.getConfiguratorPanel().getChildren().size() == 2,
                "input dual hatch with disabled circuit slot should keep only working and distinct configurators");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DualHatchPartMachineAction")
    public static void contextualPagesPreservePreviewGroupingAndHolderScope(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        DualHatchPartMachine input = createDualHatch(GTMachines.DUAL_IMPORT_HATCH[LuV]);
        DualHatchPartMachine output = createDualHatch(GTMachines.DUAL_EXPORT_HATCH[LuV]);

        boolean mismatchedHolderRejected = false;
        try {
            LDLib2FancyPartUIProvider inputProvider = input;
            inputProvider.createLDLib2FancyPage(player, new MutableDualHatchHolder(output));
        } catch (IllegalArgumentException expected) {
            mismatchedHolderRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(mismatchedHolderRejected,
                "input dual hatch contextual page accepted the output hatch holder");

        assertContextualPage(helper, player, input,
                "gtpm.multiblock.page_switcher.io.import", 1, 3, "input dual hatch");
        assertContextualPage(helper, player, output,
                "gtpm.multiblock.page_switcher.io.export", 2, 1, "output dual hatch");
        helper.succeed();
    }

    private static void assertContextualPage(GameTestHelper helper, ServerPlayer player,
                                             DualHatchPartMachine machine, String expectedGroupKey,
                                             int expectedGroupPositionWeight, int expectedConfigurators,
                                             String description) {
        LDLib2FancyPartUIProvider pageProvider = machine;
        MutableDualHatchHolder holder = new MutableDualHatchHolder(machine);
        LDLib2FancyUIProvider firstPage = pageProvider.createLDLib2FancyPage(player, holder);
        LDLib2FancyUIProvider secondPage = pageProvider.createLDLib2FancyPage(player, holder);
        helper.assertTrue(firstPage != secondPage,
                description + " reused its contextual page provider across openings");
        helper.assertTrue(firstPage.getLDLib2PageWidth() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                firstPage.getLDLib2PageHeight() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                description + " did not preserve the 100x100 contextual preview");
        PageGroupingData grouping = firstPage.getPageGroupingData();
        helper.assertTrue(grouping != null && expectedGroupKey.equals(grouping.groupKey()) &&
                grouping.groupPositionWeight() == expectedGroupPositionWeight,
                description + " lost its IO page grouping");

        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(firstPage,
                player.getInventory(), holder, firstPage.getLDLib2PageWidth(), firstPage.getLDLib2PageHeight());
        helper.assertTrue(shell.getHolder() == holder,
                description + " contextual shell lost its dedicated holder");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == expectedConfigurators,
                description + " contextual preview lost its ItemBus configurators");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                description + " contextual preview lost its directional page");
        UIElement preview = shell.getChildren().getFirst().getChildren().getFirst();
        helper.assertTrue(preview.getSizeWidth() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                preview.getSizeHeight() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT &&
                preview.getChildren().isEmpty(),
                description + " contextual page did not create the server-safe preview body");

        holder.setMachine(createDualHatch(machine.getDefinition()));
        boolean replacementRejected = false;
        try {
            new LDLib2FancyMachineUIElement(firstPage, player.getInventory(), holder,
                    firstPage.getLDLib2PageWidth(), firstPage.getLDLib2PageHeight());
        } catch (IllegalStateException expected) {
            replacementRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(replacementRejected,
                description + " contextual page accepted a same-definition replacement");
    }

    private static void assertPageSemantics(GameTestHelper helper, LDLib2FancyMachineUIElement shell,
                                            IngredientIO itemRole, boolean allowFluidDraining,
                                            int configuratorCount, String description) {
        helper.assertTrue(shell.getChildren().size() == 6,
                description + " should include the player inventory in the Fancy shell");
        UIElement pageContainer = shell.getChildren().getFirst();
        UIElement page = pageContainer.getChildren().getFirst();
        UIElement slotContainer = page.getChildren().getFirst();
        List<UIElement> slots = slotContainer.getChildren();
        long itemSlots = slots.stream().filter(GTItemSlotElement.class::isInstance).count();
        List<GTFluidSlotElement> fluidSlots = slots.stream()
                .filter(GTFluidSlotElement.class::isInstance)
                .map(GTFluidSlotElement.class::cast)
                .toList();

        helper.assertTrue(itemSlots == 4, description + " should expose four LuV item slots");
        helper.assertTrue(fluidSlots.size() == 2, description + " should expose two LuV fluid slots");
        helper.assertTrue(slots.stream()
                .filter(GTItemSlotElement.class::isInstance)
                .map(GTItemSlotElement.class::cast)
                .allMatch(slot -> slot.getIngredientIO() == itemRole),
                description + " item slots should preserve their XEI role");
        helper.assertTrue(fluidSlots.stream()
                .allMatch(slot -> slot.isAllowClickDrained() == allowFluidDraining),
                description + " fluid slots should preserve container draining permission");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == configuratorCount,
                description + " configurator count did not preserve ItemBus semantics");
    }

    private static void assertDirectionalTabReachable(GameTestHelper helper, LDLib2FancyMachineUIElement shell,
                                                      MachineUIHolder expectedHolder, String description) {
        LDLib2FancyTabsElement tabs = shell.getSideTabsElement();
        UIElement pageContainer = shell.getChildren().getFirst();
        UIElement mainPage = pageContainer.getChildren().getFirst();
        helper.assertTrue(shell.getHolder() == expectedHolder,
                description + " Fancy shell did not preserve its opening holder");
        helper.assertTrue(tabs.getChildren().size() == 2,
                description + " should expose one main tab and one directional side tab");

        clickElement(tabs.getChildren().get(1), GLFW.GLFW_MOUSE_BUTTON_LEFT, false);

        helper.assertTrue(pageContainer.getChildren().size() == 2,
                description + " directional click did not create exactly one cached page");
        UIElement directionalPage = pageContainer.getChildren().get(1);
        helper.assertTrue(!mainPage.isVisible() && !mainPage.isActive(),
                description + " main page remained active after directional navigation");
        helper.assertTrue(directionalPage.isVisible() && directionalPage.isActive(),
                description + " directional page did not become active after a real tab click");

        clickElement(tabs.getChildren().getFirst(), GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        helper.assertTrue(mainPage.isVisible() && mainPage.isActive(),
                description + " main page was not reachable after directional navigation");
        helper.assertTrue(!directionalPage.isVisible() && !directionalPage.isActive(),
                description + " directional page remained active after returning to main");

        clickElement(tabs.getChildren().get(1), GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        helper.assertTrue(pageContainer.getChildren().size() == 2,
                description + " recreated its stable directional page on repeated navigation");
    }

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player, DualHatchPartMachine machine) {
        return createShell(player, machine, new TestMachineUIHolder(machine));
    }

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player, DualHatchPartMachine machine,
                                                           MachineUIHolder holder) {
        UIElement root = machine.createLDLib2UI(player, holder).getRootElement();
        if (!(root instanceof LDLib2FancyMachineUIElement shell)) {
            throw new IllegalStateException("Dual hatch LDLib2 UI did not create a Fancy shell.");
        }
        return shell;
    }

    private static DualHatchPartMachine createDualHatch(MachineDefinition definition) {
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (!(machine instanceof DualHatchPartMachine dualHatch)) {
            throw new IllegalStateException("Dual hatch definition did not create a dual hatch machine.");
        }
        return dualHatch;
    }

    private static boolean dispatch(ServerPlayer player, Object holder, DataComponentMap payload) {
        SyncActionData action = new SyncActionData(CLICK_DUAL_HATCH_FLUID_SLOT_ACTION, 1, payload);
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static DataComponentMap payload(JsonElement tankIndex, JsonElement shiftDown) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(TANK_FIELD, tankIndex)
                        .put(SHIFT_FIELD, shiftDown)
                        .build())
                .build();
    }

    private static DataComponentMap payloadWithOnlyTank(JsonElement tankIndex) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(TANK_FIELD, tankIndex)
                        .build())
                .build();
    }

    private static void assertCapturedFluidSlotAction(GameTestHelper helper, List<CapturedAction> actions, int index,
                                                      MachineUIHolder expectedHolder, int expectedTank,
                                                      boolean expectedShift) {
        helper.assertTrue(actions.size() > index, "expected fluid-slot action " + index + " was not sent");
        CapturedAction captured = actions.get(index);
        helper.assertTrue(captured.holder() == expectedHolder, "fluid-slot action used the wrong page holder");
        helper.assertTrue(captured.action().actionId().equals(CLICK_DUAL_HATCH_FLUID_SLOT_ACTION),
                "fluid-slot action used the wrong action id");
        SyncFieldData fields = captured.action().payload().get(GTDataComponents.SYNC_FIELD_DATA.get());
        helper.assertTrue(fields != null, "fluid-slot action omitted field payload");
        helper.assertTrue(fields.get(TANK_FIELD).getAsInt() == expectedTank,
                "fluid-slot action encoded the wrong tank index");
        helper.assertTrue(fields.get(SHIFT_FIELD).getAsBoolean() == expectedShift,
                "fluid-slot action encoded the wrong shift state");
    }

    private static UIEvent clickElement(UIElement element, int button, boolean shiftDown) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = element;
        event.button = button;
        event.customData = shiftDown;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
        return event;
    }

    private static ItemStack fillFluidContainer(GameTestHelper helper, ItemStack container, FluidStack fluid) {
        var fluidHandler = FluidUtil.getFluidHandler(container)
                .orElseThrow(() -> new IllegalStateException("GT fluid cell capability was not registered."));
        int filled = fluidHandler.fill(fluid.copy(), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(filled == fluid.getAmount(), "GT fluid cell did not accept the requested test fluid");
        return fluidHandler.getContainer();
    }

    private static int countInventoryFluidContainers(ServerPlayer player, ItemStack container, int fluidAmount) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(container.getItem()) && getContainedFluidAmount(stack) == fluidAmount) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int countInventoryFluid(ServerPlayer player, ItemStack container) {
        int amount = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(container.getItem())) {
                amount += getContainedFluidAmount(stack) * stack.getCount();
            }
        }
        return amount;
    }

    private static int getContainedFluidAmount(ItemStack stack) {
        return FluidUtil.getFluidContained(stack)
                .filter(fluid -> fluid.is(Fluids.WATER))
                .map(FluidStack::getAmount)
                .orElse(0);
    }

    private static void fillInventory(ServerPlayer player, ItemStack filler) {
        player.getInventory().clearContent();
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            player.getInventory().setItem(slot, filler.copy());
        }
    }

    private static int countNearbyDroppedItems(ServerPlayer player, ItemStack item) {
        return player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4.0)).stream()
                .map(ItemEntity::getItem)
                .filter(stack -> stack.is(item.getItem()))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    private record CapturedAction(MachineUIHolder holder, SyncActionData action) {}

    private record TestMachineUIHolder(DualHatchPartMachine machine) implements MachineUIHolder {

        @Override
        public BlockPos getPos() {
            return BlockPos.ZERO;
        }

        @Override
        public ResourceLocation getMachineDefinitionId() {
            return machine.getDefinition().getId();
        }

        @Override
        public DualHatchPartMachine getMachine() {
            return machine;
        }
    }

    private static final class MutableDualHatchHolder implements MachineUIHolder {

        private DualHatchPartMachine machine;

        private MutableDualHatchHolder(DualHatchPartMachine machine) {
            this.machine = machine;
        }

        private void setMachine(DualHatchPartMachine machine) {
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
        public DualHatchPartMachine getMachine() {
            return machine;
        }
    }
}

package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class PumpMachineActionTest {

    private static final String BATCH = "PumpMachineAction";
    private static final ResourceLocation ACTION_ID = GTCEu.id("click_pump_machine_fluid_slot");
    private static final ResourceLocation SHIFT_FIELD = SyncFieldData.key("shift");
    private static final ResourceLocation TANK_FIELD = SyncFieldData.key("tank");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("other");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creatorEncodesBothShiftStatesWithoutTankIndex(GameTestHelper helper) {
        assertCreatedAction(helper, false, 0);
        assertCreatedAction(helper, true, 1);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherExecutesBothShiftStatesExactlyOnce(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper, ItemStack.EMPTY);
        TestPumpFluidSlotActionTarget target = new TestPumpFluidSlotActionTarget();

        helper.assertTrue(dispatch(player, target, PumpMachineActions.createClickPumpFluidSlotAction(false)),
                "valid unshifted pump action was rejected");
        helper.assertTrue(target.invocations == 1 && !target.lastShiftDown && target.lastPlayer == player,
                "unshifted pump action invoked the wrong target state");

        helper.assertTrue(dispatch(player, target, PumpMachineActions.createClickPumpFluidSlotAction(true)),
                "valid shifted pump action was rejected");
        helper.assertTrue(target.invocations == 2 && target.lastShiftDown && target.lastPlayer == player,
                "shifted pump action did not execute exactly once");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsMalformedPayloadsWithoutInvokingTarget(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper, ItemStack.EMPTY);
        TestPumpFluidSlotActionTarget target = new TestPumpFluidSlotActionTarget();

        helper.assertTrue(!dispatch(player, target, rawAction(DataComponentMap.EMPTY)),
                "pump action without field data was accepted");
        helper.assertTrue(!dispatch(player, target, rawAction(payload(OTHER_FIELD, new JsonPrimitive(true)))),
                "pump action without shift field was accepted");
        helper.assertTrue(!dispatch(player, target, rawAction(payload(SHIFT_FIELD, new JsonPrimitive(1)))),
                "pump action with numeric shift field was accepted");
        helper.assertTrue(!dispatch(player, target, rawAction(payload(SHIFT_FIELD, new JsonPrimitive("true")))),
                "pump action with string shift field was accepted");
        helper.assertTrue(target.invocations == 0, "rejected pump payload invoked the target");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsWrongHolderAndSpectatorWithoutInvokingTarget(GameTestHelper helper) {
        ServerPlayer player = preparePlayer(helper, ItemStack.EMPTY);
        SyncActionData action = PumpMachineActions.createClickPumpFluidSlotAction(true);
        TestPumpFluidSlotActionTarget target = new TestPumpFluidSlotActionTarget();

        helper.assertTrue(!dispatch(player, new Object(), action),
                "pump action accepted an unrelated holder");

        player.setGameMode(GameType.SPECTATOR);
        boolean spectatorResult;
        try {
            spectatorResult = dispatch(player, target, action);
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!spectatorResult, "pump action accepted a spectator");
        helper.assertTrue(target.invocations == 0,
                "rejected holder or spectator pump action invoked the target");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void rejectedActionsLeavePumpCacheAndCursorUnchanged(GameTestHelper helper) {
        PumpMachine machine = createPump();
        machine.cache.setFluidInTank(0, new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.BUCKET));

        boolean malformedResult = dispatch(player, machine,
                rawAction(payload(SHIFT_FIELD, new JsonPrimitive(0))));

        player.setGameMode(GameType.SPECTATOR);
        boolean spectatorResult;
        try {
            spectatorResult = dispatch(player, machine,
                    PumpMachineActions.createClickPumpFluidSlotAction(false));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!malformedResult, "malformed pump action was accepted");
        helper.assertTrue(!spectatorResult, "spectator pump action was accepted");
        helper.assertTrue(machine.cache.getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME,
                "rejected pump action changed the fixed cache tank");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.BUCKET) &&
                player.containerMenu.getCarried().getCount() == 1,
                "rejected pump action changed the cursor stack");
        helper.assertTrue(player.getInventory().isEmpty(),
                "rejected pump action inserted an inventory item");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherIgnoresUnknownIndexAndUsesFixedCacheTank(GameTestHelper helper) {
        PumpMachine machine = createPump();
        machine.cache.setFluidInTank(0, new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.BUCKET));
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(SHIFT_FIELD, new JsonPrimitive(false))
                        .put(TANK_FIELD, new JsonPrimitive(-1))
                        .put(OTHER_FIELD, new JsonPrimitive("ignored"))
                        .build())
                .build();

        boolean accepted = dispatch(player, machine, rawAction(payload));

        helper.assertTrue(accepted, "pump action rejected fields outside its fixed-tank protocol");
        helper.assertTrue(machine.cache.getFluidInTank(0).isEmpty(),
                "pump action with unknown fields did not use the fixed cache tank");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "pump action with unknown fields did not fill the cursor container");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void unshiftedTargetLogicTransfersOnlyOneCarriedContainer(GameTestHelper helper) {
        PumpMachine machine = createPump();
        int initialFluid = 3 * FluidType.BUCKET_VOLUME;
        machine.cache.setFluidInTank(0, new FluidStack(Fluids.WATER, initialFluid));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.BUCKET, 3));

        machine.clickPumpFluidSlot(player, false);

        int emptyContainers = countContainers(player, Items.BUCKET);
        int filledContainers = countContainers(player, Items.WATER_BUCKET);
        int remainingFluid = machine.cache.getFluidInTank(0).getAmount();
        helper.assertTrue(emptyContainers == 2,
                "unshifted pump target consumed more than one empty container");
        helper.assertTrue(filledContainers == 1,
                "unshifted pump target did not produce exactly one filled container");
        helper.assertTrue(emptyContainers + filledContainers == 3,
                "unshifted pump target did not conserve container count");
        helper.assertTrue(remainingFluid + filledContainers * FluidType.BUCKET_VOLUME == initialFluid,
                "unshifted pump target did not conserve fluid");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void shiftedTargetLogicTransfersEveryCarriedContainer(GameTestHelper helper) {
        PumpMachine machine = createPump();
        machine.cache.setFluidInTank(0, new FluidStack(Fluids.WATER, 3 * FluidType.BUCKET_VOLUME));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.BUCKET, 3));

        machine.clickPumpFluidSlot(player, true);

        helper.assertTrue(machine.cache.getFluidInTank(0).isEmpty(),
                "shifted pump target did not drain one bucket per carried container");
        helper.assertTrue(countContainers(player, Items.WATER_BUCKET) == 3,
                "shifted pump target did not return every filled container");
        helper.assertTrue(countContainers(player, Items.BUCKET) == 0,
                "shifted pump target left empty containers after filling all of them");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void targetLogicDrainsFilledCursorIntoEmptyCache(GameTestHelper helper) {
        PumpMachine machine = createPump();
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.WATER_BUCKET));

        machine.clickPumpFluidSlot(player, false);

        FluidStack cached = machine.cache.getFluidInTank(0);
        helper.assertTrue(cached.is(Fluids.WATER) && cached.getAmount() == FluidType.BUCKET_VOLUME,
                "pump target did not drain the filled cursor into its cache");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.BUCKET),
                "pump target did not return the emptied cursor container");
        helper.assertTrue(player.getInventory().isEmpty(),
                "single-container drain inserted an unexpected inventory item");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void targetLogicLeavesFullCacheAndUnsupportedCursorUnchanged(GameTestHelper helper) {
        PumpMachine machine = createPump();
        int capacity = machine.cache.getTankCapacity(0);
        machine.cache.setFluidInTank(0, new FluidStack(Fluids.WATER, capacity));
        ServerPlayer player = preparePlayer(helper, new ItemStack(Items.WATER_BUCKET));

        machine.clickPumpFluidSlot(player, false);

        helper.assertTrue(machine.cache.getFluidInTank(0).getAmount() == capacity,
                "pump target changed a full cache during a rejected drain");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "pump target changed a filled cursor that could not be drained");

        player.containerMenu.setCarried(new ItemStack(Items.STICK));
        machine.clickPumpFluidSlot(player, true);

        helper.assertTrue(machine.cache.getFluidInTank(0).getAmount() == capacity,
                "pump target changed its cache for a cursor without a fluid handler");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.STICK),
                "pump target changed a cursor without a fluid handler");
        helper.assertTrue(player.getInventory().isEmpty(),
                "rejected pump cursor interactions inserted an inventory item");
        helper.succeed();
    }

    private static void assertCreatedAction(GameTestHelper helper, boolean shiftDown, int expectedSequence) {
        SyncActionData action = PumpMachineActions.createClickPumpFluidSlotAction(shiftDown);
        SyncFieldData fields = requireFields(action.payload());
        JsonElement shift = fields.get(SHIFT_FIELD);

        helper.assertTrue(action.actionId().equals(ACTION_ID), "pump creator used the wrong action id");
        helper.assertTrue(action.sequence() == expectedSequence, "pump creator used the wrong sequence");
        helper.assertTrue(fields.fields().size() == 1, "pump creator encoded fields outside its protocol");
        helper.assertTrue(shift instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == shiftDown,
                "pump creator encoded the wrong shift state");
    }

    private static PumpMachine createPump() {
        var definition = GTMachines.PUMP[GTValues.LV];
        return new PumpMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()), GTValues.LV);
    }

    private static ServerPlayer preparePlayer(GameTestHelper helper, ItemStack carried) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.containerMenu.setCarried(carried);
        return player;
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        PumpMachineActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData rawAction(DataComponentMap payload) {
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static SyncFieldData requireFields(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Pump action creator omitted field data.");
        }
        return fields;
    }

    private static int countContainers(ServerPlayer player, Item item) {
        int count = player.getInventory().countItem(item);
        ItemStack carried = player.containerMenu.getCarried();
        if (carried.is(item)) {
            count += carried.getCount();
        }
        return count;
    }

    private static final class TestPumpFluidSlotActionTarget implements PumpFluidSlotActionTarget {

        private int invocations;
        private boolean lastShiftDown;
        private ServerPlayer lastPlayer;

        @Override
        public void clickPumpFluidSlot(@NotNull ServerPlayer player, boolean shiftDown) {
            invocations++;
            lastPlayer = player;
            lastShiftDown = shiftDown;
        }
    }
}

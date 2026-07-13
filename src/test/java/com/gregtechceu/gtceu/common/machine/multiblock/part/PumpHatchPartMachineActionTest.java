package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
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

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class PumpHatchPartMachineActionTest {

    private static final String BATCH = "PumpHatchPartMachineAction";
    private static final ResourceLocation ACTION_ID = GTCEu.id("click_pump_hatch_fluid_slot");
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
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        TestPumpHatchTargetImpl target = new TestPumpHatchTargetImpl();

        helper.assertTrue(dispatch(player, target,
                PumpHatchPartMachineActions.createClickPumpHatchFluidSlotAction(false)),
                "valid unshifted pump hatch action was rejected");
        helper.assertTrue(target.invocations == 1 && !target.lastShiftDown && target.lastPlayer == player,
                "unshifted pump hatch action invoked the wrong target state");

        helper.assertTrue(dispatch(player, target,
                PumpHatchPartMachineActions.createClickPumpHatchFluidSlotAction(true)),
                "valid shifted pump hatch action was rejected");
        helper.assertTrue(target.invocations == 2 && target.lastShiftDown && target.lastPlayer == player,
                "shifted pump hatch action did not execute exactly once");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsMalformedPayloadsWithoutInvokingTarget(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        TestPumpHatchTargetImpl target = new TestPumpHatchTargetImpl();

        helper.assertTrue(!dispatch(player, target, rawAction(DataComponentMap.EMPTY)),
                "pump hatch action without field data was accepted");
        helper.assertTrue(!dispatch(player, target, rawAction(payload(OTHER_FIELD, new JsonPrimitive(true)))),
                "pump hatch action without shift field was accepted");
        helper.assertTrue(!dispatch(player, target, rawAction(payload(SHIFT_FIELD, new JsonPrimitive(1)))),
                "pump hatch action with numeric shift field was accepted");
        helper.assertTrue(!dispatch(player, target, rawAction(payload(SHIFT_FIELD, new JsonPrimitive("true")))),
                "pump hatch action with string shift field was accepted");
        helper.assertTrue(target.invocations == 0, "rejected pump hatch payload invoked the target");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsWrongHolderAndSpectatorWithoutInvokingTarget(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SyncActionData action = PumpHatchPartMachineActions.createClickPumpHatchFluidSlotAction(true);
        TestPumpHatchTargetImpl target = new TestPumpHatchTargetImpl();

        helper.assertTrue(!dispatch(player, new Object(), action),
                "pump hatch action accepted an unrelated holder");

        player.setGameMode(GameType.SPECTATOR);
        boolean spectatorResult;
        try {
            spectatorResult = dispatch(player, target, action);
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!spectatorResult, "pump hatch action accepted a spectator");
        helper.assertTrue(target.invocations == 0,
                "rejected holder or spectator pump hatch action invoked the target");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void rejectedActionsLeavePumpTankAndCursorUnchanged(GameTestHelper helper) {
        PumpHatchPartMachine machine = createPumpHatch();
        machine.tank.setFluidInTank(0, new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME));
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));

        boolean malformedResult = dispatch(player, machine,
                rawAction(payload(SHIFT_FIELD, new JsonPrimitive(0))));

        player.setGameMode(GameType.SPECTATOR);
        boolean spectatorResult;
        try {
            spectatorResult = dispatch(player, machine,
                    PumpHatchPartMachineActions.createClickPumpHatchFluidSlotAction(false));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!malformedResult, "malformed pump hatch action was accepted");
        helper.assertTrue(!spectatorResult, "spectator pump hatch action was accepted");
        helper.assertTrue(machine.tank.getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME,
                "rejected pump hatch action changed the fixed tank");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.BUCKET) &&
                player.containerMenu.getCarried().getCount() == 1,
                "rejected pump hatch action changed the cursor stack");
        helper.assertTrue(player.getInventory().isEmpty(),
                "rejected pump hatch action inserted an inventory item");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherIgnoresUnknownTankIndexAndOtherFields(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        TestPumpHatchTargetImpl target = new TestPumpHatchTargetImpl();
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(SHIFT_FIELD, new JsonPrimitive(false))
                        .put(TANK_FIELD, new JsonPrimitive(-1))
                        .put(OTHER_FIELD, new JsonPrimitive("ignored"))
                        .build())
                .build();

        helper.assertTrue(dispatch(player, target, rawAction(payload)),
                "pump hatch action rejected fields outside its fixed-tank protocol");
        helper.assertTrue(target.invocations == 1 && !target.lastShiftDown,
                "unknown fields changed the fixed-tank pump hatch action state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherTransfersWaterFromFixedPumpTank(GameTestHelper helper) {
        PumpHatchPartMachine machine = createPumpHatch();
        machine.tank.setFluidInTank(0, new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME));
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET));

        boolean accepted = dispatch(player, machine,
                PumpHatchPartMachineActions.createClickPumpHatchFluidSlotAction(false));

        helper.assertTrue(accepted, "valid pump hatch action was rejected");
        helper.assertTrue(machine.tank.getFluidInTank(0).isEmpty(),
                "pump hatch action did not drain its fixed tank");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "pump hatch action did not fill the carried bucket");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void unshiftedTargetLogicTransfersOnlyOneCarriedContainer(GameTestHelper helper) {
        PumpHatchPartMachine machine = createPumpHatch();
        int initialFluid = 3 * FluidType.BUCKET_VOLUME;
        machine.tank.setFluidInTank(0, new FluidStack(Fluids.WATER, initialFluid));
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET, 3));

        machine.clickPumpHatchFluidSlot(player, false);

        int emptyContainers = countContainers(player, Items.BUCKET);
        int filledContainers = countContainers(player, Items.WATER_BUCKET);
        int remainingFluid = machine.tank.getFluidInTank(0).getAmount();
        helper.assertTrue(emptyContainers == 2,
                "unshifted pump hatch target consumed more than one empty container");
        helper.assertTrue(filledContainers == 1,
                "unshifted pump hatch target did not produce exactly one filled container");
        helper.assertTrue(emptyContainers + filledContainers == 3,
                "unshifted pump hatch target did not conserve container count");
        helper.assertTrue(remainingFluid + filledContainers * FluidType.BUCKET_VOLUME == initialFluid,
                "unshifted pump hatch target did not conserve fluid");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void shiftedTargetLogicTransfersEveryCarriedContainer(GameTestHelper helper) {
        PumpHatchPartMachine machine = createPumpHatch();
        machine.tank.setFluidInTank(0, new FluidStack(Fluids.WATER, 3 * FluidType.BUCKET_VOLUME));
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        player.containerMenu.setCarried(new ItemStack(Items.BUCKET, 3));

        machine.clickPumpHatchFluidSlot(player, true);

        helper.assertTrue(machine.tank.getFluidInTank(0).isEmpty(),
                "shifted pump hatch target did not drain one bucket per carried container");
        helper.assertTrue(countContainers(player, Items.WATER_BUCKET) == 3,
                "shifted pump hatch target did not return every filled container");
        helper.assertTrue(countContainers(player, Items.BUCKET) == 0,
                "shifted pump hatch target left empty containers after filling all of them");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void targetLogicLeavesRejectedCursorInteractionsUnchanged(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();

        PumpHatchPartMachine emptyMachine = createPumpHatch();
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        emptyMachine.clickPumpHatchFluidSlot(player, false);
        helper.assertTrue(emptyMachine.tank.getFluidInTank(0).isEmpty(),
                "output-only pump hatch accepted fluid from the carried container");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.WATER_BUCKET),
                "output-only pump hatch changed a rejected filled cursor stack");

        PumpHatchPartMachine filledMachine = createPumpHatch();
        filledMachine.tank.setFluidInTank(0, new FluidStack(Fluids.WATER, FluidType.BUCKET_VOLUME));
        player.containerMenu.setCarried(new ItemStack(Items.STICK));
        filledMachine.clickPumpHatchFluidSlot(player, true);
        helper.assertTrue(filledMachine.tank.getFluidInTank(0).getAmount() == FluidType.BUCKET_VOLUME,
                "pump hatch changed its tank for a cursor without a fluid handler");
        helper.assertTrue(player.containerMenu.getCarried().is(Items.STICK),
                "pump hatch changed a cursor without a fluid handler");
        helper.assertTrue(player.getInventory().isEmpty(),
                "rejected pump hatch cursor interaction inserted an inventory item");
        helper.succeed();
    }

    private static void assertCreatedAction(GameTestHelper helper, boolean shiftDown, int expectedSequence) {
        SyncActionData action = PumpHatchPartMachineActions.createClickPumpHatchFluidSlotAction(shiftDown);
        SyncFieldData fields = requireFields(action.payload());
        JsonElement shift = fields.get(SHIFT_FIELD);

        helper.assertTrue(action.actionId().equals(ACTION_ID), "pump hatch creator used the wrong action id");
        helper.assertTrue(action.sequence() == expectedSequence, "pump hatch creator used the wrong sequence");
        helper.assertTrue(fields.fields().size() == 1, "pump hatch creator encoded fields outside its protocol");
        helper.assertTrue(shift instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == shiftDown,
                "pump hatch creator encoded the wrong shift state");
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        PumpHatchPartMachineActions.initialize();
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
            throw new IllegalStateException("Pump hatch action creator omitted field data.");
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

    private static PumpHatchPartMachine createPumpHatch() {
        MetaMachine machine = GTMachines.PUMP_HATCH.getBlockEntityType()
                .create(BlockPos.ZERO, GTMachines.PUMP_HATCH.defaultBlockState());
        if (!(machine instanceof PumpHatchPartMachine pumpHatch)) {
            throw new IllegalStateException("Pump hatch definition did not create a pump hatch machine.");
        }
        return pumpHatch;
    }

    private static final class TestPumpHatchTargetImpl implements PumpHatchFluidSlotActionTarget {

        private int invocations;
        private boolean lastShiftDown;
        private ServerPlayer lastPlayer;

        @Override
        public void clickPumpHatchFluidSlot(ServerPlayer player, boolean shiftDown) {
            invocations++;
            lastPlayer = player;
            lastShiftDown = shiftDown;
        }
    }
}

package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.cover.data.BucketMode;
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode;
import com.gregtechceu.gtceu.common.data.GTCovers;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.storage.BufferMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class PumpCoverConfigActionTest {

    private static final ResourceLocation ACTION_ID = GTCEu.id("set_pump_cover_config");
    private static final ResourceLocation TRANSFER_RATE_FIELD = SyncFieldData.key("transferRate");
    private static final ResourceLocation IO_FIELD = SyncFieldData.key("io");
    private static final ResourceLocation BUCKET_MODE_FIELD = SyncFieldData.key("bucketMode");
    private static final ResourceLocation MANUAL_IO_FIELD = SyncFieldData.key("manualIO");
    private static final ResourceLocation UNKNOWN_FIELD = SyncFieldData.key("futureField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "PumpCoverConfigAction")
    public static void factoryEncodesCompletePumpConfig(GameTestHelper helper) {
        SyncActionData action = PumpCoverConfigActions.createSetConfigAction(
                32, IO.IN, BucketMode.MILLI_BUCKET, ManualIOMode.FILTERED);
        SyncFieldData fields = requireFields(action.payload(), "pump config factory");

        helper.assertTrue(action.actionId().equals(ACTION_ID), "factory encoded the wrong action id");
        helper.assertTrue(action.sequence() == 0, "factory changed the pump action sequence");
        helper.assertTrue(fields.fields().size() == 4, "factory encoded fields outside the pump protocol");
        assertIntField(helper, fields, TRANSFER_RATE_FIELD, 32, "factory transfer rate");
        assertIntField(helper, fields, IO_FIELD, IO.IN.ordinal(), "factory IO mode");
        assertIntField(helper, fields, BUCKET_MODE_FIELD, BucketMode.MILLI_BUCKET.ordinal(),
                "factory bucket mode");
        assertIntField(helper, fields, MANUAL_IO_FIELD, ManualIOMode.FILTERED.ordinal(),
                "factory manual IO mode");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "PumpCoverConfigAction")
    public static void dispatcherExecutesSettersInProtocolOrder(GameTestHelper helper) {
        TrackingPumpCover cover = createTrackingPumpCover(GTValues.LV);
        SyncActionData action = PumpCoverConfigActions.createSetConfigAction(
                32, IO.IN, BucketMode.MILLI_BUCKET, ManualIOMode.UNFILTERED);

        helper.assertTrue(dispatch(helper, cover, action), "valid pump config action was rejected");
        helper.assertTrue(cover.getSetterOrder().equals("rate>io>bucket>manual"),
                "handler changed the required transfer-rate, IO, bucket, manual-IO setter order");
        assertState(helper, cover, 32, IO.IN, BucketMode.MILLI_BUCKET, ManualIOMode.UNFILTERED,
                "valid pump action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "PumpCoverConfigAction")
    public static void dispatcherAcceptsPumpCoverSubclass(GameTestHelper helper) {
        FluidRegulatorCover cover = createFluidRegulatorCover();
        SyncActionData action = PumpCoverConfigActions.createSetConfigAction(
                0, IO.IN, BucketMode.MILLI_BUCKET, ManualIOMode.FILTERED);

        helper.assertTrue(dispatch(helper, cover, action), "pump action rejected a fluid regulator subclass");
        assertState(helper, cover, 0, IO.IN, BucketMode.MILLI_BUCKET, ManualIOMode.FILTERED,
                "fluid regulator pump action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "PumpCoverConfigAction")
    public static void dispatcherEnforcesTierBucketLimitAndTransferRateSetter(GameTestHelper helper) {
        TrackingPumpCover lowVoltageCover = createTrackingPumpCover(GTValues.LV);
        TrackingPumpCover highVoltageCover = createTrackingPumpCover(GTValues.HV);
        SyncActionData bucketAction = PumpCoverConfigActions.createSetConfigAction(
                Integer.MAX_VALUE, IO.IN, BucketMode.BUCKET, ManualIOMode.FILTERED);

        helper.assertTrue(!dispatch(helper, lowVoltageCover, bucketAction),
                "LV pump accepted a bucket multiplier above its transfer-rate limit");
        assertState(helper, lowVoltageCover, PumpCover.PUMP_SCALING.applyAsInt(GTValues.LV), IO.OUT,
                BucketMode.MILLI_BUCKET, ManualIOMode.DISABLED, "rejected LV bucket action");
        helper.assertTrue(lowVoltageCover.getSetterOrder().isEmpty(),
                "rejected LV bucket action invoked pump setters");

        helper.assertTrue(dispatch(helper, highVoltageCover, bucketAction),
                "HV pump rejected a bucket multiplier within its transfer-rate limit");
        assertState(helper, highVoltageCover, PumpCover.PUMP_SCALING.applyAsInt(GTValues.HV), IO.IN,
                BucketMode.BUCKET, ManualIOMode.FILTERED, "accepted HV bucket action");
        helper.assertTrue(highVoltageCover.getSetterOrder().equals("rate>io>bucket>manual"),
                "accepted HV bucket action bypassed the ordered setters");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "PumpCoverConfigAction")
    public static void factoryRejectsInvalidClientState(GameTestHelper helper) {
        assertFactoryRejected(() -> PumpCoverConfigActions.createSetConfigAction(
                -1, IO.IN, BucketMode.MILLI_BUCKET, ManualIOMode.DISABLED),
                "factory accepted a negative transfer rate");
        assertFactoryRejected(() -> PumpCoverConfigActions.createSetConfigAction(
                1, IO.BOTH, BucketMode.MILLI_BUCKET, ManualIOMode.DISABLED),
                "factory accepted the bidirectional IO mode");
        assertFactoryRejected(() -> PumpCoverConfigActions.createSetConfigAction(
                1, IO.NONE, BucketMode.MILLI_BUCKET, ManualIOMode.DISABLED),
                "factory accepted the disabled IO direction");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "PumpCoverConfigAction")
    public static void dispatcherRejectsMissingAndWrongTypeFields(GameTestHelper helper) {
        TrackingPumpCover cover = createTrackingPumpCover(GTValues.HV);

        assertRejected(helper, cover,
                action(payload(null, integer(1), integer(0), integer(0))), "missing transfer rate");
        assertRejected(helper, cover,
                action(payload(integer(1), null, integer(0), integer(0))), "missing IO mode");
        assertRejected(helper, cover,
                action(payload(integer(1), integer(IO.IN.ordinal()), null, integer(0))), "missing bucket mode");
        assertRejected(helper, cover,
                action(payload(integer(1), integer(IO.IN.ordinal()), integer(0), null)),
                "missing manual IO mode");
        assertRejected(helper, cover, action(DataComponentMap.EMPTY), "missing field data");

        assertRejected(helper, cover,
                action(payload(new JsonPrimitive("1"), integer(1), integer(0), integer(0))),
                "string transfer rate");
        assertRejected(helper, cover,
                action(payload(integer(1), new JsonPrimitive("1"), integer(0), integer(0))),
                "string IO mode");
        assertRejected(helper, cover,
                action(payload(integer(1), integer(1), new JsonPrimitive("0"), integer(0))),
                "string bucket mode");
        assertRejected(helper, cover,
                action(payload(integer(1), integer(1), integer(0), new JsonPrimitive("0"))),
                "string manual IO mode");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "PumpCoverConfigAction")
    public static void dispatcherRejectsNonIntegerAndOutOfRangeFields(GameTestHelper helper) {
        TrackingPumpCover cover = createTrackingPumpCover(GTValues.HV);

        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(1.5D), integer(1), integer(0), integer(0))),
                "fractional transfer rate");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(4_294_967_296L), integer(1), integer(0), integer(0))),
                "overflowing transfer rate");
        assertRejected(helper, cover,
                action(payload(integer(-1), integer(1), integer(0), integer(0))), "negative transfer rate");

        assertRejected(helper, cover,
                action(payload(integer(1), new JsonPrimitive(1.5D), integer(0), integer(0))),
                "fractional IO mode");
        assertRejected(helper, cover,
                action(payload(integer(1), new JsonPrimitive(4_294_967_296L), integer(0), integer(0))),
                "overflowing IO mode");
        assertRejected(helper, cover,
                action(payload(integer(1), integer(-1), integer(0), integer(0))), "negative IO mode");
        assertRejected(helper, cover,
                action(payload(integer(1), integer(IO.BOTH.ordinal()), integer(0), integer(0))),
                "bidirectional IO mode");
        assertRejected(helper, cover,
                action(payload(integer(1), integer(IO.NONE.ordinal()), integer(0), integer(0))),
                "disabled IO direction");
        assertRejected(helper, cover,
                action(payload(integer(1), integer(IO.values().length), integer(0), integer(0))),
                "out-of-range IO mode");

        assertRejected(helper, cover,
                action(payload(integer(1), integer(1), new JsonPrimitive(0.5D), integer(0))),
                "fractional bucket mode");
        assertRejected(helper, cover,
                action(payload(integer(1), integer(1), new JsonPrimitive(4_294_967_296L), integer(0))),
                "overflowing bucket mode");
        assertRejected(helper, cover,
                action(payload(integer(1), integer(1), integer(-1), integer(0))), "negative bucket mode");
        assertRejected(helper, cover,
                action(payload(integer(1), integer(1), integer(BucketMode.values().length), integer(0))),
                "out-of-range bucket mode");

        assertRejected(helper, cover,
                action(payload(integer(1), integer(1), integer(0), new JsonPrimitive(0.5D))),
                "fractional manual IO mode");
        assertRejected(helper, cover,
                action(payload(integer(1), integer(1), integer(0), new JsonPrimitive(4_294_967_296L))),
                "overflowing manual IO mode");
        assertRejected(helper, cover,
                action(payload(integer(1), integer(1), integer(0), integer(-1))), "negative manual IO mode");
        assertRejected(helper, cover,
                action(payload(integer(1), integer(1), integer(0), integer(ManualIOMode.VALUES.length))),
                "out-of-range manual IO mode");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "PumpCoverConfigAction")
    public static void dispatcherAcceptsUnknownPayloadFields(GameTestHelper helper) {
        TrackingPumpCover cover = createTrackingPumpCover(GTValues.LV);

        helper.assertTrue(dispatch(helper, cover, action(payloadWithUnknownField())),
                "pump action rejected an unknown future field");
        assertState(helper, cover, 32, IO.IN, BucketMode.MILLI_BUCKET, ManualIOMode.FILTERED,
                "action with unknown field");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "PumpCoverConfigAction")
    public static void dispatcherRejectsWrongHolder(GameTestHelper helper) {
        ConveyorCover conveyor = createConveyorCover();
        SyncActionData action = PumpCoverConfigActions.createSetConfigAction(
                32, IO.IN, BucketMode.MILLI_BUCKET, ManualIOMode.FILTERED);

        helper.assertTrue(!dispatch(helper, conveyor, action), "pump action accepted a conveyor holder");
        helper.assertTrue(!dispatch(helper, new Object(), action), "pump action accepted an unrelated holder");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "PumpCoverConfigAction")
    public static void dispatcherRejectsSpectator(GameTestHelper helper) {
        TrackingPumpCover cover = createTrackingPumpCover(GTValues.LV);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SPECTATOR);
        boolean result;
        try {
            result = dispatch(player, cover, PumpCoverConfigActions.createSetConfigAction(
                    32, IO.IN, BucketMode.MILLI_BUCKET, ManualIOMode.FILTERED));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!result, "pump action accepted a spectator");
        assertState(helper, cover, PumpCover.PUMP_SCALING.applyAsInt(GTValues.LV), IO.OUT,
                BucketMode.MILLI_BUCKET, ManualIOMode.DISABLED, "spectator action");
        helper.assertTrue(cover.getSetterOrder().isEmpty(), "spectator action invoked pump setters");
        helper.succeed();
    }

    private static void assertRejected(GameTestHelper helper, TrackingPumpCover cover, SyncActionData action,
                                       String description) {
        helper.assertTrue(!dispatch(helper, cover, action), description + " payload was accepted");
        assertState(helper, cover, PumpCover.PUMP_SCALING.applyAsInt(GTValues.HV), IO.OUT,
                BucketMode.MILLI_BUCKET, ManualIOMode.DISABLED, description + " payload");
        helper.assertTrue(cover.getSetterOrder().isEmpty(), description + " payload invoked pump setters");
    }

    private static void assertFactoryRejected(Runnable factoryCall, String failureMessage) {
        try {
            factoryCall.run();
        } catch (IllegalArgumentException exception) {
            return;
        }
        throw new GameTestAssertException(failureMessage);
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, SyncActionData action) {
        return dispatch(FakePlayerFactory.getMinecraft(helper.getLevel()), holder, action);
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        PumpCoverConfigActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, Direction.WEST,
                null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData action(DataComponentMap payload) {
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(JsonElement transferRate, JsonElement io, JsonElement bucketMode,
                                            JsonElement manualIO) {
        SyncFieldData.Builder fields = SyncFieldData.builder();
        if (transferRate != null) {
            fields.put(TRANSFER_RATE_FIELD, transferRate);
        }
        if (io != null) {
            fields.put(IO_FIELD, io);
        }
        if (bucketMode != null) {
            fields.put(BUCKET_MODE_FIELD, bucketMode);
        }
        if (manualIO != null) {
            fields.put(MANUAL_IO_FIELD, manualIO);
        }
        return fields.build().toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap payloadWithUnknownField() {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(TRANSFER_RATE_FIELD, integer(32))
                        .put(IO_FIELD, integer(IO.IN.ordinal()))
                        .put(BUCKET_MODE_FIELD, integer(BucketMode.MILLI_BUCKET.ordinal()))
                        .put(MANUAL_IO_FIELD, integer(ManualIOMode.FILTERED.ordinal()))
                        .put(UNKNOWN_FIELD, new JsonPrimitive("ignored"))
                        .build())
                .build();
    }

    private static JsonPrimitive integer(int value) {
        return new JsonPrimitive(value);
    }

    private static SyncFieldData requireFields(DataComponentMap payload, String description) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new GameTestAssertException(description + " omitted sync field data");
        }
        return fields;
    }

    private static void assertIntField(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                       int expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsBigDecimal().intValueExact() == expected,
                description + " was not encoded as the expected integer");
    }

    private static void assertState(GameTestHelper helper, PumpCover cover, int transferRate, IO io,
                                    BucketMode bucketMode, ManualIOMode manualIOMode, String description) {
        helper.assertTrue(cover.getTransferRate() == transferRate && cover.getIo() == io &&
                cover.getBucketMode() == bucketMode && cover.getManualIOMode() == manualIOMode,
                description + " changed the pump to an unexpected state");
    }

    private static TrackingPumpCover createTrackingPumpCover(int tier) {
        BufferMachine machine = createBuffer();
        TrackingPumpCover cover = new TrackingPumpCover(
                GTCovers.PUMPS[tier], machine.getCoverContainer(), Direction.WEST, tier);
        cover.resetSetterOrder();
        return cover;
    }

    private static FluidRegulatorCover createFluidRegulatorCover() {
        BufferMachine machine = createBuffer();
        return new FluidRegulatorCover(GTCovers.FLUID_REGULATORS[GTValues.LV],
                machine.getCoverContainer(), Direction.WEST, GTValues.LV);
    }

    private static ConveyorCover createConveyorCover() {
        BufferMachine machine = createBuffer();
        return new ConveyorCover(GTCovers.CONVEYORS[GTValues.LV],
                machine.getCoverContainer(), Direction.WEST, GTValues.LV);
    }

    private static BufferMachine createBuffer() {
        var definition = GTMachines.BUFFER[GTValues.LV];
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (machine instanceof BufferMachine buffer) {
            return buffer;
        }
        throw new IllegalStateException("LV buffer definition did not create a BufferMachine.");
    }

    private static final class TrackingPumpCover extends PumpCover {

        private final StringBuilder setterOrder = new StringBuilder();

        private TrackingPumpCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide,
                                  int tier) {
            super(definition, coverHolder, attachedSide, tier);
        }

        @Override
        public void setTransferRate(int transferRate) {
            appendSetter("rate");
            super.setTransferRate(transferRate);
        }

        @Override
        public void setIo(@NotNull IO io) {
            appendSetter("io");
            super.setIo(io);
        }

        @Override
        public void setBucketMode(@NotNull BucketMode bucketMode) {
            appendSetter("bucket");
            super.setBucketMode(bucketMode);
        }

        @Override
        public void setManualIOMode(@NotNull ManualIOMode manualIOMode) {
            appendSetter("manual");
            super.setManualIOMode(manualIOMode);
        }

        private void appendSetter(String setter) {
            if (!setterOrder.isEmpty()) {
                setterOrder.append('>');
            }
            setterOrder.append(setter);
        }

        private String getSetterOrder() {
            return setterOrder.toString();
        }

        private void resetSetterOrder() {
            setterOrder.setLength(0);
        }
    }
}

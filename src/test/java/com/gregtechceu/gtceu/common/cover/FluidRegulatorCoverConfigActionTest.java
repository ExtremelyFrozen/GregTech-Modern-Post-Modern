package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.cover.data.BucketMode;
import com.gregtechceu.gtceu.common.cover.data.TransferMode;
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
public class FluidRegulatorCoverConfigActionTest {

    private static final ResourceLocation ACTION_ID = GTCEu.id("set_fluid_regulator_cover_config");
    private static final ResourceLocation TRANSFER_MODE_FIELD = SyncFieldData.key("transferMode");
    private static final ResourceLocation TRANSFER_LIMIT_FIELD = SyncFieldData.key("transferLimit");
    private static final ResourceLocation TRANSFER_BUCKET_FIELD = SyncFieldData.key("transferBucket");
    private static final ResourceLocation UNKNOWN_FIELD = SyncFieldData.key("futureField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidRegulatorCoverConfigAction")
    public static void factoryEncodesProtocolAndDispatcherExecutesConfig(GameTestHelper helper) {
        TrackingFluidRegulatorCover cover = createFluidRegulatorCover();
        SyncActionData action = FluidRegulatorCoverConfigActions.createSetConfigAction(
                TransferMode.KEEP_EXACT, 64_000, BucketMode.BUCKET);
        SyncFieldData fields = requireFields(action.payload(), "fluid regulator config factory");

        helper.assertTrue(action.actionId().equals(ACTION_ID), "factory encoded the wrong action id");
        helper.assertTrue(action.sequence() == 0, "factory changed the fluid regulator action sequence");
        helper.assertTrue(fields.fields().size() == 3,
                "factory encoded fields outside the fluid regulator protocol");
        assertIntField(helper, fields, TRANSFER_MODE_FIELD, TransferMode.KEEP_EXACT.ordinal(),
                "factory transfer mode");
        assertIntField(helper, fields, TRANSFER_LIMIT_FIELD, 64_000, "factory transfer limit");
        assertIntField(helper, fields, TRANSFER_BUCKET_FIELD, BucketMode.BUCKET.ordinal(),
                "factory transfer bucket");

        RegulatedTransferConfigActionProtocol.Schema schema = fluidRegulatorSchema();
        RegulatedTransferConfigActionProtocol.Config config = RegulatedTransferConfigActionProtocol.INSTANCE
                .read(action.payload(), schema);
        Integer transferBucketOrdinal = config == null ? null : config.getTransferBucketOrdinal();
        helper.assertTrue(config != null && config.getTransferModeOrdinal() == TransferMode.KEEP_EXACT.ordinal() &&
                config.getTransferLimit() == 64_000 &&
                transferBucketOrdinal != null && transferBucketOrdinal == BucketMode.BUCKET.ordinal(),
                "protocol did not read the factory bucket payload");

        helper.assertTrue(dispatch(helper, cover, action), "valid fluid regulator config action was rejected");
        assertState(helper, cover, TransferMode.KEEP_EXACT, 64_000, BucketMode.BUCKET, "valid action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidRegulatorCoverConfigAction")
    public static void dispatcherPreservesModeLimitBucketExecutionOrder(GameTestHelper helper) {
        TrackingFluidRegulatorCover cover = createFluidRegulatorCover();
        SyncActionData action = FluidRegulatorCoverConfigActions.createSetConfigAction(
                TransferMode.TRANSFER_EXACT, 32_000, BucketMode.BUCKET);

        helper.assertTrue(dispatch(helper, cover, action), "order-sensitive fluid regulator action was rejected");
        helper.assertTrue(cover.getSetterOrder().equals("mode>limit>bucket"),
                "handler changed the required mode, limit, bucket setter order");
        assertState(helper, cover, TransferMode.TRANSFER_EXACT, 32_000, BucketMode.BUCKET,
                "order-sensitive action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidRegulatorCoverConfigAction")
    public static void dispatcherPreservesFluidRegulatorLimitSemantics(GameTestHelper helper) {
        TrackingFluidRegulatorCover zeroLimitCover = createFluidRegulatorCover();
        TrackingFluidRegulatorCover cappedLimitCover = createFluidRegulatorCover();

        helper.assertTrue(dispatch(helper, zeroLimitCover,
                FluidRegulatorCoverConfigActions.createSetConfigAction(
                        TransferMode.TRANSFER_EXACT, 0, BucketMode.MILLI_BUCKET)),
                "fluid regulator action rejected its valid zero transfer limit");
        assertState(helper, zeroLimitCover, TransferMode.TRANSFER_EXACT, 0, BucketMode.MILLI_BUCKET,
                "zero-limit action");

        helper.assertTrue(dispatch(helper, cappedLimitCover,
                FluidRegulatorCoverConfigActions.createSetConfigAction(
                        TransferMode.TRANSFER_EXACT, Integer.MAX_VALUE, BucketMode.MILLI_BUCKET)),
                "fluid regulator action rejected an integer transfer limit");
        assertState(helper, cappedLimitCover, TransferMode.TRANSFER_EXACT, 2_048_000_000,
                BucketMode.MILLI_BUCKET, "clamped-limit action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidRegulatorCoverConfigAction")
    public static void dispatcherRejectsMalformedConfigPayloads(GameTestHelper helper) {
        TrackingFluidRegulatorCover cover = createFluidRegulatorCover();

        assertRejected(helper, cover,
                action(payload(new JsonPrimitive("1"), new JsonPrimitive(64), new JsonPrimitive(0))),
                "string transfer mode");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(1.5D), new JsonPrimitive(64), new JsonPrimitive(0))),
                "fractional transfer mode");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(4_294_967_297L), new JsonPrimitive(64), new JsonPrimitive(0))),
                "overflowing transfer mode");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(-1), new JsonPrimitive(64), new JsonPrimitive(0))),
                "negative transfer mode");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(TransferMode.values().length), new JsonPrimitive(64),
                        new JsonPrimitive(0))),
                "out-of-range transfer mode");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(1), new JsonPrimitive("64"), new JsonPrimitive(0))),
                "string transfer limit");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(1), new JsonPrimitive(64.5D), new JsonPrimitive(0))),
                "fractional transfer limit");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(1), new JsonPrimitive(4_294_967_360L), new JsonPrimitive(0))),
                "overflowing transfer limit");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(1), new JsonPrimitive(-1), new JsonPrimitive(0))),
                "negative transfer limit");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(1), new JsonPrimitive(64), new JsonPrimitive("0"))),
                "string transfer bucket");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(1), new JsonPrimitive(64), new JsonPrimitive(0.5D))),
                "fractional transfer bucket");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(1), new JsonPrimitive(64),
                        new JsonPrimitive(4_294_967_296L))),
                "overflowing transfer bucket");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(1), new JsonPrimitive(64), new JsonPrimitive(-1))),
                "negative transfer bucket");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(1), new JsonPrimitive(64),
                        new JsonPrimitive(BucketMode.values().length))),
                "out-of-range transfer bucket");
        assertRejected(helper, cover, action(payload(null, new JsonPrimitive(64), new JsonPrimitive(0))),
                "missing transfer mode");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(1), null, new JsonPrimitive(0))),
                "missing transfer limit");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(1), new JsonPrimitive(64), null)),
                "missing transfer bucket");
        assertRejected(helper, cover, action(DataComponentMap.EMPTY), "missing field data");

        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidRegulatorCoverConfigAction")
    public static void protocolFactoryRejectsInvalidBucketBranches(GameTestHelper helper) {
        RegulatedTransferConfigActionProtocol.Schema schema = fluidRegulatorSchema();

        assertFactoryRejected(() -> RegulatedTransferConfigActionProtocol.INSTANCE.createAction(
                ACTION_ID, schema, TransferMode.TRANSFER_EXACT.ordinal(), 64, null),
                "bucket schema accepted a missing bucket ordinal");
        assertFactoryRejected(() -> RegulatedTransferConfigActionProtocol.INSTANCE.createAction(
                ACTION_ID, schema, TransferMode.TRANSFER_EXACT.ordinal(), 64, -1),
                "bucket schema accepted a negative bucket ordinal");
        assertFactoryRejected(() -> RegulatedTransferConfigActionProtocol.INSTANCE.createAction(
                ACTION_ID, schema, TransferMode.TRANSFER_EXACT.ordinal(), 64, BucketMode.values().length),
                "bucket schema accepted an out-of-range bucket ordinal");
        assertFactoryRejected(() -> FluidRegulatorCoverConfigActions.createSetConfigAction(
                TransferMode.TRANSFER_EXACT, -1, BucketMode.MILLI_BUCKET),
                "fluid regulator factory accepted a negative transfer limit");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidRegulatorCoverConfigAction")
    public static void protocolKeepsRobotArmSchemaBucketFreeAndForwardCompatible(GameTestHelper helper) {
        RegulatedTransferConfigActionProtocol.Schema schema = new RegulatedTransferConfigActionProtocol.Schema(
                TransferMode.values().length, 1, null);

        assertFactoryRejected(() -> RegulatedTransferConfigActionProtocol.INSTANCE.createAction(
                ACTION_ID, schema, TransferMode.TRANSFER_EXACT.ordinal(), 64, BucketMode.BUCKET.ordinal()),
                "bucket-free schema accepted an unexpected bucket factory argument");

        DataComponentMap forwardCompatiblePayload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(TRANSFER_MODE_FIELD, new JsonPrimitive(TransferMode.TRANSFER_EXACT.ordinal()))
                        .put(TRANSFER_LIMIT_FIELD, new JsonPrimitive(64))
                        .put(UNKNOWN_FIELD, new JsonPrimitive("ignored"))
                        .build())
                .build();
        RegulatedTransferConfigActionProtocol.Config config = RegulatedTransferConfigActionProtocol.INSTANCE
                .read(forwardCompatiblePayload, schema);

        helper.assertTrue(config != null &&
                config.getTransferModeOrdinal() == TransferMode.TRANSFER_EXACT.ordinal() &&
                config.getTransferLimit() == 64 && config.getTransferBucketOrdinal() == null,
                "bucket-free schema rejected an unknown future payload field");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidRegulatorCoverConfigAction")
    public static void dispatcherAcceptsUnknownPayloadFields(GameTestHelper helper) {
        TrackingFluidRegulatorCover cover = createFluidRegulatorCover();

        helper.assertTrue(dispatch(helper, cover, action(payloadWithUnknownField())),
                "fluid regulator action rejected an unknown payload field");
        assertState(helper, cover, TransferMode.TRANSFER_EXACT, 64, BucketMode.BUCKET,
                "action with unknown payload field");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidRegulatorCoverConfigAction")
    public static void dispatcherRejectsNonFluidRegulatorHolder(GameTestHelper helper) {
        PumpCover pump = createPumpCover();
        SyncActionData action = FluidRegulatorCoverConfigActions.createSetConfigAction(
                TransferMode.TRANSFER_EXACT, 64, BucketMode.BUCKET);

        helper.assertTrue(!dispatch(helper, pump, action), "fluid regulator action accepted a pump holder");
        helper.assertTrue(!dispatch(helper, new Object(), action),
                "fluid regulator action accepted an unrelated holder");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidRegulatorCoverConfigAction")
    public static void dispatcherRejectsSpectator(GameTestHelper helper) {
        TrackingFluidRegulatorCover cover = createFluidRegulatorCover();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SPECTATOR);
        boolean result;
        try {
            result = dispatch(player, cover, FluidRegulatorCoverConfigActions.createSetConfigAction(
                    TransferMode.TRANSFER_EXACT, 64, BucketMode.BUCKET));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!result, "fluid regulator action accepted a spectator");
        assertState(helper, cover, TransferMode.TRANSFER_ANY, 0, BucketMode.MILLI_BUCKET,
                "spectator action");
        helper.assertTrue(cover.getSetterOrder().isEmpty(), "spectator action invoked fluid regulator setters");
        helper.succeed();
    }

    private static void assertRejected(GameTestHelper helper, TrackingFluidRegulatorCover cover,
                                       SyncActionData action, String description) {
        helper.assertTrue(!dispatch(helper, cover, action), description + " payload was accepted");
        assertState(helper, cover, TransferMode.TRANSFER_ANY, 0, BucketMode.MILLI_BUCKET,
                description + " payload");
        helper.assertTrue(cover.getSetterOrder().isEmpty(), description + " payload invoked a setter");
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
        FluidRegulatorCoverConfigActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, Direction.WEST,
                null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData action(DataComponentMap payload) {
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(JsonElement transferMode, JsonElement transferLimit,
                                            JsonElement transferBucket) {
        SyncFieldData.Builder fields = SyncFieldData.builder();
        if (transferMode != null) {
            fields.put(TRANSFER_MODE_FIELD, transferMode);
        }
        if (transferLimit != null) {
            fields.put(TRANSFER_LIMIT_FIELD, transferLimit);
        }
        if (transferBucket != null) {
            fields.put(TRANSFER_BUCKET_FIELD, transferBucket);
        }
        return fields.build().toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap payloadWithUnknownField() {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(TRANSFER_MODE_FIELD, new JsonPrimitive(TransferMode.TRANSFER_EXACT.ordinal()))
                        .put(TRANSFER_LIMIT_FIELD, new JsonPrimitive(64))
                        .put(TRANSFER_BUCKET_FIELD, new JsonPrimitive(BucketMode.BUCKET.ordinal()))
                        .put(UNKNOWN_FIELD, new JsonPrimitive("ignored"))
                        .build())
                .build();
    }

    private static RegulatedTransferConfigActionProtocol.Schema fluidRegulatorSchema() {
        return new RegulatedTransferConfigActionProtocol.Schema(
                TransferMode.values().length, 0, BucketMode.values().length);
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

    private static void assertState(GameTestHelper helper, FluidRegulatorCover cover, TransferMode transferMode,
                                    int transferLimit, BucketMode transferBucketMode, String description) {
        helper.assertTrue(cover.getTransferMode() == transferMode &&
                cover.getGlobalTransferLimit() == transferLimit &&
                cover.getTransferBucketMode() == transferBucketMode,
                description + " changed the fluid regulator to an unexpected state");
    }

    private static TrackingFluidRegulatorCover createFluidRegulatorCover() {
        BufferMachine machine = createBuffer();
        TrackingFluidRegulatorCover cover = new TrackingFluidRegulatorCover(
                GTCovers.FLUID_REGULATORS[GTValues.LV], machine.getCoverContainer(), Direction.WEST, GTValues.LV);
        cover.resetSetterOrder();
        return cover;
    }

    private static PumpCover createPumpCover() {
        BufferMachine machine = createBuffer();
        return new PumpCover(GTCovers.PUMPS[GTValues.LV], machine.getCoverContainer(), Direction.WEST, GTValues.LV);
    }

    private static BufferMachine createBuffer() {
        var definition = GTMachines.BUFFER[GTValues.LV];
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (machine instanceof BufferMachine buffer) {
            return buffer;
        }
        throw new IllegalStateException("LV buffer definition did not create a BufferMachine.");
    }

    private static final class TrackingFluidRegulatorCover extends FluidRegulatorCover {

        private final StringBuilder setterOrder = new StringBuilder();

        private TrackingFluidRegulatorCover(CoverDefinition definition, ICoverable coverHolder,
                                            Direction attachedSide, int tier) {
            super(definition, coverHolder, attachedSide, tier);
        }

        @Override
        public void setTransferMode(@NotNull TransferMode transferMode) {
            appendSetter("mode");
            super.setTransferMode(transferMode);
        }

        @Override
        public void setGlobalTransferLimit(int transferLimit) {
            appendSetter("limit");
            super.setGlobalTransferLimit(transferLimit);
        }

        @Override
        public void setTransferBucketMode(@NotNull BucketMode transferBucketMode) {
            appendSetter("bucket");
            super.setTransferBucketMode(transferBucketMode);
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

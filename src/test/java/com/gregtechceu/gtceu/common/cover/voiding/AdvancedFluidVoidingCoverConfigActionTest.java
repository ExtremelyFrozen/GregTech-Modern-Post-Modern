package com.gregtechceu.gtceu.common.cover.voiding;

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
import com.gregtechceu.gtceu.common.cover.data.VoidingMode;
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
public class AdvancedFluidVoidingCoverConfigActionTest {

    private static final ResourceLocation ACTION_ID = GTCEu.id("set_advanced_fluid_voiding_cover_config");
    private static final ResourceLocation VOIDING_MODE_FIELD = SyncFieldData.key("voidingMode");
    private static final ResourceLocation VOID_SIZE_FIELD = SyncFieldData.key("voidSize");
    private static final ResourceLocation BUCKET_MODE_FIELD = SyncFieldData.key("bucketMode");
    private static final ResourceLocation UNKNOWN_FIELD = SyncFieldData.key("futureField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedFluidVoidingCoverConfigAction")
    public static void factoryPreservesActionIdSequenceAndPayload(GameTestHelper helper) {
        SyncActionData action = AdvancedFluidVoidingCoverConfigActions.createSetConfigAction(
                VoidingMode.VOID_OVERFLOW, 64_000, BucketMode.BUCKET);
        SyncFieldData fields = requireFields(action.payload(), "advanced fluid voiding factory");

        helper.assertTrue(action.actionId().equals(ACTION_ID), "factory encoded the wrong action id");
        helper.assertTrue(action.sequence() == 0, "factory changed the advanced fluid voiding action sequence");
        helper.assertTrue(fields.fields().size() == 3,
                "factory encoded fields outside the advanced fluid voiding protocol");
        assertIntField(helper, fields, VOIDING_MODE_FIELD, VoidingMode.VOID_OVERFLOW.ordinal(),
                "factory voiding mode");
        assertIntField(helper, fields, VOID_SIZE_FIELD, 64_000, "factory void size");
        assertIntField(helper, fields, BUCKET_MODE_FIELD, BucketMode.BUCKET.ordinal(), "factory bucket mode");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedFluidVoidingCoverConfigAction")
    public static void factoryRejectsNonPositiveTransferSize(GameTestHelper helper) {
        assertFactoryRejected(() -> AdvancedFluidVoidingCoverConfigActions.createSetConfigAction(
                VoidingMode.VOID_OVERFLOW, 0, BucketMode.BUCKET),
                "factory accepted a zero transfer size");
        assertFactoryRejected(() -> AdvancedFluidVoidingCoverConfigActions.createSetConfigAction(
                VoidingMode.VOID_OVERFLOW, -1, BucketMode.BUCKET),
                "factory accepted a negative transfer size");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedFluidVoidingCoverConfigAction")
    public static void dispatcherExecutesSettersInRequiredOrder(GameTestHelper helper) {
        TrackingAdvancedFluidVoidingCover cover = createTrackingCover();
        SyncActionData action = AdvancedFluidVoidingCoverConfigActions.createSetConfigAction(
                VoidingMode.VOID_OVERFLOW, 64_000, BucketMode.BUCKET);

        helper.assertTrue(dispatch(helper, cover, action), "valid advanced fluid voiding action was rejected");
        assertState(helper, cover, VoidingMode.VOID_OVERFLOW, 64_000, BucketMode.BUCKET, "valid action");
        helper.assertTrue(cover.getSetterOrder().equals("mode>bucket>size"),
                "handler changed the required mode, bucket, size setter order");

        cover.resetSetterOrder();
        SyncActionData boundaryAction = AdvancedFluidVoidingCoverConfigActions.createSetConfigAction(
                VoidingMode.VOID_ANY, 1, BucketMode.MILLI_BUCKET);
        helper.assertTrue(dispatch(helper, cover, boundaryAction),
                "valid ordinal and positive-size boundary action was rejected");
        assertState(helper, cover, VoidingMode.VOID_ANY, 1, BucketMode.MILLI_BUCKET, "boundary action");
        helper.assertTrue(cover.getSetterOrder().equals("mode>bucket>size"),
                "boundary action changed the required setter order");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedFluidVoidingCoverConfigAction")
    public static void dispatcherRejectsMalformedPayloadsBeforeMutation(GameTestHelper helper) {
        TrackingAdvancedFluidVoidingCover cover = createTrackingCover();

        assertRejected(helper, cover, action(payload(new JsonPrimitive("1"), new JsonPrimitive(64),
                new JsonPrimitive(0))), "string voiding mode");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(1.5D), new JsonPrimitive(64),
                new JsonPrimitive(0))), "fractional voiding mode");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(4_294_967_297L), new JsonPrimitive(64),
                new JsonPrimitive(0))), "overflowing voiding mode");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(-1), new JsonPrimitive(64),
                new JsonPrimitive(0))), "negative voiding mode");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(VoidingMode.values().length),
                new JsonPrimitive(64), new JsonPrimitive(0))), "out-of-range voiding mode");

        assertRejected(helper, cover, action(payload(new JsonPrimitive(1), new JsonPrimitive("64"),
                new JsonPrimitive(0))), "string void size");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(1), new JsonPrimitive(64.5D),
                new JsonPrimitive(0))), "fractional void size");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(1), new JsonPrimitive(4_294_967_360L),
                new JsonPrimitive(0))), "overflowing void size");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(1), new JsonPrimitive(-1),
                new JsonPrimitive(0))), "negative void size");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(1), new JsonPrimitive(0),
                new JsonPrimitive(0))), "zero void size");

        assertRejected(helper, cover, action(payload(new JsonPrimitive(1), new JsonPrimitive(64),
                new JsonPrimitive("0"))), "string bucket mode");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(1), new JsonPrimitive(64),
                new JsonPrimitive(0.5D))), "fractional bucket mode");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(1), new JsonPrimitive(64),
                new JsonPrimitive(4_294_967_296L))), "overflowing bucket mode");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(1), new JsonPrimitive(64),
                new JsonPrimitive(-1))), "negative bucket mode");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(1), new JsonPrimitive(64),
                new JsonPrimitive(BucketMode.values().length))), "out-of-range bucket mode");

        assertRejected(helper, cover, action(payloadWithoutVoidingMode()), "missing voiding mode");
        assertRejected(helper, cover, action(payloadWithoutVoidSize()), "missing void size");
        assertRejected(helper, cover, action(payloadWithoutBucketMode()), "missing bucket mode");
        assertRejected(helper, cover, action(DataComponentMap.EMPTY), "missing field data");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedFluidVoidingCoverConfigAction")
    public static void dispatcherAcceptsUnknownPayloadFields(GameTestHelper helper) {
        TrackingAdvancedFluidVoidingCover cover = createTrackingCover();

        helper.assertTrue(dispatch(helper, cover, action(payloadWithUnknownField())),
                "advanced fluid voiding action rejected an unknown payload field");
        assertState(helper, cover, VoidingMode.VOID_OVERFLOW, 64, BucketMode.BUCKET,
                "action with unknown payload field");
        helper.assertTrue(cover.getSetterOrder().equals("mode>bucket>size"),
                "action with unknown payload field changed setter order");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedFluidVoidingCoverConfigAction")
    public static void dispatcherKeepsAdvancedFluidVoidingHolderScope(GameTestHelper helper) {
        SyncActionData action = AdvancedFluidVoidingCoverConfigActions.createSetConfigAction(
                VoidingMode.VOID_OVERFLOW, 64, BucketMode.BUCKET);
        FluidVoidingCover baseFluidCover = createBaseFluidCover();
        FakeActionTarget fakeTarget = new FakeActionTarget();

        helper.assertTrue(!dispatch(helper, baseFluidCover, action),
                "advanced fluid voiding action accepted a base fluid voiding cover");
        helper.assertTrue(!dispatch(helper, fakeTarget, action),
                "advanced fluid voiding action accepted an interface-only holder");
        helper.assertTrue(fakeTarget.getSetterCalls() == 0,
                "rejected interface-only holder received a setter call");
        helper.assertTrue(!dispatch(helper, new Object(), action),
                "advanced fluid voiding action accepted an unrelated holder");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedFluidVoidingCoverConfigAction")
    public static void dispatcherRejectsSpectator(GameTestHelper helper) {
        TrackingAdvancedFluidVoidingCover cover = createTrackingCover();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SPECTATOR);
        boolean result;
        try {
            result = dispatch(player, cover, AdvancedFluidVoidingCoverConfigActions.createSetConfigAction(
                    VoidingMode.VOID_OVERFLOW, 64, BucketMode.BUCKET));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!result, "advanced fluid voiding action accepted a spectator");
        assertInitialState(helper, cover, "spectator action");
        helper.assertTrue(cover.getSetterOrder().isEmpty(), "spectator action invoked a setter");
        helper.succeed();
    }

    private static void assertRejected(GameTestHelper helper, TrackingAdvancedFluidVoidingCover cover,
                                       SyncActionData action, String description) {
        helper.assertTrue(!dispatch(helper, cover, action), description + " payload was accepted");
        assertInitialState(helper, cover, description + " payload");
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
        AdvancedFluidVoidingCoverConfigActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, Direction.WEST,
                null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData action(DataComponentMap payload) {
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(JsonElement voidingMode, JsonElement voidSize, JsonElement bucketMode) {
        return SyncFieldData.builder()
                .put(VOIDING_MODE_FIELD, voidingMode)
                .put(VOID_SIZE_FIELD, voidSize)
                .put(BUCKET_MODE_FIELD, bucketMode)
                .build()
                .toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap payloadWithoutVoidingMode() {
        return SyncFieldData.builder()
                .put(VOID_SIZE_FIELD, new JsonPrimitive(64))
                .put(BUCKET_MODE_FIELD, new JsonPrimitive(0))
                .build()
                .toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap payloadWithoutVoidSize() {
        return SyncFieldData.builder()
                .put(VOIDING_MODE_FIELD, new JsonPrimitive(1))
                .put(BUCKET_MODE_FIELD, new JsonPrimitive(0))
                .build()
                .toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap payloadWithoutBucketMode() {
        return SyncFieldData.builder()
                .put(VOIDING_MODE_FIELD, new JsonPrimitive(1))
                .put(VOID_SIZE_FIELD, new JsonPrimitive(64))
                .build()
                .toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap payloadWithUnknownField() {
        return SyncFieldData.builder()
                .put(VOIDING_MODE_FIELD, new JsonPrimitive(VoidingMode.VOID_OVERFLOW.ordinal()))
                .put(VOID_SIZE_FIELD, new JsonPrimitive(64))
                .put(BUCKET_MODE_FIELD, new JsonPrimitive(BucketMode.BUCKET.ordinal()))
                .put(UNKNOWN_FIELD, new JsonPrimitive("ignored"))
                .build()
                .toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
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

    private static void assertInitialState(GameTestHelper helper, AdvancedFluidVoidingCover cover,
                                           String description) {
        assertState(helper, cover, VoidingMode.VOID_ANY, 1, BucketMode.MILLI_BUCKET, description);
    }

    private static void assertState(GameTestHelper helper, AdvancedFluidVoidingCover cover,
                                    VoidingMode voidingMode, int voidSize, BucketMode bucketMode,
                                    String description) {
        helper.assertTrue(cover.getVoidingMode() == voidingMode &&
                cover.getGlobalTransferSizeMillibuckets() == voidSize &&
                cover.getTransferBucketMode() == bucketMode,
                description + " changed the advanced fluid voiding cover to an unexpected state");
    }

    private static TrackingAdvancedFluidVoidingCover createTrackingCover() {
        BufferMachine machine = createBuffer();
        TrackingAdvancedFluidVoidingCover cover = new TrackingAdvancedFluidVoidingCover(
                GTCovers.FLUID_VOIDING_ADVANCED, machine.getCoverContainer(), Direction.WEST);
        cover.resetSetterOrder();
        return cover;
    }

    private static FluidVoidingCover createBaseFluidCover() {
        BufferMachine machine = createBuffer();
        return new FluidVoidingCover(GTCovers.FLUID_VOIDING, machine.getCoverContainer(), Direction.WEST);
    }

    private static BufferMachine createBuffer() {
        var definition = GTMachines.BUFFER[GTValues.LV];
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (machine instanceof BufferMachine buffer) {
            return buffer;
        }
        throw new IllegalStateException("LV buffer definition did not create a BufferMachine.");
    }

    private static final class TrackingAdvancedFluidVoidingCover extends AdvancedFluidVoidingCover {

        private final StringBuilder setterOrder = new StringBuilder();

        private TrackingAdvancedFluidVoidingCover(CoverDefinition definition, ICoverable coverHolder,
                                                  Direction attachedSide) {
            super(definition, coverHolder, attachedSide);
        }

        @Override
        public void setVoidingMode(@NotNull VoidingMode voidingMode) {
            appendSetter("mode");
            super.setVoidingMode(voidingMode);
        }

        @Override
        public void setTransferBucketMode(@NotNull BucketMode transferBucketMode) {
            appendSetter("bucket");
            super.setTransferBucketMode(transferBucketMode);
        }

        @Override
        public void setGlobalTransferSizeMillibuckets(int transferSize) {
            appendSetter("size");
            super.setGlobalTransferSizeMillibuckets(transferSize);
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

    private static final class FakeActionTarget implements AdvancedFluidVoidingCoverConfigActionTarget {

        private int setterCalls;

        @Override
        public void setVoidingMode(@NotNull VoidingMode voidingMode) {
            setterCalls++;
        }

        @Override
        public void setTransferBucketMode(@NotNull BucketMode transferBucketMode) {
            setterCalls++;
        }

        @Override
        public void setGlobalTransferSizeMillibuckets(int transferSize) {
            setterCalls++;
        }

        private int getSetterCalls() {
            return setterCalls;
        }
    }
}

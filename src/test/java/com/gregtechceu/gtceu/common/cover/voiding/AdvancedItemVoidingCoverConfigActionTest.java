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
public class AdvancedItemVoidingCoverConfigActionTest {

    private static final ResourceLocation ACTION_ID = GTCEu.id("set_advanced_item_voiding_cover_config");
    private static final ResourceLocation VOIDING_MODE_FIELD = SyncFieldData.key("voidingMode");
    private static final ResourceLocation VOID_SIZE_FIELD = SyncFieldData.key("voidSize");
    private static final ResourceLocation UNKNOWN_FIELD = SyncFieldData.key("futureField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedItemVoidingCoverConfigAction")
    public static void factoryPreservesActionIdSequenceAndPayload(GameTestHelper helper) {
        SyncActionData action = AdvancedItemVoidingCoverConfigActions.createSetConfigAction(
                VoidingMode.VOID_OVERFLOW, 64);
        SyncFieldData fields = requireFields(action.payload(), "advanced item voiding factory");

        helper.assertTrue(action.actionId().equals(ACTION_ID), "factory encoded the wrong action id");
        helper.assertTrue(action.sequence() == 0, "factory changed the advanced item voiding action sequence");
        helper.assertTrue(fields.fields().size() == 2,
                "factory encoded fields outside the advanced item voiding protocol");
        assertIntField(helper, fields, VOIDING_MODE_FIELD, VoidingMode.VOID_OVERFLOW.ordinal(),
                "factory voiding mode");
        assertIntField(helper, fields, VOID_SIZE_FIELD, 64, "factory voiding size");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedItemVoidingCoverConfigAction")
    public static void factoryRejectsNonPositiveVoidingLimit(GameTestHelper helper) {
        assertFactoryRejected(() -> AdvancedItemVoidingCoverConfigActions.createSetConfigAction(
                VoidingMode.VOID_OVERFLOW, 0), "factory accepted a zero voiding limit");
        assertFactoryRejected(() -> AdvancedItemVoidingCoverConfigActions.createSetConfigAction(
                VoidingMode.VOID_OVERFLOW, -1), "factory accepted a negative voiding limit");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedItemVoidingCoverConfigAction")
    public static void dispatcherExecutesSettersInRequiredOrderAndAcceptsBoundaries(GameTestHelper helper) {
        TrackingAdvancedItemVoidingCover cover = createTrackingCover();
        SyncActionData action = AdvancedItemVoidingCoverConfigActions.createSetConfigAction(
                VoidingMode.VOID_OVERFLOW, 64);

        helper.assertTrue(dispatch(helper, cover, action), "valid advanced item voiding action was rejected");
        assertState(helper, cover, VoidingMode.VOID_OVERFLOW, 64, "valid action");
        helper.assertTrue(cover.getSetterOrder().equals("mode>size"),
                "handler changed the required mode, size setter order");

        cover.resetSetterOrder();
        SyncActionData boundaryAction = AdvancedItemVoidingCoverConfigActions.createSetConfigAction(
                VoidingMode.VOID_ANY, 1);
        helper.assertTrue(dispatch(helper, cover, boundaryAction),
                "valid ordinal and positive-size boundary action was rejected");
        assertState(helper, cover, VoidingMode.VOID_ANY, 1, "boundary action");
        helper.assertTrue(cover.getSetterOrder().equals("mode>size"),
                "boundary action changed the required setter order");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedItemVoidingCoverConfigAction")
    public static void dispatcherRejectsMalformedPayloadsBeforeMutation(GameTestHelper helper) {
        TrackingAdvancedItemVoidingCover cover = createTrackingCover();

        assertRejected(helper, cover,
                action(payload(new JsonPrimitive("1"), new JsonPrimitive(64))), "string voiding mode");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(1.5D), new JsonPrimitive(64))), "fractional voiding mode");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(4_294_967_297L), new JsonPrimitive(64))),
                "overflowing voiding mode");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(-1), new JsonPrimitive(64))), "negative voiding mode");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(VoidingMode.values().length), new JsonPrimitive(64))),
                "out-of-range voiding mode");

        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(1), new JsonPrimitive("64"))), "string void size");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(1), new JsonPrimitive(64.5D))), "fractional void size");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(1), new JsonPrimitive(4_294_967_360L))),
                "overflowing void size");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(1), new JsonPrimitive(-1))), "negative void size");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(1), new JsonPrimitive(0))), "zero void size");

        assertRejected(helper, cover, action(payloadWithoutVoidingMode()), "missing voiding mode");
        assertRejected(helper, cover, action(payloadWithoutVoidSize()), "missing void size");
        assertRejected(helper, cover, action(DataComponentMap.EMPTY), "missing field data");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedItemVoidingCoverConfigAction")
    public static void dispatcherAcceptsUnknownPayloadFields(GameTestHelper helper) {
        TrackingAdvancedItemVoidingCover cover = createTrackingCover();

        helper.assertTrue(dispatch(helper, cover, action(payloadWithUnknownField())),
                "advanced item voiding action rejected an unknown payload field");
        assertState(helper, cover, VoidingMode.VOID_OVERFLOW, 64, "action with unknown payload field");
        helper.assertTrue(cover.getSetterOrder().equals("mode>size"),
                "action with unknown payload field changed setter order");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedItemVoidingCoverConfigAction")
    public static void dispatcherKeepsAdvancedItemVoidingHolderScope(GameTestHelper helper) {
        SyncActionData action = AdvancedItemVoidingCoverConfigActions.createSetConfigAction(
                VoidingMode.VOID_OVERFLOW, 64);
        ItemVoidingCover baseItemCover = createBaseItemCover();
        FakeActionTarget fakeTarget = new FakeActionTarget();

        helper.assertTrue(!dispatch(helper, baseItemCover, action),
                "advanced item voiding action accepted a base item voiding cover");
        helper.assertTrue(!dispatch(helper, fakeTarget, action),
                "advanced item voiding action accepted an interface-only holder");
        helper.assertTrue(fakeTarget.getSetterCalls() == 0,
                "rejected interface-only holder received a setter call");
        helper.assertTrue(!dispatch(helper, new Object(), action),
                "advanced item voiding action accepted an unrelated holder");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedItemVoidingCoverConfigAction")
    public static void dispatcherRejectsSpectatorWithoutMutation(GameTestHelper helper) {
        TrackingAdvancedItemVoidingCover cover = createTrackingCover();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SPECTATOR);
        boolean result;
        try {
            result = dispatch(player, cover, AdvancedItemVoidingCoverConfigActions.createSetConfigAction(
                    VoidingMode.VOID_OVERFLOW, 64));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!result, "advanced item voiding action accepted a spectator");
        assertInitialState(helper, cover, "spectator action");
        helper.assertTrue(cover.getSetterOrder().isEmpty(), "spectator action invoked a setter");
        helper.succeed();
    }

    private static void assertRejected(GameTestHelper helper, TrackingAdvancedItemVoidingCover cover,
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
        AdvancedItemVoidingCoverConfigActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, Direction.WEST,
                null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData action(DataComponentMap payload) {
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(JsonElement voidingMode, JsonElement voidSize) {
        return SyncFieldData.builder()
                .put(VOIDING_MODE_FIELD, voidingMode)
                .put(VOID_SIZE_FIELD, voidSize)
                .build()
                .toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap payloadWithoutVoidingMode() {
        return SyncFieldData.builder()
                .put(VOID_SIZE_FIELD, new JsonPrimitive(64))
                .build()
                .toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap payloadWithoutVoidSize() {
        return SyncFieldData.builder()
                .put(VOIDING_MODE_FIELD, new JsonPrimitive(1))
                .build()
                .toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap payloadWithUnknownField() {
        return SyncFieldData.builder()
                .put(VOIDING_MODE_FIELD, new JsonPrimitive(VoidingMode.VOID_OVERFLOW.ordinal()))
                .put(VOID_SIZE_FIELD, new JsonPrimitive(64))
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

    private static void assertInitialState(GameTestHelper helper, AdvancedItemVoidingCover cover,
                                           String description) {
        assertState(helper, cover, VoidingMode.VOID_ANY, 1, description);
    }

    private static void assertState(GameTestHelper helper, AdvancedItemVoidingCover cover,
                                    VoidingMode voidingMode, int voidSize, String description) {
        helper.assertTrue(cover.getVoidingMode() == voidingMode && cover.getGlobalVoidingLimit() == voidSize,
                description + " changed the advanced item voiding cover to an unexpected state");
    }

    private static TrackingAdvancedItemVoidingCover createTrackingCover() {
        BufferMachine machine = createBuffer();
        return new TrackingAdvancedItemVoidingCover(
                GTCovers.ITEM_VOIDING_ADVANCED, machine.getCoverContainer(), Direction.WEST);
    }

    private static ItemVoidingCover createBaseItemCover() {
        BufferMachine machine = createBuffer();
        return new ItemVoidingCover(GTCovers.ITEM_VOIDING, machine.getCoverContainer(), Direction.WEST);
    }

    private static BufferMachine createBuffer() {
        var definition = GTMachines.BUFFER[GTValues.LV];
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (machine instanceof BufferMachine buffer) {
            return buffer;
        }
        throw new IllegalStateException("LV buffer definition did not create a BufferMachine.");
    }

    private static final class TrackingAdvancedItemVoidingCover extends AdvancedItemVoidingCover {

        private final StringBuilder setterOrder = new StringBuilder();

        private TrackingAdvancedItemVoidingCover(CoverDefinition definition, ICoverable coverHolder,
                                                 Direction attachedSide) {
            super(definition, coverHolder, attachedSide);
        }

        @Override
        public void setVoidingMode(@NotNull VoidingMode voidingMode) {
            appendSetter("mode");
            super.setVoidingMode(voidingMode);
        }

        @Override
        public void setGlobalVoidingLimit(int voidingLimit) {
            appendSetter("size");
            super.setGlobalVoidingLimit(voidingLimit);
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

    private static final class FakeActionTarget implements AdvancedItemVoidingCoverConfigActionTarget {

        private int setterCalls;

        @Override
        public void setVoidingMode(@NotNull VoidingMode voidingMode) {
            setterCalls++;
        }

        @Override
        public void setGlobalVoidingLimit(int voidingLimit) {
            setterCalls++;
        }

        private int getSetterCalls() {
            return setterCalls;
        }
    }
}

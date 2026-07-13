package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
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

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class RobotArmCoverConfigActionTest {

    private static final ResourceLocation ACTION_ID = GTCEu.id("set_robot_arm_cover_config");
    private static final ResourceLocation TRANSFER_MODE_FIELD = SyncFieldData.key("transferMode");
    private static final ResourceLocation TRANSFER_LIMIT_FIELD = SyncFieldData.key("transferLimit");
    private static final ResourceLocation UNKNOWN_FIELD = SyncFieldData.key("futureField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "RobotArmCoverConfigAction")
    public static void factoryEncodesAndDispatcherExecutesConfig(GameTestHelper helper) {
        RobotArmCover cover = createRobotArmCover();
        SyncActionData action = RobotArmCoverConfigActions.createSetConfigAction(TransferMode.KEEP_EXACT, 256);
        SyncFieldData fields = requireFields(action.payload(), "robot arm config factory");

        helper.assertTrue(action.actionId().equals(ACTION_ID), "factory encoded the wrong action id");
        helper.assertTrue(action.sequence() == 0, "factory changed the robot arm action sequence");
        helper.assertTrue(fields.fields().size() == 2, "factory encoded fields outside the robot arm protocol");
        assertIntField(helper, fields, TRANSFER_MODE_FIELD, TransferMode.KEEP_EXACT.ordinal(),
                "factory transfer mode");
        assertIntField(helper, fields, TRANSFER_LIMIT_FIELD, 256, "factory transfer limit");

        helper.assertTrue(dispatch(helper, cover, action), "valid robot arm config action was rejected");
        assertState(helper, cover, TransferMode.KEEP_EXACT, 256, "valid action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "RobotArmCoverConfigAction")
    public static void dispatcherPreservesModeBeforeLimitExecutionOrder(GameTestHelper helper) {
        RobotArmCover cover = createRobotArmCover();
        SyncActionData action = RobotArmCoverConfigActions.createSetConfigAction(TransferMode.TRANSFER_EXACT, 64);

        helper.assertTrue(dispatch(helper, cover, action), "order-sensitive robot arm action was rejected");
        assertState(helper, cover, TransferMode.TRANSFER_EXACT, 64,
                "action did not apply transfer mode before its mode-dependent limit");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "RobotArmCoverConfigAction")
    public static void dispatcherRejectsNonRobotArmHolder(GameTestHelper helper) {
        ConveyorCover conveyor = createConveyorCover();
        SyncActionData action = RobotArmCoverConfigActions.createSetConfigAction(TransferMode.TRANSFER_EXACT, 64);

        helper.assertTrue(!dispatch(helper, conveyor, action), "robot arm action accepted a conveyor holder");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "RobotArmCoverConfigAction")
    public static void dispatcherRejectsMalformedConfigPayloads(GameTestHelper helper) {
        RobotArmCover cover = createRobotArmCover();

        assertRejected(helper, cover, action(payload(new JsonPrimitive("1"), new JsonPrimitive(64))),
                "string transfer mode");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(1.5D), new JsonPrimitive(64))),
                "fractional transfer mode");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(4_294_967_297L), new JsonPrimitive(64))),
                "overflowing transfer mode");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(-1), new JsonPrimitive(64))),
                "negative transfer mode");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(TransferMode.values().length), new JsonPrimitive(64))),
                "out-of-range transfer mode");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(1), new JsonPrimitive("64"))),
                "string transfer limit");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(1), new JsonPrimitive(64.5D))),
                "fractional transfer limit");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(1), new JsonPrimitive(4_294_967_360L))),
                "overflowing transfer limit");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(1), new JsonPrimitive(0))),
                "zero transfer limit");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(1), new JsonPrimitive(-1))),
                "negative transfer limit");
        assertRejected(helper, cover, action(payload(null, new JsonPrimitive(64))), "missing transfer mode");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(1), null)), "missing transfer limit");
        assertRejected(helper, cover, action(DataComponentMap.EMPTY), "missing field data");

        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "RobotArmCoverConfigAction")
    public static void factoryRejectsNonPositiveTransferLimits(GameTestHelper helper) {
        boolean rejectedZero = false;
        boolean rejectedNegative = false;
        try {
            RobotArmCoverConfigActions.createSetConfigAction(TransferMode.TRANSFER_EXACT, 0);
        } catch (IllegalArgumentException exception) {
            rejectedZero = true;
        }
        try {
            RobotArmCoverConfigActions.createSetConfigAction(TransferMode.TRANSFER_EXACT, -1);
        } catch (IllegalArgumentException exception) {
            rejectedNegative = true;
        }

        helper.assertTrue(rejectedZero, "factory accepted a zero robot arm transfer limit");
        helper.assertTrue(rejectedNegative, "factory accepted a negative robot arm transfer limit");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "RobotArmCoverConfigAction")
    public static void dispatcherAcceptsUnknownPayloadFields(GameTestHelper helper) {
        RobotArmCover cover = createRobotArmCover();
        SyncActionData action = action(payloadWithUnknownField());

        helper.assertTrue(dispatch(helper, cover, action), "robot arm action rejected an unknown payload field");
        assertState(helper, cover, TransferMode.TRANSFER_EXACT, 64, "action with unknown payload field");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "RobotArmCoverConfigAction")
    public static void dispatcherRejectsSpectator(GameTestHelper helper) {
        RobotArmCover cover = createRobotArmCover();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SPECTATOR);
        boolean result;
        try {
            result = dispatch(player, cover,
                    RobotArmCoverConfigActions.createSetConfigAction(TransferMode.TRANSFER_EXACT, 64));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!result, "robot arm action accepted a spectator");
        assertState(helper, cover, TransferMode.TRANSFER_ANY, 0, "spectator action");
        helper.succeed();
    }

    private static void assertRejected(GameTestHelper helper, RobotArmCover cover, SyncActionData action,
                                       String description) {
        helper.assertTrue(!dispatch(helper, cover, action), description + " payload was accepted");
        assertState(helper, cover, TransferMode.TRANSFER_ANY, 0, description + " payload");
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, SyncActionData action) {
        return dispatch(FakePlayerFactory.getMinecraft(helper.getLevel()), holder, action);
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        RobotArmCoverConfigActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, Direction.WEST,
                null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData action(DataComponentMap payload) {
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(JsonElement transferMode, JsonElement transferLimit) {
        SyncFieldData.Builder fields = SyncFieldData.builder();
        if (transferMode != null) {
            fields.put(TRANSFER_MODE_FIELD, transferMode);
        }
        if (transferLimit != null) {
            fields.put(TRANSFER_LIMIT_FIELD, transferLimit);
        }
        return fields.build().toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap payloadWithUnknownField() {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(TRANSFER_MODE_FIELD, new JsonPrimitive(TransferMode.TRANSFER_EXACT.ordinal()))
                        .put(TRANSFER_LIMIT_FIELD, new JsonPrimitive(64))
                        .put(UNKNOWN_FIELD, new JsonPrimitive("ignored"))
                        .build())
                .build();
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
                primitive.getAsInt() == expected, description + " was not encoded as the expected integer");
    }

    private static void assertState(GameTestHelper helper, RobotArmCover cover, TransferMode transferMode,
                                    int transferLimit, String description) {
        helper.assertTrue(cover.getTransferMode() == transferMode && cover.getGlobalTransferLimit() == transferLimit,
                description + " changed the robot arm to an unexpected state");
    }

    private static RobotArmCover createRobotArmCover() {
        BufferMachine machine = createBuffer();
        return new RobotArmCover(GTCovers.ROBOT_ARMS[GTValues.LV], machine.getCoverContainer(), Direction.WEST,
                GTValues.LV);
    }

    private static ConveyorCover createConveyorCover() {
        BufferMachine machine = createBuffer();
        return new ConveyorCover(GTCovers.CONVEYORS[GTValues.LV], machine.getCoverContainer(), Direction.WEST,
                GTValues.LV);
    }

    private static BufferMachine createBuffer() {
        var definition = GTMachines.BUFFER[GTValues.LV];
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (machine instanceof BufferMachine buffer) {
            return buffer;
        }
        throw new IllegalStateException("LV buffer definition did not create a BufferMachine.");
    }
}

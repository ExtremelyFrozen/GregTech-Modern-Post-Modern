package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.cover.data.DistributionMode;
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode;
import com.gregtechceu.gtceu.common.data.GTCovers;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.storage.BufferMachine;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

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
public class ConveyorCoverConfigActionTest {

    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final ResourceLocation ACTION_ID = GTCEu.id("set_conveyor_cover_config");
    private static final ResourceLocation TRANSFER_RATE_FIELD = SyncFieldData.key("transferRate");
    private static final ResourceLocation IO_FIELD = SyncFieldData.key("io");
    private static final ResourceLocation DISTRIBUTION_MODE_FIELD = SyncFieldData.key("distributionMode");
    private static final ResourceLocation MANUAL_IO_FIELD = SyncFieldData.key("manualIO");
    private static final ResourceLocation UNKNOWN_FIELD = SyncFieldData.key("futureField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ConveyorCoverConfigAction")
    public static void factoryEncodesCompleteConveyorConfig(GameTestHelper helper) {
        SyncActionData action = ConveyorCoverConfigActions.createSetConfigAction(
                32, IO.IN, DistributionMode.ROUND_ROBIN_PRIO, ManualIOMode.FILTERED);
        SyncFieldData fields = requireFields(action.payload(), "conveyor config factory");

        helper.assertTrue(action.actionId().equals(ACTION_ID), "factory encoded the wrong action id");
        helper.assertTrue(action.sequence() == 0, "factory changed the conveyor action sequence");
        helper.assertTrue(fields.fields().size() == 4, "factory encoded fields outside the conveyor protocol");
        assertIntField(helper, fields, TRANSFER_RATE_FIELD, 32, "factory transfer rate");
        assertIntField(helper, fields, IO_FIELD, IO.IN.ordinal(), "factory IO mode");
        assertIntField(helper, fields, DISTRIBUTION_MODE_FIELD, DistributionMode.ROUND_ROBIN_PRIO.ordinal(),
                "factory distribution mode");
        assertIntField(helper, fields, MANUAL_IO_FIELD, ManualIOMode.FILTERED.ordinal(),
                "factory manual IO mode");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ConveyorCoverConfigAction")
    public static void dispatcherAppliesEveryConfigurationDimensionAndTransferBoundary(GameTestHelper helper) {
        TrackingConveyorCover cover = createTrackingConveyorCover(helper);
        int maximum = cover.maxItemTransferRate;
        int[] requestedRates = { 1, maximum, Integer.MAX_VALUE };
        IO[] directions = { IO.IN, IO.OUT };

        for (int requestedRate : requestedRates) {
            for (IO direction : directions) {
                for (DistributionMode distributionMode : DistributionMode.VALUES) {
                    for (ManualIOMode manualIOMode : ManualIOMode.VALUES) {
                        cover.resetSetterOrder();
                        SyncActionData action = ConveyorCoverConfigActions.createSetConfigAction(
                                requestedRate, direction, distributionMode, manualIOMode);

                        helper.assertTrue(dispatch(helper, cover, action),
                                "dispatcher rejected a valid conveyor configuration");
                        assertState(helper, cover, Math.min(requestedRate, maximum), direction, distributionMode,
                                manualIOMode, "valid conveyor configuration");
                        helper.assertTrue(cover.getSetterOrder().equals("rate>io>distribution>manual"),
                                "handler changed the required conveyor setter order");
                    }
                }
            }
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ConveyorCoverConfigAction")
    public static void dispatcherPreservesConveyorSubclassScope(GameTestHelper helper) {
        RobotArmCover robotArm = createRobotArmCover(helper);
        SyncActionData action = ConveyorCoverConfigActions.createSetConfigAction(
                1, IO.IN, DistributionMode.ROUND_ROBIN_GLOBAL, ManualIOMode.UNFILTERED);

        helper.assertTrue(dispatch(helper, robotArm, action),
                "conveyor action rejected a conveyor subclass");
        assertState(helper, robotArm, 1, IO.IN, DistributionMode.ROUND_ROBIN_GLOBAL,
                ManualIOMode.UNFILTERED, "conveyor subclass action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ConveyorCoverConfigAction")
    public static void factoryRejectsInvalidClientState(GameTestHelper helper) {
        assertFactoryRejected(() -> ConveyorCoverConfigActions.createSetConfigAction(
                0, IO.IN, DistributionMode.INSERT_FIRST, ManualIOMode.DISABLED),
                "factory accepted a zero transfer rate");
        assertFactoryRejected(() -> ConveyorCoverConfigActions.createSetConfigAction(
                -1, IO.IN, DistributionMode.INSERT_FIRST, ManualIOMode.DISABLED),
                "factory accepted a negative transfer rate");
        assertFactoryRejected(() -> ConveyorCoverConfigActions.createSetConfigAction(
                1, IO.BOTH, DistributionMode.INSERT_FIRST, ManualIOMode.DISABLED),
                "factory accepted a bidirectional IO mode");
        assertFactoryRejected(() -> ConveyorCoverConfigActions.createSetConfigAction(
                1, IO.NONE, DistributionMode.INSERT_FIRST, ManualIOMode.DISABLED),
                "factory accepted a disabled IO direction");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ConveyorCoverConfigAction")
    public static void dispatcherRejectsMissingAndWrongTypeFieldsBeforeMutation(GameTestHelper helper) {
        TrackingConveyorCover cover = createTrackingConveyorCover(helper);

        assertRejected(helper, cover, action(payload(null, integer(1), integer(0), integer(0))),
                "missing transfer rate");
        assertRejected(helper, cover, action(payload(integer(1), null, integer(0), integer(0))),
                "missing IO mode");
        assertRejected(helper, cover, action(payload(integer(1), integer(IO.IN.ordinal()), null, integer(0))),
                "missing distribution mode");
        assertRejected(helper, cover, action(payload(integer(1), integer(IO.IN.ordinal()), integer(0), null)),
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
                "string distribution mode");
        assertRejected(helper, cover,
                action(payload(integer(1), integer(1), integer(0), new JsonPrimitive("0"))),
                "string manual IO mode");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ConveyorCoverConfigAction")
    public static void dispatcherRejectsNonIntegerAndOutOfRangeFieldsBeforeMutation(GameTestHelper helper) {
        TrackingConveyorCover cover = createTrackingConveyorCover(helper);

        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(1.5D), integer(1), integer(0), integer(0))),
                "fractional transfer rate");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(4_294_967_296L), integer(1), integer(0), integer(0))),
                "overflowing transfer rate");
        assertRejected(helper, cover, action(payload(integer(0), integer(1), integer(0), integer(0))),
                "zero transfer rate");
        assertRejected(helper, cover, action(payload(integer(-1), integer(1), integer(0), integer(0))),
                "negative transfer rate");

        assertRejected(helper, cover,
                action(payload(integer(1), new JsonPrimitive(1.5D), integer(0), integer(0))),
                "fractional IO mode");
        assertRejected(helper, cover,
                action(payload(integer(1), new JsonPrimitive(4_294_967_296L), integer(0), integer(0))),
                "overflowing IO mode");
        assertRejected(helper, cover, action(payload(integer(1), integer(-1), integer(0), integer(0))),
                "negative IO mode");
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
                "fractional distribution mode");
        assertRejected(helper, cover,
                action(payload(integer(1), integer(1), new JsonPrimitive(4_294_967_296L), integer(0))),
                "overflowing distribution mode");
        assertRejected(helper, cover, action(payload(integer(1), integer(1), integer(-1), integer(0))),
                "negative distribution mode");
        assertRejected(helper, cover,
                action(payload(integer(1), integer(1), integer(DistributionMode.VALUES.length), integer(0))),
                "out-of-range distribution mode");

        assertRejected(helper, cover,
                action(payload(integer(1), integer(1), integer(0), new JsonPrimitive(0.5D))),
                "fractional manual IO mode");
        assertRejected(helper, cover,
                action(payload(integer(1), integer(1), integer(0), new JsonPrimitive(4_294_967_296L))),
                "overflowing manual IO mode");
        assertRejected(helper, cover, action(payload(integer(1), integer(1), integer(0), integer(-1))),
                "negative manual IO mode");
        assertRejected(helper, cover,
                action(payload(integer(1), integer(1), integer(0), integer(ManualIOMode.VALUES.length))),
                "out-of-range manual IO mode");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ConveyorCoverConfigAction")
    public static void dispatcherAcceptsUnknownPayloadFields(GameTestHelper helper) {
        TrackingConveyorCover cover = createTrackingConveyorCover(helper);

        helper.assertTrue(dispatch(helper, cover, action(payloadWithUnknownField())),
                "conveyor action rejected an unknown future field");
        assertState(helper, cover, Math.min(32, cover.maxItemTransferRate), IO.IN,
                DistributionMode.ROUND_ROBIN_PRIO, ManualIOMode.FILTERED,
                "action with unknown field");
        helper.assertTrue(cover.getSetterOrder().equals("rate>io>distribution>manual"),
                "action with unknown field changed setter order");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ConveyorCoverConfigAction")
    public static void dispatcherKeepsConcreteConveyorHolderScope(GameTestHelper helper) {
        PumpCover pump = createPumpCover(helper);
        TestConveyorActionTarget fakeTarget = new TestConveyorActionTarget();
        SyncActionData action = ConveyorCoverConfigActions.createSetConfigAction(
                32, IO.IN, DistributionMode.ROUND_ROBIN_PRIO, ManualIOMode.FILTERED);

        helper.assertTrue(!dispatch(helper, pump, action), "conveyor action accepted a pump holder");
        helper.assertTrue(!dispatch(helper, fakeTarget, action),
                "conveyor action accepted an interface-only holder");
        helper.assertTrue(fakeTarget.getSetterCalls() == 0,
                "rejected interface-only holder received a setter call");
        helper.assertTrue(!dispatch(helper, new Object(), action),
                "conveyor action accepted an unrelated holder");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ConveyorCoverConfigAction")
    public static void dispatcherRejectsSpectatorWithoutMutation(GameTestHelper helper) {
        TrackingConveyorCover cover = createTrackingConveyorCover(helper);
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SPECTATOR);
        boolean result;
        try {
            result = dispatch(player, cover, ConveyorCoverConfigActions.createSetConfigAction(
                    32, IO.IN, DistributionMode.ROUND_ROBIN_PRIO, ManualIOMode.FILTERED));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!result, "conveyor action accepted a spectator");
        assertInitialState(helper, cover, "spectator action");
        helper.assertTrue(cover.getSetterOrder().isEmpty(), "spectator action invoked a setter");
        helper.succeed();
    }

    private static void assertRejected(GameTestHelper helper, TrackingConveyorCover cover, SyncActionData action,
                                       String description) {
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
        ConveyorCoverConfigActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, Direction.WEST,
                null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData action(DataComponentMap payload) {
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(JsonElement transferRate, JsonElement io, JsonElement distributionMode,
                                            JsonElement manualIO) {
        SyncFieldData.Builder fields = SyncFieldData.builder();
        if (transferRate != null) {
            fields.put(TRANSFER_RATE_FIELD, transferRate);
        }
        if (io != null) {
            fields.put(IO_FIELD, io);
        }
        if (distributionMode != null) {
            fields.put(DISTRIBUTION_MODE_FIELD, distributionMode);
        }
        if (manualIO != null) {
            fields.put(MANUAL_IO_FIELD, manualIO);
        }
        return fields.build().toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap payloadWithUnknownField() {
        return SyncFieldData.builder()
                .put(TRANSFER_RATE_FIELD, integer(32))
                .put(IO_FIELD, integer(IO.IN.ordinal()))
                .put(DISTRIBUTION_MODE_FIELD, integer(DistributionMode.ROUND_ROBIN_PRIO.ordinal()))
                .put(MANUAL_IO_FIELD, integer(ManualIOMode.FILTERED.ordinal()))
                .put(UNKNOWN_FIELD, new JsonPrimitive("ignored"))
                .build()
                .toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
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

    private static void assertInitialState(GameTestHelper helper, ConveyorCover cover, String description) {
        assertState(helper, cover, cover.maxItemTransferRate, IO.OUT, DistributionMode.INSERT_FIRST,
                ManualIOMode.DISABLED, description);
    }

    private static void assertState(GameTestHelper helper, ConveyorCover cover, int transferRate, IO io,
                                    DistributionMode distributionMode, ManualIOMode manualIOMode,
                                    String description) {
        helper.assertTrue(cover.getTransferRate() == transferRate && cover.getIo() == io &&
                cover.getDistributionMode() == distributionMode && cover.getManualIOMode() == manualIOMode,
                description + " changed the conveyor to an unexpected state");
    }

    private static TrackingConveyorCover createTrackingConveyorCover(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        return new TrackingConveyorCover(GTCovers.CONVEYORS[GTValues.LV], machine.getCoverContainer(),
                Direction.WEST, GTValues.LV);
    }

    private static RobotArmCover createRobotArmCover(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        return new RobotArmCover(GTCovers.ROBOT_ARMS[GTValues.LV], machine.getCoverContainer(), Direction.WEST,
                GTValues.LV);
    }

    private static PumpCover createPumpCover(GameTestHelper helper) {
        BufferMachine machine = createBuffer(helper);
        return new PumpCover(GTCovers.PUMPS[GTValues.LV], machine.getCoverContainer(), Direction.WEST, GTValues.LV);
    }

    private static BufferMachine createBuffer(GameTestHelper helper) {
        return (BufferMachine) TestUtils.setMachine(helper, MACHINE_POS, GTMachines.BUFFER[GTValues.LV]);
    }

    private static final class TrackingConveyorCover extends ConveyorCover {

        private final StringBuilder setterOrder = new StringBuilder();

        private TrackingConveyorCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide,
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
        public void setDistributionMode(@NotNull DistributionMode distributionMode) {
            appendSetter("distribution");
            super.setDistributionMode(distributionMode);
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

    private static final class TestConveyorActionTarget implements ConveyorCoverConfigActionTarget {

        private int setterCalls;

        @Override
        public void setTransferRate(int transferRate) {
            setterCalls++;
        }

        @Override
        public void setIo(@NotNull IO io) {
            setterCalls++;
        }

        @Override
        public void setDistributionMode(@NotNull DistributionMode distributionMode) {
            setterCalls++;
        }

        @Override
        public void setManualIOMode(@NotNull ManualIOMode manualIOMode) {
            setterCalls++;
        }

        private int getSetterCalls() {
            return setterCalls;
        }
    }
}

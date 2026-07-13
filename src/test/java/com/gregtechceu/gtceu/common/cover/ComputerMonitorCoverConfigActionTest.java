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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class ComputerMonitorCoverConfigActionTest {

    private static final int FORMAT_LINE_COUNT = 8;
    private static final int UPDATE_INTERVAL_MIN = 1;
    private static final int UPDATE_INTERVAL_MAX = 60 * 20;
    private static final ResourceLocation ACTION_ID = GTCEu.id("set_computer_monitor_cover_config");
    private static final ResourceLocation FORMAT_LINES_FIELD = SyncFieldData.key("formatLines");
    private static final ResourceLocation FORMAT_ARGS_FIELD = SyncFieldData.key("formatArgs");
    private static final ResourceLocation UPDATE_INTERVAL_FIELD = SyncFieldData.key("updateInterval");
    private static final ResourceLocation UNKNOWN_FIELD = SyncFieldData.key("futureField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ComputerMonitorCoverConfigAction")
    public static void factoryEncodesCompleteSnapshotWithoutListTruncation(GameTestHelper helper) {
        List<String> lines = new ArrayList<>(List.of("line 1", "", "line {arg}", "line 4", "line 5",
                "line 6", "line 7", "line 8", "line 9"));
        List<String> args = new ArrayList<>(List.of("first", "second"));
        List<String> expectedLines = List.copyOf(lines);
        List<String> expectedArgs = List.copyOf(args);
        SyncActionData action = ComputerMonitorCoverConfigActions.createSetConfigAction(
                lines, args, UPDATE_INTERVAL_MIN);
        lines.set(0, "changed after creation");
        args.clear();
        SyncFieldData fields = requireFields(action.payload(), "computer monitor factory");

        helper.assertTrue(action.actionId().equals(ACTION_ID), "factory encoded the wrong action id");
        helper.assertTrue(action.sequence() == 0, "factory changed the computer monitor action sequence");
        helper.assertTrue(fields.fields().size() == 3,
                "factory encoded fields outside the computer monitor protocol");
        assertStringListField(helper, fields, FORMAT_LINES_FIELD, expectedLines, "factory format lines");
        assertStringListField(helper, fields, FORMAT_ARGS_FIELD, expectedArgs, "factory format arguments");
        assertIntField(helper, fields, UPDATE_INTERVAL_FIELD, UPDATE_INTERVAL_MIN, "factory update interval");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ComputerMonitorCoverConfigAction")
    public static void dispatcherPreservesListAndStringLengthSemanticsAtIntervalBoundaries(GameTestHelper helper) {
        TrackingComputerMonitorCover cover = createTrackingCover();
        String longArgument = "x".repeat(4096);
        List<String> shortLines = List.of();
        List<String> shortArgs = List.of("", longArgument);

        helper.assertTrue(dispatch(helper, cover, ComputerMonitorCoverConfigActions.createSetConfigAction(
                shortLines, shortArgs, UPDATE_INTERVAL_MIN)),
                "dispatcher rejected empty or short monitor lists at the minimum interval");
        assertState(helper, cover, padded(shortLines), padded(shortArgs), UPDATE_INTERVAL_MIN,
                "minimum interval action");
        helper.assertTrue(cover.getSetterOrder().equals("lines>args>interval"),
                "handler changed the required monitor setter order");

        List<String> longLines = List.of("0", "1", "2", "3", "4", "5", "6", "7", "8", "9");
        List<String> exactArgs = List.of("a", "b", "c", "d", "e", "f", "g", "h");
        cover.resetSetterOrder();
        helper.assertTrue(dispatch(helper, cover, ComputerMonitorCoverConfigActions.createSetConfigAction(
                longLines, exactArgs, UPDATE_INTERVAL_MAX)),
                "dispatcher rejected an over-eight-line list at the maximum interval");
        assertState(helper, cover, longLines, exactArgs, UPDATE_INTERVAL_MAX, "maximum interval action");
        helper.assertTrue(cover.getSetterOrder().equals("lines>args>interval"),
                "maximum interval action changed the required monitor setter order");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ComputerMonitorCoverConfigAction")
    public static void factoryRejectsUpdateIntervalOutsideExistingBounds(GameTestHelper helper) {
        assertFactoryRejected(() -> ComputerMonitorCoverConfigActions.createSetConfigAction(
                List.of(), List.of(), UPDATE_INTERVAL_MIN - 1),
                "factory accepted an update interval below 1");
        assertFactoryRejected(() -> ComputerMonitorCoverConfigActions.createSetConfigAction(
                List.of(), List.of(), UPDATE_INTERVAL_MAX + 1),
                "factory accepted an update interval above 1200");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ComputerMonitorCoverConfigAction")
    public static void dispatcherRejectsMissingAndWrongContainerTypesBeforeMutation(GameTestHelper helper) {
        TrackingComputerMonitorCover cover = createTrackingCover();
        JsonArray empty = stringArray();

        assertRejected(helper, cover, action(payload(null, empty, integer(100))), "missing format lines");
        assertRejected(helper, cover, action(payload(empty, null, integer(100))), "missing format arguments");
        assertRejected(helper, cover, action(payload(empty, empty, null)), "missing update interval");
        assertRejected(helper, cover, action(DataComponentMap.EMPTY), "missing field data");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive("line"), empty, integer(100))), "scalar format lines");
        assertRejected(helper, cover,
                action(payload(empty, new JsonPrimitive("arg"), integer(100))), "scalar format arguments");
        assertRejected(helper, cover,
                action(payload(empty, empty, new JsonPrimitive("100"))), "string update interval");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ComputerMonitorCoverConfigAction")
    public static void dispatcherRejectsNonStringEntriesAndInvalidIntervalsBeforeMutation(GameTestHelper helper) {
        TrackingComputerMonitorCover cover = createTrackingCover();
        JsonArray empty = stringArray();

        assertRejected(helper, cover,
                action(payload(array(new JsonPrimitive("line"), integer(1)), empty, integer(100))),
                "numeric format line");
        assertRejected(helper, cover,
                action(payload(empty, array(new JsonPrimitive("arg"), new JsonPrimitive(true)), integer(100))),
                "boolean format argument");
        assertRejected(helper, cover,
                action(payload(array(JsonNull.INSTANCE), empty, integer(100))), "null format line");

        assertRejected(helper, cover,
                action(payload(empty, empty, new JsonPrimitive(1.5D))), "fractional update interval");
        assertRejected(helper, cover,
                action(payload(empty, empty, new JsonPrimitive(4_294_967_296L))),
                "overflowing update interval");
        assertRejected(helper, cover, action(payload(empty, empty, integer(0))),
                "update interval below range");
        assertRejected(helper, cover, action(payload(empty, empty, integer(UPDATE_INTERVAL_MAX + 1))),
                "update interval above range");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ComputerMonitorCoverConfigAction")
    public static void dispatcherAcceptsUnknownPayloadFields(GameTestHelper helper) {
        TrackingComputerMonitorCover cover = createTrackingCover();

        helper.assertTrue(dispatch(helper, cover, action(payloadWithUnknownField())),
                "computer monitor action rejected an unknown future field");
        assertState(helper, cover, padded(List.of("line")), padded(List.of("arg")), 100,
                "action with unknown field");
        helper.assertTrue(cover.getSetterOrder().equals("lines>args>interval"),
                "action with unknown field changed setter order");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ComputerMonitorCoverConfigAction")
    public static void dispatcherKeepsConcreteMonitorHolderScope(GameTestHelper helper) {
        PumpCover pump = createPumpCover();
        FakeActionTargetImpl fakeTarget = new FakeActionTargetImpl();
        SyncActionData action = ComputerMonitorCoverConfigActions.createSetConfigAction(
                List.of("line"), List.of("arg"), 100);

        helper.assertTrue(!dispatch(helper, pump, action),
                "computer monitor action accepted another cover type");
        helper.assertTrue(!dispatch(helper, fakeTarget, action),
                "computer monitor action accepted an interface-only holder");
        helper.assertTrue(fakeTarget.getSetterCalls() == 0,
                "rejected interface-only holder received a setter call");
        helper.assertTrue(!dispatch(helper, new Object(), action),
                "computer monitor action accepted an unrelated holder");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ComputerMonitorCoverConfigAction")
    public static void dispatcherRejectsSpectatorWithoutMutation(GameTestHelper helper) {
        TrackingComputerMonitorCover cover = createTrackingCover();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SPECTATOR);
        boolean result;
        try {
            result = dispatch(player, cover, ComputerMonitorCoverConfigActions.createSetConfigAction(
                    List.of("line"), List.of("arg"), UPDATE_INTERVAL_MIN));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!result, "computer monitor action accepted a spectator");
        assertInitialState(helper, cover, "spectator action");
        helper.assertTrue(cover.getSetterOrder().isEmpty(), "spectator action invoked a setter");
        helper.succeed();
    }

    private static void assertRejected(GameTestHelper helper, TrackingComputerMonitorCover cover,
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
        ComputerMonitorCoverConfigActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, Direction.WEST,
                null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData action(DataComponentMap payload) {
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(JsonElement lines, JsonElement args, JsonElement updateInterval) {
        SyncFieldData.Builder fields = SyncFieldData.builder();
        if (lines != null) {
            fields.put(FORMAT_LINES_FIELD, lines);
        }
        if (args != null) {
            fields.put(FORMAT_ARGS_FIELD, args);
        }
        if (updateInterval != null) {
            fields.put(UPDATE_INTERVAL_FIELD, updateInterval);
        }
        return fields.build().toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap payloadWithUnknownField() {
        return SyncFieldData.builder()
                .put(FORMAT_LINES_FIELD, stringArray("line"))
                .put(FORMAT_ARGS_FIELD, stringArray("arg"))
                .put(UPDATE_INTERVAL_FIELD, integer(100))
                .put(UNKNOWN_FIELD, new JsonPrimitive("ignored"))
                .build()
                .toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static JsonArray stringArray(String... values) {
        JsonArray array = new JsonArray(values.length);
        for (String value : values) {
            array.add(value);
        }
        return array;
    }

    private static JsonArray array(JsonElement... values) {
        JsonArray array = new JsonArray(values.length);
        for (JsonElement value : values) {
            array.add(value);
        }
        return array;
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

    private static void assertStringListField(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                              List<String> expected, String description) {
        JsonElement element = fields.get(field);
        if (!(element instanceof JsonArray array)) {
            throw new GameTestAssertException(description + " was not encoded as an array");
        }
        List<String> actual = new ArrayList<>(array.size());
        for (JsonElement entry : array) {
            if (!(entry instanceof JsonPrimitive primitive) || !primitive.isString()) {
                throw new GameTestAssertException(description + " contained a non-string entry");
            }
            actual.add(primitive.getAsString());
        }
        helper.assertTrue(actual.equals(expected), description + " changed its values or order");
    }

    private static void assertIntField(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                       int expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsBigDecimal().intValueExact() == expected,
                description + " was not encoded as the expected integer");
    }

    private static List<String> padded(List<String> values) {
        List<String> padded = new ArrayList<>(values);
        while (padded.size() < FORMAT_LINE_COUNT) {
            padded.add("");
        }
        return padded;
    }

    private static void assertInitialState(GameTestHelper helper, ComputerMonitorCover cover, String description) {
        assertState(helper, cover, List.of(), List.of(), 100, description);
    }

    private static void assertState(GameTestHelper helper, ComputerMonitorCover cover, List<String> lines,
                                    List<String> args, int updateInterval, String description) {
        helper.assertTrue(cover.getFormatStringLines().equals(lines) &&
                cover.getFormatStringArgs().equals(args) && cover.getUpdateInterval() == updateInterval,
                description + " changed the computer monitor to an unexpected state");
    }

    private static TrackingComputerMonitorCover createTrackingCover() {
        BufferMachine machine = createBuffer();
        return new TrackingComputerMonitorCover(
                GTCovers.COMPUTER_MONITOR, machine.getCoverContainer(), Direction.WEST);
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

    private static final class TrackingComputerMonitorCover extends ComputerMonitorCover {

        private final StringBuilder setterOrder = new StringBuilder();

        private TrackingComputerMonitorCover(CoverDefinition definition, ICoverable coverHolder,
                                             Direction attachedSide) {
            super(definition, coverHolder, attachedSide);
        }

        @Override
        public void replaceFormatStringLines(@NotNull List<String> lines) {
            appendSetter("lines");
            super.replaceFormatStringLines(lines);
        }

        @Override
        public void replaceFormatStringArgs(@NotNull List<String> args) {
            appendSetter("args");
            super.replaceFormatStringArgs(args);
        }

        @Override
        public void setUpdateInterval(int updateInterval) {
            appendSetter("interval");
            super.setUpdateInterval(updateInterval);
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

    private static final class FakeActionTargetImpl implements ComputerMonitorCoverConfigActionTarget {

        private int setterCalls;

        @Override
        public void replaceFormatStringLines(@NotNull List<String> lines) {
            setterCalls++;
        }

        @Override
        public void replaceFormatStringArgs(@NotNull List<String> args) {
            setterCalls++;
        }

        @Override
        public void setUpdateInterval(int updateInterval) {
            setterCalls++;
        }

        private int getSetterCalls() {
            return setterCalls;
        }
    }
}

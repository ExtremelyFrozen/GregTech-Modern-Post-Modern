package com.gregtechceu.gtceu.common.cover.detector;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
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

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class AdvancedItemDetectorConfigActionTest {

    private static final int DEFAULT_MIN = 64;
    private static final int DEFAULT_MAX = 512;
    private static final ResourceLocation ACTION_ID = GTCEu.id("set_advanced_item_detector_config");
    private static final ResourceLocation MIN_FIELD = SyncFieldData.key("min");
    private static final ResourceLocation MAX_FIELD = SyncFieldData.key("max");
    private static final ResourceLocation LATCHED_FIELD = SyncFieldData.key("latched");
    private static final ResourceLocation INVERTED_FIELD = SyncFieldData.key("inverted");
    private static final ResourceLocation UNKNOWN_FIELD = SyncFieldData.key("futureField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedItemDetectorConfigAction")
    public static void factoryEncodesAndDispatcherExecutesConfig(GameTestHelper helper) {
        AdvancedItemDetectorCover cover = createItemCover();
        SyncActionData action = AdvancedItemDetectorConfigActions.createSetConfigAction(128, 1024, true, true);
        SyncFieldData fields = requireFields(action.payload(), "advanced item detector config factory");

        helper.assertTrue(action.actionId().equals(ACTION_ID), "factory encoded the wrong action id");
        helper.assertTrue(action.sequence() == 0, "factory changed the advanced item detector action sequence");
        helper.assertTrue(fields.fields().size() == 4, "factory encoded fields outside the config protocol");
        assertIntField(helper, fields, MIN_FIELD, 128, "factory minimum");
        assertIntField(helper, fields, MAX_FIELD, 1024, "factory maximum");
        assertBooleanField(helper, fields, LATCHED_FIELD, true, "factory latched state");
        assertBooleanField(helper, fields, INVERTED_FIELD, true, "factory inverted state");

        helper.assertTrue(dispatch(helper, cover, action), "valid advanced item detector action was rejected");
        assertState(helper, cover, 128, 1024, true, true, "valid action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedItemDetectorConfigAction")
    public static void dispatcherPreservesMinBeforeMaxExecutionOrder(GameTestHelper helper) {
        AdvancedItemDetectorCover cover = createItemCover();
        SyncActionData action = AdvancedItemDetectorConfigActions.createSetConfigAction(900, 1000, true, true);

        helper.assertTrue(dispatch(helper, cover, action), "order-sensitive advanced item action was rejected");
        assertState(helper, cover, DEFAULT_MAX - 1, 1000, true, true,
                "order-sensitive action did not apply minimum before maximum");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedItemDetectorConfigAction")
    public static void dispatcherRejectsNonItemDetectorHolders(GameTestHelper helper) {
        SyncActionData action = AdvancedItemDetectorConfigActions.createSetConfigAction(128, 1024, true, true);
        AdvancedFluidDetectorCover fluidCover = createFluidCover();

        helper.assertTrue(!dispatch(helper, new Object(), action),
                "advanced item detector action accepted an unrelated holder");
        helper.assertTrue(!dispatch(helper, fluidCover, action),
                "advanced item detector action accepted an advanced fluid detector holder");
        helper.assertTrue(fluidCover.getMinValue() == DEFAULT_MIN && fluidCover.getMaxValue() == DEFAULT_MAX &&
                !fluidCover.isLatched() && !fluidCover.isInverted(),
                "rejected item action changed the advanced fluid detector");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedItemDetectorConfigAction")
    public static void dispatcherRejectsMalformedConfigPayloads(GameTestHelper helper) {
        AdvancedItemDetectorCover cover = createItemCover();

        assertRejected(helper, cover, action(payload(new JsonPrimitive("128"), new JsonPrimitive(1024),
                new JsonPrimitive(true), new JsonPrimitive(false))), "string minimum");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(128.5D), new JsonPrimitive(1024),
                new JsonPrimitive(true), new JsonPrimitive(false))), "fractional minimum");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(Long.MAX_VALUE), new JsonPrimitive(1024),
                new JsonPrimitive(true), new JsonPrimitive(false))), "overflowing minimum");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(-1), new JsonPrimitive(1024),
                new JsonPrimitive(true), new JsonPrimitive(false))), "negative minimum");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(128), new JsonPrimitive(-1),
                new JsonPrimitive(true), new JsonPrimitive(false))), "negative maximum");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(128), new JsonPrimitive(1024),
                new JsonPrimitive(1), new JsonPrimitive(false))), "non-boolean latched state");
        assertRejected(helper, cover, action(payload(new JsonPrimitive(128), new JsonPrimitive(1024),
                new JsonPrimitive(true), new JsonPrimitive("false"))), "non-boolean inverted state");
        assertRejected(helper, cover, action(payloadWithoutInverted()), "missing inverted state");
        assertRejected(helper, cover, action(DataComponentMap.EMPTY), "missing field data");

        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedItemDetectorConfigAction")
    public static void dispatcherAcceptsUnknownPayloadFields(GameTestHelper helper) {
        AdvancedItemDetectorCover cover = createItemCover();
        SyncActionData action = action(payloadWithUnknownField());

        helper.assertTrue(dispatch(helper, cover, action),
                "advanced item detector action rejected an unknown payload field");
        assertState(helper, cover, 128, 1024, true, false, "action with unknown payload field");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "AdvancedItemDetectorConfigAction")
    public static void dispatcherRejectsSpectator(GameTestHelper helper) {
        AdvancedItemDetectorCover cover = createItemCover();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SPECTATOR);
        boolean result;
        try {
            result = dispatch(player, cover,
                    AdvancedItemDetectorConfigActions.createSetConfigAction(128, 1024, true, true));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!result, "advanced item detector action accepted a spectator");
        assertState(helper, cover, DEFAULT_MIN, DEFAULT_MAX, false, false, "spectator action");
        helper.succeed();
    }

    private static void assertRejected(GameTestHelper helper, AdvancedItemDetectorCover cover,
                                       SyncActionData action, String description) {
        helper.assertTrue(!dispatch(helper, cover, action), description + " payload was accepted");
        assertState(helper, cover, DEFAULT_MIN, DEFAULT_MAX, false, false, description + " payload");
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, SyncActionData action) {
        return dispatch(FakePlayerFactory.getMinecraft(helper.getLevel()), holder, action);
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        AdvancedItemDetectorConfigActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, Direction.WEST,
                null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData action(DataComponentMap payload) {
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(JsonElement min, JsonElement max, JsonElement latched,
                                            JsonElement inverted) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(MIN_FIELD, min)
                        .put(MAX_FIELD, max)
                        .put(LATCHED_FIELD, latched)
                        .put(INVERTED_FIELD, inverted)
                        .build())
                .build();
    }

    private static DataComponentMap payloadWithoutInverted() {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(MIN_FIELD, new JsonPrimitive(128))
                        .put(MAX_FIELD, new JsonPrimitive(1024))
                        .put(LATCHED_FIELD, new JsonPrimitive(true))
                        .build())
                .build();
    }

    private static DataComponentMap payloadWithUnknownField() {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(MIN_FIELD, new JsonPrimitive(128))
                        .put(MAX_FIELD, new JsonPrimitive(1024))
                        .put(LATCHED_FIELD, new JsonPrimitive(true))
                        .put(INVERTED_FIELD, new JsonPrimitive(false))
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

    private static void assertBooleanField(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                           boolean expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected, description + " was not encoded as the expected boolean");
    }

    private static void assertState(GameTestHelper helper, AdvancedItemDetectorCover cover, int min, int max,
                                    boolean latched, boolean inverted, String description) {
        helper.assertTrue(cover.getMinValue() == min && cover.getMaxValue() == max &&
                cover.isLatched() == latched && cover.isInverted() == inverted,
                description + " changed the cover to an unexpected state");
    }

    private static AdvancedItemDetectorCover createItemCover() {
        BufferMachine machine = createBuffer();
        return new AdvancedItemDetectorCover(GTCovers.ITEM_DETECTOR_ADVANCED, machine.getCoverContainer(),
                Direction.WEST);
    }

    private static AdvancedFluidDetectorCover createFluidCover() {
        BufferMachine machine = createBuffer();
        return new AdvancedFluidDetectorCover(GTCovers.FLUID_DETECTOR_ADVANCED, machine.getCoverContainer(),
                Direction.WEST);
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

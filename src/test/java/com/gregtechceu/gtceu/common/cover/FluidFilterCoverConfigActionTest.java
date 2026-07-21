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
import com.gregtechceu.gtceu.common.cover.data.FilterMode;
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
public class FluidFilterCoverConfigActionTest {

    private static final ResourceLocation ACTION_ID = GTCEu.id("set_fluid_filter_cover_config");
    private static final ResourceLocation FILTER_MODE_FIELD = SyncFieldData.key("filterMode");
    private static final ResourceLocation MANUAL_IO_FIELD = SyncFieldData.key("manualIO");
    private static final ResourceLocation UNKNOWN_FIELD = SyncFieldData.key("futureField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidFilterCoverConfigAction")
    public static void factoryPreservesActionIdSequenceAndPayload(GameTestHelper helper) {
        SyncActionData action = FluidFilterCoverConfigActions.createSetConfigAction(
                FilterMode.FILTER_BOTH, ManualIOMode.UNFILTERED);
        SyncFieldData fields = requireFields(action.payload(), "fluid filter factory");

        helper.assertTrue(action.actionId().equals(ACTION_ID), "factory encoded the wrong action id");
        helper.assertTrue(action.sequence() == 0, "factory changed the fluid filter action sequence");
        helper.assertTrue(fields.fields().size() == 2, "factory encoded fields outside the fluid filter protocol");
        assertIntField(helper, fields, FILTER_MODE_FIELD, FilterMode.FILTER_BOTH.ordinal(),
                "factory filter mode");
        assertIntField(helper, fields, MANUAL_IO_FIELD, ManualIOMode.UNFILTERED.ordinal(),
                "factory manual IO mode");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidFilterCoverConfigAction")
    public static void dispatcherAcceptsEveryEnumValueInRequiredSetterOrder(GameTestHelper helper) {
        TrackingFluidFilterCover cover = createTrackingCover();

        for (FilterMode filterMode : FilterMode.VALUES) {
            for (ManualIOMode manualIOMode : ManualIOMode.VALUES) {
                cover.resetSetterOrder();
                SyncActionData action = FluidFilterCoverConfigActions.createSetConfigAction(filterMode, manualIOMode);

                helper.assertTrue(dispatch(helper, cover, action),
                        "dispatcher rejected valid filter/manual IO enum values");
                assertState(helper, cover, filterMode, manualIOMode, "valid enum action");
                helper.assertTrue(cover.getSetterOrder().equals("filter>manual"),
                        "handler changed the required filter, manual IO setter order");
            }
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidFilterCoverConfigAction")
    public static void dispatcherRejectsMalformedPayloadsBeforeMutation(GameTestHelper helper) {
        TrackingFluidFilterCover cover = createTrackingCover();

        assertRejected(helper, cover,
                action(payload(new JsonPrimitive("0"), new JsonPrimitive(0))), "string filter mode");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(0.5D), new JsonPrimitive(0))), "fractional filter mode");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(4_294_967_296L), new JsonPrimitive(0))),
                "overflowing filter mode");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(-1), new JsonPrimitive(0))), "negative filter mode");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(FilterMode.VALUES.length), new JsonPrimitive(0))),
                "out-of-range filter mode");

        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(0), new JsonPrimitive("0"))), "string manual IO mode");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(0), new JsonPrimitive(0.5D))), "fractional manual IO mode");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(0), new JsonPrimitive(4_294_967_296L))),
                "overflowing manual IO mode");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(0), new JsonPrimitive(-1))), "negative manual IO mode");
        assertRejected(helper, cover,
                action(payload(new JsonPrimitive(0), new JsonPrimitive(ManualIOMode.VALUES.length))),
                "out-of-range manual IO mode");

        assertRejected(helper, cover, action(payloadWithoutFilterMode()), "missing filter mode");
        assertRejected(helper, cover, action(payloadWithoutManualIO()), "missing manual IO mode");
        assertRejected(helper, cover, action(DataComponentMap.EMPTY), "missing field data");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidFilterCoverConfigAction")
    public static void dispatcherAcceptsUnknownPayloadFields(GameTestHelper helper) {
        TrackingFluidFilterCover cover = createTrackingCover();

        helper.assertTrue(dispatch(helper, cover, action(payloadWithUnknownField())),
                "fluid filter action rejected an unknown payload field");
        assertState(helper, cover, FilterMode.FILTER_BOTH, ManualIOMode.FILTERED,
                "action with unknown payload field");
        helper.assertTrue(cover.getSetterOrder().equals("filter>manual"),
                "action with unknown payload field changed setter order");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidFilterCoverConfigAction")
    public static void dispatcherKeepsFluidFilterHolderScope(GameTestHelper helper) {
        SyncActionData action = FluidFilterCoverConfigActions.createSetConfigAction(
                FilterMode.FILTER_BOTH, ManualIOMode.FILTERED);
        ItemFilterCover itemFilterCover = createItemFilterCover();
        FakeActionTarget fakeTarget = new FakeActionTarget();

        helper.assertTrue(!dispatch(helper, itemFilterCover, action),
                "fluid filter action accepted an item filter cover");
        helper.assertTrue(!dispatch(helper, fakeTarget, action),
                "fluid filter action accepted an interface-only holder");
        helper.assertTrue(fakeTarget.getSetterCalls() == 0,
                "rejected interface-only holder received a setter call");
        helper.assertTrue(!dispatch(helper, new Object(), action),
                "fluid filter action accepted an unrelated holder");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "FluidFilterCoverConfigAction")
    public static void dispatcherRejectsSpectatorWithoutMutation(GameTestHelper helper) {
        TrackingFluidFilterCover cover = createTrackingCover();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SPECTATOR);
        boolean result;
        try {
            result = dispatch(player, cover, FluidFilterCoverConfigActions.createSetConfigAction(
                    FilterMode.FILTER_BOTH, ManualIOMode.FILTERED));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!result, "fluid filter action accepted a spectator");
        assertInitialState(helper, cover, "spectator action");
        helper.assertTrue(cover.getSetterOrder().isEmpty(), "spectator action invoked a setter");
        helper.succeed();
    }

    private static void assertRejected(GameTestHelper helper, TrackingFluidFilterCover cover,
                                       SyncActionData action, String description) {
        helper.assertTrue(!dispatch(helper, cover, action), description + " payload was accepted");
        assertInitialState(helper, cover, description + " payload");
        helper.assertTrue(cover.getSetterOrder().isEmpty(), description + " payload invoked a setter");
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, SyncActionData action) {
        return dispatch(FakePlayerFactory.getMinecraft(helper.getLevel()), holder, action);
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        FluidFilterCoverConfigActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, Direction.WEST,
                null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData action(DataComponentMap payload) {
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(JsonElement filterMode, JsonElement manualIO) {
        return SyncFieldData.builder()
                .put(FILTER_MODE_FIELD, filterMode)
                .put(MANUAL_IO_FIELD, manualIO)
                .build()
                .toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap payloadWithoutFilterMode() {
        return SyncFieldData.builder()
                .put(MANUAL_IO_FIELD, new JsonPrimitive(0))
                .build()
                .toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap payloadWithoutManualIO() {
        return SyncFieldData.builder()
                .put(FILTER_MODE_FIELD, new JsonPrimitive(0))
                .build()
                .toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static DataComponentMap payloadWithUnknownField() {
        return SyncFieldData.builder()
                .put(FILTER_MODE_FIELD, new JsonPrimitive(FilterMode.FILTER_BOTH.ordinal()))
                .put(MANUAL_IO_FIELD, new JsonPrimitive(ManualIOMode.FILTERED.ordinal()))
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

    private static void assertInitialState(GameTestHelper helper, FluidFilterCover cover, String description) {
        assertState(helper, cover, FilterMode.FILTER_INSERT, ManualIOMode.DISABLED, description);
    }

    private static void assertState(GameTestHelper helper, FluidFilterCover cover, FilterMode filterMode,
                                    ManualIOMode manualIOMode, String description) {
        helper.assertTrue(cover.getFilterMode() == filterMode && cover.getAllowFlow() == manualIOMode,
                description + " changed the fluid filter cover to an unexpected state");
    }

    private static TrackingFluidFilterCover createTrackingCover() {
        BufferMachine machine = createBuffer();
        return new TrackingFluidFilterCover(GTCovers.FLUID_FILTER, machine.getCoverContainer(), Direction.WEST);
    }

    private static ItemFilterCover createItemFilterCover() {
        BufferMachine machine = createBuffer();
        return new ItemFilterCover(GTCovers.ITEM_FILTER, machine.getCoverContainer(), Direction.WEST);
    }

    private static BufferMachine createBuffer() {
        var definition = GTMachines.BUFFER[GTValues.LV];
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (machine instanceof BufferMachine buffer) {
            return buffer;
        }
        throw new IllegalStateException("LV buffer definition did not create a BufferMachine.");
    }

    private static final class TrackingFluidFilterCover extends FluidFilterCover {

        private final StringBuilder setterOrder = new StringBuilder();

        private TrackingFluidFilterCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
            super(definition, coverHolder, attachedSide);
        }

        @Override
        public void setFilterMode(@NotNull FilterMode filterMode) {
            appendSetter("filter");
            super.setFilterMode(filterMode);
        }

        @Override
        public void setAllowFlow(@NotNull ManualIOMode allowFlow) {
            appendSetter("manual");
            super.setAllowFlow(allowFlow);
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

    private static final class FakeActionTarget implements FluidFilterCoverConfigActionTarget {

        private int setterCalls;

        @Override
        public void setFilterMode(@NotNull FilterMode filterMode) {
            setterCalls++;
        }

        @Override
        public void setAllowFlow(@NotNull ManualIOMode manualIOMode) {
            setterCalls++;
        }

        private int getSetterCalls() {
            return setterCalls;
        }
    }
}

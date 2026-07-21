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
public class ItemVoidingCoverConfigActionTest {

    private static final ResourceLocation ACTION_ID = GTCEu.id("set_item_voiding_cover_config");
    private static final ResourceLocation WORKING_ENABLED_FIELD = SyncFieldData.key("workingEnabled");
    private static final ResourceLocation UNKNOWN_FIELD = SyncFieldData.key("futureField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ItemVoidingCoverConfigAction")
    public static void factoryPreservesActionIdBooleanSequencesAndPayload(GameTestHelper helper) {
        SyncActionData disabled = ItemVoidingCoverConfigActions.createSetWorkingEnabledAction(false);
        SyncActionData enabled = ItemVoidingCoverConfigActions.createSetWorkingEnabledAction(true);

        assertFactoryAction(helper, disabled, false, 0, "disabled action");
        assertFactoryAction(helper, enabled, true, 1, "enabled action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ItemVoidingCoverConfigAction")
    public static void dispatcherExecutesThroughCoverSetter(GameTestHelper helper) {
        TrackingItemVoidingCover cover = createTrackingItemCover();

        helper.assertTrue(dispatch(helper, cover,
                ItemVoidingCoverConfigActions.createSetWorkingEnabledAction(false)),
                "valid disabled item voiding action was rejected");
        helper.assertTrue(!cover.isWorkingEnabled() && cover.getSetWorkingEnabledCalls() == 1,
                "disabled action did not invoke the cover setter for the existing state");

        helper.assertTrue(dispatch(helper, cover,
                ItemVoidingCoverConfigActions.createSetWorkingEnabledAction(true)),
                "valid enabled item voiding action was rejected");
        helper.assertTrue(cover.isWorkingEnabled() && cover.getSetWorkingEnabledCalls() == 2,
                "enabled action bypassed the cover setter or wrote the wrong state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ItemVoidingCoverConfigAction")
    public static void dispatcherKeepsItemHolderScopeAndRejectsFluidCover(GameTestHelper helper) {
        SyncActionData action = ItemVoidingCoverConfigActions.createSetWorkingEnabledAction(true);
        AdvancedItemVoidingCover advancedItemCover = createAdvancedItemCover();
        FluidVoidingCover fluidCover = createFluidCover();

        helper.assertTrue(dispatch(helper, advancedItemCover, action),
                "item voiding action rejected the advanced item cover that inherits its working toggle");
        helper.assertTrue(advancedItemCover.isWorkingEnabled(),
                "accepted item voiding action did not update the advanced item cover");
        helper.assertTrue(!dispatch(helper, fluidCover, action),
                "item voiding action accepted a fluid voiding cover");
        helper.assertTrue(!fluidCover.isWorkingEnabled(),
                "rejected item voiding action changed the fluid voiding cover");
        helper.assertTrue(!dispatch(helper, new Object(), action),
                "item voiding action accepted an unrelated holder");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ItemVoidingCoverConfigAction")
    public static void dispatcherRejectsMalformedBooleanPayloads(GameTestHelper helper) {
        TrackingItemVoidingCover cover = createTrackingItemCover();

        assertRejected(helper, cover, action(payload(new JsonPrimitive(1))), "numeric enabled state");
        assertRejected(helper, cover, action(payload(new JsonPrimitive("true"))), "string enabled state");
        assertRejected(helper, cover, action(payloadWithoutWorkingEnabled()), "missing enabled state");
        assertRejected(helper, cover, action(DataComponentMap.EMPTY), "missing field data");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ItemVoidingCoverConfigAction")
    public static void dispatcherAcceptsUnknownPayloadFields(GameTestHelper helper) {
        TrackingItemVoidingCover cover = createTrackingItemCover();

        helper.assertTrue(dispatch(helper, cover, action(payloadWithUnknownField())),
                "item voiding action rejected an unknown payload field");
        helper.assertTrue(cover.isWorkingEnabled() && cover.getSetWorkingEnabledCalls() == 1,
                "action with an unknown field did not execute through the cover setter");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ItemVoidingCoverConfigAction")
    public static void dispatcherRejectsSpectator(GameTestHelper helper) {
        TrackingItemVoidingCover cover = createTrackingItemCover();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SPECTATOR);
        boolean result;
        try {
            result = dispatch(player, cover, ItemVoidingCoverConfigActions.createSetWorkingEnabledAction(true));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!result, "item voiding action accepted a spectator");
        helper.assertTrue(!cover.isWorkingEnabled() && cover.getSetWorkingEnabledCalls() == 0,
                "spectator action invoked the item voiding cover setter");
        helper.succeed();
    }

    private static void assertFactoryAction(GameTestHelper helper, SyncActionData action, boolean expectedState,
                                            int expectedSequence, String description) {
        SyncFieldData fields = requireFields(action.payload(), description);
        helper.assertTrue(action.actionId().equals(ACTION_ID), description + " encoded the wrong action id");
        helper.assertTrue(action.sequence() == expectedSequence, description + " encoded the wrong sequence");
        helper.assertTrue(fields.fields().size() == 1, description + " encoded fields outside the protocol");
        JsonElement value = fields.get(WORKING_ENABLED_FIELD);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expectedState,
                description + " did not encode the expected JSON boolean");
    }

    private static void assertRejected(GameTestHelper helper, TrackingItemVoidingCover cover,
                                       SyncActionData action, String description) {
        helper.assertTrue(!dispatch(helper, cover, action), description + " payload was accepted");
        helper.assertTrue(!cover.isWorkingEnabled() && cover.getSetWorkingEnabledCalls() == 0,
                description + " payload invoked the item voiding cover setter");
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, SyncActionData action) {
        return dispatch(FakePlayerFactory.getMinecraft(helper.getLevel()), holder, action);
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        ItemVoidingCoverConfigActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, Direction.WEST,
                null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData action(DataComponentMap payload) {
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(JsonElement workingEnabled) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(WORKING_ENABLED_FIELD, workingEnabled)
                        .build())
                .build();
    }

    private static DataComponentMap payloadWithoutWorkingEnabled() {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(UNKNOWN_FIELD, new JsonPrimitive(true))
                        .build())
                .build();
    }

    private static DataComponentMap payloadWithUnknownField() {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(WORKING_ENABLED_FIELD, new JsonPrimitive(true))
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

    private static TrackingItemVoidingCover createTrackingItemCover() {
        BufferMachine machine = createBuffer();
        TrackingItemVoidingCover cover = new TrackingItemVoidingCover(GTCovers.ITEM_VOIDING,
                machine.getCoverContainer(), Direction.WEST);
        cover.resetSetWorkingEnabledCalls();
        return cover;
    }

    private static AdvancedItemVoidingCover createAdvancedItemCover() {
        BufferMachine machine = createBuffer();
        return new AdvancedItemVoidingCover(GTCovers.ITEM_VOIDING_ADVANCED, machine.getCoverContainer(),
                Direction.WEST);
    }

    private static FluidVoidingCover createFluidCover() {
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

    private static final class TrackingItemVoidingCover extends ItemVoidingCover {

        private int setWorkingEnabledCalls;

        private TrackingItemVoidingCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
            super(definition, coverHolder, attachedSide);
        }

        @Override
        public void setWorkingEnabled(boolean isWorkingAllowed) {
            setWorkingEnabledCalls++;
            super.setWorkingEnabled(isWorkingAllowed);
        }

        private int getSetWorkingEnabledCalls() {
            return setWorkingEnabledCalls;
        }

        private void resetSetWorkingEnabledCalls() {
            setWorkingEnabledCalls = 0;
        }
    }
}

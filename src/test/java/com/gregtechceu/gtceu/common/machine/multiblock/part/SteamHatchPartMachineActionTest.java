package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
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
public class SteamHatchPartMachineActionTest {

    private static final String BATCH = "SteamHatchPartMachineAction";
    private static final ResourceLocation ACTION_ID = GTCEu.id("click_steam_hatch_fluid_slot");
    private static final ResourceLocation SHIFT_FIELD = SyncFieldData.key("shift");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("other");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creatorEncodesBothShiftStates(GameTestHelper helper) {
        assertCreatedAction(helper, false, 0);
        assertCreatedAction(helper, true, 1);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherExecutesBothShiftStatesExactlyOnce(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        TestSteamHatchTarget target = new TestSteamHatchTarget();

        helper.assertTrue(dispatch(player, target,
                SteamHatchPartMachineActions.createClickSteamHatchFluidSlotAction(false)),
                "valid unshifted steam hatch action was rejected");
        helper.assertTrue(target.invocations == 1 && !target.lastShiftDown && target.lastPlayer == player,
                "unshifted steam hatch action invoked the wrong target state");

        helper.assertTrue(dispatch(player, target,
                SteamHatchPartMachineActions.createClickSteamHatchFluidSlotAction(true)),
                "valid shifted steam hatch action was rejected");
        helper.assertTrue(target.invocations == 2 && target.lastShiftDown && target.lastPlayer == player,
                "shifted steam hatch action did not execute exactly once");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsMalformedPayloadsWithoutInvokingTarget(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        TestSteamHatchTarget target = new TestSteamHatchTarget();

        helper.assertTrue(!dispatch(player, target, rawAction(DataComponentMap.EMPTY)),
                "steam hatch action without field data was accepted");
        helper.assertTrue(!dispatch(player, target, rawAction(payload(OTHER_FIELD, new JsonPrimitive(true)))),
                "steam hatch action without shift field was accepted");
        helper.assertTrue(!dispatch(player, target, rawAction(payload(SHIFT_FIELD, new JsonPrimitive(1)))),
                "steam hatch action with numeric shift field was accepted");
        helper.assertTrue(!dispatch(player, target, rawAction(payload(SHIFT_FIELD, new JsonPrimitive("true")))),
                "steam hatch action with string shift field was accepted");
        helper.assertTrue(target.invocations == 0, "rejected steam hatch payload invoked the target");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsWrongHolderAndSpectator(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SyncActionData action = SteamHatchPartMachineActions.createClickSteamHatchFluidSlotAction(true);
        TestSteamHatchTarget target = new TestSteamHatchTarget();

        helper.assertTrue(!dispatch(player, new Object(), action),
                "steam hatch action accepted an unrelated holder");

        player.setGameMode(GameType.SPECTATOR);
        boolean spectatorResult;
        try {
            spectatorResult = dispatch(player, target, action);
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!spectatorResult, "steam hatch action accepted a spectator");
        helper.assertTrue(target.invocations == 0, "rejected holder or spectator action invoked the target");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherAcceptsUnknownPayloadFields(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        TestSteamHatchTarget target = new TestSteamHatchTarget();
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(SHIFT_FIELD, new JsonPrimitive(false))
                        .put(OTHER_FIELD, new JsonPrimitive("ignored"))
                        .build())
                .build();

        helper.assertTrue(dispatch(player, target, rawAction(payload)),
                "steam hatch action rejected an unknown payload field");
        helper.assertTrue(target.invocations == 1 && !target.lastShiftDown,
                "action with an unknown field invoked the wrong target state");
        helper.succeed();
    }

    private static void assertCreatedAction(GameTestHelper helper, boolean shiftDown, int expectedSequence) {
        SyncActionData action = SteamHatchPartMachineActions.createClickSteamHatchFluidSlotAction(shiftDown);
        SyncFieldData fields = requireFields(action.payload());
        JsonElement shift = fields.get(SHIFT_FIELD);

        helper.assertTrue(action.actionId().equals(ACTION_ID), "steam hatch creator used the wrong action id");
        helper.assertTrue(action.sequence() == expectedSequence, "steam hatch creator used the wrong sequence");
        helper.assertTrue(fields.fields().size() == 1, "steam hatch creator encoded fields outside its protocol");
        helper.assertTrue(shift instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == shiftDown,
                "steam hatch creator encoded the wrong shift state");
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        SteamHatchPartMachineActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData rawAction(DataComponentMap payload) {
        return new SyncActionData(ACTION_ID, 0, payload);
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static SyncFieldData requireFields(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Steam hatch action creator omitted field data.");
        }
        return fields;
    }

    private static final class TestSteamHatchTarget implements SteamHatchFluidSlotActionTarget {

        private int invocations;
        private boolean lastShiftDown;
        private ServerPlayer lastPlayer;

        @Override
        public void clickSteamHatchFluidSlot(ServerPlayer player, boolean shiftDown) {
            invocations++;
            lastPlayer = player;
            lastShiftDown = shiftDown;
        }
    }
}

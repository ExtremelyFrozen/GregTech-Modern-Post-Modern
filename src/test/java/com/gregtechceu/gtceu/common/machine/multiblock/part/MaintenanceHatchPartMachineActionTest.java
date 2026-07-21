package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
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
public class MaintenanceHatchPartMachineActionTest {

    private static final String BATCH = "MaintenanceHatchPartMachineAction";
    private static final ResourceLocation DURATION_ACTION_ID = GTCEu
            .id("adjust_maintenance_duration_multiplier");
    private static final ResourceLocation FIX_ACTION_ID = GTCEu.id("fix_maintenance_problems");
    private static final ResourceLocation DIRECTION_FIELD = SyncFieldData.key("direction");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("other");
    private static final float EPSILON = 0.0001f;

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creatorsPreserveBothActionProtocols(GameTestHelper helper) {
        assertDurationAction(helper, -1, 0);
        assertDurationAction(helper, 1, 1);

        SyncActionData fixAction = MaintenanceHatchPartMachineActions.createFixMaintenanceProblemsAction();
        helper.assertTrue(fixAction.actionId().equals(FIX_ACTION_ID),
                "maintenance fix creator used the wrong action id");
        helper.assertTrue(fixAction.sequence() == 0,
                "maintenance fix creator changed the zero sequence");
        helper.assertTrue(fixAction.payload().isEmpty(),
                "maintenance fix creator encoded a non-empty payload");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRoutesBothActionsThroughTheNarrowTarget(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestMaintenanceTarget target = new TestMaintenanceTarget(true);

        boolean durationResult = dispatch(player, target,
                MaintenanceHatchPartMachineActions.createAdjustMaintenanceDurationAction(-1));
        boolean fixResult = dispatch(player, target,
                MaintenanceHatchPartMachineActions.createFixMaintenanceProblemsAction());

        helper.assertTrue(durationResult, "valid maintenance duration action was rejected");
        helper.assertTrue(target.durationInvocations == 1 && target.lastDirection == -1,
                "duration action invoked the target with the wrong direction or count");
        helper.assertTrue(fixResult, "valid maintenance fix action was rejected");
        helper.assertTrue(target.fixInvocations == 1 && target.lastPlayer == player,
                "fix action invoked the target with the wrong player or count");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void configurableDurationActionAdjustsAndDirectTargetClampsBothBounds(GameTestHelper helper) {
        MaintenanceHatchPartMachine hatch = createMaintenanceHatch(GTMachines.CONFIGURABLE_MAINTENANCE_HATCH);
        ServerPlayer player = preparedPlayer(helper);

        boolean decrementResult = dispatch(player, hatch,
                MaintenanceHatchPartMachineActions.createAdjustMaintenanceDurationAction(-1));
        helper.assertTrue(decrementResult, "configurable maintenance hatch rejected a decrement action");
        assertFloat(helper, hatch.getDurationMultiplier(), 0.99f,
                "duration decrement did not apply one 0.01 step");

        boolean incrementResult = dispatch(player, hatch,
                MaintenanceHatchPartMachineActions.createAdjustMaintenanceDurationAction(1));
        helper.assertTrue(incrementResult, "configurable maintenance hatch rejected an increment action");
        assertFloat(helper, hatch.getDurationMultiplier(), 1f,
                "duration increment did not restore the original multiplier");

        for (int step = 0; step < 20; step++) {
            hatch.adjustMaintenanceDuration(-1);
        }
        assertFloat(helper, hatch.getDurationMultiplier(), 0.9f,
                "direct duration target crossed or missed the lower clamp");

        for (int step = 0; step < 40; step++) {
            hatch.adjustMaintenanceDuration(1);
        }
        assertFloat(helper, hatch.getDurationMultiplier(), 1.1f,
                "direct duration target crossed or missed the upper clamp");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void durationHandlerRejectsNonConfigurableHolderWithoutMutation(GameTestHelper helper) {
        MaintenanceHatchPartMachine hatch = createMaintenanceHatch(GTMachines.MAINTENANCE_HATCH);
        ServerPlayer player = preparedPlayer(helper);

        boolean result = dispatch(player, hatch,
                MaintenanceHatchPartMachineActions.createAdjustMaintenanceDurationAction(1));

        helper.assertTrue(!result, "duration action accepted a non-configurable maintenance hatch");
        assertFloat(helper, hatch.getDurationMultiplier(), 1f,
                "rejected duration action changed a non-configurable hatch");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void durationHandlerRejectsMalformedPayloadsWithoutInvokingTarget(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestMaintenanceTarget target = new TestMaintenanceTarget(true);

        helper.assertTrue(!dispatch(player, target, rawDurationAction(DataComponentMap.EMPTY)),
                "duration action without field data was accepted");
        helper.assertTrue(!dispatch(player, target, rawDurationAction(payloadWithOnly(
                OTHER_FIELD, new JsonPrimitive(1)))),
                "duration action without a direction was accepted");
        helper.assertTrue(!dispatch(player, target, rawDurationAction(durationPayload(
                new JsonPrimitive("1")))),
                "duration action with a string direction was accepted");
        helper.assertTrue(!dispatch(player, target, rawDurationAction(durationPayload(
                new JsonPrimitive(0)))),
                "duration action with a zero direction was accepted");
        helper.assertTrue(!dispatch(player, target, rawDurationAction(durationPayload(
                new JsonPrimitive(2)))),
                "duration action above the positive boundary was accepted");
        helper.assertTrue(!dispatch(player, target, rawDurationAction(durationPayload(
                new JsonPrimitive(-2)))),
                "duration action below the negative boundary was accepted");
        helper.assertTrue(target.durationInvocations == 0,
                "rejected duration payload invoked the target");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void durationHandlerAcceptsUnknownFieldsWithoutChangingDirection(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestMaintenanceTarget target = new TestMaintenanceTarget(true);
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(DIRECTION_FIELD, new JsonPrimitive(1))
                        .put(OTHER_FIELD, new JsonPrimitive("ignored"))
                        .build())
                .build();

        boolean result = dispatch(player, target, rawDurationAction(payload));

        helper.assertTrue(result, "duration action rejected an unknown payload field");
        helper.assertTrue(target.durationInvocations == 1 && target.lastDirection == 1,
                "unknown payload field changed the validated direction");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fixHandlerRequiresAnEmptyPayload(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestMaintenanceTarget target = new TestMaintenanceTarget(false);
        DataComponentMap nonEmptyPayload = payloadWithOnly(OTHER_FIELD, new JsonPrimitive(true));

        boolean result = dispatch(player, target, rawFixAction(nonEmptyPayload));

        helper.assertTrue(!result, "maintenance fix action accepted a non-empty payload");
        helper.assertTrue(target.fixInvocations == 0,
                "rejected maintenance fix payload invoked the target");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void bothHandlersRejectWrongHolderAndSpectatorWithoutMutation(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestMaintenanceTarget target = new TestMaintenanceTarget(true);
        SyncActionData durationAction = MaintenanceHatchPartMachineActions
                .createAdjustMaintenanceDurationAction(-1);
        SyncActionData fixAction = MaintenanceHatchPartMachineActions.createFixMaintenanceProblemsAction();

        helper.assertTrue(!dispatch(player, new Object(), durationAction),
                "duration action accepted an unrelated holder");
        helper.assertTrue(!dispatch(player, new Object(), fixAction),
                "fix action accepted an unrelated holder");

        player.setGameMode(GameType.SPECTATOR);
        boolean spectatorDuration;
        boolean spectatorFix;
        try {
            spectatorDuration = dispatch(player, target, durationAction);
            spectatorFix = dispatch(player, target, fixAction);
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!spectatorDuration && !spectatorFix,
                "maintenance action accepted a spectator");
        helper.assertTrue(target.durationInvocations == 0 && target.fixInvocations == 0,
                "rejected holder or spectator action mutated the target");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creativeFixRepairsEverythingWithoutTape(GameTestHelper helper) {
        MaintenanceHatchPartMachine hatch = createBrokenMaintenanceHatch();
        ServerPlayer player = preparedPlayer(helper);
        player.setGameMode(GameType.CREATIVE);

        boolean result;
        try {
            result = dispatch(player, hatch,
                    MaintenanceHatchPartMachineActions.createFixMaintenanceProblemsAction());
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(result, "creative maintenance fix action was rejected");
        helper.assertTrue(hatch.getMaintenanceProblems() == 0b111111,
                "creative maintenance fix did not repair every problem");
        helper.assertTrue(!hatch.isTaped(),
                "creative maintenance fix incorrectly marked the hatch as taped");
        helper.assertTrue(player.getInventory().isEmpty() && player.containerMenu.getCarried().isEmpty(),
                "creative maintenance fix unexpectedly created or consumed an item");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void ductTapeFixConsumesOneBeforeTryingTools(GameTestHelper helper) {
        MaintenanceHatchPartMachine hatch = createBrokenMaintenanceHatch();
        ServerPlayer player = preparedPlayer(helper);
        player.getInventory().setItem(0, GTItems.DUCT_TAPE.asStack(2));
        ItemStack carriedWrench = ToolHelper.get(GTToolType.WRENCH, GTMaterials.Steel);
        helper.assertTrue(!carriedWrench.isEmpty(), "steel wrench was unavailable for maintenance testing");
        player.containerMenu.setCarried(carriedWrench);
        int wrenchDamage = carriedWrench.getDamageValue();

        boolean result = dispatch(player, hatch,
                MaintenanceHatchPartMachineActions.createFixMaintenanceProblemsAction());

        helper.assertTrue(result, "duct-tape maintenance fix action was rejected");
        helper.assertTrue(hatch.getMaintenanceProblems() == 0b111111,
                "duct tape did not repair every maintenance problem");
        helper.assertTrue(hatch.isTaped(), "duct-tape repair did not mark the hatch as taped");
        helper.assertTrue(player.getInventory().countItem(GTItems.DUCT_TAPE.get()) == 1,
                "duct-tape repair did not consume exactly one tape");
        helper.assertTrue(player.containerMenu.getCarried().getDamageValue() == wrenchDamage,
                "duct-tape repair reached and damaged the lower-priority tool");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void cursorToolFixRepairsItsProblemAndConsumesDurability(GameTestHelper helper) {
        MaintenanceHatchPartMachine hatch = createBrokenMaintenanceHatch();
        ServerPlayer player = preparedPlayer(helper);
        ItemStack wrench = ToolHelper.get(GTToolType.WRENCH, GTMaterials.Steel);
        helper.assertTrue(!wrench.isEmpty(), "steel wrench was unavailable for maintenance testing");
        player.containerMenu.setCarried(wrench);
        int initialDamage = wrench.getDamageValue();

        boolean result = dispatch(player, hatch,
                MaintenanceHatchPartMachineActions.createFixMaintenanceProblemsAction());

        ItemStack carried = player.containerMenu.getCarried();
        helper.assertTrue(result, "tool maintenance fix action was rejected");
        helper.assertTrue(hatch.getMaintenanceProblems() == 1,
                "wrench repair changed problems outside the wrench bit");
        helper.assertTrue(!hatch.isTaped(), "tool repair incorrectly marked the hatch as taped");
        helper.assertTrue(ToolHelper.is(carried, GTToolType.WRENCH),
                "tool repair removed or replaced the cursor wrench");
        helper.assertTrue(carried.getDamageValue() == initialDamage + 1,
                "tool repair did not consume exactly one durability");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void resourceShortageKeepsStateDespiteAcceptedFixAction(GameTestHelper helper) {
        MaintenanceHatchPartMachine hatch = createBrokenMaintenanceHatch();
        ServerPlayer player = preparedPlayer(helper);

        hatch.fixMaintenanceProblemsForAction(player);
        helper.assertTrue(hatch.getMaintenanceProblems() == 0 && !hatch.isTaped(),
                "direct maintenance target changed state without repair resources");

        boolean result = dispatch(player, hatch,
                MaintenanceHatchPartMachineActions.createFixMaintenanceProblemsAction());

        helper.assertTrue(result,
                "resource shortage incorrectly converted a valid fix action into a protocol rejection");
        helper.assertTrue(hatch.getMaintenanceProblems() == 0 && !hatch.isTaped(),
                "accepted resource-shortage repair changed maintenance state");
        helper.assertTrue(player.getInventory().isEmpty() && player.containerMenu.getCarried().isEmpty(),
                "resource-shortage repair changed the player's empty inventory");
        helper.succeed();
    }

    private static void assertDurationAction(GameTestHelper helper, int direction, int expectedSequence) {
        SyncActionData action = MaintenanceHatchPartMachineActions.createAdjustMaintenanceDurationAction(direction);
        SyncFieldData fields = requireFields(action.payload());
        JsonElement encodedDirection = fields.get(DIRECTION_FIELD);

        helper.assertTrue(action.actionId().equals(DURATION_ACTION_ID),
                "maintenance duration creator used the wrong action id");
        helper.assertTrue(action.sequence() == expectedSequence,
                "maintenance duration creator used the wrong sequence");
        helper.assertTrue(fields.fields().size() == 1,
                "maintenance duration creator encoded fields outside its protocol");
        helper.assertTrue(encodedDirection instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsInt() == direction,
                "maintenance duration creator encoded the wrong direction");
    }

    private static void assertFloat(GameTestHelper helper, float actual, float expected, String message) {
        helper.assertTrue(Math.abs(actual - expected) < EPSILON, message);
    }

    private static ServerPlayer preparedPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();
        player.containerMenu.setCarried(ItemStack.EMPTY);
        return player;
    }

    private static MaintenanceHatchPartMachine createBrokenMaintenanceHatch() {
        MaintenanceHatchPartMachine hatch = createMaintenanceHatch(GTMachines.MAINTENANCE_HATCH);
        hatch.setMaintenanceProblems((byte) 0);
        hatch.setTaped(false);
        return hatch;
    }

    private static MaintenanceHatchPartMachine createMaintenanceHatch(MachineDefinition definition) {
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (!(machine instanceof MaintenanceHatchPartMachine hatch)) {
            throw new IllegalStateException("Maintenance hatch definition created the wrong machine type.");
        }
        return hatch;
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        MaintenanceHatchPartMachineActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData rawDurationAction(DataComponentMap payload) {
        return new SyncActionData(DURATION_ACTION_ID, 0, payload);
    }

    private static SyncActionData rawFixAction(DataComponentMap payload) {
        return new SyncActionData(FIX_ACTION_ID, 0, payload);
    }

    private static DataComponentMap durationPayload(JsonElement direction) {
        return payloadWithOnly(DIRECTION_FIELD, direction);
    }

    private static DataComponentMap payloadWithOnly(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static SyncFieldData requireFields(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Maintenance duration action creator omitted field data.");
        }
        return fields;
    }

    private static final class TestMaintenanceTarget implements MaintenanceHatchActionTarget {

        private final boolean configurable;
        private int durationInvocations;
        private int lastDirection;
        private int fixInvocations;
        private ServerPlayer lastPlayer;

        private TestMaintenanceTarget(boolean configurable) {
            this.configurable = configurable;
        }

        @Override
        public boolean supportsMaintenanceDurationAdjustment() {
            return configurable;
        }

        @Override
        public void adjustMaintenanceDuration(int direction) {
            durationInvocations++;
            lastDirection = direction;
        }

        @Override
        public void fixMaintenanceProblemsForAction(@NotNull ServerPlayer player) {
            fixInvocations++;
            lastPlayer = player;
        }
    }
}

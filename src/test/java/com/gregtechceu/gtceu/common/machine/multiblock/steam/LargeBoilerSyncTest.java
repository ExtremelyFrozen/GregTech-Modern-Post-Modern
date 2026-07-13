package com.gregtechceu.gtceu.common.machine.multiblock.steam;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.steam.LargeBoilerMachine.LargeBoilerRecipeLogic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
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
public class LargeBoilerSyncTest {

    private static final String BATCH = "LargeBoilerSync";
    private static final ResourceLocation ADJUST_THROTTLE_ACTION = GTCEu.id("adjust_large_boiler_throttle");
    private static final ResourceLocation DIRECTION_FIELD = SyncFieldData.key("direction");
    private static final ResourceLocation CURRENT_TEMPERATURE_FIELD = SyncFieldData.key("currentTemperature");
    private static final ResourceLocation THROTTLE_FIELD = SyncFieldData.key("throttle");
    private static final ResourceLocation STEAM_GENERATED_FIELD = SyncFieldData.key("steamGenerated");
    private static final ResourceLocation CURRENT_THROTTLE_FIELD = SyncFieldData.key("currentThrottle");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fieldsSyncPersistAndSkipNoOpWrites(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        LargeBoilerMachine server = createMachine();
        LargeBoilerMachine client = createMachine();
        LargeBoilerRecipeLogic serverLogic = server.getRecipeLogic();
        LargeBoilerRecipeLogic clientLogic = client.getRecipeLogic();

        server.setCurrentTemperature(450);
        server.setSteamGenerated(1_200);
        server.setThrottle(80);
        serverLogic.setCurrentThrottle(75);

        DataComponentMap machineFull = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        DataComponentMap logicFull = serverLogic.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        SyncFieldData machineSaved = server.getSyncDataHolder().serializeToFieldData(registries, false, false);
        SyncFieldData logicSaved = serverLogic.getSyncDataHolder().serializeToFieldData(registries, false, false);

        assertIntField(helper, machineFull, CURRENT_TEMPERATURE_FIELD, 450, "large boiler full sync");
        assertIntField(helper, machineFull, THROTTLE_FIELD, 80, "large boiler full sync");
        assertIntField(helper, machineFull, STEAM_GENERATED_FIELD, 1_200, "large boiler full sync");
        assertIntField(helper, logicFull, CURRENT_THROTTLE_FIELD, 75, "large boiler logic full sync");
        assertIntField(helper, machineSaved, CURRENT_TEMPERATURE_FIELD, 450, "large boiler saved state");
        assertIntField(helper, machineSaved, THROTTLE_FIELD, 80, "large boiler saved state");
        helper.assertTrue(machineSaved.get(STEAM_GENERATED_FIELD) == null,
                "derived steam output was unexpectedly persisted");
        assertIntField(helper, logicSaved, CURRENT_THROTTLE_FIELD, 75, "large boiler logic saved state");

        client.getSyncDataHolder().applyClientNetworkUpdate(registries, machineFull);
        clientLogic.getSyncDataHolder().applyClientNetworkUpdate(registries, logicFull);
        helper.assertTrue(client.getCurrentTemperature() == 450 && client.getThrottle() == 80,
                "large boiler client did not apply full machine state");
        helper.assertTrue(clientLogic.getCurrentThrottle() == 75,
                "large boiler client did not apply full recipe logic state");
        assertIntField(helper, client.getSyncDataHolder().serializeFullClientSyncData(registries),
                STEAM_GENERATED_FIELD, 1_200, "large boiler applied client state");

        server.setCurrentTemperature(451);
        server.setSteamGenerated(1_250);
        server.setThrottle(70);

        SyncFieldData machineDelta = requireFields(
                server.getSyncDataHolder().serializeToComponents(registries, true, false));
        SyncFieldData logicDelta = requireFields(
                serverLogic.getSyncDataHolder().serializeToComponents(registries, true, false));
        helper.assertTrue(machineDelta.fields().size() == 3,
                "large boiler delta included fields outside the changed machine state");
        assertIntField(helper, machineDelta, CURRENT_TEMPERATURE_FIELD, 451, "large boiler delta");
        assertIntField(helper, machineDelta, THROTTLE_FIELD, 70, "large boiler delta");
        assertIntField(helper, machineDelta, STEAM_GENERATED_FIELD, 1_250, "large boiler delta");
        helper.assertTrue(logicDelta.fields().size() == 1,
                "large boiler logic delta included fields outside current throttle");
        assertIntField(helper, logicDelta, CURRENT_THROTTLE_FIELD, 70, "large boiler logic delta");

        server.setCurrentTemperature(451);
        server.setSteamGenerated(1_250);
        server.setThrottle(70);
        serverLogic.setCurrentThrottle(70);

        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "unchanged large boiler fields produced a client delta");
        helper.assertTrue(serverLogic.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "unchanged large boiler logic throttle produced a client delta");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void serverOwnedFieldsRejectClientWrites(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        LargeBoilerMachine machine = createMachine();
        LargeBoilerRecipeLogic logic = machine.getRecipeLogic();
        machine.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        logic.getSyncDataHolder().serializeFullClientSyncComponents(registries);

        ServerFieldUpdateResult machineResult = machine.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, machinePayload(450, 80, 1_200));
        ServerFieldUpdateResult logicResult = logic.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, payload(CURRENT_THROTTLE_FIELD, new JsonPrimitive(80)));

        helper.assertTrue(!machineResult.getAccepted(),
                "large boiler accepted client writes for server-owned machine fields");
        helper.assertTrue(!logicResult.getAccepted(),
                "large boiler recipe logic accepted a client write for current throttle");
        helper.assertTrue(machine.getCurrentTemperature() == 0 && machine.getThrottle() == 100,
                "rejected client fields changed large boiler machine state");
        helper.assertTrue(logic.getCurrentThrottle() == 100,
                "rejected client field changed large boiler recipe logic state");
        assertIntField(helper, machine.getSyncDataHolder().serializeFullClientSyncData(registries),
                STEAM_GENERATED_FIELD, 0, "rejected large boiler client update");
        helper.assertTrue(machine.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "rejected large boiler client update produced an acknowledgement");
        helper.assertTrue(logic.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "rejected large boiler logic update produced an acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void throttleActionCreatorEncodesDirectionSequenceAndPayload(GameTestHelper helper) {
        assertCreatedThrottleAction(helper, -1, 0);
        assertCreatedThrottleAction(helper, 1, 1);

        boolean rejectedInvalidDirection = false;
        try {
            LargeBoilerMachineActions.createAdjustLargeBoilerThrottleAction(0);
        } catch (IllegalArgumentException exception) {
            rejectedInvalidDirection = true;
        }
        helper.assertTrue(rejectedInvalidDirection,
                "large boiler throttle creator accepted a direction outside -1 and 1");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void throttleTargetAppliesBothDirectionsAndClampsBoundaries(GameTestHelper helper) {
        LargeBoilerMachine machine = createMachine();

        machine.setThrottle(30);
        machine.adjustLargeBoilerThrottle(-1);
        helper.assertTrue(machine.getThrottle() == 25 && machine.getRecipeLogic().getCurrentThrottle() == 25,
                "large boiler throttle target did not apply its lower-bound decrement");
        machine.adjustLargeBoilerThrottle(-1);
        helper.assertTrue(machine.getThrottle() == 25 && machine.getRecipeLogic().getCurrentThrottle() == 25,
                "large boiler throttle target crossed its lower bound");

        machine.setThrottle(95);
        machine.adjustLargeBoilerThrottle(1);
        helper.assertTrue(machine.getThrottle() == 100 && machine.getRecipeLogic().getCurrentThrottle() == 100,
                "large boiler throttle target did not apply its upper-bound increment");
        machine.adjustLargeBoilerThrottle(1);
        helper.assertTrue(machine.getThrottle() == 100 && machine.getRecipeLogic().getCurrentThrottle() == 100,
                "large boiler throttle target crossed its upper bound");

        boolean rejectedInvalidDirection = false;
        try {
            machine.adjustLargeBoilerThrottle(0);
        } catch (IllegalArgumentException exception) {
            rejectedInvalidDirection = true;
        }
        helper.assertTrue(rejectedInvalidDirection && machine.getThrottle() == 100 &&
                machine.getRecipeLogic().getCurrentThrottle() == 100,
                "large boiler throttle target accepted or applied an invalid direction");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void throttleActionClampsExecutesOnceAndRetimesActiveFuel(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        LargeBoilerMachine machine = createMachine();
        LargeBoilerRecipeLogic logic = machine.getRecipeLogic();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        GTRecipe recipe = GTRecipeTypes.LARGE_BOILER_RECIPES
                .recipeBuilder(GTCEu.id("large_boiler_sync_fuel"))
                .duration(200)
                .build();
        logic.setupRecipe(recipe);
        logic.setProgress(40);
        helper.assertTrue(logic.getLastRecipe() != null && logic.getMaxProgress() == 200,
                "large boiler test fuel did not start at full throttle");
        machine.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        logic.getSyncDataHolder().serializeFullClientSyncComponents(registries);

        boolean decremented = dispatchThrottle(player, machine, -1);

        helper.assertTrue(decremented, "registered large boiler throttle action rejected decrement");
        helper.assertTrue(machine.getThrottle() == 95 && logic.getCurrentThrottle() == 95,
                "large boiler throttle action did not execute exactly one five-percent decrement");
        helper.assertTrue(logic.getMaxProgress() == 211 && logic.getProgress() == 42,
                "large boiler throttle action did not preserve fuel burn-time retiming");
        assertOnlyIntField(helper,
                requireFields(machine.getSyncDataHolder().serializeToComponents(registries, true, false)),
                THROTTLE_FIELD, 95, "large boiler throttle action delta");
        assertIntField(helper,
                requireFields(logic.getSyncDataHolder().serializeToComponents(registries, true, false)),
                CURRENT_THROTTLE_FIELD, 95, "large boiler logic throttle action delta");

        machine.setThrottle(25);
        machine.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        logic.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        helper.assertTrue(dispatchThrottle(player, machine, -1),
                "large boiler throttle action rejected a lower-bound decrement");
        helper.assertTrue(machine.getThrottle() == 25 && logic.getCurrentThrottle() == 25,
                "large boiler throttle action crossed its lower bound");
        assertNoDelta(helper, machine, logic, registries, "lower-bound large boiler throttle action");

        machine.setThrottle(100);
        machine.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        logic.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        helper.assertTrue(dispatchThrottle(player, machine, 1),
                "large boiler throttle action rejected an upper-bound increment");
        helper.assertTrue(machine.getThrottle() == 100 && logic.getCurrentThrottle() == 100,
                "large boiler throttle action crossed its upper bound");
        assertNoDelta(helper, machine, logic, registries, "upper-bound large boiler throttle action");

        helper.assertTrue(!dispatchThrottle(player, machine, 0) && !dispatchThrottle(player, machine, 2),
                "large boiler throttle action accepted an out-of-range direction");
        helper.assertTrue(machine.getThrottle() == 100 && logic.getCurrentThrottle() == 100,
                "rejected large boiler throttle action changed throttle state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void throttleActionRejectsInvalidHolderPayloadAndSpectatorWithoutMutation(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        LargeBoilerRecipeLogic invalidHolder = createMachine().getRecipeLogic();
        int invalidHolderThrottle = invalidHolder.getCurrentThrottle();
        helper.assertTrue(!dispatch(player, invalidHolder,
                LargeBoilerMachineActions.createAdjustLargeBoilerThrottleAction(-1)),
                "large boiler throttle action accepted a recipe-logic holder");
        helper.assertTrue(invalidHolder.getCurrentThrottle() == invalidHolderThrottle,
                "rejected large boiler holder action changed recipe-logic state");

        LargeBoilerMachine machine = createMachine();
        machine.setThrottle(80);
        assertRejectedThrottle(helper, player, machine,
                new SyncActionData(ADJUST_THROTTLE_ACTION, 0, DataComponentMap.EMPTY),
                "large boiler throttle action without field data");
        assertRejectedThrottle(helper, player, machine,
                new SyncActionData(ADJUST_THROTTLE_ACTION, 0,
                        payload(DIRECTION_FIELD, new JsonPrimitive(true))),
                "large boiler throttle action with a boolean direction");
        assertRejectedThrottle(helper, player, machine,
                new SyncActionData(ADJUST_THROTTLE_ACTION, 0,
                        payload(DIRECTION_FIELD, new JsonPrimitive(0))),
                "large boiler throttle action with an out-of-range direction");

        player.setGameMode(GameType.SPECTATOR);
        boolean spectatorAccepted;
        try {
            spectatorAccepted = dispatch(player, machine,
                    LargeBoilerMachineActions.createAdjustLargeBoilerThrottleAction(1));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }
        helper.assertTrue(!spectatorAccepted,
                "large boiler throttle action accepted a spectator");
        helper.assertTrue(machine.getThrottle() == 80 && machine.getRecipeLogic().getCurrentThrottle() == 80,
                "rejected spectator throttle action changed large boiler state");
        helper.succeed();
    }

    private static LargeBoilerMachine createMachine() {
        var definition = GTMultiMachines.LARGE_BOILER_BRONZE;
        return new LargeBoilerMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()), 500, 1);
    }

    private static boolean dispatchThrottle(ServerPlayer player, LargeBoilerMachine machine, int direction) {
        SyncActionData action = new SyncActionData(ADJUST_THROTTLE_ACTION, direction > 0 ? 1 : 0,
                payload(DIRECTION_FIELD, new JsonPrimitive(direction)));
        return dispatch(player, machine, action);
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        LargeBoilerMachineActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO,
                null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static void assertCreatedThrottleAction(GameTestHelper helper, int direction, int sequence) {
        SyncActionData action = LargeBoilerMachineActions.createAdjustLargeBoilerThrottleAction(direction);
        SyncFieldData fields = requireFields(action.payload());

        helper.assertTrue(action.actionId().equals(ADJUST_THROTTLE_ACTION),
                "large boiler throttle creator encoded the wrong action id");
        helper.assertTrue(action.sequence() == sequence,
                "large boiler throttle creator encoded the wrong sequence");
        assertOnlyIntField(helper, fields, DIRECTION_FIELD, direction,
                "large boiler throttle creator payload");
    }

    private static void assertRejectedThrottle(GameTestHelper helper, ServerPlayer player,
                                               LargeBoilerMachine machine, SyncActionData action,
                                               String description) {
        int throttle = machine.getThrottle();
        int currentThrottle = machine.getRecipeLogic().getCurrentThrottle();
        int progress = machine.getRecipeLogic().getProgress();
        int maxProgress = machine.getRecipeLogic().getMaxProgress();

        helper.assertTrue(!dispatch(player, machine, action), description + " was accepted");
        helper.assertTrue(machine.getThrottle() == throttle &&
                machine.getRecipeLogic().getCurrentThrottle() == currentThrottle &&
                machine.getRecipeLogic().getProgress() == progress &&
                machine.getRecipeLogic().getMaxProgress() == maxProgress,
                description + " changed large boiler state");
    }

    private static DataComponentMap machinePayload(int currentTemperature, int throttle, int steamGenerated) {
        return payload(SyncFieldData.builder()
                .put(CURRENT_TEMPERATURE_FIELD, new JsonPrimitive(currentTemperature))
                .put(THROTTLE_FIELD, new JsonPrimitive(throttle))
                .put(STEAM_GENERATED_FIELD, new JsonPrimitive(steamGenerated))
                .build());
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return payload(SyncFieldData.builder().put(field, value).build());
    }

    private static DataComponentMap payload(SyncFieldData fields) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
                .build();
    }

    private static SyncFieldData requireFields(DataComponentMap components) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Large boiler update omitted sync field data.");
        }
        return fields;
    }

    private static void assertNoDelta(GameTestHelper helper, LargeBoilerMachine machine,
                                      LargeBoilerRecipeLogic logic, RegistryAccess registries,
                                      String description) {
        helper.assertTrue(machine.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                description + " produced a machine delta");
        helper.assertTrue(logic.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                description + " produced a recipe logic delta");
    }

    private static void assertOnlyIntField(GameTestHelper helper, SyncFieldData fields,
                                           ResourceLocation field, int expected, String description) {
        helper.assertTrue(fields.fields().size() == 1,
                description + " included fields outside " + field.getPath());
        assertIntField(helper, fields, field, expected, description);
    }

    private static void assertIntField(GameTestHelper helper, DataComponentMap components,
                                       ResourceLocation field, int expected, String description) {
        assertIntField(helper, requireFields(components), field, expected, description);
    }

    private static void assertIntField(GameTestHelper helper, SyncFieldData fields,
                                       ResourceLocation field, int expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsInt() == expected,
                description + " did not contain expected " + field.getPath());
    }
}

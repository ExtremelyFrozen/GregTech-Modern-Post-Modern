package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.utils.GTUtil;

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

import java.math.BigInteger;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CreativeEnergyContainerMachineActionTest {

    private static final String BATCH = "CreativeEnergyContainerMachineAction";
    private static final ResourceLocation SET_VOLTAGE_ACTION = GTCEu.id("set_creative_energy_voltage");
    private static final ResourceLocation SET_TIER_ACTION = GTCEu.id("set_creative_energy_tier");
    private static final ResourceLocation VOLTAGE_FIELD = SyncFieldData.key("voltage");
    private static final ResourceLocation TIER_FIELD = SyncFieldData.key("setTier");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void voltageCreatorPreservesBoundariesPayloadAndSequence(GameTestHelper helper) {
        SyncActionData zero = CreativeEnergyContainerMachineActions.createSetVoltageAction(0L);
        SyncActionData maximum = CreativeEnergyContainerMachineActions.createSetVoltageAction(Long.MAX_VALUE);

        assertLongAction(helper, zero, SET_VOLTAGE_ACTION, VOLTAGE_FIELD, 0L, Long.hashCode(0L),
                "zero voltage action");
        assertLongAction(helper, maximum, SET_VOLTAGE_ACTION, VOLTAGE_FIELD, Long.MAX_VALUE,
                Long.hashCode(Long.MAX_VALUE), "maximum voltage action");
        helper.assertTrue(rejectsIllegalArgument(
                () -> CreativeEnergyContainerMachineActions.createSetVoltageAction(-1L)),
                "voltage creator accepted a negative value");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void tierCreatorPreservesBoundariesPayloadAndSequence(GameTestHelper helper) {
        int highestTier = GTValues.VNF.length - 1;
        SyncActionData zero = CreativeEnergyContainerMachineActions.createSetTierAction(0);
        SyncActionData maximum = CreativeEnergyContainerMachineActions.createSetTierAction(highestTier);

        assertIntegerAction(helper, zero, SET_TIER_ACTION, TIER_FIELD, 0, 0, "zero tier action");
        assertIntegerAction(helper, maximum, SET_TIER_ACTION, TIER_FIELD, highestTier, highestTier,
                "maximum tier action");
        helper.assertTrue(rejectsIllegalArgument(
                () -> CreativeEnergyContainerMachineActions.createSetTierAction(-1)),
                "tier creator accepted a negative index");
        helper.assertTrue(rejectsIllegalArgument(
                () -> CreativeEnergyContainerMachineActions.createSetTierAction(GTValues.VNF.length)),
                "tier creator accepted an index beyond the tier array");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void targetAppliesCoupledValuesAndMarksFieldsDirty(GameTestHelper helper) {
        CreativeEnergyContainerMachine machine = createMachine();
        CreativeEnergyActionTarget target = machine;
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.getSyncDataHolder().serializeFullClientSyncData(registries);

        target.setCreativeEnergyVoltage(Long.MAX_VALUE);

        SyncFieldData voltageChanges = machine.getSyncDataHolder()
                .serializeToFieldData(registries, true, false);
        assertCoupledFields(helper, voltageChanges, Long.MAX_VALUE, GTUtil.getTierByVoltage(Long.MAX_VALUE),
                "direct voltage target");

        int highestTier = GTValues.VNF.length - 1;
        target.setCreativeEnergyTier(highestTier);

        SyncFieldData tierChanges = machine.getSyncDataHolder()
                .serializeToFieldData(registries, true, false);
        assertCoupledFields(helper, tierChanges, GTValues.VEX[highestTier], highestTier,
                "direct tier target");
        helper.assertTrue(rejectsIllegalArgument(() -> target.setCreativeEnergyVoltage(-1L)),
                "direct voltage target accepted a negative value");
        helper.assertTrue(rejectsIllegalArgument(() -> target.setCreativeEnergyTier(-1)),
                "direct tier target accepted a negative index");
        helper.assertTrue(rejectsIllegalArgument(() -> target.setCreativeEnergyTier(GTValues.VNF.length)),
                "direct tier target accepted an out-of-range index");
        helper.assertTrue(machine.getSyncDataHolder().serializeToFieldData(registries, true, false).isEmpty(),
                "rejected target inputs marked client fields dirty");
        assertState(helper, machine, registries, GTValues.VEX[highestTier], highestTier,
                "state after direct target calls");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherAppliesVoltageAndTierActions(GameTestHelper helper) {
        CreativeEnergyContainerMachine machine = createMachine();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.getSyncDataHolder().serializeFullClientSyncData(registries);

        long voltage = GTValues.VEX[GTValues.HV];
        boolean voltageApplied = dispatch(player, machine,
                CreativeEnergyContainerMachineActions.createSetVoltageAction(voltage));

        helper.assertTrue(voltageApplied, "valid creative energy voltage action was rejected");
        assertCoupledFields(helper,
                machine.getSyncDataHolder().serializeToFieldData(registries, true, false),
                voltage, GTUtil.getTierByVoltage(voltage), "dispatched voltage action");

        int tier = GTValues.VNF.length - 1;
        boolean tierApplied = dispatch(player, machine,
                CreativeEnergyContainerMachineActions.createSetTierAction(tier));

        helper.assertTrue(tierApplied, "valid creative energy tier action was rejected");
        assertCoupledFields(helper,
                machine.getSyncDataHolder().serializeToFieldData(registries, true, false),
                GTValues.VEX[tier], tier, "dispatched tier action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsWrongHolderAndSpectator(GameTestHelper helper) {
        CreativeEnergyContainerMachine machine = createMachine();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SyncActionData voltageAction = CreativeEnergyContainerMachineActions.createSetVoltageAction(32L);
        SyncActionData tierAction = CreativeEnergyContainerMachineActions.createSetTierAction(1);

        helper.assertTrue(!dispatch(player, new Object(), voltageAction),
                "creative energy voltage action accepted a wrong holder");
        helper.assertTrue(!dispatch(player, new Object(), tierAction),
                "creative energy tier action accepted a wrong holder");

        player.setGameMode(GameType.SPECTATOR);
        boolean spectatorVoltage = dispatch(player, machine, voltageAction);
        boolean spectatorTier = dispatch(player, machine, tierAction);
        player.setGameMode(GameType.SURVIVAL);

        helper.assertTrue(!spectatorVoltage, "creative energy voltage action accepted a spectator");
        helper.assertTrue(!spectatorTier, "creative energy tier action accepted a spectator");
        assertState(helper, machine, helper.getLevel().registryAccess(), 0L, 0,
                "state after rejected holder and spectator actions");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsMalformedVoltagePayloads(GameTestHelper helper) {
        CreativeEnergyContainerMachine machine = createMachine();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());

        assertRejected(helper, player, machine, SET_VOLTAGE_ACTION, DataComponentMap.EMPTY,
                "missing voltage payload");
        assertRejected(helper, player, machine, SET_VOLTAGE_ACTION,
                payload(TIER_FIELD, new JsonPrimitive(0)), "missing voltage field");
        assertRejected(helper, player, machine, SET_VOLTAGE_ACTION,
                payload(VOLTAGE_FIELD, new JsonPrimitive(-1L)), "negative voltage");
        assertRejected(helper, player, machine, SET_VOLTAGE_ACTION,
                payload(VOLTAGE_FIELD, new JsonPrimitive(new BigInteger("9223372036854775808"))),
                "overflowing voltage");
        assertRejected(helper, player, machine, SET_VOLTAGE_ACTION,
                payload(VOLTAGE_FIELD, new JsonPrimitive(1.5D)), "fractional voltage");
        assertRejected(helper, player, machine, SET_VOLTAGE_ACTION,
                payload(VOLTAGE_FIELD, new JsonPrimitive("1")), "string voltage");

        assertState(helper, machine, helper.getLevel().registryAccess(), 0L, 0,
                "state after malformed voltage payloads");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dispatcherRejectsMalformedTierPayloads(GameTestHelper helper) {
        CreativeEnergyContainerMachine machine = createMachine();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());

        assertRejected(helper, player, machine, SET_TIER_ACTION, DataComponentMap.EMPTY,
                "missing tier payload");
        assertRejected(helper, player, machine, SET_TIER_ACTION,
                payload(VOLTAGE_FIELD, new JsonPrimitive(0L)), "missing tier field");
        assertRejected(helper, player, machine, SET_TIER_ACTION,
                payload(TIER_FIELD, new JsonPrimitive(-1)), "negative tier");
        assertRejected(helper, player, machine, SET_TIER_ACTION,
                payload(TIER_FIELD, new JsonPrimitive(GTValues.VNF.length)), "out-of-range tier");
        assertRejected(helper, player, machine, SET_TIER_ACTION,
                payload(TIER_FIELD, new JsonPrimitive(Long.MAX_VALUE)), "overflowing tier");
        assertRejected(helper, player, machine, SET_TIER_ACTION,
                payload(TIER_FIELD, new JsonPrimitive(1.5D)), "fractional tier");
        assertRejected(helper, player, machine, SET_TIER_ACTION,
                payload(TIER_FIELD, new JsonPrimitive("1")), "string tier");

        assertState(helper, machine, helper.getLevel().registryAccess(), 0L, 0,
                "state after malformed tier payloads");
        helper.succeed();
    }

    private static CreativeEnergyContainerMachine createMachine() {
        var definition = GTMachines.CREATIVE_ENERGY;
        return new CreativeEnergyContainerMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()));
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static void assertRejected(GameTestHelper helper, ServerPlayer player,
                                       CreativeEnergyContainerMachine machine, ResourceLocation actionId,
                                       DataComponentMap payload, String description) {
        SyncActionData action = new SyncActionData(actionId, 0, payload);
        helper.assertTrue(!dispatch(player, machine, action), description + " was accepted");
    }

    private static void assertLongAction(GameTestHelper helper, SyncActionData action,
                                         ResourceLocation expectedActionId, ResourceLocation field,
                                         long expectedValue, int expectedSequence, String description) {
        helper.assertTrue(action.actionId().equals(expectedActionId),
                description + " used the wrong action id");
        helper.assertTrue(action.sequence() == expectedSequence,
                description + " used the wrong sequence");
        SyncFieldData fields = requireFields(action);
        helper.assertTrue(fields.fields().size() == 1,
                description + " did not contain exactly one field");
        assertLong(helper, fields, field, expectedValue, description + " payload");
    }

    private static void assertIntegerAction(GameTestHelper helper, SyncActionData action,
                                            ResourceLocation expectedActionId, ResourceLocation field,
                                            int expectedValue, int expectedSequence, String description) {
        helper.assertTrue(action.actionId().equals(expectedActionId),
                description + " used the wrong action id");
        helper.assertTrue(action.sequence() == expectedSequence,
                description + " used the wrong sequence");
        SyncFieldData fields = requireFields(action);
        helper.assertTrue(fields.fields().size() == 1,
                description + " did not contain exactly one field");
        assertInteger(helper, fields, field, expectedValue, description + " payload");
    }

    private static SyncFieldData requireFields(SyncActionData action) {
        SyncFieldData fields = action.payload().get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Creative energy action creator omitted its field payload.");
        }
        return fields;
    }

    private static void assertCoupledFields(GameTestHelper helper, SyncFieldData fields,
                                            long expectedVoltage, int expectedTier, String description) {
        helper.assertTrue(fields.fields().size() == 2,
                description + " did not dirty exactly voltage and tier");
        assertLong(helper, fields, VOLTAGE_FIELD, expectedVoltage, description + " voltage");
        assertInteger(helper, fields, TIER_FIELD, expectedTier, description + " tier");
    }

    private static void assertState(GameTestHelper helper, CreativeEnergyContainerMachine machine,
                                    RegistryAccess registries, long expectedVoltage, int expectedTier,
                                    String description) {
        SyncFieldData fields = machine.getSyncDataHolder().serializeToSaveFieldData(registries);
        assertLong(helper, fields, VOLTAGE_FIELD, expectedVoltage, description + " voltage");
        assertInteger(helper, fields, TIER_FIELD, expectedTier, description + " tier");
    }

    private static void assertLong(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                   long expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsLong() == expected,
                description + " was not " + expected);
    }

    private static void assertInteger(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                      int expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsInt() == expected,
                description + " was not " + expected);
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return SyncFieldData.builder()
                .put(field, value)
                .build()
                .toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get());
    }

    private static boolean rejectsIllegalArgument(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException exception) {
            return true;
        }
    }
}

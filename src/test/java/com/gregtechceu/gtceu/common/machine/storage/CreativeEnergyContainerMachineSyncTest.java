package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CreativeEnergyContainerMachineSyncTest {

    private static final String BATCH = "CreativeEnergyContainerMachineSync";
    private static final ResourceLocation ACTIVE_FIELD = SyncFieldData.key("active");
    private static final ResourceLocation AMPS_FIELD = SyncFieldData.key("amps");
    private static final ResourceLocation SOURCE_FIELD = SyncFieldData.key("source");
    private static final ResourceLocation VOLTAGE_FIELD = SyncFieldData.key("voltage");
    private static final ResourceLocation TIER_FIELD = SyncFieldData.key("setTier");
    private static final ResourceLocation UNKNOWN_FIELD = SyncFieldData.key("unknown");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void activeUpdatesPersistFullSyncAndProduceIndependentAcknowledgements(GameTestHelper helper) {
        CreativeEnergyContainerMachine machine = createMachine();
        RegistryAccess registries = helper.getLevel().registryAccess();

        assertActive(helper, machine.getSyncDataHolder().serializeToSaveFieldData(registries), false,
                "default saved active value");
        assertActive(helper, machine.getSyncDataHolder().serializeFullClientSyncData(registries), false,
                "default full-sync active value");

        ServerFieldUpdateResult activated = apply(machine, registries, ACTIVE_FIELD, new JsonPrimitive(true));

        helper.assertTrue(activated.getAccepted() && activated.getChanged(),
                "false-to-true active update was not accepted as changed");
        assertActive(helper, machine.getSyncDataHolder().serializeToSaveFieldData(registries), true,
                "saved active value after activation");
        assertOnlyActiveAcknowledgement(helper,
                machine.getSyncDataHolder().serializeToFieldData(registries, true, false), true,
                "false-to-true active update");

        ServerFieldUpdateResult unchanged = apply(machine, registries, ACTIVE_FIELD, new JsonPrimitive(true));

        helper.assertTrue(unchanged.getAccepted() && !unchanged.getChanged(),
                "repeated true active update was not accepted as unchanged");
        assertOnlyActiveAcknowledgement(helper,
                machine.getSyncDataHolder().serializeToFieldData(registries, true, false), true,
                "repeated true active update");

        ServerFieldUpdateResult deactivated = apply(machine, registries, ACTIVE_FIELD, new JsonPrimitive(false));

        helper.assertTrue(deactivated.getAccepted() && deactivated.getChanged(),
                "true-to-false active update was not accepted as changed");
        assertActive(helper, machine.getSyncDataHolder().serializeToSaveFieldData(registries), false,
                "saved active value after deactivation");
        assertActive(helper, machine.getSyncDataHolder().serializeFullClientSyncData(registries), false,
                "full-sync active value after deactivation");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void ampsUpdatesPersistFullSyncAndProduceIndependentAcknowledgements(GameTestHelper helper) {
        CreativeEnergyContainerMachine machine = createMachine();
        RegistryAccess registries = helper.getLevel().registryAccess();

        assertAmps(helper, machine.getSyncDataHolder().serializeToSaveFieldData(registries), 1,
                "default saved amperage");
        assertAmps(helper, machine.getSyncDataHolder().serializeFullClientSyncData(registries), 1,
                "default full-sync amperage");

        ServerFieldUpdateResult ordinary = apply(machine, registries, AMPS_FIELD, new JsonPrimitive(8));

        helper.assertTrue(ordinary.getAccepted() && ordinary.getChanged(),
                "ordinary amperage update was not accepted as changed");
        assertAmps(helper, machine.getSyncDataHolder().serializeToSaveFieldData(registries), 8,
                "saved ordinary amperage");
        assertOnlyAmpsAcknowledgement(helper,
                machine.getSyncDataHolder().serializeToFieldData(registries, true, false), 8,
                "ordinary amperage update");

        ServerFieldUpdateResult zero = apply(machine, registries, AMPS_FIELD, new JsonPrimitive(0));

        helper.assertTrue(zero.getAccepted() && zero.getChanged(),
                "zero amperage update was not accepted as changed");
        assertOnlyAmpsAcknowledgement(helper,
                machine.getSyncDataHolder().serializeToFieldData(registries, true, false), 0,
                "zero amperage update");

        ServerFieldUpdateResult maximum = apply(machine, registries, AMPS_FIELD,
                new JsonPrimitive(Integer.MAX_VALUE));

        helper.assertTrue(maximum.getAccepted() && maximum.getChanged(),
                "maximum amperage update was not accepted as changed");
        assertAmps(helper, machine.getSyncDataHolder().serializeToSaveFieldData(registries), Integer.MAX_VALUE,
                "saved maximum amperage");
        assertOnlyAmpsAcknowledgement(helper,
                machine.getSyncDataHolder().serializeToFieldData(registries, true, false), Integer.MAX_VALUE,
                "maximum amperage update");
        assertAmps(helper, machine.getSyncDataHolder().serializeFullClientSyncData(registries), Integer.MAX_VALUE,
                "full-sync maximum amperage");

        ServerFieldUpdateResult unchanged = apply(machine, registries, AMPS_FIELD,
                new JsonPrimitive(Integer.MAX_VALUE));

        helper.assertTrue(unchanged.getAccepted() && !unchanged.getChanged(),
                "repeated maximum amperage update was not accepted as unchanged");
        assertOnlyAmpsAcknowledgement(helper,
                machine.getSyncDataHolder().serializeToFieldData(registries, true, false), Integer.MAX_VALUE,
                "repeated maximum amperage update");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void invalidAmpsUpdatesAreRejectedWithoutChangingState(GameTestHelper helper) {
        CreativeEnergyContainerMachine machine = createMachine();
        RegistryAccess registries = helper.getLevel().registryAccess();

        assertRejectedAmpsCandidateUnchanged(helper, machine, registries,
                new JsonPrimitive(-1), "negative candidate");
        assertRejectedAmpsCandidateUnchanged(helper, machine, registries,
                new JsonPrimitive("1"), "string candidate");
        assertRejectedAmpsCandidateUnchanged(helper, machine, registries,
                new JsonPrimitive(1.5), "decimal candidate");
        assertRejectedAmpsCandidateUnchanged(helper, machine, registries,
                new JsonPrimitive((long) Integer.MAX_VALUE + 1L), "integer overflow candidate");
        assertRejectedAmpsCandidateUnchanged(helper, machine, registries,
                JsonNull.INSTANCE, "null candidate");
        assertRejectedAmpsUnchanged(helper, machine, registries,
                payload(UNKNOWN_FIELD, new JsonPrimitive(1)), "unknown field");

        DataComponentMap missingSyncComponent = DataComponentMap.builder()
                .set(DataComponents.CUSTOM_NAME, Component.literal("missing sync field data"))
                .build();
        assertRejectedAmpsUnchanged(helper, machine, registries, missingSyncComponent,
                "missing sync component");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void sourceUpdatesPersistAndSynchronizeCoupledState(GameTestHelper helper) {
        CreativeEnergyContainerMachine machine = createMachine();
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.getSyncDataHolder().serializeFullClientSyncData(registries);

        ServerFieldUpdateResult sink = apply(machine, registries, sourceAndAmpsPayload(false, 7));

        helper.assertTrue(sink.getAccepted() && sink.getChanged(),
                "sink mode update was not accepted as changed");
        assertSourceState(helper, machine, registries, false, GTValues.V[14], Integer.MAX_VALUE, 14,
                "sink mode");
        assertCoupledSourceChanges(helper,
                machine.getSyncDataHolder().serializeToFieldData(registries, true, false),
                false, GTValues.V[14], Integer.MAX_VALUE, 14, "sink mode update");

        ServerFieldUpdateResult unchangedSink = apply(machine, registries, SOURCE_FIELD, new JsonPrimitive(false));

        helper.assertTrue(unchangedSink.getAccepted() && !unchangedSink.getChanged(),
                "repeated sink mode update was not accepted as unchanged");
        assertOnlySourceAcknowledgement(helper,
                machine.getSyncDataHolder().serializeToFieldData(registries, true, false), false,
                "repeated sink mode update");

        ServerFieldUpdateResult source = apply(machine, registries, sourceAndAmpsPayload(true, 8));

        helper.assertTrue(source.getAccepted() && source.getChanged(),
                "source mode update was not accepted as changed");
        assertSourceState(helper, machine, registries, true, 0L, 0, 0, "source mode");
        assertCoupledSourceChanges(helper,
                machine.getSyncDataHolder().serializeToFieldData(registries, true, false),
                true, 0L, 0, 0, "source mode update");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void unchangedDefaultSourcePreservesAmperageAndAcknowledgesOnlySource(GameTestHelper helper) {
        CreativeEnergyContainerMachine machine = createMachine();
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.getSyncDataHolder().serializeFullClientSyncData(registries);

        ServerFieldUpdateResult result = apply(machine, registries, SOURCE_FIELD, new JsonPrimitive(true));

        helper.assertTrue(result.getAccepted() && !result.getChanged(),
                "default source update was not accepted as unchanged");
        assertSourceState(helper, machine, registries, true, 0L, 1, 0,
                "unchanged default source mode");
        assertOnlySourceAcknowledgement(helper,
                machine.getSyncDataHolder().serializeToFieldData(registries, true, false), true,
                "unchanged default source update");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void sourceRoundTripAcknowledgesCoupledStateWithoutClientEcho(GameTestHelper helper) {
        CreativeEnergyContainerMachine server = createMachine();
        CreativeEnergyContainerMachine client = createMachine();
        RegistryAccess registries = helper.getLevel().registryAccess();

        DataComponentMap initialSync = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, initialSync);
        client.getSyncDataHolder().deserializeComponents(
                registries, sourceAndAmpsPayload(false, 7), false);

        DataComponentMap request = client.getSyncDataHolder().collectServerNetworkChanges(registries);
        SyncFieldData requestFields = request.get(GTDataComponents.SYNC_FIELD_DATA.get());
        helper.assertTrue(requestFields != null, "source client request omitted sync field data");
        helper.assertTrue(requestFields.fields().size() == 2,
                "source client request did not contain exactly source and amperage");
        assertBoolean(helper, requestFields, SOURCE_FIELD, false, "source client request source");
        assertAmps(helper, requestFields, 7, "source client request amperage");

        ServerFieldUpdateResult result = apply(server, registries, request);

        helper.assertTrue(result.getAccepted() && result.getChanged(),
                "source client request was not accepted as changed");
        assertSourceState(helper, server, registries, false, GTValues.V[14], Integer.MAX_VALUE, 14,
                "server state after source client request");
        helper.assertTrue(server.getSyncDataHolder().scanAndMarkChanges(registries),
                "source client request did not produce a server acknowledgement");

        DataComponentMap acknowledgement = server.getSyncDataHolder()
                .collectClientNetworkChanges(registries, false);
        SyncFieldData acknowledgementFields = acknowledgement.get(GTDataComponents.SYNC_FIELD_DATA.get());
        helper.assertTrue(acknowledgementFields != null, "source acknowledgement omitted sync field data");
        assertCoupledSourceChanges(helper, acknowledgementFields,
                false, GTValues.V[14], Integer.MAX_VALUE, 14, "source acknowledgement");

        client.getSyncDataHolder().applyClientNetworkUpdate(registries, acknowledgement);

        assertSourceState(helper, client, registries, false, GTValues.V[14], Integer.MAX_VALUE, 14,
                "client state after source acknowledgement");
        helper.assertTrue(client.getSyncDataHolder().collectServerNetworkChanges(registries).isEmpty(),
                "client echoed the authoritative source acknowledgement back to the server");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void invalidSourceUpdatesAreRejectedAtomically(GameTestHelper helper) {
        CreativeEnergyContainerMachine machine = createMachine();
        RegistryAccess registries = helper.getLevel().registryAccess();

        assertRejectedSourceCandidateUnchanged(helper, machine, registries,
                new JsonPrimitive("false"), "string candidate");
        assertRejectedSourceCandidateUnchanged(helper, machine, registries,
                new JsonPrimitive(0), "numeric candidate");
        assertRejectedSourceCandidateUnchanged(helper, machine, registries,
                JsonNull.INSTANCE, "null candidate");

        machine.getSyncDataHolder().serializeFullClientSyncData(registries);
        ServerFieldUpdateResult result = apply(machine, registries, sourceAndAmpsPayload(false, -1));

        helper.assertTrue(!result.getAccepted(), "source and invalid amperage batch was accepted");
        assertSourceState(helper, machine, registries, true, 0L, 1, 0,
                "state after rejecting source and invalid amperage batch");
        SyncFieldData acknowledgement = machine.getSyncDataHolder()
                .serializeToFieldData(registries, true, false);
        helper.assertTrue(acknowledgement.fields().size() == 2,
                "rejected source and amperage batch acknowledgement contained unrelated fields");
        assertBoolean(helper, acknowledgement, SOURCE_FIELD, true,
                "rejected source and amperage batch source acknowledgement");
        assertAmps(helper, acknowledgement, 1,
                "rejected source and amperage batch amperage acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void invalidActiveUpdatesAreRejectedWithoutChangingState(GameTestHelper helper) {
        CreativeEnergyContainerMachine machine = createMachine();
        RegistryAccess registries = helper.getLevel().registryAccess();

        assertRejectedInactive(helper, machine, registries,
                payload(ACTIVE_FIELD, new JsonPrimitive("true")), "string candidate");
        assertRejectedInactive(helper, machine, registries,
                payload(ACTIVE_FIELD, JsonNull.INSTANCE), "null candidate");
        assertRejectedInactive(helper, machine, registries,
                payload(UNKNOWN_FIELD, new JsonPrimitive(true)), "unknown field");

        DataComponentMap missingSyncComponent = DataComponentMap.builder()
                .set(DataComponents.CUSTOM_NAME, Component.literal("missing sync field data"))
                .build();
        assertRejectedInactive(helper, machine, registries, missingSyncComponent, "missing sync component");
        helper.succeed();
    }

    private static CreativeEnergyContainerMachine createMachine() {
        var definition = GTMachines.CREATIVE_ENERGY;
        return new CreativeEnergyContainerMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()));
    }

    private static ServerFieldUpdateResult apply(CreativeEnergyContainerMachine machine, RegistryAccess registries,
                                                 ResourceLocation field, JsonElement candidate) {
        return apply(machine, registries, payload(field, candidate));
    }

    private static ServerFieldUpdateResult apply(CreativeEnergyContainerMachine machine, RegistryAccess registries,
                                                 DataComponentMap components) {
        return machine.getSyncDataHolder().tryApplyServerNetworkUpdate(registries, components);
    }

    private static void assertRejectedInactive(GameTestHelper helper, CreativeEnergyContainerMachine machine,
                                               RegistryAccess registries, DataComponentMap components,
                                               String description) {
        assertActive(helper, machine.getSyncDataHolder().serializeFullClientSyncData(registries), false,
                "precondition for " + description);

        ServerFieldUpdateResult result = machine.getSyncDataHolder()
                .tryApplyServerNetworkUpdate(registries, components);

        helper.assertTrue(!result.getAccepted(), description + " was accepted");
        assertActive(helper, machine.getSyncDataHolder().serializeToSaveFieldData(registries), false,
                "saved active value after rejecting " + description);
    }

    private static void assertOnlyActiveAcknowledgement(GameTestHelper helper, SyncFieldData acknowledgement,
                                                        boolean expected, String description) {
        helper.assertTrue(acknowledgement.fields().size() == 1,
                description + " acknowledgement contained unrelated fields");
        assertActive(helper, acknowledgement, expected, description + " acknowledgement");
    }

    private static void assertRejectedAmpsUnchanged(GameTestHelper helper, CreativeEnergyContainerMachine machine,
                                                    RegistryAccess registries, DataComponentMap components,
                                                    String description) {
        assertAmps(helper, machine.getSyncDataHolder().serializeFullClientSyncData(registries), 1,
                "precondition for " + description);

        ServerFieldUpdateResult result = machine.getSyncDataHolder()
                .tryApplyServerNetworkUpdate(registries, components);

        helper.assertTrue(!result.getAccepted(), description + " was accepted");
        assertAmps(helper, machine.getSyncDataHolder().serializeToSaveFieldData(registries), 1,
                "saved amperage after rejecting " + description);
    }

    private static void assertRejectedAmpsCandidateUnchanged(GameTestHelper helper,
                                                             CreativeEnergyContainerMachine machine,
                                                             RegistryAccess registries, JsonElement candidate,
                                                             String description) {
        assertRejectedAmpsUnchanged(helper, machine, registries, payload(AMPS_FIELD, candidate), description);
        assertOnlyAmpsAcknowledgement(helper,
                machine.getSyncDataHolder().serializeToFieldData(registries, true, false), 1,
                description + " rejection");
    }

    private static void assertOnlyAmpsAcknowledgement(GameTestHelper helper, SyncFieldData acknowledgement,
                                                      int expected, String description) {
        helper.assertTrue(acknowledgement.fields().size() == 1,
                description + " acknowledgement contained unrelated fields");
        assertAmps(helper, acknowledgement, expected, description + " acknowledgement");
    }

    private static void assertRejectedSourceCandidateUnchanged(GameTestHelper helper,
                                                               CreativeEnergyContainerMachine machine,
                                                               RegistryAccess registries, JsonElement candidate,
                                                               String description) {
        machine.getSyncDataHolder().serializeFullClientSyncData(registries);

        ServerFieldUpdateResult result = apply(machine, registries, SOURCE_FIELD, candidate);

        helper.assertTrue(!result.getAccepted(), description + " was accepted as a source value");
        assertSourceState(helper, machine, registries, true, 0L, 1, 0,
                "state after rejecting source " + description);
        assertOnlySourceAcknowledgement(helper,
                machine.getSyncDataHolder().serializeToFieldData(registries, true, false), true,
                "source " + description + " rejection");
    }

    private static void assertSourceState(GameTestHelper helper, CreativeEnergyContainerMachine machine,
                                          RegistryAccess registries, boolean source, long voltage, int amps, int tier,
                                          String description) {
        SyncFieldData saved = machine.getSyncDataHolder().serializeToSaveFieldData(registries);
        assertBoolean(helper, saved, SOURCE_FIELD, source, description + " saved source");
        assertLong(helper, saved, VOLTAGE_FIELD, voltage, description + " saved voltage");
        assertAmps(helper, saved, amps, description + " saved amperage");
        assertInteger(helper, saved, TIER_FIELD, tier, description + " saved tier");
        helper.assertTrue(machine.inputsEnergy(Direction.NORTH) != source,
                description + " input mode did not match source state");
        helper.assertTrue(machine.outputsEnergy(Direction.NORTH) == source,
                description + " output mode did not match source state");
        helper.assertTrue(machine.getInputVoltage() == (source ? 0L : voltage),
                description + " input voltage did not match source state");
        helper.assertTrue(machine.getOutputVoltage() == (source ? voltage : 0L),
                description + " output voltage did not match source state");
        helper.assertTrue(machine.getInputAmperage() == (source ? 0L : amps),
                description + " input amperage did not match source state");
        helper.assertTrue(machine.getOutputAmperage() == (source ? amps : 0L),
                description + " output amperage did not match source state");
    }

    private static void assertCoupledSourceChanges(GameTestHelper helper, SyncFieldData changes,
                                                   boolean source, long voltage, int amps, int tier,
                                                   String description) {
        helper.assertTrue(changes.fields().size() == 4,
                description + " did not synchronize exactly its four coupled fields");
        assertBoolean(helper, changes, SOURCE_FIELD, source, description + " source");
        assertLong(helper, changes, VOLTAGE_FIELD, voltage, description + " voltage");
        assertAmps(helper, changes, amps, description + " amperage");
        assertInteger(helper, changes, TIER_FIELD, tier, description + " tier");
    }

    private static void assertOnlySourceAcknowledgement(GameTestHelper helper, SyncFieldData acknowledgement,
                                                        boolean expected, String description) {
        helper.assertTrue(acknowledgement.fields().size() == 1,
                description + " acknowledgement contained unrelated fields");
        assertBoolean(helper, acknowledgement, SOURCE_FIELD, expected, description + " acknowledgement");
    }

    private static void assertActive(GameTestHelper helper, SyncFieldData fields, boolean expected,
                                     String description) {
        JsonElement value = fields.get(ACTIVE_FIELD);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " was not " + expected);
    }

    private static void assertAmps(GameTestHelper helper, SyncFieldData fields, int expected, String description) {
        assertInteger(helper, fields, AMPS_FIELD, expected, description);
    }

    private static void assertInteger(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                      int expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsInt() == expected,
                description + " was not " + expected);
    }

    private static void assertLong(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                   long expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsLong() == expected,
                description + " was not " + expected);
    }

    private static void assertBoolean(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                      boolean expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " was not " + expected);
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static DataComponentMap sourceAndAmpsPayload(boolean source, int amps) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(SOURCE_FIELD, new JsonPrimitive(source))
                        .put(AMPS_FIELD, new JsonPrimitive(amps))
                        .build())
                .build();
    }
}

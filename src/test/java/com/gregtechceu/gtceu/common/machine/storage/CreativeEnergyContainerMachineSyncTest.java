package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
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
    private static final ResourceLocation SET_SOURCE_ACTION = GTCEu.id("set_creative_energy_source");
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
    public static void sourceActionAutomaticallySynchronizesDerivedAmperage(GameTestHelper helper) {
        CreativeEnergyContainerMachine machine = createMachine();
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.getSyncDataHolder().serializeFullClientSyncData(registries);

        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SyncActionData action = new SyncActionData(SET_SOURCE_ACTION, 0,
                payload(SOURCE_FIELD, new JsonPrimitive(false)));
        SyncActionContext context = new SyncActionContext(player, machine, action, BlockPos.ZERO,
                null, null, null);

        helper.assertTrue(SyncActionDispatchers.server().dispatch(context),
                "valid creative energy source action was rejected");

        SyncFieldData changes = machine.getSyncDataHolder().serializeToFieldData(registries, true, false);
        helper.assertTrue(changes.fields().size() == 4,
                "source action did not synchronize exactly its four coupled fields");
        assertBoolean(helper, changes, SOURCE_FIELD, false, "source action source value");
        assertAmps(helper, changes, Integer.MAX_VALUE, "source action amperage");
        assertInteger(helper, changes, TIER_FIELD, 14, "source action tier");
        assertLong(helper, changes, VOLTAGE_FIELD, GTValues.V[14], "source action voltage");
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
        return machine.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, payload(field, candidate));
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
}

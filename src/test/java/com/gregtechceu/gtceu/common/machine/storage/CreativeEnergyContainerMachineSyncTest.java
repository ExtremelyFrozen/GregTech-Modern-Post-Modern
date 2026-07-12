package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
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

        ServerFieldUpdateResult activated = apply(machine, registries, new JsonPrimitive(true));

        helper.assertTrue(activated.getAccepted() && activated.getChanged(),
                "false-to-true active update was not accepted as changed");
        assertActive(helper, machine.getSyncDataHolder().serializeToSaveFieldData(registries), true,
                "saved active value after activation");
        assertOnlyActiveAcknowledgement(helper,
                machine.getSyncDataHolder().serializeToFieldData(registries, true, false), true,
                "false-to-true active update");

        ServerFieldUpdateResult unchanged = apply(machine, registries, new JsonPrimitive(true));

        helper.assertTrue(unchanged.getAccepted() && !unchanged.getChanged(),
                "repeated true active update was not accepted as unchanged");
        assertOnlyActiveAcknowledgement(helper,
                machine.getSyncDataHolder().serializeToFieldData(registries, true, false), true,
                "repeated true active update");

        ServerFieldUpdateResult deactivated = apply(machine, registries, new JsonPrimitive(false));

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
                                                 JsonElement candidate) {
        return machine.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, payload(ACTIVE_FIELD, candidate));
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

    private static void assertActive(GameTestHelper helper, SyncFieldData fields, boolean expected,
                                     String description) {
        JsonElement value = fields.get(ACTIVE_FIELD);
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

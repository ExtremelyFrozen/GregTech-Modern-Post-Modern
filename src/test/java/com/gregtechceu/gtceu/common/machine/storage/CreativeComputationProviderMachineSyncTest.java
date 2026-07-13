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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CreativeComputationProviderMachineSyncTest {

    private static final int CHANGED_LAST_REQUESTED_CWUT = 24;
    private static final ResourceLocation MAX_CWUT_FIELD = SyncFieldData.key("maxCWUt");
    private static final ResourceLocation ACTIVE_FIELD = SyncFieldData.key("active");
    private static final ResourceLocation LAST_REQUESTED_CWUT_FIELD = SyncFieldData.key("lastRequestedCWUt");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CreativeComputationProviderMachineSync")
    public static void serverFieldUpdatesCommitCanonicalValuesAndNotifyOnlyForActiveChanges(GameTestHelper helper) {
        TestCreativeComputationProviderMachine machine = createMachine();
        RegistryAccess registries = helper.getLevel().registryAccess();

        ServerFieldUpdateResult initialUpdate = machine.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, payload(SyncFieldData.builder()
                        .put(MAX_CWUT_FIELD, new JsonPrimitive(64))
                        .put(ACTIVE_FIELD, new JsonPrimitive(true))
                        .build()));

        helper.assertTrue(initialUpdate.getAccepted(), "valid max CWUt and active update batch was rejected");
        helper.assertTrue(initialUpdate.getChanged(), "valid max CWUt and active update batch was not changed");
        helper.assertTrue(machine.getOfferedCWUt() == 64,
                "accepted batch did not expose the committed max CWUt while active");
        helper.assertTrue(machine.isActive(), "accepted batch did not activate the computation provider");
        helper.assertTrue(machine.activeListenerCalls == 1,
                "active field listener was not invoked exactly once for the initial change");

        ServerFieldUpdateResult unchangedActive = machine.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, payload(ACTIVE_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(unchangedActive.getAccepted(), "valid unchanged active update was rejected");
        helper.assertTrue(!unchangedActive.getChanged(), "unchanged active update was reported as changed");
        helper.assertTrue(machine.activeListenerCalls == 1,
                "unchanged active update invoked the active field listener again");

        ServerFieldUpdateResult maxUpdate = machine.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, payload(MAX_CWUT_FIELD, new JsonPrimitive(128)));

        helper.assertTrue(maxUpdate.getAccepted(), "valid positive max CWUt update was rejected");
        helper.assertTrue(maxUpdate.getChanged(), "valid positive max CWUt update was not changed");
        helper.assertTrue(machine.getOfferedCWUt() == 128,
                "valid positive max CWUt update did not commit the new value");
        helper.assertTrue(machine.activeListenerCalls == 1,
                "max CWUt update unexpectedly invoked the active field listener");

        ServerFieldUpdateResult rejectedMax = machine.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, payload(MAX_CWUT_FIELD, new JsonPrimitive(-1)));

        helper.assertTrue(!rejectedMax.getAccepted(), "negative max CWUt update was accepted");
        helper.assertTrue(rejectedMax.getRejectionReason() != null &&
                rejectedMax.getRejectionReason().contains("Server normalizer rejected field maxCWUt"),
                "negative max CWUt update was rejected without maxCWUt normalizer context");
        helper.assertTrue(machine.getOfferedCWUt() == 128,
                "rejected negative max CWUt update changed the committed value");
        helper.assertTrue(machine.activeListenerCalls == 1,
                "rejected negative max CWUt update invoked the active field listener");

        machine.setActive(false);

        helper.assertTrue(!machine.isActive(), "public active setter did not deactivate the computation provider");
        helper.assertTrue(machine.getOfferedCWUt() == 0,
                "deactivated computation provider continued to offer computation");
        helper.assertTrue(machine.activeListenerCalls == 2,
                "public active setter did not invoke the computation lifecycle update exactly once");

        machine.setActive(false);

        helper.assertTrue(machine.activeListenerCalls == 2,
                "unchanged public active setter call repeated the computation lifecycle update");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CreativeComputationProviderMachineSync")
    public static void lastRequestedCWUtUsesChangedOnlyServerOwnedSync(GameTestHelper helper) {
        TestCreativeComputationProviderMachine server = createMachine();
        TestCreativeComputationProviderMachine client = createMachine();
        RegistryAccess registries = helper.getLevel().registryAccess();

        DataComponentMap full = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        assertIntField(helper, full, LAST_REQUESTED_CWUT_FIELD, 0,
                "creative computation last requested full sync");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, full);
        assertLastRequestedCWUtState(helper, client, registries, 0,
                "creative computation last requested full sync client");
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "unchanged creative computation provider produced a delta after full sync");

        server.setLevel(helper.getLevel());
        server.applyProducedCWUt(CHANGED_LAST_REQUESTED_CWUT * 20);
        int ticksUntilUpdate = 20 - (int) (server.getOffsetTimer() % 20L);
        helper.runAfterDelay(ticksUntilUpdate, () -> {
            server.runComputationTick();

            DataComponentMap delta = server.getSyncDataHolder().serializeToComponents(registries, true, false);
            SyncFieldData changedFields = delta.get(GTDataComponents.SYNC_FIELD_DATA.get());
            helper.assertTrue(changedFields != null && changedFields.fields().size() == 1,
                    "creative computation last requested delta contained unrelated fields");
            assertIntField(helper, delta, LAST_REQUESTED_CWUT_FIELD, CHANGED_LAST_REQUESTED_CWUT,
                    "creative computation last requested delta");
            client.getSyncDataHolder().applyClientNetworkUpdate(registries, delta);
            assertLastRequestedCWUtState(helper, client, registries, CHANGED_LAST_REQUESTED_CWUT,
                    "creative computation last requested delta client");

            server.applyProducedCWUt(CHANGED_LAST_REQUESTED_CWUT * 20);
            server.runComputationTick();
            helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                    "unchanged creative computation last requested value produced a redundant delta");

            SyncFieldData saved = server.getSyncDataHolder().serializeToFieldData(registries, false, false);
            helper.assertTrue(!saved.fields().containsKey(LAST_REQUESTED_CWUT_FIELD),
                    "transient creative computation last requested value was saved");

            ServerFieldUpdateResult rejected = server.getSyncDataHolder().tryApplyServerNetworkUpdate(
                    registries, payload(LAST_REQUESTED_CWUT_FIELD, new JsonPrimitive(1)));
            helper.assertTrue(!rejected.getAccepted(),
                    "creative computation provider accepted a client last requested update");
            helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                    "rejected creative computation last requested update produced an acknowledgement");
            assertLastRequestedCWUtState(helper, server, registries, CHANGED_LAST_REQUESTED_CWUT,
                    "rejected creative computation last requested update");
            helper.succeed();
        });
    }

    private static TestCreativeComputationProviderMachine createMachine() {
        var definition = GTMachines.CREATIVE_COMPUTATION_PROVIDER;
        return new TestCreativeComputationProviderMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()));
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return payload(SyncFieldData.builder()
                .put(field, value)
                .build());
    }

    private static DataComponentMap payload(SyncFieldData fields) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
                .build();
    }

    private static void assertLastRequestedCWUtState(GameTestHelper helper,
                                                     TestCreativeComputationProviderMachine machine,
                                                     RegistryAccess registries, int expected, String description) {
        assertIntField(helper, machine.getSyncDataHolder().serializeFullClientSyncComponents(registries),
                LAST_REQUESTED_CWUT_FIELD, expected, description);
    }

    private static void assertIntField(GameTestHelper helper, DataComponentMap components, ResourceLocation field,
                                       int expected, String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        JsonElement value = fields == null ? null : fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsInt() == expected,
                description + " did not contain the expected " + field.getPath() + " value");
    }

    private static final class TestCreativeComputationProviderMachine extends CreativeComputationProviderMachine {

        private int activeListenerCalls;

        private TestCreativeComputationProviderMachine(BlockEntityCreationInfo info) {
            super(info);
        }

        @Override
        protected void updateComputationSubscription() {
            activeListenerCalls++;
        }

        private void runComputationTick() {
            updateComputationTick();
        }
    }
}

package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class ItemCollectorMachineSyncTest {

    private static final String BATCH = "ItemCollectorMachineSync";
    private static final int TIER = GTValues.LV;
    private static final int MIN_RANGE = 1;
    private static final int MAX_RANGE = 1 << (TIER + 2);
    private static final ResourceLocation RANGE_FIELD = SyncFieldData.key("range");
    private static final ResourceLocation WORKING_ENABLED_FIELD = SyncFieldData.key("isWorkingEnabled");
    private static final ResourceLocation ACTIVE_FIELD = SyncFieldData.key("active");
    private static final ResourceLocation UNKNOWN_FIELD = SyncFieldData.key("unknown");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void serverUpdatesAcceptBoundariesAndInvalidateOnlyForChanges(GameTestHelper helper) {
        TestItemCollectorMachine machine = createMachine();
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.getSyncDataHolder().serializeFullClientSyncData(registries);

        helper.assertTrue(machine.getRange() == MAX_RANGE,
                "LV item collector did not initialize to its maximum range");

        ServerFieldUpdateResult changed = apply(machine, registries, new JsonPrimitive(4));
        helper.assertTrue(changed.getAccepted() && changed.getChanged(),
                "valid item collector range was not accepted as changed");
        helper.assertTrue(machine.getRange() == 4 && machine.boundsInvalidations == 1,
                "valid item collector range did not commit and invalidate bounds exactly once");

        ServerFieldUpdateResult unchanged = apply(machine, registries, new JsonPrimitive(4));
        helper.assertTrue(unchanged.getAccepted() && !unchanged.getChanged(),
                "repeated canonical range was not accepted as unchanged");
        helper.assertTrue(machine.getRange() == 4 && machine.boundsInvalidations == 1,
                "repeated canonical range changed state or repeated its listener");

        ServerFieldUpdateResult minimum = apply(machine, registries, new JsonPrimitive(MIN_RANGE));
        helper.assertTrue(minimum.getAccepted() && minimum.getChanged(),
                "minimum item collector range was rejected");
        helper.assertTrue(machine.getRange() == MIN_RANGE && machine.boundsInvalidations == 2,
                "minimum item collector range did not commit exactly once");

        machine.getSyncDataHolder().serializeFullClientSyncData(registries);
        ServerFieldUpdateResult maximum = apply(machine, registries, new JsonPrimitive(MAX_RANGE));
        helper.assertTrue(maximum.getAccepted() && maximum.getChanged(),
                "maximum item collector range was rejected");
        helper.assertTrue(machine.getRange() == MAX_RANGE && machine.boundsInvalidations == 3,
                "maximum item collector range did not commit exactly once");
        assertAcknowledgedRange(helper, machine, registries, MAX_RANGE,
                "valid update did not request an authoritative range acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void invalidServerUpdatesPreserveRangeAndRequestCanonicalAck(GameTestHelper helper) {
        TestItemCollectorMachine machine = createMachine();
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.getSyncDataHolder().serializeFullClientSyncData(registries);

        assertRejectedUnchanged(helper, machine, registries,
                payload(RANGE_FIELD, new JsonPrimitive(MIN_RANGE - 1)), "below-minimum range");
        assertAcknowledgedRange(helper, machine, registries, MAX_RANGE,
                "rejected range did not request an authoritative acknowledgement");

        assertRejectedUnchanged(helper, machine, registries,
                payload(RANGE_FIELD, new JsonPrimitive(MAX_RANGE + 1)), "above-maximum range");
        assertRejectedUnchanged(helper, machine, registries,
                payload(RANGE_FIELD, new JsonPrimitive("4")), "string candidate");
        assertRejectedUnchanged(helper, machine, registries,
                payload(RANGE_FIELD, new JsonPrimitive(4.5)), "decimal candidate");
        assertRejectedUnchanged(helper, machine, registries,
                payload(RANGE_FIELD, new JsonPrimitive((long) Integer.MAX_VALUE + 1L)),
                "out-of-range integer candidate");
        assertRejectedUnchanged(helper, machine, registries,
                payload(UNKNOWN_FIELD, new JsonPrimitive(4)), "unknown field");

        DataComponentMap missingSyncComponent = DataComponentMap.builder()
                .set(DataComponents.CUSTOM_NAME, Component.literal("missing sync field data"))
                .build();
        assertRejectedUnchanged(helper, machine, registries, missingSyncComponent, "missing sync component");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void publicSetterSharesValidationAndChangedOnlyInvalidation(GameTestHelper helper) {
        TestItemCollectorMachine machine = createMachine();

        machine.setRange(4);
        helper.assertTrue(machine.getRange() == 4 && machine.boundsInvalidations == 1,
                "public setter did not apply a valid range exactly once");

        machine.setRange(4);
        helper.assertTrue(machine.getRange() == 4 && machine.boundsInvalidations == 1,
                "public setter invalidated bounds for an unchanged range");

        machine.setRange(MIN_RANGE);
        helper.assertTrue(machine.getRange() == MIN_RANGE && machine.boundsInvalidations == 2,
                "public setter rejected the minimum range");

        machine.setRange(MAX_RANGE);
        helper.assertTrue(machine.getRange() == MAX_RANGE && machine.boundsInvalidations == 3,
                "public setter rejected the maximum range");

        assertSetterRejected(helper, machine, MIN_RANGE - 1, "below-minimum range");
        assertSetterRejected(helper, machine, MAX_RANGE + 1, "above-maximum range");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void workingEnabledSyncRetainsSubscriptionRefreshSemantics(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        TestItemCollectorMachine server = createMachine();
        TestItemCollectorMachine client = createMachine();
        TestItemCollectorMachine loaded = createMachine();

        DataComponentMap full = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        assertBooleanField(helper, full, WORKING_ENABLED_FIELD, true,
                "item collector full sync");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, full);
        helper.assertTrue(client.isWorkingEnabled(),
                "item collector full sync disabled the client field");

        server.energyContainer.setEnergyStored(GTValues.V[TIER] * 4L);
        server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        server.resetWorkingTracking();

        server.setWorkingEnabled(true);
        helper.assertTrue(server.collectionRefreshes == 1,
                "identical enabled setter call did not reevaluate the collection subscription");
        helper.assertTrue(server.subscriptionRequests == 1 && server.hasActiveCollectionSubscription(),
                "identical enabled setter call did not restore the powered collection subscription");
        DataComponentMap refreshed = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        assertBooleanField(helper, refreshed, ACTIVE_FIELD, true,
                "item collector restored subscription delta");
        assertFieldAbsent(helper, refreshed, WORKING_ENABLED_FIELD,
                "identical enabled setter call produced a redundant working-enabled delta");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, refreshed);
        helper.assertTrue(client.isWorkingEnabled() && client.isActive(),
                "restored subscription delta changed the wrong client state");

        TickableSubscription restoredSubscription = server.lastSubscription;
        server.setWorkingEnabled(false);
        helper.assertTrue(server.collectionRefreshes == 2,
                "disabled setter call did not reevaluate the collection subscription");
        helper.assertTrue(!server.isWorkingEnabled() && !server.isActive(),
                "disabled setter call retained working or active state");
        helper.assertTrue(restoredSubscription != null && !restoredSubscription.isStillSubscribed() &&
                !server.hasActiveCollectionSubscription(),
                "disabled setter call did not cancel the restored collection subscription");
        DataComponentMap disabled = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        assertBooleanField(helper, disabled, WORKING_ENABLED_FIELD, false,
                "item collector disabled delta");
        assertBooleanField(helper, disabled, ACTIVE_FIELD, false,
                "item collector disabled delta");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, disabled);
        helper.assertTrue(!client.isWorkingEnabled() && !client.isActive(),
                "disabled delta did not update the client state");

        server.setWorkingEnabled(false);
        helper.assertTrue(server.collectionRefreshes == 3,
                "identical disabled setter call skipped subscription reevaluation");
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "identical disabled setter call produced a redundant client delta");

        SyncFieldData saved = server.getSyncDataHolder().serializeToFieldData(registries, false, false);
        assertBooleanField(helper, saved, WORKING_ENABLED_FIELD, false,
                "item collector saved state");
        loaded.getSyncDataHolder().deserializeFieldData(registries, saved, false);
        helper.assertTrue(!loaded.isWorkingEnabled(),
                "item collector did not load the saved working-enabled state");

        ServerFieldUpdateResult rejected = server.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, payload(WORKING_ENABLED_FIELD, new JsonPrimitive(true)));
        helper.assertTrue(!rejected.getAccepted(),
                "item collector accepted a client working-enabled field write");
        helper.assertTrue(!server.isWorkingEnabled() && server.collectionRefreshes == 3,
                "rejected client write changed state or reevaluated the collection subscription");
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "rejected client write produced a client acknowledgement");
        helper.succeed();
    }

    private static TestItemCollectorMachine createMachine() {
        var definition = GTMachines.ITEM_COLLECTOR[TIER];
        return new TestItemCollectorMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()));
    }

    private static ServerFieldUpdateResult apply(TestItemCollectorMachine machine, RegistryAccess registries,
                                                 JsonElement candidate) {
        return machine.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, payload(RANGE_FIELD, candidate));
    }

    private static void assertRejectedUnchanged(GameTestHelper helper, TestItemCollectorMachine machine,
                                                RegistryAccess registries, DataComponentMap components,
                                                String description) {
        int currentRange = machine.getRange();
        int boundsInvalidations = machine.boundsInvalidations;

        ServerFieldUpdateResult result = machine.getSyncDataHolder()
                .tryApplyServerNetworkUpdate(registries, components);

        helper.assertTrue(!result.getAccepted(), description + " was accepted");
        helper.assertTrue(machine.getRange() == currentRange,
                description + " changed the item collector range");
        helper.assertTrue(machine.boundsInvalidations == boundsInvalidations,
                description + " invoked the range change listener");
    }

    private static void assertAcknowledgedRange(GameTestHelper helper, TestItemCollectorMachine machine,
                                                RegistryAccess registries, int expectedRange, String failureMessage) {
        SyncFieldData acknowledgement = machine.getSyncDataHolder()
                .serializeToFieldData(registries, true, false);
        JsonElement acknowledgedValue = acknowledgement.get(RANGE_FIELD);
        helper.assertTrue(acknowledgedValue instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsInt() == expectedRange, failureMessage);
    }

    private static void assertBooleanField(GameTestHelper helper, DataComponentMap components,
                                           ResourceLocation field, boolean expected, String description) {
        assertBooleanField(helper, requireFields(components, description), field, expected, description);
    }

    private static void assertBooleanField(GameTestHelper helper, SyncFieldData fields,
                                           ResourceLocation field, boolean expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " did not contain the expected " + field.getPath() + " state");
    }

    private static void assertFieldAbsent(GameTestHelper helper, DataComponentMap components,
                                          ResourceLocation field, String description) {
        helper.assertTrue(requireFields(components, description).get(field) == null, description);
    }

    private static SyncFieldData requireFields(DataComponentMap components, String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new GameTestAssertException(description + " omitted sync field data");
        }
        return fields;
    }

    private static void assertSetterRejected(GameTestHelper helper, TestItemCollectorMachine machine, int range,
                                             String description) {
        int currentRange = machine.getRange();
        int boundsInvalidations = machine.boundsInvalidations;
        try {
            machine.setRange(range);
        } catch (IllegalArgumentException exception) {
            helper.assertTrue(machine.getRange() == currentRange,
                    description + " changed the item collector range");
            helper.assertTrue(machine.boundsInvalidations == boundsInvalidations,
                    description + " invalidated collection bounds");
            return;
        }
        throw new GameTestAssertException(description + " was accepted by the public setter");
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static final class TestItemCollectorMachine extends ItemCollectorMachine {

        private int boundsInvalidations;
        private int collectionRefreshes;
        private int subscriptionRequests;
        private TickableSubscription lastSubscription;

        private TestItemCollectorMachine(BlockEntityCreationInfo info) {
            super(info, TIER);
        }

        @Override
        protected void invalidateCollectionBounds() {
            super.invalidateCollectionBounds();
            boundsInvalidations++;
        }

        @Override
        public void updateCollectionSubscription() {
            collectionRefreshes++;
            super.updateCollectionSubscription();
        }

        @Override
        public TickableSubscription subscribeServerTick(Runnable runnable) {
            subscriptionRequests++;
            lastSubscription = new TickableSubscription(runnable);
            return lastSubscription;
        }

        private boolean hasActiveCollectionSubscription() {
            return collectionSubs != null && collectionSubs.isStillSubscribed();
        }

        private void resetWorkingTracking() {
            collectionRefreshes = 0;
            subscriptionRequests = 0;
            lastSubscription = null;
        }
    }
}

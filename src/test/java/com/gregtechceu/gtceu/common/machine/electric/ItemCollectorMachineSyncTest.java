package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
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

        private TestItemCollectorMachine(BlockEntityCreationInfo info) {
            super(info, TIER);
        }

        @Override
        protected void invalidateCollectionBounds() {
            super.invalidateCollectionBounds();
            boundsInvalidations++;
        }
    }
}

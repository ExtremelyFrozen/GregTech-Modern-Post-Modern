package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.machines.GCYMMachines;

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
import com.google.gson.JsonPrimitive;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class ParallelHatchPartMachineSyncTest {

    private static final String BATCH = "ParallelHatchPartMachineSync";
    private static final ResourceLocation CURRENT_PARALLEL_FIELD = SyncFieldData.key("currentParallel");
    private static final ResourceLocation UNKNOWN_FIELD = SyncFieldData.key("unknown");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void serverUpdatesClampAndNotifyOnlyForCanonicalChanges(GameTestHelper helper) {
        TestParallelHatchPartMachine machine = createMachine();
        RegistryAccess registries = helper.getLevel().registryAccess();

        helper.assertTrue(machine.getCurrentParallel() == 4,
                "IV parallel hatch did not initialize to its maximum parallel value");
        machine.getSyncDataHolder().serializeFullClientSyncData(registries);

        ServerFieldUpdateResult changed = apply(machine, registries, new JsonPrimitive(3));

        helper.assertTrue(changed.getAccepted() && changed.getChanged(),
                "valid changed current parallel update was not accepted as changed");
        helper.assertTrue(machine.getCurrentParallel() == 3,
                "valid changed current parallel update did not commit");
        helper.assertTrue(machine.recipeDirtyCalls == 1,
                "valid changed current parallel update did not dirty controller recipes exactly once");

        ServerFieldUpdateResult unchanged = apply(machine, registries, new JsonPrimitive(3));

        helper.assertTrue(unchanged.getAccepted() && !unchanged.getChanged(),
                "repeated canonical current parallel update was not accepted as unchanged");
        helper.assertTrue(machine.getCurrentParallel() == 3 && machine.recipeDirtyCalls == 1,
                "repeated canonical current parallel update changed state or repeated its listener");

        ServerFieldUpdateResult clampedMinimum = apply(machine, registries, new JsonPrimitive(0));

        helper.assertTrue(clampedMinimum.getAccepted() && clampedMinimum.getChanged(),
                "below-minimum current parallel update was not accepted as a canonical change");
        helper.assertTrue(machine.getCurrentParallel() == 1 && machine.recipeDirtyCalls == 2,
                "below-minimum current parallel update did not commit canonical value 1 once");

        machine.getSyncDataHolder().serializeFullClientSyncData(registries);

        ServerFieldUpdateResult clampedMaximum = apply(machine, registries, new JsonPrimitive(8));

        helper.assertTrue(clampedMaximum.getAccepted() && clampedMaximum.getChanged(),
                "above-maximum current parallel update was not accepted as a canonical change");
        helper.assertTrue(machine.getCurrentParallel() == 4 && machine.recipeDirtyCalls == 3,
                "above-maximum current parallel update did not commit canonical value 4 once");

        SyncFieldData acknowledgement = machine.getSyncDataHolder()
                .serializeToFieldData(registries, true, false);
        JsonElement acknowledgedValue = acknowledgement.get(CURRENT_PARALLEL_FIELD);
        helper.assertTrue(acknowledgedValue instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsInt() == 4,
                "above-maximum update did not produce an authoritative acknowledgement with canonical value 4");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void invalidServerUpdatesLeaveCurrentParallelAndListenerUntouched(GameTestHelper helper) {
        TestParallelHatchPartMachine machine = createMachine();
        RegistryAccess registries = helper.getLevel().registryAccess();

        assertRejectedUnchanged(helper, machine, registries,
                payload(CURRENT_PARALLEL_FIELD, new JsonPrimitive("3")), "string candidate");
        assertRejectedUnchanged(helper, machine, registries,
                payload(CURRENT_PARALLEL_FIELD, new JsonPrimitive(1.5)), "decimal candidate");
        assertRejectedUnchanged(helper, machine, registries,
                payload(CURRENT_PARALLEL_FIELD, new JsonPrimitive((long) Integer.MAX_VALUE + 1L)),
                "out-of-range integer candidate");
        assertRejectedUnchanged(helper, machine, registries,
                payload(UNKNOWN_FIELD, new JsonPrimitive(3)), "unknown field");

        DataComponentMap missingSyncComponent = DataComponentMap.builder()
                .set(DataComponents.CUSTOM_NAME, Component.literal("missing sync field data"))
                .build();
        assertRejectedUnchanged(helper, machine, registries, missingSyncComponent, "missing sync component");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void publicSetterClampsAndDirtiesRecipesOnlyForCanonicalChanges(GameTestHelper helper) {
        TestParallelHatchPartMachine machine = createMachine();

        machine.setCurrentParallel(0);
        helper.assertTrue(machine.getCurrentParallel() == 1 && machine.recipeDirtyCalls == 1,
                "public setter did not clamp to 1 and dirty recipes once");

        machine.setCurrentParallel(0);
        helper.assertTrue(machine.getCurrentParallel() == 1 && machine.recipeDirtyCalls == 1,
                "public setter repeated recipe invalidation for an unchanged canonical value");

        machine.setCurrentParallel(8);
        helper.assertTrue(machine.getCurrentParallel() == 4 && machine.recipeDirtyCalls == 2,
                "public setter did not clamp to 4 and dirty recipes once");

        machine.setCurrentParallel(8);
        helper.assertTrue(machine.getCurrentParallel() == 4 && machine.recipeDirtyCalls == 2,
                "public setter repeated recipe invalidation at the canonical maximum");
        helper.succeed();
    }

    private static TestParallelHatchPartMachine createMachine() {
        var definition = GCYMMachines.PARALLEL_HATCH[GTValues.IV];
        return new TestParallelHatchPartMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()));
    }

    private static ServerFieldUpdateResult apply(TestParallelHatchPartMachine machine, RegistryAccess registries,
                                                 JsonElement candidate) {
        return machine.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, payload(CURRENT_PARALLEL_FIELD, candidate));
    }

    private static void assertRejectedUnchanged(GameTestHelper helper, TestParallelHatchPartMachine machine,
                                                RegistryAccess registries, DataComponentMap components,
                                                String description) {
        int currentParallel = machine.getCurrentParallel();
        int recipeDirtyCalls = machine.recipeDirtyCalls;

        ServerFieldUpdateResult result = machine.getSyncDataHolder()
                .tryApplyServerNetworkUpdate(registries, components);

        helper.assertTrue(!result.getAccepted(), description + " was accepted");
        helper.assertTrue(machine.getCurrentParallel() == currentParallel,
                description + " changed current parallel");
        helper.assertTrue(machine.recipeDirtyCalls == recipeDirtyCalls,
                description + " invoked the server field listener");
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static final class TestParallelHatchPartMachine extends ParallelHatchPartMachine {

        private int recipeDirtyCalls;

        private TestParallelHatchPartMachine(BlockEntityCreationInfo info) {
            super(info, GTValues.IV);
        }

        @Override
        protected void markControllerRecipesDirty() {
            recipeDirtyCalls++;
        }
    }
}

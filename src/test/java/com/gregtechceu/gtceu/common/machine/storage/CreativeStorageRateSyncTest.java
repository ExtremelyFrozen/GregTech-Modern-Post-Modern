package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.element.GTTextFieldElement;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
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
public class CreativeStorageRateSyncTest {

    private static final String BATCH = "CreativeStorageRateSync";
    private static final ResourceLocation ITEMS_PER_CYCLE_FIELD = SyncFieldData.key("itemsPerCycle");
    private static final ResourceLocation MB_PER_CYCLE_FIELD = SyncFieldData.key("mBPerCycle");
    private static final ResourceLocation TICKS_PER_CYCLE_FIELD = SyncFieldData.key("ticksPerCycle");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creativeChestAcceptsChangedAndNoOpRateBatches(GameTestHelper helper) {
        TestCreativeChestMachine chest = createChest(false);
        RegistryAccess registries = helper.getLevel().registryAccess();
        chest.getSyncDataHolder().serializeFullClientSyncData(registries);
        chest.resetItemChangeCalls();

        ServerFieldUpdateResult changed = apply(chest, registries,
                payload(ITEMS_PER_CYCLE_FIELD, 64, 5));

        helper.assertTrue(changed.getAccepted() && changed.getChanged(),
                "creative chest rejected a valid changed rate batch");
        assertChestState(helper, chest, 64, 5, 2, "changed creative chest rate batch");
        assertFields(helper, chest.getSyncDataHolder().serializeToFieldData(registries, true, false),
                ITEMS_PER_CYCLE_FIELD, 64, 5, "changed creative chest acknowledgement");

        ServerFieldUpdateResult noOp = apply(chest, registries,
                payload(ITEMS_PER_CYCLE_FIELD, 64, 5));

        helper.assertTrue(noOp.getAccepted() && !noOp.getChanged(),
                "creative chest did not accept an identical rate batch as a no-op");
        assertChestState(helper, chest, 64, 5, 2, "no-op creative chest rate batch");
        assertOnlyFields(helper, chest.getSyncDataHolder().serializeToFieldData(registries, true, false),
                ITEMS_PER_CYCLE_FIELD, 64, 5, "no-op creative chest acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creativeTankAcceptsChangedAndNoOpRateBatches(GameTestHelper helper) {
        TestCreativeTankMachine tank = createTank(false);
        RegistryAccess registries = helper.getLevel().registryAccess();
        tank.getSyncDataHolder().serializeFullClientSyncData(registries);
        tank.resetFluidChangeCalls();

        ServerFieldUpdateResult changed = apply(tank, registries,
                payload(MB_PER_CYCLE_FIELD, 2_000, 4));

        helper.assertTrue(changed.getAccepted() && changed.getChanged(),
                "creative tank rejected a valid changed rate batch");
        assertTankState(helper, tank, 2_000, 4, 2, "changed creative tank rate batch");
        assertFields(helper, tank.getSyncDataHolder().serializeToFieldData(registries, true, false),
                MB_PER_CYCLE_FIELD, 2_000, 4, "changed creative tank acknowledgement");

        ServerFieldUpdateResult noOp = apply(tank, registries,
                payload(MB_PER_CYCLE_FIELD, 2_000, 4));

        helper.assertTrue(noOp.getAccepted() && !noOp.getChanged(),
                "creative tank did not accept an identical rate batch as a no-op");
        assertTankState(helper, tank, 2_000, 4, 2, "no-op creative tank rate batch");
        assertOnlyFields(helper, tank.getSyncDataHolder().serializeToFieldData(registries, true, false),
                MB_PER_CYCLE_FIELD, 2_000, 4, "no-op creative tank acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creativeChestRejectsNonPositiveRatesAtomically(GameTestHelper helper) {
        TestCreativeChestMachine chest = createChest(false);
        RegistryAccess registries = helper.getLevel().registryAccess();
        chest.getSyncDataHolder().serializeFullClientSyncData(registries);
        apply(chest, registries, payload(ITEMS_PER_CYCLE_FIELD, 64, 5));
        chest.getSyncDataHolder().serializeToFieldData(registries, true, false);
        int listenerCalls = chest.itemChangeCalls;

        ServerFieldUpdateResult zeroRate = apply(chest, registries,
                payload(ITEMS_PER_CYCLE_FIELD, 0, 6));

        helper.assertTrue(!zeroRate.getAccepted(), "creative chest accepted a zero items-per-cycle candidate");
        assertChestState(helper, chest, 64, 5, listenerCalls,
                "creative chest after zero items-per-cycle rejection");
        assertOnlyFields(helper, chest.getSyncDataHolder().serializeToFieldData(registries, true, false),
                ITEMS_PER_CYCLE_FIELD, 64, 5, "zero items-per-cycle rejection acknowledgement");

        ServerFieldUpdateResult negativeTicks = apply(chest, registries,
                payload(ITEMS_PER_CYCLE_FIELD, 65, -1));

        helper.assertTrue(!negativeTicks.getAccepted(), "creative chest accepted negative ticks per cycle");
        assertChestState(helper, chest, 64, 5, listenerCalls,
                "creative chest after negative ticks-per-cycle rejection");
        assertOnlyFields(helper, chest.getSyncDataHolder().serializeToFieldData(registries, true, false),
                ITEMS_PER_CYCLE_FIELD, 64, 5, "negative chest ticks rejection acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void creativeTankRejectsNonPositiveRatesAtomically(GameTestHelper helper) {
        TestCreativeTankMachine tank = createTank(false);
        RegistryAccess registries = helper.getLevel().registryAccess();
        tank.getSyncDataHolder().serializeFullClientSyncData(registries);
        apply(tank, registries, payload(MB_PER_CYCLE_FIELD, 2_000, 4));
        tank.getSyncDataHolder().serializeToFieldData(registries, true, false);
        int listenerCalls = tank.fluidChangeCalls;

        ServerFieldUpdateResult negativeRate = apply(tank, registries,
                payload(MB_PER_CYCLE_FIELD, -1, 5));

        helper.assertTrue(!negativeRate.getAccepted(), "creative tank accepted negative millibuckets per cycle");
        assertTankState(helper, tank, 2_000, 4, listenerCalls,
                "creative tank after negative millibuckets-per-cycle rejection");
        assertOnlyFields(helper, tank.getSyncDataHolder().serializeToFieldData(registries, true, false),
                MB_PER_CYCLE_FIELD, 2_000, 4, "negative millibuckets-per-cycle rejection acknowledgement");

        ServerFieldUpdateResult zeroTicks = apply(tank, registries,
                payload(MB_PER_CYCLE_FIELD, 2_001, 0));

        helper.assertTrue(!zeroTicks.getAccepted(), "creative tank accepted zero ticks per cycle");
        assertTankState(helper, tank, 2_000, 4, listenerCalls,
                "creative tank after zero ticks-per-cycle rejection");
        assertOnlyFields(helper, tank.getSyncDataHolder().serializeToFieldData(registries, true, false),
                MB_PER_CYCLE_FIELD, 2_000, 4, "zero tank ticks rejection acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void ldlib2RateFieldsUpdateOwnersFlushAndExposeCandidates(GameTestHelper helper) {
        TestCreativeChestMachine chest = createChest(true);
        TestCreativeTankMachine tank = createTank(true);
        RegistryAccess registries = helper.getLevel().registryAccess();
        chest.getSyncDataHolder().collectServerNetworkChanges(registries);
        tank.getSyncDataHolder().collectServerNetworkChanges(registries);
        GTTextFieldElement chestRate = chest.createLDLib2ItemsPerCycleField();
        GTTextFieldElement chestTicks = chest.createLDLib2TicksPerCycleField();
        GTTextFieldElement tankRate = tank.createLDLib2MillibucketsPerCycleField();
        GTTextFieldElement tankTicks = tank.createLDLib2TicksPerCycleField();

        chestRate.setText("64", true);
        chestTicks.setText("5", true);
        tankRate.setText("2000", true);
        tankTicks.setText("4", true);

        assertChestState(helper, chest, 64, 5, 2, "LDLib2 creative chest rate fields");
        assertTankState(helper, tank, 2_000, 4, 2, "LDLib2 creative tank rate fields");
        helper.assertTrue(chest.syncRequests == 2 && tank.syncRequests == 2,
                "LDLib2 creative rate fields did not flush each owner change exactly once");
        assertOnlyFields(helper, requireFieldData(chest.getSyncDataHolder().collectServerNetworkChanges(registries)),
                ITEMS_PER_CYCLE_FIELD, 64, 5, "creative chest rate field request");
        assertOnlyFields(helper, requireFieldData(tank.getSyncDataHolder().collectServerNetworkChanges(registries)),
                MB_PER_CYCLE_FIELD, 2_000, 4, "creative tank rate field request");
        helper.succeed();
    }

    private static TestCreativeChestMachine createChest(boolean clientSide) {
        var definition = GTMachines.CREATIVE_ITEM;
        return new TestCreativeChestMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()), clientSide);
    }

    private static TestCreativeTankMachine createTank(boolean clientSide) {
        var definition = GTMachines.CREATIVE_FLUID;
        return new TestCreativeTankMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()), clientSide);
    }

    private static ServerFieldUpdateResult apply(MetaMachine machine, RegistryAccess registries,
                                                 DataComponentMap components) {
        return machine.getSyncDataHolder().tryApplyServerNetworkUpdate(registries, components);
    }

    private static DataComponentMap payload(ResourceLocation rateField, int rate, int ticksPerCycle) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(rateField, new JsonPrimitive(rate))
                        .put(TICKS_PER_CYCLE_FIELD, new JsonPrimitive(ticksPerCycle))
                        .build())
                .build();
    }

    private static SyncFieldData requireFieldData(DataComponentMap components) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Creative storage rate update omitted sync field data.");
        }
        return fields;
    }

    private static void assertChestState(GameTestHelper helper, TestCreativeChestMachine chest, int itemsPerCycle,
                                         int ticksPerCycle, int listenerCalls, String description) {
        helper.assertTrue(chest.getItemsPerCycle() == itemsPerCycle && chest.getTicksPerCycle() == ticksPerCycle,
                description + " did not retain the expected root field values");
        helper.assertTrue(chest.autoOutput.getTicksPerCycle() == ticksPerCycle,
                description + " did not update the item auto-output period");
        helper.assertTrue(chest.itemChangeCalls == listenerCalls,
                description + " invoked item-change side effects an unexpected number of times");
    }

    private static void assertTankState(GameTestHelper helper, TestCreativeTankMachine tank, int mBPerCycle,
                                        int ticksPerCycle, int listenerCalls, String description) {
        helper.assertTrue(tank.getMBPerCycle() == mBPerCycle && tank.getTicksPerCycle() == ticksPerCycle,
                description + " did not retain the expected root field values");
        helper.assertTrue(tank.autoOutput.getTicksPerCycle() == ticksPerCycle,
                description + " did not update the fluid auto-output period");
        helper.assertTrue(tank.fluidChangeCalls == listenerCalls,
                description + " invoked fluid-change side effects an unexpected number of times");
    }

    private static void assertOnlyFields(GameTestHelper helper, SyncFieldData fields, ResourceLocation rateField,
                                         int rate, int ticksPerCycle, String description) {
        helper.assertTrue(fields.fields().size() == 2,
                description + " contained fields other than rate and ticks per cycle");
        assertFields(helper, fields, rateField, rate, ticksPerCycle, description);
    }

    private static void assertFields(GameTestHelper helper, SyncFieldData fields, ResourceLocation rateField,
                                     int rate, int ticksPerCycle, String description) {
        assertIntegerField(helper, fields, rateField, rate, description + " rate");
        assertIntegerField(helper, fields, TICKS_PER_CYCLE_FIELD, ticksPerCycle, description + " ticks");
    }

    private static void assertIntegerField(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                           int expected, String description) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsInt() == expected, description + " did not contain the expected integer");
    }

    private static final class TestCreativeChestMachine extends CreativeChestMachine {

        private final boolean clientSide;
        private int itemChangeCalls;
        private int syncRequests;

        private TestCreativeChestMachine(BlockEntityCreationInfo info, boolean clientSide) {
            super(info);
            this.clientSide = clientSide;
        }

        @Override
        public boolean isRemote() {
            return clientSide;
        }

        @Override
        protected void onItemChanged() {
            super.onItemChanged();
            itemChangeCalls++;
        }

        @Override
        public void sendServerSyncChanges() {
            syncRequests++;
        }

        private void resetItemChangeCalls() {
            itemChangeCalls = 0;
        }
    }

    private static final class TestCreativeTankMachine extends CreativeTankMachine {

        private final boolean clientSide;
        private int fluidChangeCalls;
        private int syncRequests;

        private TestCreativeTankMachine(BlockEntityCreationInfo info, boolean clientSide) {
            super(info);
            this.clientSide = clientSide;
        }

        @Override
        public boolean isRemote() {
            return clientSide;
        }

        @Override
        protected void onFluidChanged() {
            super.onFluidChanged();
            fluidChangeCalls++;
        }

        @Override
        public void sendServerSyncChanges() {
            syncRequests++;
        }

        private void resetFluidChangeCalls() {
            fluidChangeCalls = 0;
        }
    }
}

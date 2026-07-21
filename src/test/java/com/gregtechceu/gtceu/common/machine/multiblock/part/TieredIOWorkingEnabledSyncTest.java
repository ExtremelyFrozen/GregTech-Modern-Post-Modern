package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
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

import static com.gregtechceu.gtceu.api.GTValues.LV;
import static com.gregtechceu.gtceu.api.GTValues.LuV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class TieredIOWorkingEnabledSyncTest {

    private static final String BATCH = "TieredIOWorkingEnabledSync";
    private static final ResourceLocation WORKING_ENABLED_FIELD = SyncFieldData.key("workingEnabled");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void basePartUsesTheSameHookForSetterAndServerUpdate(GameTestHelper helper) {
        assertWorkingEnabledSync(helper, createBasePart(), createBasePart(), "base tiered IO part");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void itemBusPreservesInventorySubscriptionHook(GameTestHelper helper) {
        assertWorkingEnabledSync(helper, createItemBus(), createItemBus(), "item bus");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fluidHatchPreservesTankSubscriptionHook(GameTestHelper helper) {
        assertWorkingEnabledSync(helper, createFluidHatch(), createFluidHatch(), "fluid hatch");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void dualHatchDynamicallyDispatchesTheInheritedItemBusHook(GameTestHelper helper) {
        assertWorkingEnabledSync(helper, createDualHatch(), createDualHatch(), "dual hatch");
        helper.succeed();
    }

    private static <T extends MetaMachine & HookTrackingMachine> void assertWorkingEnabledSync(
                                                                                               GameTestHelper helper,
                                                                                               T directMachine,
                                                                                               T networkMachine,
                                                                                               String owner) {
        directMachine.resetWorkingEnabledHookCalls();

        directMachine.setWorkingEnabled(false);

        helper.assertTrue(!directMachine.isWorkingEnabled(), owner + " direct setter did not change the field");
        helper.assertTrue(directMachine.getWorkingEnabledHookCalls() == 1,
                owner + " direct setter did not invoke its side-effect hook exactly once");

        RegistryAccess registries = helper.getLevel().registryAccess();
        networkMachine.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        networkMachine.resetWorkingEnabledHookCalls();

        ServerFieldUpdateResult changed = apply(networkMachine, registries, false);

        helper.assertTrue(changed.getAccepted() && changed.getChanged(),
                owner + " rejected a changed working-enabled update");
        helper.assertTrue(!networkMachine.isWorkingEnabled(),
                owner + " did not commit the changed working-enabled value");
        helper.assertTrue(networkMachine.getWorkingEnabledHookCalls() == 1,
                owner + " server listener did not invoke its side-effect hook exactly once");

        ServerFieldUpdateResult noOp = apply(networkMachine, registries, false);

        helper.assertTrue(noOp.getAccepted() && !noOp.getChanged(),
                owner + " did not accept an identical working-enabled update as a no-op");
        helper.assertTrue(networkMachine.getWorkingEnabledHookCalls() == 1,
                owner + " no-op update invoked its side-effect hook");

        SyncFieldData acknowledgement = networkMachine.getSyncDataHolder()
                .serializeToFieldData(registries, true, false);
        JsonElement acknowledgedValue = acknowledgement.get(WORKING_ENABLED_FIELD);
        helper.assertTrue(acknowledgedValue instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                !primitive.getAsBoolean(), owner + " did not acknowledge the authoritative false value");
    }

    private static ServerFieldUpdateResult apply(MetaMachine machine, RegistryAccess registries,
                                                 boolean workingEnabled) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(WORKING_ENABLED_FIELD, new JsonPrimitive(workingEnabled))
                        .build())
                .build();
        return machine.getSyncDataHolder().tryApplyServerNetworkUpdate(registries, payload);
    }

    private static TestTieredIOPartMachine createBasePart() {
        return new TestTieredIOPartMachine(info(GTMachines.ITEM_IMPORT_BUS[LV]), LV, IO.IN);
    }

    private static TestItemBusPartMachine createItemBus() {
        return new TestItemBusPartMachine(info(GTMachines.ITEM_IMPORT_BUS[LV]), LV, IO.IN);
    }

    private static TestFluidHatchPartMachine createFluidHatch() {
        return new TestFluidHatchPartMachine(info(GTMachines.FLUID_IMPORT_HATCH[LV]), LV, IO.IN,
                FluidHatchPartMachine.INITIAL_TANK_CAPACITY_1X, 1);
    }

    private static TestDualHatchPartMachine createDualHatch() {
        return new TestDualHatchPartMachine(info(GTMachines.DUAL_IMPORT_HATCH[LuV]), LuV, IO.IN);
    }

    private static BlockEntityCreationInfo info(MachineDefinition definition) {
        return new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.defaultBlockState());
    }

    private interface HookTrackingMachine extends IControllable {

        int getWorkingEnabledHookCalls();

        void resetWorkingEnabledHookCalls();
    }

    private static final class TestTieredIOPartMachine extends TieredIOPartMachine
                                                       implements HookTrackingMachine {

        private int workingEnabledHookCalls;

        private TestTieredIOPartMachine(BlockEntityCreationInfo info, int tier, IO io) {
            super(info, tier, io);
        }

        @Override
        protected void onWorkingEnabledChanged() {
            workingEnabledHookCalls++;
            super.onWorkingEnabledChanged();
        }

        @Override
        public int getWorkingEnabledHookCalls() {
            return workingEnabledHookCalls;
        }

        @Override
        public void resetWorkingEnabledHookCalls() {
            workingEnabledHookCalls = 0;
        }
    }

    private static final class TestItemBusPartMachine extends ItemBusPartMachine implements HookTrackingMachine {

        private int workingEnabledHookCalls;

        private TestItemBusPartMachine(BlockEntityCreationInfo info, int tier, IO io) {
            super(info, tier, io);
        }

        @Override
        protected void updateInventorySubscription() {
            workingEnabledHookCalls++;
            super.updateInventorySubscription();
        }

        @Override
        public int getWorkingEnabledHookCalls() {
            return workingEnabledHookCalls;
        }

        @Override
        public void resetWorkingEnabledHookCalls() {
            workingEnabledHookCalls = 0;
        }
    }

    private static final class TestFluidHatchPartMachine extends FluidHatchPartMachine
                                                         implements HookTrackingMachine {

        private int workingEnabledHookCalls;

        private TestFluidHatchPartMachine(BlockEntityCreationInfo info, int tier, IO io, int initialCapacity,
                                          int slots) {
            super(info, tier, io, initialCapacity, slots);
        }

        @Override
        protected void updateTankSubscription() {
            workingEnabledHookCalls++;
            super.updateTankSubscription();
        }

        @Override
        public int getWorkingEnabledHookCalls() {
            return workingEnabledHookCalls;
        }

        @Override
        public void resetWorkingEnabledHookCalls() {
            workingEnabledHookCalls = 0;
        }
    }

    private static final class TestDualHatchPartMachine extends DualHatchPartMachine implements HookTrackingMachine {

        private int workingEnabledHookCalls;

        private TestDualHatchPartMachine(BlockEntityCreationInfo info, int tier, IO io) {
            super(info, tier, io);
        }

        @Override
        protected void updateInventorySubscription() {
            workingEnabledHookCalls++;
            super.updateInventorySubscription();
        }

        @Override
        public int getWorkingEnabledHookCalls() {
            return workingEnabledHookCalls;
        }

        @Override
        public void resetWorkingEnabledHookCalls() {
            workingEnabledHookCalls = 0;
        }
    }
}

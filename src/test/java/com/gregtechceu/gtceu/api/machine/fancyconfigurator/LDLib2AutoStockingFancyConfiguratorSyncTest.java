package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTIntInputElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.multiblock.AutoStockingPart;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

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
public class LDLib2AutoStockingFancyConfiguratorSyncTest {

    private static final String BATCH = "LDLib2AutoStockingFancyConfiguratorSync";
    private static final ResourceLocation MIN_STACK_SIZE_FIELD = SyncFieldData.key("minStackSize");
    private static final ResourceLocation TICKS_PER_CYCLE_FIELD = SyncFieldData.key("ticksPerCycle");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void stockingBusFieldsAcceptChangedAndNoOpBatchesWithAcknowledgements(GameTestHelper helper) {
        TestStockingBusMachine machine = createBus(false);

        assertChangedAndNoOpBatches(helper, machine, machine, "stocking bus");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void stockingHatchFieldsAcceptChangedAndNoOpBatchesWithAcknowledgements(GameTestHelper helper) {
        TestStockingHatchMachine machine = createHatch(false);

        assertChangedAndNoOpBatches(helper, machine, machine, "stocking hatch");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void stockingBusRejectsBothLowerBoundViolationsAtomically(GameTestHelper helper) {
        TestStockingBusMachine machine = createBus(false);

        assertLowerBoundsAreRejected(helper, machine, machine, "stocking bus");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void stockingHatchRejectsBothLowerBoundViolationsAtomically(GameTestHelper helper) {
        TestStockingHatchMachine machine = createHatch(false);

        assertLowerBoundsAreRejected(helper, machine, machine, "stocking hatch");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void ldlib2IncrementButtonsUpdateBothOwnersAndFlushEachChange(GameTestHelper helper) {
        TestStockingBusMachine bus = createBus(true);
        TestStockingHatchMachine hatch = createHatch(true);
        RegistryAccess registries = helper.getLevel().registryAccess();
        int baselineTicks = validTicksPerCycle() - 1;
        bus.setTicksPerCycle(baselineTicks);
        hatch.setTicksPerCycle(baselineTicks);
        bus.getSyncDataHolder().collectServerNetworkChanges(registries);
        hatch.getSyncDataHolder().collectServerNetworkChanges(registries);

        clickBothIncrementButtons(bus, new TestMachineUIHolder(bus));
        clickBothIncrementButtons(hatch, new TestMachineUIHolder(hatch));

        helper.assertTrue(bus.getMinStackSize() == 2 && bus.getTicksPerCycle() == baselineTicks + 1,
                "stocking bus LDLib2 increment buttons did not update both local fields");
        helper.assertTrue(hatch.getMinStackSize() == 2 && hatch.getTicksPerCycle() == baselineTicks + 1,
                "stocking hatch LDLib2 increment buttons did not update both local fields");
        helper.assertTrue(bus.syncRequests == 2 && hatch.syncRequests == 2,
                "auto-stocking LDLib2 increment buttons did not flush each owner change exactly once");
        assertRequest(helper, bus.getSyncDataHolder().collectServerNetworkChanges(registries), 2,
                baselineTicks + 1, "stocking bus button request");
        assertRequest(helper, hatch.getSyncDataHolder().collectServerNetworkChanges(registries), 2,
                baselineTicks + 1, "stocking hatch button request");
        helper.succeed();
    }

    private static void assertChangedAndNoOpBatches(GameTestHelper helper, MetaMachine machine,
                                                    AutoStockingPart stocking, String owner) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        int minStackSize = 1;
        int ticksPerCycle = ConfigHolder.INSTANCE.compat.ae2.updateIntervals;
        stocking.setMinStackSize(2);
        stocking.setTicksPerCycle(validTicksPerCycle());
        machine.getSyncDataHolder().serializeFullClientSyncData(registries);

        ServerFieldUpdateResult changed = apply(machine, registries,
                payload(minStackSize, ticksPerCycle));

        helper.assertTrue(changed.getAccepted() && changed.getChanged(),
                owner + " rejected a valid changed field batch");
        helper.assertTrue(stocking.getMinStackSize() == minStackSize &&
                stocking.getTicksPerCycle() == ticksPerCycle,
                owner + " did not commit both valid field candidates");
        assertAcknowledgement(helper, machine, registries, minStackSize, ticksPerCycle,
                owner + " changed acknowledgement");

        ServerFieldUpdateResult noOp = apply(machine, registries,
                payload(minStackSize, ticksPerCycle));

        helper.assertTrue(noOp.getAccepted() && !noOp.getChanged(),
                owner + " did not accept an identical field batch as a no-op");
        assertAcknowledgement(helper, machine, registries, minStackSize, ticksPerCycle,
                owner + " no-op acknowledgement");
    }

    private static void assertLowerBoundsAreRejected(GameTestHelper helper, MetaMachine machine,
                                                     AutoStockingPart stocking, String owner) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        int originalMinStackSize = stocking.getMinStackSize();
        int originalTicksPerCycle = stocking.getTicksPerCycle();
        int belowTicksMinimum = ConfigHolder.INSTANCE.compat.ae2.updateIntervals - 1;
        machine.getSyncDataHolder().serializeFullClientSyncData(registries);

        ServerFieldUpdateResult invalidMin = apply(machine, registries,
                payload(MIN_STACK_SIZE_FIELD, new JsonPrimitive(0)));
        ServerFieldUpdateResult invalidTicks = apply(machine, registries,
                payload(TICKS_PER_CYCLE_FIELD, new JsonPrimitive(belowTicksMinimum)));
        ServerFieldUpdateResult invalidAtomicBatch = apply(machine, registries,
                payload(16, belowTicksMinimum));

        helper.assertTrue(!invalidMin.getAccepted(), owner + " accepted zero minimum stack size");
        helper.assertTrue(!invalidTicks.getAccepted(), owner + " accepted ticks below the configured minimum");
        helper.assertTrue(!invalidAtomicBatch.getAccepted(), owner + " accepted a partially invalid field batch");
        helper.assertTrue(stocking.getMinStackSize() == originalMinStackSize &&
                stocking.getTicksPerCycle() == originalTicksPerCycle,
                owner + " partially committed a rejected field batch");
    }

    private static void clickBothIncrementButtons(AutoStockingPart machine, MachineUIHolder holder) {
        UIElement root = new LDLib2AutoStockingFancyConfigurator(machine, holder).createLDLib2Configurator();
        GTIntInputElement minStackSize = (GTIntInputElement) root.getChildren().get(1);
        GTIntInputElement ticksPerCycle = (GTIntInputElement) root.getChildren().get(3);
        GTButtonElement minIncrement = (GTButtonElement) minStackSize.getChildren().get(2);
        GTButtonElement ticksIncrement = (GTButtonElement) ticksPerCycle.getChildren().get(2);

        minIncrement.onClick(UIEvent.create(UIEvents.MOUSE_DOWN));
        ticksIncrement.onClick(UIEvent.create(UIEvents.MOUSE_DOWN));
    }

    private static TestStockingBusMachine createBus(boolean clientSide) {
        var definition = GTAEMachines.STOCKING_IMPORT_BUS_ME;
        return new TestStockingBusMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()), clientSide);
    }

    private static TestStockingHatchMachine createHatch(boolean clientSide) {
        var definition = GTAEMachines.STOCKING_IMPORT_HATCH_ME;
        return new TestStockingHatchMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()), clientSide);
    }

    private static int validTicksPerCycle() {
        return Math.max(40, ConfigHolder.INSTANCE.compat.ae2.updateIntervals) + 1;
    }

    private static ServerFieldUpdateResult apply(MetaMachine machine, RegistryAccess registries,
                                                 DataComponentMap components) {
        return machine.getSyncDataHolder().tryApplyServerNetworkUpdate(registries, components);
    }

    private static void assertAcknowledgement(GameTestHelper helper, MetaMachine machine,
                                              RegistryAccess registries, int expectedMinStackSize,
                                              int expectedTicksPerCycle, String description) {
        SyncFieldData fields = machine.getSyncDataHolder().serializeToFieldData(registries, true, false);
        assertFields(helper, fields, expectedMinStackSize, expectedTicksPerCycle,
                description + " did not contain both canonical fields");
    }

    private static void assertRequest(GameTestHelper helper, DataComponentMap components, int expectedMinStackSize,
                                      int expectedTicksPerCycle, String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        helper.assertTrue(fields != null, description + " did not contain sync field data");
        assertFields(helper, fields, expectedMinStackSize, expectedTicksPerCycle,
                description + " did not contain both changed fields");
    }

    private static void assertFields(GameTestHelper helper, SyncFieldData fields, int expectedMinStackSize,
                                     int expectedTicksPerCycle, String message) {
        JsonElement minStackSize = fields.get(MIN_STACK_SIZE_FIELD);
        JsonElement ticksPerCycle = fields.get(TICKS_PER_CYCLE_FIELD);
        helper.assertTrue(fields.fields().size() == 2 &&
                minStackSize instanceof JsonPrimitive minPrimitive && minPrimitive.isNumber() &&
                minPrimitive.getAsInt() == expectedMinStackSize &&
                ticksPerCycle instanceof JsonPrimitive ticksPrimitive && ticksPrimitive.isNumber() &&
                ticksPrimitive.getAsInt() == expectedTicksPerCycle,
                message);
    }

    private static DataComponentMap payload(int minStackSize, int ticksPerCycle) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(MIN_STACK_SIZE_FIELD, new JsonPrimitive(minStackSize))
                        .put(TICKS_PER_CYCLE_FIELD, new JsonPrimitive(ticksPerCycle))
                        .build())
                .build();
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static final class TestStockingBusMachine extends MEStockingBusPartMachine {

        private final boolean clientSide;
        private int syncRequests;

        private TestStockingBusMachine(BlockEntityCreationInfo info, boolean clientSide) {
            super(info);
            this.clientSide = clientSide;
        }

        @Override
        public boolean isRemote() {
            return clientSide;
        }

        @Override
        public void sendServerSyncChanges() {
            syncRequests++;
        }
    }

    private static final class TestStockingHatchMachine extends MEStockingHatchPartMachine {

        private final boolean clientSide;
        private int syncRequests;

        private TestStockingHatchMachine(BlockEntityCreationInfo info, boolean clientSide) {
            super(info);
            this.clientSide = clientSide;
        }

        @Override
        public boolean isRemote() {
            return clientSide;
        }

        @Override
        public void sendServerSyncChanges() {
            syncRequests++;
        }
    }

    private record TestMachineUIHolder(MetaMachine machine) implements MachineUIHolder {

        @Override
        public BlockPos getPos() {
            return machine.getBlockPos();
        }

        @Override
        public ResourceLocation getMachineDefinitionId() {
            return machine.getDefinition().getId();
        }

        @Override
        public MetaMachine getMachine() {
            return machine;
        }
    }
}

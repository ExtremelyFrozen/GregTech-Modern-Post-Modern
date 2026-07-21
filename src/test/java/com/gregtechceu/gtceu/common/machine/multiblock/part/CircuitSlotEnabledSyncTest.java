package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import static com.gregtechceu.gtceu.api.GTValues.LV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CircuitSlotEnabledSyncTest {

    private static final String BATCH = "CircuitSlotEnabledSync";
    private static final ResourceLocation CIRCUIT_SLOT_ENABLED_FIELD = SyncFieldData.key("circuitSlotEnabled");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void itemBusUsesChangedOnlyCircuitSlotSync(GameTestHelper helper) {
        assertCircuitSlotLifecycle(helper, createItemBus(), createItemBus(), createItemBus(), "item bus");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fluidHatchUsesChangedOnlyCircuitSlotSync(GameTestHelper helper) {
        assertCircuitSlotLifecycle(helper, createFluidHatch(), createFluidHatch(), createFluidHatch(), "fluid hatch");
        helper.succeed();
    }

    private static <T extends MetaMachine & CircuitSlotOwner> void assertCircuitSlotLifecycle(
                                                                                              GameTestHelper helper,
                                                                                              T server,
                                                                                              T client,
                                                                                              T loaded,
                                                                                              String owner) {
        RegistryAccess registries = helper.getLevel().registryAccess();

        DataComponentMap full = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        assertCircuitSlotField(helper, full, true, owner + " full sync");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, full);
        helper.assertTrue(client.isCircuitSlotEnabled(), owner + " full sync disabled the client circuit slot");

        server.setCircuitSlotEnabled(false);
        DataComponentMap delta = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        assertOnlyCircuitSlotField(helper, delta, false, owner + " changed delta");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, delta);
        helper.assertTrue(!client.isCircuitSlotEnabled(), owner + " changed delta did not update the client field");

        server.setCircuitSlotEnabled(false);
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                owner + " identical setter call produced a redundant client delta");

        SyncFieldData saved = server.getSyncDataHolder().serializeToFieldData(registries, false, false);
        assertCircuitSlotField(helper, saved, false, owner + " saved state");
        loaded.getSyncDataHolder().deserializeFieldData(registries, saved, false);
        helper.assertTrue(!loaded.isCircuitSlotEnabled(), owner + " did not load the saved circuit slot state");

        ServerFieldUpdateResult rejected = server.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, circuitSlotPayload(true));
        helper.assertTrue(!rejected.getAccepted(), owner + " accepted a client circuit slot write");
        helper.assertTrue(!server.isCircuitSlotEnabled(), owner + " rejected client write changed the server field");
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                owner + " rejected client write produced a client delta");
    }

    private static TestItemBusPartMachine createItemBus() {
        return new TestItemBusPartMachine(info(GTMachines.ITEM_IMPORT_BUS[LV]), LV, IO.IN);
    }

    private static TestFluidHatchPartMachine createFluidHatch() {
        return new TestFluidHatchPartMachine(info(GTMachines.FLUID_IMPORT_HATCH[LV]), LV, IO.IN,
                FluidHatchPartMachine.INITIAL_TANK_CAPACITY_1X, 1);
    }

    private static BlockEntityCreationInfo info(MachineDefinition definition) {
        return new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.defaultBlockState());
    }

    private static DataComponentMap circuitSlotPayload(boolean enabled) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(CIRCUIT_SLOT_ENABLED_FIELD, new JsonPrimitive(enabled))
                        .build())
                .build();
    }

    private static void assertOnlyCircuitSlotField(GameTestHelper helper, DataComponentMap components,
                                                   boolean expected, String description) {
        SyncFieldData fields = requireFields(components, description);
        helper.assertTrue(fields.fields().size() == 1,
                description + " contained fields other than the circuit slot state");
        assertCircuitSlotField(helper, fields, expected, description);
    }

    private static void assertCircuitSlotField(GameTestHelper helper, DataComponentMap components,
                                               boolean expected, String description) {
        assertCircuitSlotField(helper, requireFields(components, description), expected, description);
    }

    private static void assertCircuitSlotField(GameTestHelper helper, SyncFieldData fields,
                                               boolean expected, String description) {
        JsonElement value = fields.get(CIRCUIT_SLOT_ENABLED_FIELD);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " did not contain the expected circuit slot state");
    }

    private static SyncFieldData requireFields(DataComponentMap components, String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new GameTestAssertException(description + " omitted sync field data");
        }
        return fields;
    }

    /**
     * Exposes the shared circuit-slot state contract implemented by both test owners.
     */
    private interface CircuitSlotOwner {

        /**
         * Returns whether the owner's programmed-circuit slot is currently available.
         */
        boolean isCircuitSlotEnabled();

        /**
         * Changes whether the owner's programmed-circuit slot is currently available.
         */
        void setCircuitSlotEnabled(boolean enabled);
    }

    private static final class TestItemBusPartMachine extends ItemBusPartMachine implements CircuitSlotOwner {

        private TestItemBusPartMachine(BlockEntityCreationInfo info, int tier, IO io) {
            super(info, tier, io);
        }
    }

    private static final class TestFluidHatchPartMachine extends FluidHatchPartMachine implements CircuitSlotOwner {

        private TestFluidHatchPartMachine(BlockEntityCreationInfo info, int tier, IO io, int initialCapacity,
                                          int slots) {
            super(info, tier, io, initialCapacity, slots);
        }
    }
}

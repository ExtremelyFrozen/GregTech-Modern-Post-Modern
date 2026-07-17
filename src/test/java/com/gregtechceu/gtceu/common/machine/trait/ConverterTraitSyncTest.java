package com.gregtechceu.gtceu.common.machine.trait;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.client.model.machine.MachineRenderState;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.electric.ConverterMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
public class ConverterTraitSyncTest {

    private static final String BATCH = "ConverterTraitSync";
    private static final BlockPos SERVER_POS = new BlockPos(1, 1, 1);
    private static final BlockPos CLIENT_POS = new BlockPos(2, 1, 1);
    private static final ResourceLocation FE_TO_EU_FIELD = SyncFieldData.key("feToEu");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void directionSyncPersistsAndRetainsEverySetterSideEffect(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        TestConverterMachine server = createMachine(helper, SERVER_POS);
        TestConverterMachine client = createMachine(helper, CLIENT_POS);
        ConverterTrait serverTrait = server.getConverterTrait();
        ConverterTrait clientTrait = client.getConverterTrait();
        server.resetSideEffects();

        serverTrait.setFeToEu(true);

        helper.assertTrue(server.renderStateUpdates == 1 && server.blockUpdates == 1,
                "changed converter direction did not run both setter side effects exactly once");
        assertFeToEuDirection(helper, server, serverTrait, "changed converter direction");
        DataComponentMap traitFull = serverTrait.getSyncDataHolder()
                .serializeFullClientSyncComponents(registries);
        DataComponentMap machineFull = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        SyncFieldData saved = serverTrait.getSyncDataHolder().serializeToFieldData(registries, false, false);
        assertBooleanField(helper, traitFull, true, "converter direction full sync");
        assertBooleanField(helper, saved, true, "saved converter direction");

        client.getSyncDataHolder().applyClientNetworkUpdate(registries, machineFull);
        clientTrait.getSyncDataHolder().applyClientNetworkUpdate(registries, traitFull);
        assertFeToEuDirection(helper, client, clientTrait, "applied converter client direction");

        server.resetSideEffects();
        serverTrait.setFeToEu(true);

        helper.assertTrue(server.renderStateUpdates == 1 && server.blockUpdates == 1,
                "unchanged converter direction skipped an existing setter side effect");
        helper.assertTrue(serverTrait.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "unchanged converter direction produced a client delta");
        assertFeToEuDirection(helper, server, serverTrait, "unchanged converter direction");

        server.resetSideEffects();
        serverTrait.setFeToEu(false);
        SyncFieldData delta = requireFields(
                serverTrait.getSyncDataHolder().serializeToComponents(registries, true, false));

        helper.assertTrue(server.renderStateUpdates == 1 && server.blockUpdates == 1,
                "reversed converter direction did not run both setter side effects exactly once");
        helper.assertTrue(delta.fields().size() == 1,
                "converter direction delta included unrelated fields");
        assertBooleanField(helper, delta, false, "converter direction delta");
        assertEuToFeDirection(helper, server, serverTrait, "reversed converter direction");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void converterDirectionRejectsClientWritesWithoutSideEffects(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        TestConverterMachine machine = createMachine(helper, SERVER_POS);
        ConverterTrait trait = machine.getConverterTrait();
        trait.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        machine.resetSideEffects();

        ServerFieldUpdateResult result = trait.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, payload(true));

        helper.assertTrue(!result.getAccepted(),
                "converter trait accepted a client direction write");
        helper.assertTrue(!trait.isFeToEu(),
                "rejected client direction write changed converter state");
        helper.assertTrue(machine.renderStateUpdates == 0 && machine.blockUpdates == 0,
                "rejected client direction write invoked converter setter side effects");
        helper.assertTrue(trait.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "rejected client direction write produced an acknowledgement");
        assertEuToFeDirection(helper, machine, trait, "rejected converter client direction");
        helper.succeed();
    }

    private static TestConverterMachine createMachine(GameTestHelper helper, BlockPos relativePos) {
        var definition = GTMachines.ENERGY_CONVERTER_1A[GTValues.LV];
        helper.setBlock(relativePos, definition.getBlock());
        BlockPos absolutePos = helper.absolutePos(relativePos);
        helper.getLevel().removeBlockEntity(absolutePos);
        TestConverterMachine machine = new TestConverterMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), absolutePos, helper.getLevel().getBlockState(absolutePos)));
        helper.getLevel().setBlockEntity(machine);
        machine.setFrontFacing(Direction.NORTH);
        machine.resetSideEffects();
        return machine;
    }

    private static DataComponentMap payload(boolean feToEu) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(FE_TO_EU_FIELD, new JsonPrimitive(feToEu))
                        .build())
                .build();
    }

    private static SyncFieldData requireFields(DataComponentMap components) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Converter direction update omitted sync field data.");
        }
        return fields;
    }

    private static void assertFeToEuDirection(GameTestHelper helper, ConverterMachine machine,
                                              ConverterTrait trait, String description) {
        helper.assertTrue(trait.isFeToEu() &&
                machine.getRenderState().getValue(GTMachineModelProperties.IS_FE_TO_EU),
                description + " did not expose the FE-to-EU render state");
        helper.assertTrue(trait.outputsEnergy(Direction.NORTH) &&
                !trait.inputsEnergy(Direction.NORTH) && !trait.inputsEnergy(Direction.SOUTH),
                description + " did not keep EU output on the converter front");
        helper.assertTrue(trait.getFeContainer().canReceive(),
                description + " did not allow FE input");
    }

    private static void assertEuToFeDirection(GameTestHelper helper, ConverterMachine machine,
                                              ConverterTrait trait, String description) {
        helper.assertTrue(!trait.isFeToEu() &&
                !machine.getRenderState().getValue(GTMachineModelProperties.IS_FE_TO_EU),
                description + " did not expose the EU-to-FE render state");
        helper.assertTrue(!trait.outputsEnergy(Direction.NORTH) &&
                !trait.inputsEnergy(Direction.NORTH) && trait.inputsEnergy(Direction.SOUTH),
                description + " did not keep EU input off the converter front");
        helper.assertTrue(!trait.getFeContainer().canReceive(),
                description + " unexpectedly allowed FE input");
    }

    private static void assertBooleanField(GameTestHelper helper, DataComponentMap components,
                                           boolean expected, String description) {
        assertBooleanField(helper, requireFields(components), expected, description);
    }

    private static void assertBooleanField(GameTestHelper helper, SyncFieldData fields,
                                           boolean expected, String description) {
        JsonElement value = fields.get(FE_TO_EU_FIELD);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " did not contain the expected converter direction");
    }

    private static final class TestConverterMachine extends ConverterMachine {

        private int renderStateUpdates;
        private int blockUpdates;

        private TestConverterMachine(BlockEntityCreationInfo info) {
            super(info, GTValues.LV, 1);
        }

        @Override
        public void setRenderState(MachineRenderState renderState) {
            super.setRenderState(renderState);
            renderStateUpdates++;
        }

        @Override
        public void notifyBlockUpdate() {
            super.notifyBlockUpdate();
            blockUpdates++;
        }

        private void resetSideEffects() {
            renderStateUpdates = 0;
            blockUpdates = 0;
        }
    }
}

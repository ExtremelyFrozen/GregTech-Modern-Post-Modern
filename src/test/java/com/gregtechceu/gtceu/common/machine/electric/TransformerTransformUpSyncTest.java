package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
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
public class TransformerTransformUpSyncTest {

    private static final String BATCH = "TransformerTransformUpSync";
    private static final int TIER = GTValues.LV;
    private static final int BASE_AMP = 1;
    private static final ResourceLocation TRANSFORM_UP_FIELD = SyncFieldData.key("isTransformUp");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void fullSyncInitializesClientTransformModeAndEnergyContainer(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        TestTransformerMachine server = createMachine();
        TestTransformerMachine client = createMachine();

        DataComponentMap initial = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        client.resetSideEffectCounts();
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, initial, true);

        helper.assertTrue(!client.isTransformUp(),
                "initial transformer sync changed the default transform direction");
        helper.assertTrue(client.energyContainerUpdates == 1,
                "initial transformer sync did not invoke the energy-container listener exactly once");
        assertEnergyMode(helper, client, false, "initial transformer sync");
        helper.assertTrue(!client.getRenderState().getValue(TransformerMachine.TRANSFORM_UP_PROPERTY),
                "initial transformer render state did not match the synchronized direction");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void changedDeltaUpdatesClientOnceAndUnchangedStateProducesNoDelta(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        TestTransformerMachine server = createMachine();
        TestTransformerMachine client = createMachine();
        client.getSyncDataHolder().applyClientNetworkUpdate(registries,
                server.getSyncDataHolder().serializeFullClientSyncComponents(registries));
        client.resetSideEffectCounts();

        server.setTransformUp(true);
        DataComponentMap changed = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        assertTransformUpField(helper, changed, true, "changed transformer delta");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, changed, false);

        helper.assertTrue(client.isTransformUp(),
                "changed transformer delta did not update the client direction");
        helper.assertTrue(client.energyContainerUpdates == 1,
                "changed transformer delta did not invoke the energy-container listener exactly once");
        helper.assertTrue(client.renderUpdates == 1,
                "changed transformer delta did not rerender the synchronized render state exactly once");
        assertEnergyMode(helper, client, true, "changed transformer delta");
        helper.assertTrue(client.getRenderState().getValue(TransformerMachine.TRANSFORM_UP_PROPERTY),
                "changed transformer render state did not match the synchronized direction");

        server.setTransformUp(true);
        DataComponentMap unchanged = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        helper.assertTrue(unchanged.isEmpty(),
                "unchanged transformer direction produced a redundant client delta");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, unchanged, false);
        helper.assertTrue(client.energyContainerUpdates == 1 && client.renderUpdates == 1,
                "unchanged transformer direction repeated a client-side listener");
        helper.succeed();
    }

    private static TestTransformerMachine createMachine() {
        var definition = GTMachines.TRANSFORMER[TIER];
        return new TestTransformerMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()));
    }

    private static void assertTransformUpField(GameTestHelper helper, DataComponentMap components,
                                               boolean expected, String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        JsonElement value = fields == null ? null : fields.get(TRANSFORM_UP_FIELD);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " did not contain the expected transform direction");
    }

    private static void assertEnergyMode(GameTestHelper helper, TransformerMachine machine,
                                         boolean transformUp, String description) {
        long tierVoltage = GTValues.V[TIER];
        long lowAmperage = BASE_AMP * 4L;
        long expectedInputVoltage = transformUp ? tierVoltage : tierVoltage * 4L;
        long expectedInputAmperage = transformUp ? lowAmperage : BASE_AMP;
        long expectedOutputVoltage = transformUp ? tierVoltage * 4L : tierVoltage;
        long expectedOutputAmperage = transformUp ? BASE_AMP : lowAmperage;
        helper.assertTrue(machine.energyContainer.getInputVoltage() == expectedInputVoltage &&
                machine.energyContainer.getInputAmperage() == expectedInputAmperage &&
                machine.energyContainer.getOutputVoltage() == expectedOutputVoltage &&
                machine.energyContainer.getOutputAmperage() == expectedOutputAmperage,
                description + " did not configure the expected energy input and output limits");
    }

    private static final class TestTransformerMachine extends TransformerMachine {

        private int energyContainerUpdates;
        private int renderUpdates;

        private TestTransformerMachine(BlockEntityCreationInfo info) {
            super(info, TIER, BASE_AMP);
        }

        @Override
        public void updateEnergyContainer(boolean isTransformUp) {
            super.updateEnergyContainer(isTransformUp);
            energyContainerUpdates++;
        }

        @Override
        public void scheduleRenderUpdate() {
            super.scheduleRenderUpdate();
            renderUpdates++;
        }

        private void resetSideEffectCounts() {
            energyContainerUpdates = 0;
            renderUpdates = 0;
        }
    }
}

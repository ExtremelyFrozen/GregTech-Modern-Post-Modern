package com.gregtechceu.gtceu.common.machine.multiblock.generator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;

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
public class LargeCombustionEngineSyncTest {

    private static final String BATCH = "LargeCombustionEngineSync";
    private static final ResourceLocation OXYGEN_BOOSTED_FIELD = SyncFieldData.key("isOxygenBoosted");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void oxygenBoostSyncIsServerOwnedTransientAndChangedOnly(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        LargeCombustionEngineMachine server = createLargeEngine();
        LargeCombustionEngineMachine client = createLargeEngine();
        server.setOxygenBoosted(true);

        DataComponentMap full = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        SyncFieldData saved = server.getSyncDataHolder().serializeToFieldData(registries, false, false);

        assertBooleanField(helper, full, true, "large combustion engine full sync");
        helper.assertTrue(saved.get(OXYGEN_BOOSTED_FIELD) == null,
                "large combustion engine persisted its runtime oxygen boost");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, full);
        helper.assertTrue(client.getOverclockVoltage() == GTValues.V[GTValues.EV] * 2 &&
                client.getProductionBoost() == 1.5,
                "large combustion engine client did not apply its oxygen boost");

        server.setOxygenBoosted(false);
        SyncFieldData delta = requireFields(
                server.getSyncDataHolder().serializeToComponents(registries, true, false));
        helper.assertTrue(delta.fields().size() == 1,
                "large combustion engine oxygen delta included unrelated fields");
        assertBooleanField(helper, delta, false, "large combustion engine oxygen delta");

        server.setOxygenBoosted(false);
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "unchanged large combustion engine oxygen boost produced a client delta");

        ServerFieldUpdateResult rejected = server.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, payload(true));
        helper.assertTrue(!rejected.getAccepted(),
                "large combustion engine accepted a client oxygen-boost write");
        helper.assertTrue(server.getOverclockVoltage() == GTValues.V[GTValues.EV] &&
                server.getProductionBoost() == 1.0,
                "rejected oxygen-boost write changed large combustion engine behavior");
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "rejected oxygen-boost write produced an acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void oxygenBoostPreservesTierSpecificVoltageAndProductionMultipliers(GameTestHelper helper) {
        LargeCombustionEngineMachine large = createLargeEngine();
        LargeCombustionEngineMachine extreme = createExtremeEngine();

        helper.assertTrue(large.getOverclockVoltage() == GTValues.V[GTValues.EV] &&
                large.getProductionBoost() == 1.0,
                "unboosted large combustion engine used boosted recipe parameters");
        helper.assertTrue(extreme.getOverclockVoltage() == GTValues.V[GTValues.IV] &&
                extreme.getProductionBoost() == 1.0,
                "unboosted extreme combustion engine used boosted recipe parameters");

        large.setOxygenBoosted(true);
        extreme.setOxygenBoosted(true);

        helper.assertTrue(large.getOverclockVoltage() == GTValues.V[GTValues.EV] * 2 &&
                large.getProductionBoost() == 1.5,
                "oxygen boost changed large combustion engine recipe parameters incorrectly");
        helper.assertTrue(extreme.getOverclockVoltage() == GTValues.V[GTValues.IV] * 2 &&
                extreme.getProductionBoost() == 2.0,
                "liquid-oxygen boost changed extreme combustion engine recipe parameters incorrectly");
        helper.succeed();
    }

    private static LargeCombustionEngineMachine createLargeEngine() {
        var definition = GTMultiMachines.LARGE_COMBUSTION_ENGINE;
        return new LargeCombustionEngineMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()), GTValues.EV);
    }

    private static LargeCombustionEngineMachine createExtremeEngine() {
        var definition = GTMultiMachines.EXTREME_COMBUSTION_ENGINE;
        return new LargeCombustionEngineMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()), GTValues.IV);
    }

    private static DataComponentMap payload(boolean oxygenBoosted) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(OXYGEN_BOOSTED_FIELD, new JsonPrimitive(oxygenBoosted))
                        .build())
                .build();
    }

    private static SyncFieldData requireFields(DataComponentMap components) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Large combustion engine update omitted sync field data.");
        }
        return fields;
    }

    private static void assertBooleanField(GameTestHelper helper, DataComponentMap components,
                                           boolean expected, String description) {
        assertBooleanField(helper, requireFields(components), expected, description);
    }

    private static void assertBooleanField(GameTestHelper helper, SyncFieldData fields,
                                           boolean expected, String description) {
        JsonElement value = fields.get(OXYGEN_BOOSTED_FIELD);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " did not contain the expected oxygen boost");
    }
}

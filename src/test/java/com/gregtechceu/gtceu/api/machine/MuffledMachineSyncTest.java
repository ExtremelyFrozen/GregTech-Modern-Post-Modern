package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.feature.IMufflableMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
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

import static com.gregtechceu.gtceu.common.data.machines.GTMachineUtils.defaultTankSizeFunction;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MuffledMachineSyncTest {

    private static final String BATCH = "MuffledMachineSync";
    private static final int TIER = GTValues.LV;
    private static final ResourceLocation MUFFLED_FIELD = SyncFieldData.key("isMuffled");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void workableTieredMufflingUsesSavedServerOwnedChangedOnlySync(GameTestHelper helper) {
        assertMuffledLifecycle(helper, createTieredMachine(), createTieredMachine(), createTieredMachine(),
                "workable tiered machine");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void workableMultiblockMufflingUsesSavedServerOwnedChangedOnlySync(GameTestHelper helper) {
        assertMuffledLifecycle(helper, createMultiblockMachine(), createMultiblockMachine(), createMultiblockMachine(),
                "workable multiblock machine");
        helper.succeed();
    }

    private static <T extends MetaMachine & MuffledProbe> void assertMuffledLifecycle(
                                                                                      GameTestHelper helper,
                                                                                      T server, T client, T loaded,
                                                                                      String description) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        server.setMuffled(true);
        client.getSyncDataHolder().serializeFullClientSyncComponents(registries);

        DataComponentMap full = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        assertMuffledField(helper, full, true, description + " full sync");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, full);
        helper.assertTrue(client.isMuffled(), description + " full sync did not update the client field");
        helper.assertTrue(client.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                description + " full network deserialization produced a redundant changed-only delta");
        helper.assertTrue(client.wasPreviouslyMuffled(),
                description + " full sync changed the client sound-transition sentinel before its tick");
        client.clientTick();
        helper.assertTrue(client.wasPreviouslyMuffled(),
                description + " unchanged client tick changed the sound-transition sentinel");
        helper.assertTrue(client.soundUpdateCount() == 0,
                description + " unchanged client tick requested a sound update");

        SyncFieldData saved = server.getSyncDataHolder().serializeToFieldData(registries, false, false);
        assertMuffledField(helper, saved, true, description + " saved state");
        loaded.getSyncDataHolder().deserializeFieldData(registries, saved, false);
        helper.assertTrue(loaded.isMuffled(), description + " did not load its saved muffled state");

        server.setMuffled(false);
        DataComponentMap delta = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        assertOnlyMuffledField(helper, delta, false, description + " changed delta");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, delta);
        helper.assertTrue(!client.isMuffled(), description + " changed delta did not update the client field");
        helper.assertTrue(client.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                description + " delta network deserialization produced a redundant changed-only delta");
        helper.assertTrue(client.wasPreviouslyMuffled(),
                description + " changed delta updated the sound-transition sentinel before its tick");
        client.clientTick();
        helper.assertTrue(!client.wasPreviouslyMuffled(),
                description + " client tick did not advance the sound-transition sentinel");
        helper.assertTrue(client.soundUpdateCount() == 1,
                description + " client transition did not request exactly one sound update");
        client.clientTick();
        helper.assertTrue(client.soundUpdateCount() == 1,
                description + " unchanged follow-up client tick requested another sound update");

        server.setMuffled(false);
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                description + " unchanged setter call produced a redundant client delta");

        ServerFieldUpdateResult rejected = server.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, payload(true));
        helper.assertTrue(!rejected.getAccepted(), description + " accepted a client muffled-state write");
        helper.assertTrue(!server.isMuffled(), description + " rejected client write changed the server field");
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                description + " rejected client write produced an acknowledgement");
    }

    private static TestWorkableTieredMachine createTieredMachine() {
        var definition = GTMachines.ELECTRIC_FURNACE[TIER];
        return new TestWorkableTieredMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()));
    }

    private static TestWorkableMultiblockMachine createMultiblockMachine() {
        var definition = GTMultiMachines.ELECTRIC_BLAST_FURNACE;
        return new TestWorkableMultiblockMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()));
    }

    private static DataComponentMap payload(boolean muffled) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(MUFFLED_FIELD, new JsonPrimitive(muffled))
                        .build())
                .build();
    }

    private static void assertOnlyMuffledField(GameTestHelper helper, DataComponentMap components,
                                               boolean expected, String description) {
        SyncFieldData fields = requireFields(components, description);
        helper.assertTrue(fields.fields().size() == 1,
                description + " contained fields other than the muffled state");
        assertMuffledField(helper, fields, expected, description);
    }

    private static void assertMuffledField(GameTestHelper helper, DataComponentMap components,
                                           boolean expected, String description) {
        assertMuffledField(helper, requireFields(components, description), expected, description);
    }

    private static void assertMuffledField(GameTestHelper helper, SyncFieldData fields,
                                           boolean expected, String description) {
        JsonElement value = fields.get(MUFFLED_FIELD);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isBoolean() &&
                primitive.getAsBoolean() == expected,
                description + " did not contain the expected muffled state");
    }

    private static SyncFieldData requireFields(DataComponentMap components, String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException(description + " omitted sync field data");
        }
        return fields;
    }

    /**
     * Exposes the protected transition sentinel and injected sound probe to lifecycle assertions.
     */
    private interface MuffledProbe extends IMufflableMachine {

        /**
         * Returns the value that the next client tick compares with the synchronized state.
         */
        boolean wasPreviouslyMuffled();

        /**
         * Returns how many sound refreshes client transitions requested.
         */
        int soundUpdateCount();
    }

    private static final class TestWorkableTieredMachine extends WorkableTieredMachine implements MuffledProbe {

        private final CountingRecipeLogic soundProbe;

        private TestWorkableTieredMachine(BlockEntityCreationInfo info) {
            this(info, new CountingRecipeLogic());
        }

        private TestWorkableTieredMachine(BlockEntityCreationInfo info, CountingRecipeLogic soundProbe) {
            super(info, TIER, soundProbe, 0, 0, 0, 0, defaultTankSizeFunction);
            this.soundProbe = soundProbe;
        }

        @Override
        public boolean wasPreviouslyMuffled() {
            return previouslyMuffled;
        }

        @Override
        public int soundUpdateCount() {
            return soundProbe.soundUpdateCount();
        }
    }

    private static final class TestWorkableMultiblockMachine extends WorkableMultiblockMachine
                                                             implements MuffledProbe {

        private final CountingRecipeLogic soundProbe;

        private TestWorkableMultiblockMachine(BlockEntityCreationInfo info) {
            this(info, new CountingRecipeLogic());
        }

        private TestWorkableMultiblockMachine(BlockEntityCreationInfo info, CountingRecipeLogic soundProbe) {
            super(info, soundProbe);
            this.soundProbe = soundProbe;
        }

        @Override
        public boolean wasPreviouslyMuffled() {
            return previouslyMuffled;
        }

        @Override
        public int soundUpdateCount() {
            return soundProbe.soundUpdateCount();
        }
    }

    private static final class CountingRecipeLogic extends RecipeLogic {

        private int soundUpdateCount;

        @Override
        public void updateSound() {
            soundUpdateCount++;
        }

        private int soundUpdateCount() {
            return soundUpdateCount;
        }
    }
}

package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.sync_system.FieldCodecs;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTCovers;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.storage.BufferMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import static com.gregtechceu.gtceu.api.GTValues.LV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class FacadeCoverSyncTest {

    private static final String BATCH = "FacadeCoverSync";
    private static final Direction COVER_SIDE = Direction.NORTH;
    private static final ResourceLocation FACADE_STATE_FIELD = SyncFieldData.key("facadeState");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void facadeStateUsesBlockStateCodecAndChangedOnlySync(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        helper.assertTrue(FieldCodecs.get(BlockState.class) == BlockState.CODEC,
                "BlockState did not resolve to its exact field codec");

        TestBufferMachine serverMachine = createBuffer();
        TestBufferMachine clientMachine = createBuffer();
        TestBufferMachine loadedMachine = createBuffer();
        TestBufferMachine pastedMachine = createBuffer();
        FacadeCover server = installFacade(serverMachine);
        FacadeCover client = installFacade(clientMachine);
        FacadeCover loaded = installFacade(loadedMachine);
        FacadeCover pasted = installFacade(pastedMachine);

        BlockState initialState = GTMachines.BUFFER[LV].defaultBlockState();
        server.setFacadeState(initialState);
        DataComponentMap full = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        assertFacadeStateField(helper, registries, full, initialState, "facade full sync");
        clientMachine.resetRenderUpdates();
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, full);
        helper.assertTrue(client.getFacadeState() == initialState,
                "facade full sync did not preserve the registered block state");
        helper.assertTrue(clientMachine.renderUpdates == 1,
                "facade full sync did not rerender the client exactly once");

        server.setFacadeState(initialState);
        DataComponentMap unchanged = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        helper.assertTrue(unchanged.isEmpty(),
                "identical facade state setter call produced a redundant delta");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, unchanged);
        helper.assertTrue(clientMachine.renderUpdates == 1,
                "identical facade state setter call repeated the client rerender hook");

        BlockState changedState = Blocks.OAK_LOG.defaultBlockState()
                .setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
        server.setFacadeState(changedState);
        DataComponentMap delta = server.getSyncDataHolder().serializeToComponents(registries, true, false);
        SyncFieldData deltaFields = requireFields(delta, "changed facade delta");
        helper.assertTrue(deltaFields.fields().size() == 1,
                "changed facade delta contained fields other than facadeState");
        assertFacadeStateField(helper, registries, deltaFields, changedState, "changed facade delta");
        clientMachine.resetRenderUpdates();
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, delta);
        helper.assertTrue(client.getFacadeState() == changedState,
                "changed facade delta did not preserve block-state properties");
        helper.assertTrue(clientMachine.renderUpdates == 1,
                "changed facade delta did not rerender the client exactly once");

        SyncFieldData saved = server.getSyncDataHolder().serializeToFieldData(registries, false, false);
        assertFacadeStateField(helper, registries, saved, changedState, "saved facade state");
        loaded.getSyncDataHolder().deserializeFieldData(registries, saved, false);
        helper.assertTrue(loaded.getFacadeState() == changedState,
                "facade state did not load with its block-state properties");

        DataComponentMap copied = server.copyConfig(registries);
        assertFacadeStateField(helper, registries, copied, changedState, "copied facade config");
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        pasted.pasteConfig(player, registries, copied);
        helper.assertTrue(pasted.getFacadeState() == changedState,
                "facade copy-paste did not use the facadeState save key");

        ServerFieldUpdateResult rejected = server.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, facadePayload(registries, initialState));
        helper.assertTrue(!rejected.getAccepted(), "facade state accepted a client write");
        helper.assertTrue(server.getFacadeState() == changedState,
                "rejected client write changed the server facade state");
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "rejected facade client write produced an acknowledgement");
        helper.succeed();
    }

    private static TestBufferMachine createBuffer() {
        var definition = GTMachines.BUFFER[LV];
        return new TestBufferMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()));
    }

    private static FacadeCover installFacade(BufferMachine machine) {
        var cover = GTCovers.FACADE.createCoverBehavior(machine.getCoverContainer(), COVER_SIDE);
        if (!(cover instanceof FacadeCover facade)) {
            throw new GameTestAssertException("Facade definition created the wrong cover type.");
        }
        machine.getCoverContainer().setCoverAtSide(facade, COVER_SIDE);
        return facade;
    }

    private static DataComponentMap facadePayload(RegistryAccess registries, BlockState state) {
        JsonElement encoded = BlockState.CODEC
                .encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), state)
                .getOrThrow();
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(FACADE_STATE_FIELD, encoded)
                        .build())
                .build();
    }

    private static void assertFacadeStateField(GameTestHelper helper, RegistryAccess registries,
                                               DataComponentMap components, BlockState expected,
                                               String description) {
        assertFacadeStateField(helper, registries, requireFields(components, description), expected, description);
    }

    private static void assertFacadeStateField(GameTestHelper helper, RegistryAccess registries,
                                               SyncFieldData fields, BlockState expected, String description) {
        JsonElement encoded = fields.get(FACADE_STATE_FIELD);
        if (encoded == null) {
            throw new GameTestAssertException(description + " omitted the facadeState field");
        }
        BlockState decoded = BlockState.CODEC
                .parse(registries.createSerializationContext(JsonOps.INSTANCE), encoded)
                .getOrThrow();
        helper.assertTrue(decoded == expected,
                description + " did not preserve the expected registered block state and properties");
    }

    private static SyncFieldData requireFields(DataComponentMap components, String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new GameTestAssertException(description + " omitted sync field data");
        }
        return fields;
    }

    private static final class TestBufferMachine extends BufferMachine {

        private int renderUpdates;

        private TestBufferMachine(BlockEntityCreationInfo info) {
            super(info, LV);
        }

        @Override
        public void scheduleRenderUpdate() {
            super.scheduleRenderUpdate();
            renderUpdates++;
        }

        private void resetRenderUpdates() {
            renderUpdates = 0;
        }
    }
}

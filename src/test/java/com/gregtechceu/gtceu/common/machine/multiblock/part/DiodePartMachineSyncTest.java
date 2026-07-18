package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class DiodePartMachineSyncTest {

    private static final String BATCH = "DiodePartMachineSync";
    private static final int TIER = GTValues.LV;
    private static final BlockPos PRIMARY_POS = new BlockPos(0, 1, 1);
    private static final BlockPos SECONDARY_POS = new BlockPos(1, 1, 1);
    private static final BlockPos TERTIARY_POS = new BlockPos(2, 1, 1);
    private static final ResourceLocation AMP_MODE_FIELD = SyncFieldData.key("amp_mode");
    private static final ResourceLocation RAW_AMPS_FIELD = SyncFieldData.key("amps");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void amperageUsesAutomaticClientSyncAndLegacySaveKey(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        TestDiodePartMachine server = createMachine(helper, PRIMARY_POS, false, DiodePartMachine.MAX_AMPS);
        TestDiodePartMachine client = createMachine(helper, SECONDARY_POS, true, DiodePartMachine.MAX_AMPS);

        server.setAmps(8);
        DataComponentMap full = server.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        assertAmperageField(helper, full, 8, "diode full sync");

        client.resetSideEffects();
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, full, true);
        helper.assertTrue(client.getAmps() == 8,
                "diode full sync did not initialize the client amperage");
        helper.assertTrue(client.getRenderState().getValue(DiodePartMachine.AMP_MODE_PROPERTY) ==
                DiodePartMachine.AmpMode.MODE_8A,
                "diode full sync did not initialize the client amp-mode render state");
        assertClientSyncSideEffects(helper, client, "diode full sync");

        server.resetSideEffects();
        client.resetSideEffects();
        server.setAmps(4);
        DataComponentMap delta = server.getSyncDataHolder().serializeToComponents(registries, true, false);

        assertOnlyAmperageField(helper, delta, 4, "changed diode delta");
        assertServerSetterSideEffects(helper, server, 1, 1, "changed diode amperage");
        client.getSyncDataHolder().applyClientNetworkUpdate(registries, delta, false);
        helper.assertTrue(client.getAmps() == 4,
                "changed diode delta did not update the client amperage");
        helper.assertTrue(client.getRenderState().getValue(DiodePartMachine.AMP_MODE_PROPERTY) ==
                DiodePartMachine.AmpMode.MODE_4A,
                "changed diode delta did not update the client amp-mode render state");
        assertClientSyncSideEffects(helper, client, "changed diode delta");

        server.setAmps(4);
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "unchanged diode amperage produced a redundant client delta");
        assertServerSetterSideEffects(helper, server, 1, 1, "unchanged diode amperage");

        SyncFieldData saved = server.getSyncDataHolder().serializeToFieldData(registries, false, false);
        assertAmperageField(helper, saved, 4, "saved diode amperage");
        helper.assertTrue(saved.get(RAW_AMPS_FIELD) == null,
                "saved diode amperage used the field name instead of the legacy amp_mode key");

        TestDiodePartMachine loaded = createMachine(helper, TERTIARY_POS, false, DiodePartMachine.MAX_AMPS);
        loaded.getSyncDataHolder().deserializeFieldData(registries, SyncFieldData.builder()
                .put(AMP_MODE_FIELD, new JsonPrimitive(16))
                .build(), false);
        helper.assertTrue(loaded.getAmps() == 16,
                "diode did not load amperage from the legacy amp_mode key");

        ServerFieldUpdateResult rejected = server.getSyncDataHolder().tryApplyServerNetworkUpdate(
                registries, payload(16));
        helper.assertTrue(!rejected.getAccepted(),
                "diode accepted a client write to its server-owned amperage");
        helper.assertTrue(server.getAmps() == 4,
                "rejected diode client write changed the server amperage");
        assertServerSetterSideEffects(helper, server, 1, 1, "rejected diode client write");
        helper.assertTrue(server.getSyncDataHolder().serializeToComponents(registries, true, false).isEmpty(),
                "rejected diode client write produced an acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void serverSoftMalletCyclesEveryModeAndRetainsEnergySideEffects(GameTestHelper helper) {
        TestDiodePartMachine diode = createMachine(helper, PRIMARY_POS, false, DiodePartMachine.MAX_AMPS);
        diode.setFrontFacing(Direction.NORTH);
        diode.resetSideEffects();
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ExtendedUseOnContext context = context(player, diode.getBlockPos());
        int[] expectedAmperages = { 2, 4, 8, 16, 1 };

        for (int i = 0; i < expectedAmperages.length; i++) {
            InteractionResult result = diode.useSoftMallet(context);
            int expectedAmperage = expectedAmperages[i];

            helper.assertTrue(result == InteractionResult.SUCCESS,
                    "server diode soft-mallet click was not successful at " + expectedAmperage + "A");
            helper.assertTrue(diode.getAmps() == expectedAmperage,
                    "server diode soft-mallet cycle did not select " + expectedAmperage + "A");
            assertEnergyConfiguration(helper, diode, expectedAmperage,
                    "server diode " + expectedAmperage + "A mode");
            assertServerSetterSideEffects(helper, diode, i + 1, i + 1,
                    "server diode " + expectedAmperage + "A mode");
            helper.assertTrue(diode.renderUpdates == i + 1,
                    "server diode soft-mallet click did not schedule one render update at " +
                            expectedAmperage + "A");
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void clientClickTimingAndMaximumOverrideRemainUnchanged(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        TestDiodePartMachine client = createMachine(helper, PRIMARY_POS, true, DiodePartMachine.MAX_AMPS);
        ExtendedUseOnContext clientContext = context(player, client.getBlockPos());
        client.resetSideEffects();

        InteractionResult clientResult = client.useSoftMallet(clientContext);

        helper.assertTrue(clientResult == InteractionResult.CONSUME,
                "client diode soft-mallet click was not consumed");
        helper.assertTrue(client.getAmps() == 2,
                "client diode soft-mallet click did not preserve its local cycle timing");
        helper.assertTrue(client.getRenderState().getValue(DiodePartMachine.AMP_MODE_PROPERTY) ==
                DiodePartMachine.AmpMode.MODE_1A,
                "client diode soft-mallet click updated render state before server synchronization");
        assertClientNoSyncSideEffects(helper, client, "client diode soft-mallet click");

        TestDiodePartMachine limited = createMachine(helper, SECONDARY_POS, false, 8);
        limited.setFrontFacing(Direction.NORTH);
        limited.resetSideEffects();
        ExtendedUseOnContext limitedContext = context(player, limited.getBlockPos());
        int[] expectedAmperages = { 2, 4, 8, 1 };
        for (int expectedAmperage : expectedAmperages) {
            limited.useSoftMallet(limitedContext);
            helper.assertTrue(limited.getAmps() == expectedAmperage,
                    "overridden diode maximum did not cycle to " + expectedAmperage + "A");
            assertEnergyConfiguration(helper, limited, expectedAmperage,
                    "overridden diode maximum at " + expectedAmperage + "A");
        }
        helper.assertTrue(limited.energyReinitializations == expectedAmperages.length &&
                limited.blockUpdates == expectedAmperages.length,
                "overridden diode maximum changed setter side-effect counts");
        helper.succeed();
    }

    private static TestDiodePartMachine createMachine(GameTestHelper helper, BlockPos relativePos, boolean clientSide,
                                                      int maxAmperage) {
        var definition = GTMachines.DIODE[TIER];
        helper.setBlock(relativePos, definition.getBlock());
        BlockPos absolutePos = helper.absolutePos(relativePos);
        helper.getLevel().removeBlockEntity(absolutePos);
        TestDiodePartMachine machine = new TestDiodePartMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), absolutePos, helper.getLevel().getBlockState(absolutePos)),
                clientSide, maxAmperage);
        helper.getLevel().setBlockEntity(machine);
        return machine;
    }

    private static ExtendedUseOnContext context(ServerPlayer player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return new ExtendedUseOnContext(player, InteractionHand.MAIN_HAND, hit);
    }

    private static DataComponentMap payload(int amperage) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(AMP_MODE_FIELD, new JsonPrimitive(amperage))
                        .build())
                .build();
    }

    private static void assertOnlyAmperageField(GameTestHelper helper, DataComponentMap components,
                                                int expected, String description) {
        SyncFieldData fields = requireFields(components);
        helper.assertTrue(fields.fields().size() == 1,
                description + " contained fields other than the diode amperage");
        assertAmperageField(helper, fields, expected, description);
    }

    private static void assertAmperageField(GameTestHelper helper, DataComponentMap components,
                                            int expected, String description) {
        assertAmperageField(helper, requireFields(components), expected, description);
    }

    private static void assertAmperageField(GameTestHelper helper, SyncFieldData fields,
                                            int expected, String description) {
        JsonElement value = fields.get(AMP_MODE_FIELD);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isNumber() &&
                primitive.getAsInt() == expected,
                description + " did not contain the expected amperage");
    }

    private static SyncFieldData requireFields(DataComponentMap components) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Diode update omitted sync field data.");
        }
        return fields;
    }

    private static void assertServerSetterSideEffects(GameTestHelper helper, TestDiodePartMachine diode,
                                                      int expectedReinitializations, int expectedBlockUpdates,
                                                      String description) {
        helper.assertTrue(diode.energyReinitializations == expectedReinitializations,
                description + " did not reinitialize the energy container exactly once per change");
        helper.assertTrue(diode.blockUpdates == expectedBlockUpdates,
                description + " did not notify the block exactly once per change");
    }

    private static void assertClientSyncSideEffects(GameTestHelper helper, TestDiodePartMachine diode,
                                                    String description) {
        helper.assertTrue(diode.energyReinitializations == 0 && diode.blockUpdates == 0,
                description + " invoked server-only diode side effects on the client");
        helper.assertTrue(diode.ampUpdateCallbacks == 1 && diode.renderUpdates >= 1,
                description + " did not apply exactly one amperage render callback");
        helper.assertTrue(diode.energyContainer.getInputAmperage() == 1 &&
                diode.energyContainer.getOutputAmperage() == 1,
                description + " reinitialized the client energy container before a load");
    }

    private static void assertClientNoSyncSideEffects(GameTestHelper helper, TestDiodePartMachine diode,
                                                      String description) {
        helper.assertTrue(diode.energyReinitializations == 0 && diode.blockUpdates == 0 &&
                diode.renderUpdates == 0 && diode.ampUpdateCallbacks == 0,
                description + " produced a client sync side effect");
        helper.assertTrue(diode.energyContainer.getInputAmperage() == 1 &&
                diode.energyContainer.getOutputAmperage() == 1,
                description + " reinitialized the client energy container before a load");
    }

    private static void assertEnergyConfiguration(GameTestHelper helper, TestDiodePartMachine diode,
                                                  int expectedAmperage, String description) {
        helper.assertTrue(diode.energyContainer.getInputAmperage() == expectedAmperage &&
                diode.energyContainer.getOutputAmperage() == expectedAmperage,
                description + " did not configure matching input and output amperage");
        helper.assertTrue(diode.energyContainer.inputsEnergy(Direction.SOUTH) &&
                !diode.energyContainer.inputsEnergy(Direction.NORTH) &&
                diode.energyContainer.outputsEnergy(Direction.NORTH) &&
                !diode.energyContainer.outputsEnergy(Direction.SOUTH),
                description + " changed the diode's directional energy behavior");
    }

    private static final class TestDiodePartMachine extends DiodePartMachine {

        private final boolean clientSide;
        private final int maxAmperage;
        private int energyReinitializations;
        private int blockUpdates;
        private int renderUpdates;
        private int ampUpdateCallbacks;

        private TestDiodePartMachine(BlockEntityCreationInfo info, boolean clientSide, int maxAmperage) {
            super(info, TIER);
            this.clientSide = clientSide;
            this.maxAmperage = maxAmperage;
            resetSideEffects();
        }

        @Override
        public boolean isRemote() {
            return clientSide;
        }

        @Override
        protected int getMaxAmperage() {
            return maxAmperage;
        }

        @Override
        protected void reinitializeEnergyContainer() {
            super.reinitializeEnergyContainer();
            energyReinitializations++;
        }

        @Override
        public void notifyBlockUpdate() {
            super.notifyBlockUpdate();
            blockUpdates++;
        }

        @Override
        public void scheduleRenderUpdate() {
            super.scheduleRenderUpdate();
            renderUpdates++;
        }

        @Override
        public void onAmpUpdated() {
            super.onAmpUpdated();
            ampUpdateCallbacks++;
        }

        private InteractionResult useSoftMallet(ExtendedUseOnContext context) {
            return super.onSoftMalletClick(context);
        }

        private void resetSideEffects() {
            energyReinitializations = 0;
            blockUpdates = 0;
            renderUpdates = 0;
            ampUpdateCallbacks = 0;
        }
    }
}

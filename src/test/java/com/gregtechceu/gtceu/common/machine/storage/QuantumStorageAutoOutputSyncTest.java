package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.network.packets.CPacketMachineSyncToServer;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.handling.ServerPayloadContext;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonPrimitive;
import com.mojang.authlib.GameProfile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static com.gregtechceu.gtceu.api.GTValues.IV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class QuantumStorageAutoOutputSyncTest {

    private static final String BATCH = "QuantumStorageAutoOutputSync";
    private static final BlockPos CHEST_POS = new BlockPos(1, 1, 1);
    private static final BlockPos TANK_POS = new BlockPos(3, 1, 1);
    private static final ResourceLocation AUTO_OUTPUT_ITEMS_FIELD = SyncFieldData.key("autoOutputItems");
    private static final ResourceLocation AUTO_OUTPUT_FLUIDS_FIELD = SyncFieldData.key("autoOutputFluids");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void quantumChestOwnerRoutesItemAutoOutputTraitUpdate(GameTestHelper helper) {
        QuantumChestMachine chest = setQuantumChest(helper);
        RegistryAccess registries = helper.getLevel().registryAccess();
        chest.autoOutput.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        ServerPlayer player = preparePlayer(helper, chest, "quantum_chest_auto_output");

        execute(player, CPacketMachineSyncToServer.forMachineTrait(chest, chest.autoOutput,
                payload(AUTO_OUTPUT_ITEMS_FIELD, true)));

        helper.assertTrue(chest.autoOutput.isAutoOutputItems(),
                "quantum chest owner did not route item auto-output update to its trait");
        SyncFieldData acknowledgement = chest.autoOutput.getSyncDataHolder()
                .serializeToFieldData(registries, true, false);
        helper.assertTrue(acknowledgement.get(AUTO_OUTPUT_ITEMS_FIELD).getAsBoolean(),
                "quantum chest item auto-output update did not request an authoritative acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void quantumTankOwnerRoutesFluidAutoOutputTraitUpdate(GameTestHelper helper) {
        QuantumTankMachine tank = setQuantumTank(helper);
        RegistryAccess registries = helper.getLevel().registryAccess();
        tank.autoOutput.getSyncDataHolder().serializeFullClientSyncComponents(registries);
        ServerPlayer player = preparePlayer(helper, tank, "quantum_tank_auto_output");

        execute(player, CPacketMachineSyncToServer.forMachineTrait(tank, tank.autoOutput,
                payload(AUTO_OUTPUT_FLUIDS_FIELD, true)));

        helper.assertTrue(tank.autoOutput.isAutoOutputFluids(),
                "quantum tank owner did not route fluid auto-output update to its trait");
        SyncFieldData acknowledgement = tank.autoOutput.getSyncDataHolder()
                .serializeToFieldData(registries, true, false);
        helper.assertTrue(acknowledgement.get(AUTO_OUTPUT_FLUIDS_FIELD).getAsBoolean(),
                "quantum tank fluid auto-output update did not request an authoritative acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void quantumOwnersRejectUnsupportedAutoOutputCapabilities(GameTestHelper helper) {
        QuantumChestMachine chest = setQuantumChest(helper);
        QuantumTankMachine tank = setQuantumTank(helper);
        ServerPlayer player = preparePlayer(helper, chest, "quantum_auto_output_reject");
        helper.assertTrue(chest.autoOutput.supportsAutoOutputItems() &&
                !chest.autoOutput.supportsAutoOutputFluids(),
                "quantum chest test owner does not expose the expected item-only capability");
        helper.assertTrue(!tank.autoOutput.supportsAutoOutputItems() &&
                tank.autoOutput.supportsAutoOutputFluids(),
                "quantum tank test owner does not expose the expected fluid-only capability");

        execute(player, CPacketMachineSyncToServer.forMachineTrait(chest, chest.autoOutput,
                payload(AUTO_OUTPUT_FLUIDS_FIELD, true)));
        player.moveTo(Vec3.atCenterOf(tank.getBlockPos()));
        execute(player, CPacketMachineSyncToServer.forMachineTrait(tank, tank.autoOutput,
                payload(AUTO_OUTPUT_ITEMS_FIELD, true)));

        helper.assertTrue(!chest.autoOutput.isAutoOutputItems() && !chest.autoOutput.isAutoOutputFluids(),
                "unsupported fluid update changed the quantum chest auto-output trait");
        helper.assertTrue(!tank.autoOutput.isAutoOutputItems() && !tank.autoOutput.isAutoOutputFluids(),
                "unsupported item update changed the quantum tank auto-output trait");
        helper.succeed();
    }

    private static QuantumChestMachine setQuantumChest(GameTestHelper helper) {
        return (QuantumChestMachine) TestUtils.setMachine(helper, CHEST_POS, GTMachines.QUANTUM_CHEST[IV]);
    }

    private static QuantumTankMachine setQuantumTank(GameTestHelper helper) {
        return (QuantumTankMachine) TestUtils.setMachine(helper, TANK_POS, GTMachines.QUANTUM_TANK[IV]);
    }

    private static ServerPlayer preparePlayer(GameTestHelper helper, MetaMachine machine, String name) {
        UUID profileId = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(profileId, name));
        player.closeContainer();
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(Vec3.atCenterOf(machine.getBlockPos()));
        return player;
    }

    private static void execute(ServerPlayer player, CPacketMachineSyncToServer packet) {
        packet.execute(new ServerPayloadContext(player.connection, CPacketMachineSyncToServer.ID));
    }

    private static DataComponentMap payload(ResourceLocation field, boolean value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, new JsonPrimitive(value))
                        .build())
                .build();
    }
}

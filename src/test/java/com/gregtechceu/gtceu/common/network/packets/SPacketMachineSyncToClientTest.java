package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonPrimitive;
import io.netty.buffer.Unpooled;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class SPacketMachineSyncToClientTest {

    private static final BlockPos POSITION = new BlockPos(2, 3, 4);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SPacketMachineSyncToClient")
    public static void fullSyncFlagRoundTripsWithClientPacket(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        DataComponentMap data = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(SyncFieldData.key("value"), new JsonPrimitive(7))
                        .build())
                .build();

        RegistryFriendlyByteBuf encoded = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), registries, ConnectionType.OTHER);
        RegistryFriendlyByteBuf decodedBuffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(), registries, ConnectionType.OTHER);
        try {
            SPacketMachineSyncToClient full = new SPacketMachineSyncToClient(POSITION, data, true);
            SPacketMachineSyncToClient.CODEC.encode(encoded, full);
            SPacketMachineSyncToClient decodedFull = SPacketMachineSyncToClient.CODEC.decode(encoded);
            helper.assertTrue(decodedFull.isFullSync(), "full sync packet lost its authoritative flag");

            SPacketMachineSyncToClient delta = new SPacketMachineSyncToClient(POSITION, data, false);
            SPacketMachineSyncToClient.CODEC.encode(decodedBuffer, delta);
            SPacketMachineSyncToClient decodedDelta = SPacketMachineSyncToClient.CODEC.decode(decodedBuffer);
            helper.assertTrue(!decodedDelta.isFullSync(), "changed-only packet was encoded as full sync");
            helper.assertTrue(!encoded.isReadable() && !decodedBuffer.isReadable(),
                    "machine sync packet codec left unread bytes");
        } finally {
            encoded.release();
            decodedBuffer.release();
        }
        helper.succeed();
    }
}

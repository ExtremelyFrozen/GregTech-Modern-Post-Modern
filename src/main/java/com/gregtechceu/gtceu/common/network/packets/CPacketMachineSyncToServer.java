package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.managed.ManagedSyncBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jetbrains.annotations.NotNull;

public class CPacketMachineSyncToServer implements CustomPacketPayload {

    public static final ResourceLocation ID = GTCEu.id("machine_sync_to_server");
    public static final Type<CPacketMachineSyncToServer> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, CPacketMachineSyncToServer> CODEC = StreamCodec
            .ofMember(CPacketMachineSyncToServer::encode, CPacketMachineSyncToServer::new);

    private final BlockPos pos;
    private final byte[] data;

    public CPacketMachineSyncToServer(BlockPos pos, byte[] data) {
        this.pos = pos;
        this.data = data;
    }

    public CPacketMachineSyncToServer(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.data = buf.readByteArray();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeByteArray(data);
    }

    public void execute(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || data.length == 0) {
            return;
        }

        Level level = player.level();
        if (!level.isLoaded(pos)) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ManagedSyncBlockEntity syncBlockEntity)) {
            return;
        }

        syncBlockEntity.getSyncDataHolder().applyServerNetworkUpdate(level.registryAccess(), data);
        syncBlockEntity.markAsChanged();
        syncBlockEntity.setChanged();
    }

    @Override
    public @NotNull Type<CPacketMachineSyncToServer> type() {
        return TYPE;
    }
}

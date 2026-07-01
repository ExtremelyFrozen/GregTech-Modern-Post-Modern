package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.managed.ManagedSyncBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
    public static final StreamCodec<RegistryFriendlyByteBuf, CPacketMachineSyncToServer> CODEC = StreamCodec
            .ofMember(CPacketMachineSyncToServer::encode, CPacketMachineSyncToServer::new);

    private final BlockPos pos;
    private final DataComponentMap data;

    public CPacketMachineSyncToServer(BlockPos pos, DataComponentMap data) {
        this.pos = pos;
        this.data = data;
    }

    public CPacketMachineSyncToServer(RegistryFriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.data = SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.decode(buf);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.encode(buf, data);
    }

    public void execute(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || data.isEmpty()) {
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

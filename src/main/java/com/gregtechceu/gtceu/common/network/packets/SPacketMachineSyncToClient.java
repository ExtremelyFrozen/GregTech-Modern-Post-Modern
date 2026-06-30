package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.managed.ManagedSyncBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jetbrains.annotations.NotNull;

public class SPacketMachineSyncToClient implements CustomPacketPayload {

    public static final ResourceLocation ID = GTCEu.id("machine_sync_to_client");
    public static final Type<SPacketMachineSyncToClient> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, SPacketMachineSyncToClient> CODEC = StreamCodec
            .ofMember(SPacketMachineSyncToClient::encode, SPacketMachineSyncToClient::new);

    private final BlockPos pos;
    private final byte[] data;

    public SPacketMachineSyncToClient(BlockPos pos, byte[] data) {
        this.pos = pos;
        this.data = data;
    }

    public SPacketMachineSyncToClient(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.data = buf.readByteArray();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeByteArray(data);
    }

    public void execute(IPayloadContext context) {
        if (data.length == 0) {
            return;
        }

        Level level = context.player().level();
        if (!level.isClientSide || !level.isLoaded(pos)) {
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ManagedSyncBlockEntity syncBlockEntity)) {
            return;
        }

        syncBlockEntity.getSyncDataHolder().applyClientNetworkUpdate(level.registryAccess(), data);
    }

    @Override
    public @NotNull Type<SPacketMachineSyncToClient> type() {
        return TYPE;
    }
}

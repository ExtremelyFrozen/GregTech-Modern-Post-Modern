package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.sync_system.managed.ManagedSyncBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jetbrains.annotations.NotNull;

public class CPacketCoverSyncToServer implements CustomPacketPayload {

    public static final ResourceLocation ID = GTCEu.id("cover_sync_to_server");
    public static final Type<CPacketCoverSyncToServer> TYPE = new Type<>(ID);
    public static final StreamCodec<FriendlyByteBuf, CPacketCoverSyncToServer> CODEC = StreamCodec
            .ofMember(CPacketCoverSyncToServer::encode, CPacketCoverSyncToServer::new);

    private final BlockPos pos;
    private final Direction side;
    private final byte[] data;

    public CPacketCoverSyncToServer(BlockPos pos, Direction side, byte[] data) {
        this.pos = pos;
        this.side = side;
        this.data = data;
    }

    public CPacketCoverSyncToServer(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.side = buf.readEnum(Direction.class);
        this.data = buf.readByteArray();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(side);
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

        var coverable = GTCapabilityHelper.getCoverable(level, pos, side);
        if (coverable == null) {
            return;
        }

        CoverBehavior cover = coverable.getCoverAtSide(side);
        if (cover == null) {
            return;
        }

        cover.getSyncDataHolder().applyServerNetworkUpdate(level.registryAccess(), data);
        cover.markAsChanged();
        if (level.getBlockEntity(pos) instanceof ManagedSyncBlockEntity syncBlockEntity) {
            syncBlockEntity.setChanged();
        }
    }

    @Override
    public @NotNull Type<CPacketCoverSyncToServer> type() {
        return TYPE;
    }
}

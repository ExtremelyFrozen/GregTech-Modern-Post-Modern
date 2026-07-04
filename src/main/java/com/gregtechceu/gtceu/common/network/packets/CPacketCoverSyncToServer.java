package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.managed.ManagedSyncBlockEntity;
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
    public static final StreamCodec<RegistryFriendlyByteBuf, CPacketCoverSyncToServer> CODEC = StreamCodec
            .ofMember(CPacketCoverSyncToServer::encode, CPacketCoverSyncToServer::new);

    private static final double MAX_INTERACTION_DISTANCE = 8.0;

    private final BlockPos pos;
    private final Direction side;
    private final ResourceLocation coverDefinitionId;
    private final DataComponentMap data;

    public CPacketCoverSyncToServer(BlockPos pos, Direction side, ResourceLocation coverDefinitionId,
                                    DataComponentMap data) {
        this.pos = pos;
        this.side = side;
        this.coverDefinitionId = coverDefinitionId;
        this.data = data;
    }

    public CPacketCoverSyncToServer(RegistryFriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.side = buf.readEnum(Direction.class);
        this.coverDefinitionId = buf.readResourceLocation();
        this.data = SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.decode(buf);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(side);
        buf.writeResourceLocation(coverDefinitionId);
        SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.encode(buf, data);
    }

    public void execute(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            GTCEu.LOGGER.warn("Sync: rejecting cover field update without server player");
            return;
        }

        if (data.isEmpty()) {
            GTCEu.LOGGER.warn("Sync: rejecting cover field update from {} because payload is empty",
                    player.getGameProfile().getName());
            return;
        }

        Level level = player.level();
        if (!level.isLoaded(pos)) {
            GTCEu.LOGGER.warn("Sync: rejecting cover field update from {} because {} is not loaded",
                    player.getGameProfile().getName(), pos);
            return;
        }

        if (!canInteract(player, pos)) {
            GTCEu.LOGGER.warn("Sync: rejecting cover field update from {} because interaction is not allowed",
                    player.getGameProfile().getName());
            return;
        }

        var coverable = GTCapabilityHelper.getCoverable(level, pos, side);
        if (coverable == null) {
            GTCEu.LOGGER.warn("Sync: rejecting cover field update from {} because cover holder at {} is invalid",
                    player.getGameProfile().getName(), pos);
            return;
        }

        CoverBehavior cover = coverable.getCoverAtSide(side);
        if (cover == null) {
            GTCEu.LOGGER.warn("Sync: rejecting cover field update from {} because side {} has no cover",
                    player.getGameProfile().getName(), side);
            return;
        }

        if (!cover.coverDefinition.getId().equals(coverDefinitionId)) {
            GTCEu.LOGGER.warn("Sync: rejecting cover field update from {} because cover at {} {} changed",
                    player.getGameProfile().getName(), pos, side);
            return;
        }

        MetaMachine machine = MetaMachine.getMachine(level, pos);
        if (machine != null && !MachineOwner.canOpenOwnerMachine(player, machine)) {
            GTCEu.LOGGER.warn("Sync: rejecting cover field update from {} because owner permission failed",
                    player.getGameProfile().getName());
            return;
        }

        cover.getSyncDataHolder().applyServerNetworkUpdate(level.registryAccess(), data);
        cover.markAsChanged();
        if (level.getBlockEntity(pos) instanceof ManagedSyncBlockEntity syncBlockEntity) {
            syncBlockEntity.setChanged();
        }
    }

    private boolean canInteract(ServerPlayer player, BlockPos pos) {
        return !player.isSpectator() && player.canInteractWithBlock(pos, MAX_INTERACTION_DISTANCE);
    }

    @Override
    public @NotNull Type<CPacketCoverSyncToServer> type() {
        return TYPE;
    }
}

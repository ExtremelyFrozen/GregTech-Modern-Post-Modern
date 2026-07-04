package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.managed.ManagedSyncBlockEntity;
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
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

    private static final double MAX_INTERACTION_DISTANCE = 8.0;

    private final BlockPos pos;
    private final ResourceLocation blockEntityTypeId;
    private final DataComponentMap data;

    public CPacketMachineSyncToServer(BlockPos pos, ResourceLocation blockEntityTypeId, DataComponentMap data) {
        this.pos = pos;
        this.blockEntityTypeId = blockEntityTypeId;
        this.data = data;
    }

    public CPacketMachineSyncToServer(RegistryFriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.blockEntityTypeId = buf.readResourceLocation();
        this.data = SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.decode(buf);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeResourceLocation(blockEntityTypeId);
        SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.encode(buf, data);
    }

    public void execute(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            GTCEu.LOGGER.warn("Sync: rejecting block entity field update without server player");
            return;
        }

        if (data.isEmpty()) {
            GTCEu.LOGGER.warn("Sync: rejecting block entity field update from {} because payload is empty",
                    player.getGameProfile().getName());
            return;
        }

        Level level = player.level();
        if (!level.isLoaded(pos)) {
            GTCEu.LOGGER.warn("Sync: rejecting block entity field update from {} because {} is not loaded",
                    player.getGameProfile().getName(), pos);
            return;
        }

        if (!canInteract(player, pos)) {
            GTCEu.LOGGER.warn("Sync: rejecting block entity field update from {} because interaction is not allowed",
                    player.getGameProfile().getName());
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ManagedSyncBlockEntity syncBlockEntity)) {
            GTCEu.LOGGER.warn("Sync: rejecting block entity field update from {} because holder at {} is invalid",
                    player.getGameProfile().getName(), pos);
            return;
        }

        if (!BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.getType()).equals(blockEntityTypeId)) {
            GTCEu.LOGGER.warn("Sync: rejecting block entity field update from {} because holder at {} changed",
                    player.getGameProfile().getName(), pos);
            return;
        }

        if (syncBlockEntity instanceof MetaMachine machine && !MachineOwner.canOpenOwnerMachine(player, machine)) {
            GTCEu.LOGGER.warn("Sync: rejecting block entity field update from {} because owner permission failed",
                    player.getGameProfile().getName());
            return;
        }

        syncBlockEntity.getSyncDataHolder().applyServerNetworkUpdate(level.registryAccess(), data);
        syncBlockEntity.markAsChanged();
        syncBlockEntity.setChanged();
    }

    private boolean canInteract(ServerPlayer player, BlockPos pos) {
        return !player.isSpectator() && player.canInteractWithBlock(pos, MAX_INTERACTION_DISTANCE);
    }

    @Override
    public @NotNull Type<CPacketMachineSyncToServer> type() {
        return TYPE;
    }
}

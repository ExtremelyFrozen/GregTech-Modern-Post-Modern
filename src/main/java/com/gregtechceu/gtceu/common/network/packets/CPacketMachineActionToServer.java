package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.managed.ManagedSyncBlockEntity;
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jetbrains.annotations.NotNull;

public class CPacketMachineActionToServer implements CustomPacketPayload {

    public static final ResourceLocation ID = GTCEu.id("machine_action_to_server");
    public static final Type<CPacketMachineActionToServer> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, CPacketMachineActionToServer> CODEC = StreamCodec
            .ofMember(CPacketMachineActionToServer::encode, CPacketMachineActionToServer::new);

    private static final double MAX_INTERACTION_DISTANCE = 8.0;

    private final BlockPos pos;
    private final SyncActionData action;

    public CPacketMachineActionToServer(BlockPos pos, SyncActionData action) {
        this.pos = pos;
        this.action = action;
    }

    public CPacketMachineActionToServer(RegistryFriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.action = SyncActionData.STREAM_CODEC.decode(buf);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        SyncActionData.STREAM_CODEC.encode(buf, action);
    }

    public void execute(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            GTCEu.LOGGER.warn("Sync action: rejecting machine action {} without server player", action.actionId());
            return;
        }

        Level level = player.level();
        if (!level.isLoaded(pos)) {
            GTCEu.LOGGER.warn("Sync action: rejecting machine action {} from {} because {} is not loaded",
                    action.actionId(), player.getGameProfile().getName(), pos);
            return;
        }

        if (!canInteract(player, pos)) {
            GTCEu.LOGGER.warn("Sync action: rejecting machine action {} from {} because interaction is not allowed",
                    action.actionId(), player.getGameProfile().getName());
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ManagedSyncBlockEntity syncBlockEntity)) {
            GTCEu.LOGGER.warn("Sync action: rejecting machine action {} from {} because holder at {} is invalid",
                    action.actionId(), player.getGameProfile().getName(), pos);
            return;
        }

        if (syncBlockEntity instanceof MetaMachine machine && !MachineOwner.canOpenOwnerMachine(player, machine)) {
            GTCEu.LOGGER.warn("Sync action: rejecting machine action {} from {} because owner permission failed",
                    action.actionId(), player.getGameProfile().getName());
            return;
        }

        SyncActionDispatchers.server().dispatch(SyncActionContext.machine(player, syncBlockEntity, action, pos));
    }

    private boolean canInteract(ServerPlayer player, BlockPos pos) {
        return !player.isSpectator() && player.canInteractWithBlock(pos, MAX_INTERACTION_DISTANCE);
    }

    @Override
    public @NotNull Type<CPacketMachineActionToServer> type() {
        return TYPE;
    }
}

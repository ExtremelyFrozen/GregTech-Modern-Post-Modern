package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jetbrains.annotations.NotNull;

public class CPacketCoverActionToServer implements CustomPacketPayload {

    public static final ResourceLocation ID = GTCEu.id("cover_action_to_server");
    public static final Type<CPacketCoverActionToServer> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, CPacketCoverActionToServer> CODEC = StreamCodec
            .ofMember(CPacketCoverActionToServer::encode, CPacketCoverActionToServer::new);

    private static final double MAX_INTERACTION_DISTANCE = 8.0;

    private final BlockPos pos;
    private final Direction side;
    private final ResourceLocation coverDefinitionId;
    private final SyncActionData action;

    public CPacketCoverActionToServer(BlockPos pos, Direction side, ResourceLocation coverDefinitionId,
                                      SyncActionData action) {
        this.pos = pos;
        this.side = side;
        this.coverDefinitionId = coverDefinitionId;
        this.action = action;
    }

    public CPacketCoverActionToServer(RegistryFriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.side = buf.readEnum(Direction.class);
        this.coverDefinitionId = buf.readResourceLocation();
        this.action = SyncActionData.STREAM_CODEC.decode(buf);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(side);
        buf.writeResourceLocation(coverDefinitionId);
        SyncActionData.STREAM_CODEC.encode(buf, action);
    }

    public void execute(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            GTCEu.LOGGER.warn("Sync action: rejecting cover action {} without server player", action.actionId());
            return;
        }

        if (action.payload().isEmpty()) {
            GTCEu.LOGGER.warn("Sync action: rejecting cover action {} because payload is empty", action.actionId());
            return;
        }

        Level level = player.level();
        if (!level.isLoaded(pos)) {
            GTCEu.LOGGER.warn("Sync action: rejecting cover action {} from {} because {} is not loaded",
                    action.actionId(), player.getGameProfile().getName(), pos);
            return;
        }

        if (!canInteract(player, pos)) {
            GTCEu.LOGGER.warn("Sync action: rejecting cover action {} from {} because interaction is not allowed",
                    action.actionId(), player.getGameProfile().getName());
            return;
        }

        var coverable = GTCapabilityHelper.getCoverable(level, pos, side);
        if (coverable == null) {
            GTCEu.LOGGER.warn("Sync action: rejecting cover action {} from {} because cover holder at {} is invalid",
                    action.actionId(), player.getGameProfile().getName(), pos);
            return;
        }

        CoverBehavior cover = coverable.getCoverAtSide(side);
        if (cover == null) {
            GTCEu.LOGGER.warn("Sync action: rejecting cover action {} from {} because side {} has no cover",
                    action.actionId(), player.getGameProfile().getName(), side);
            return;
        }

        if (!cover.coverDefinition.getId().equals(coverDefinitionId)) {
            GTCEu.LOGGER.warn("Sync action: rejecting cover action {} from {} because cover at {} {} changed",
                    action.actionId(), player.getGameProfile().getName(), pos, side);
            return;
        }

        MetaMachine machine = MetaMachine.getMachine(level, pos);
        if (machine != null && !MachineOwner.canOpenOwnerMachine(player, machine)) {
            GTCEu.LOGGER.warn("Sync action: rejecting cover action {} from {} because owner permission failed",
                    action.actionId(), player.getGameProfile().getName());
            return;
        }

        SyncActionDispatchers.server().dispatch(SyncActionContext.cover(player, cover, action, pos, side));
    }

    private boolean canInteract(ServerPlayer player, BlockPos pos) {
        return !player.isSpectator() && player.canInteractWithBlock(pos, MAX_INTERACTION_DISTANCE);
    }

    @Override
    public @NotNull Type<CPacketCoverActionToServer> type() {
        return TYPE;
    }
}

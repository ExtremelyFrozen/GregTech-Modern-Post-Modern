package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jetbrains.annotations.NotNull;

public class CPacketItemActionToServer implements CustomPacketPayload {

    public static final ResourceLocation ID = GTCEu.id("item_action_to_server");
    public static final Type<CPacketItemActionToServer> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, CPacketItemActionToServer> CODEC = StreamCodec
            .ofMember(CPacketItemActionToServer::encode, CPacketItemActionToServer::new);

    private final InteractionHand hand;
    private final ItemStack openedStack;
    private final SyncActionData action;

    public CPacketItemActionToServer(InteractionHand hand, ItemStack openedStack, SyncActionData action) {
        this.hand = hand;
        this.openedStack = openedStack;
        this.action = action;
    }

    public CPacketItemActionToServer(RegistryFriendlyByteBuf buf) {
        this.hand = buf.readEnum(InteractionHand.class);
        this.openedStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        this.action = SyncActionData.STREAM_CODEC.decode(buf);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(hand);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, openedStack);
        SyncActionData.STREAM_CODEC.encode(buf, action);
    }

    public void execute(IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            GTCEu.LOGGER.warn("Sync action: rejecting item action {} without server player", action.actionId());
            return;
        }

        if (action.payload().isEmpty()) {
            GTCEu.LOGGER.warn("Sync action: rejecting item action {} because payload is empty", action.actionId());
            return;
        }

        if (player.isSpectator()) {
            GTCEu.LOGGER.warn("Sync action: rejecting item action {} from {} because interaction is not allowed",
                    action.actionId(), player.getGameProfile().getName());
            return;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty()) {
            GTCEu.LOGGER.warn("Sync action: rejecting item action {} from {} because held item is missing",
                    action.actionId(), player.getGameProfile().getName());
            return;
        }

        if (!ItemStack.isSameItem(stack, openedStack)) {
            GTCEu.LOGGER.warn("Sync action: rejecting item action {} from {} because held item changed",
                    action.actionId(), player.getGameProfile().getName());
            return;
        }

        SyncActionDispatchers.server().dispatch(SyncActionContext.item(player, stack, openedStack, action, hand));
    }

    @Override
    public @NotNull Type<CPacketItemActionToServer> type() {
        return TYPE;
    }
}

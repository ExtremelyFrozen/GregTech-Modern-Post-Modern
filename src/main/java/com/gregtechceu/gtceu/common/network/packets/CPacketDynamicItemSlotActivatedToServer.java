package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.factory.GTDynamicItemSlotContainerMenu;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotOpeningToken;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Confirms that the client applied ACTIVATE before any server dynamic slot becomes interactive. */
public record CPacketDynamicItemSlotActivatedToServer(DynamicItemSlotOpeningToken token)
        implements CustomPacketPayload {

    public static final ResourceLocation ID = GTCEu.id("dynamic_item_slot_activated_to_server");
    public static final Type<CPacketDynamicItemSlotActivatedToServer> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, CPacketDynamicItemSlotActivatedToServer> CODEC = StreamCodec
            .ofMember(CPacketDynamicItemSlotActivatedToServer::encode,
                    CPacketDynamicItemSlotActivatedToServer::decode);

    public CPacketDynamicItemSlotActivatedToServer {
        if (token == null) {
            throw new IllegalArgumentException("dynamic item-slot ACTIVATED token must be present");
        }
    }

    private static CPacketDynamicItemSlotActivatedToServer decode(RegistryFriendlyByteBuf buffer) {
        return new CPacketDynamicItemSlotActivatedToServer(
                DynamicItemSlotOpeningToken.STREAM_CODEC.decode(buffer));
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        DynamicItemSlotOpeningToken.STREAM_CODEC.encode(buffer, token);
    }

    public void execute(IPayloadContext context) {
        GTDynamicItemSlotContainerMenu menu = DynamicItemSlotPacketRoute.resolveServer(context, token, ID);
        if (menu != null) {
            menu.receiveActivated((ServerPlayer) context.player(), token);
        }
    }

    @Override
    public Type<CPacketDynamicItemSlotActivatedToServer> type() {
        return TYPE;
    }
}

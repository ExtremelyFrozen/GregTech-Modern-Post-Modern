package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.factory.GTDynamicItemSlotContainerMenu;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotOpeningToken;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotSelection;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Requests one present binding page without changing client interaction before the server ACK. */
public record CPacketDynamicItemSlotSelectionToServer(DynamicItemSlotOpeningToken token,
                                                      DynamicItemSlotSelection selection)
        implements CustomPacketPayload {

    public static final ResourceLocation ID = GTCEu.id("dynamic_item_slot_selection_to_server");
    public static final Type<CPacketDynamicItemSlotSelectionToServer> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, CPacketDynamicItemSlotSelectionToServer> CODEC = StreamCodec
            .ofMember(CPacketDynamicItemSlotSelectionToServer::encode,
                    CPacketDynamicItemSlotSelectionToServer::decode);

    public CPacketDynamicItemSlotSelectionToServer {
        if (token == null || selection == null) {
            throw new IllegalArgumentException("dynamic item-slot selection request identity must be present");
        }
    }

    private static CPacketDynamicItemSlotSelectionToServer decode(RegistryFriendlyByteBuf buffer) {
        return new CPacketDynamicItemSlotSelectionToServer(
                DynamicItemSlotOpeningToken.STREAM_CODEC.decode(buffer),
                DynamicItemSlotSelection.STREAM_CODEC.decode(buffer));
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        DynamicItemSlotOpeningToken.STREAM_CODEC.encode(buffer, token);
        DynamicItemSlotSelection.STREAM_CODEC.encode(buffer, selection);
    }

    public void execute(IPayloadContext context) {
        GTDynamicItemSlotContainerMenu menu = DynamicItemSlotPacketRoute.resolveServer(context, token, ID);
        if (menu != null) {
            menu.receiveSelectionRequest((ServerPlayer) context.player(), token, selection);
        }
    }

    @Override
    public Type<CPacketDynamicItemSlotSelectionToServer> type() {
        return TYPE;
    }
}

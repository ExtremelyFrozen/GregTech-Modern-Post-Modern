package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.factory.GTDynamicItemSlotContainerMenu;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotOpeningToken;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotSelection;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Acknowledges the exact selection sequence and binding accepted by the authoritative server menu. */
public record SPacketDynamicItemSlotSelectionToClient(DynamicItemSlotOpeningToken token,
                                                      DynamicItemSlotSelection selection)
        implements CustomPacketPayload {

    public static final ResourceLocation ID = GTCEu.id("dynamic_item_slot_selection_to_client");
    public static final Type<SPacketDynamicItemSlotSelectionToClient> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SPacketDynamicItemSlotSelectionToClient> CODEC = StreamCodec
            .ofMember(SPacketDynamicItemSlotSelectionToClient::encode,
                    SPacketDynamicItemSlotSelectionToClient::decode);

    public SPacketDynamicItemSlotSelectionToClient {
        if (token == null || selection == null) {
            throw new IllegalArgumentException("dynamic item-slot selection acknowledgement identity must be present");
        }
    }

    private static SPacketDynamicItemSlotSelectionToClient decode(RegistryFriendlyByteBuf buffer) {
        return new SPacketDynamicItemSlotSelectionToClient(
                DynamicItemSlotOpeningToken.STREAM_CODEC.decode(buffer),
                DynamicItemSlotSelection.STREAM_CODEC.decode(buffer));
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        DynamicItemSlotOpeningToken.STREAM_CODEC.encode(buffer, token);
        DynamicItemSlotSelection.STREAM_CODEC.encode(buffer, selection);
    }

    public void execute(IPayloadContext context) {
        Player player = context.player();
        GTDynamicItemSlotContainerMenu menu = DynamicItemSlotPacketRoute.resolveClient(context, token, ID);
        if (menu != null) {
            menu.receiveSelectionAcknowledgement(player, token, selection);
        }
    }

    @Override
    public Type<SPacketDynamicItemSlotSelectionToClient> type() {
        return TYPE;
    }
}

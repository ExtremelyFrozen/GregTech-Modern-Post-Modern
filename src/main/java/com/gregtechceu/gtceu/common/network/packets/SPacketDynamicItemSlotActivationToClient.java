package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.factory.GTDynamicItemSlotContainerMenu;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotOpeningToken;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

/** Activates a prepared manifest with the server-confirmed optional page selection. */
public record SPacketDynamicItemSlotActivationToClient(DynamicItemSlotOpeningToken token,
                                                       Optional<UUID> selectedBindingId)
        implements CustomPacketPayload {

    public static final ResourceLocation ID = GTCEu.id("dynamic_item_slot_activation_to_client");
    public static final Type<SPacketDynamicItemSlotActivationToClient> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SPacketDynamicItemSlotActivationToClient> CODEC = StreamCodec
            .ofMember(SPacketDynamicItemSlotActivationToClient::encode,
                    SPacketDynamicItemSlotActivationToClient::decode);

    public SPacketDynamicItemSlotActivationToClient {
        if (token == null || selectedBindingId == null) {
            throw new IllegalArgumentException("dynamic item-slot ACTIVATE identity and selection must be present");
        }
    }

    private static SPacketDynamicItemSlotActivationToClient decode(RegistryFriendlyByteBuf buffer) {
        DynamicItemSlotOpeningToken token = DynamicItemSlotOpeningToken.STREAM_CODEC.decode(buffer);
        Optional<UUID> selectedBindingId = buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty();
        return new SPacketDynamicItemSlotActivationToClient(token, selectedBindingId);
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        DynamicItemSlotOpeningToken.STREAM_CODEC.encode(buffer, token);
        buffer.writeBoolean(selectedBindingId.isPresent());
        selectedBindingId.ifPresent(buffer::writeUUID);
    }

    public void execute(IPayloadContext context) {
        Player player = context.player();
        GTDynamicItemSlotContainerMenu menu = DynamicItemSlotPacketRoute.resolveClient(context, token, ID);
        if (menu != null) {
            menu.receiveActivation(player, token, selectedBindingId);
        }
    }

    @Override
    public Type<SPacketDynamicItemSlotActivationToClient> type() {
        return TYPE;
    }
}

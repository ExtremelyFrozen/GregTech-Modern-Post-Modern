package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.factory.GTDynamicItemSlotContainerMenu;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotManifest;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotOpeningToken;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Declares the next authoritative append-only dynamic-slot layout for one exact menu opening. */
public record SPacketDynamicItemSlotManifestToClient(DynamicItemSlotOpeningToken token,
                                                     DynamicItemSlotManifest manifest)
        implements CustomPacketPayload {

    public static final ResourceLocation ID = GTCEu.id("dynamic_item_slot_manifest_to_client");
    public static final Type<SPacketDynamicItemSlotManifestToClient> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SPacketDynamicItemSlotManifestToClient> CODEC = StreamCodec
            .ofMember(SPacketDynamicItemSlotManifestToClient::encode,
                    SPacketDynamicItemSlotManifestToClient::decode);

    public SPacketDynamicItemSlotManifestToClient {
        if (token == null || manifest == null) {
            throw new IllegalArgumentException("dynamic item-slot MANIFEST identity and layout must be present");
        }
        if (!token.matches(token.containerId(), token.menuSessionId(), manifest)) {
            throw new IllegalArgumentException("dynamic item-slot MANIFEST token does not match its layout");
        }
    }

    private static SPacketDynamicItemSlotManifestToClient decode(RegistryFriendlyByteBuf buffer) {
        return new SPacketDynamicItemSlotManifestToClient(
                DynamicItemSlotOpeningToken.STREAM_CODEC.decode(buffer),
                DynamicItemSlotManifest.STREAM_CODEC.decode(buffer));
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        DynamicItemSlotOpeningToken.STREAM_CODEC.encode(buffer, token);
        DynamicItemSlotManifest.STREAM_CODEC.encode(buffer, manifest);
    }

    public void execute(IPayloadContext context) {
        Player player = context.player();
        GTDynamicItemSlotContainerMenu menu = DynamicItemSlotPacketRoute.resolveClient(context, token, ID);
        if (menu != null) {
            menu.receiveManifest(player, token, manifest);
        }
    }

    @Override
    public Type<SPacketDynamicItemSlotManifestToClient> type() {
        return TYPE;
    }
}

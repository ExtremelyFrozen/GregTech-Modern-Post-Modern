package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.misc.PacketProspecting;
import com.gregtechceu.gtceu.api.gui.misc.ProspectorMode;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.jetbrains.annotations.NotNull;

public class SPacketProspectingMapData implements CustomPacketPayload {

    public static final ResourceLocation ID = GTCEu.id("prospecting_map_data");
    public static final Type<SPacketProspectingMapData> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SPacketProspectingMapData> CODEC = StreamCodec
            .ofMember(SPacketProspectingMapData::encode, SPacketProspectingMapData::new);

    private final InteractionHand hand;
    private final ItemStack openedStack;
    private final ProspectorMode<?> mode;
    private final PacketProspecting packet;

    public SPacketProspectingMapData(InteractionHand hand, ItemStack openedStack, PacketProspecting packet) {
        this.hand = hand;
        this.openedStack = openedStack.copy();
        this.mode = packet.mode;
        this.packet = packet;
    }

    public SPacketProspectingMapData(RegistryFriendlyByteBuf buf) {
        this.hand = buf.readEnum(InteractionHand.class);
        this.openedStack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
        this.mode = ProspectorMode.fromNetworkId(buf.readVarInt());
        this.packet = PacketProspecting.readPacketData(mode, buf);
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeEnum(hand);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, openedStack);
        buf.writeVarInt(ProspectorMode.getNetworkId(mode));
        packet.writePacketData(buf);
    }

    public void execute(IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            ClientPacketHandlers.handleProspectingMapData(this, context);
        }
    }

    public InteractionHand hand() {
        return hand;
    }

    public ItemStack openedStack() {
        return openedStack;
    }

    public ProspectorMode<?> mode() {
        return mode;
    }

    public PacketProspecting packet() {
        return packet;
    }

    @Override
    public @NotNull Type<SPacketProspectingMapData> type() {
        return TYPE;
    }
}

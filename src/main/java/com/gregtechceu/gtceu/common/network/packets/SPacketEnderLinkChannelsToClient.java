package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SPacketEnderLinkChannelsToClient implements CustomPacketPayload {

    public static final ResourceLocation ID = GTCEu.id("ender_link_channels_to_client");
    public static final Type<SPacketEnderLinkChannelsToClient> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, SPacketEnderLinkChannelsToClient> CODEC = StreamCodec
            .ofMember(SPacketEnderLinkChannelsToClient::encode, SPacketEnderLinkChannelsToClient::new);

    private final BlockPos pos;
    private final Direction side;
    private final ResourceLocation coverDefinitionId;
    private final List<JsonElement> entries;

    public SPacketEnderLinkChannelsToClient(BlockPos pos, Direction side, ResourceLocation coverDefinitionId,
                                            List<JsonElement> entries) {
        this.pos = pos;
        this.side = side;
        this.coverDefinitionId = coverDefinitionId;
        this.entries = List.copyOf(entries);
    }

    public SPacketEnderLinkChannelsToClient(RegistryFriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.side = buf.readEnum(Direction.class);
        this.coverDefinitionId = buf.readResourceLocation();
        int size = buf.readVarInt();
        this.entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(buf.readJsonWithCodec(ExtraCodecs.JSON));
        }
    }

    public void encode(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(pos);
        buf.writeEnum(side);
        buf.writeResourceLocation(coverDefinitionId);
        buf.writeVarInt(entries.size());
        for (JsonElement entry : entries) {
            buf.writeJsonWithCodec(ExtraCodecs.JSON, entry);
        }
    }

    public void execute(IPayloadContext context) {
        if (context.flow() == PacketFlow.CLIENTBOUND) {
            ClientPacketHandlers.handleEnderLinkChannels(this, context);
        }
    }

    public BlockPos pos() {
        return pos;
    }

    public Direction side() {
        return side;
    }

    public ResourceLocation coverDefinitionId() {
        return coverDefinitionId;
    }

    public List<JsonElement> entries() {
        return entries;
    }

    @Override
    public @NotNull Type<SPacketEnderLinkChannelsToClient> type() {
        return TYPE;
    }
}

package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputWaitingListReceiver;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputWaitingListRoute;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputWaitingListTarget;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputWaitingListUpdate;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Delivers one bounded waiting-list chunk only to its exact LDLib2 machine menu opening.
 *
 * @param containerId     active LDLib2 menu id captured by the server subscription
 * @param target          persistent output bus identity resolved inside the menu tree
 * @param openingId       client element opening that requested the publication
 * @param requestSequence request generation echoed from the accepted full-state action
 * @param update          one bounded chunk of the authoritative publication
 */
public record SPacketMEOutputWaitingListToClient(int containerId, MEOutputWaitingListTarget target,
                                                 UUID openingId, int requestSequence,
                                                 MEOutputWaitingListUpdate update)
        implements CustomPacketPayload {

    /**
     * Packet identifier registered only while AE2 integration is available.
     */
    public static final ResourceLocation ID = GTCEu.id("me_output_waiting_list_to_client");

    /**
     * Custom payload type for the waiting-list S2C channel.
     */
    public static final Type<SPacketMEOutputWaitingListToClient> TYPE = new Type<>(ID);

    /**
     * Registry-aware packet codec whose nested update enforces all chunk bounds before allocation.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, SPacketMEOutputWaitingListToClient> CODEC = StreamCodec
            .ofMember(SPacketMEOutputWaitingListToClient::encode,
                    SPacketMEOutputWaitingListToClient::decode);

    /**
     * Validates required immutable packet identity fields.
     */
    public SPacketMEOutputWaitingListToClient {
        if (containerId < 0) {
            throw new IllegalArgumentException("ME output waiting-list container id must be non-negative: " +
                    containerId);
        }
        if (target == null || openingId == null || update == null) {
            throw new IllegalArgumentException("ME output waiting-list packet identity and update must be present.");
        }
        if (requestSequence < 0) {
            throw new IllegalArgumentException("ME output waiting-list request sequence must be non-negative: " +
                    requestSequence);
        }
    }

    private static SPacketMEOutputWaitingListToClient decode(RegistryFriendlyByteBuf buffer) {
        return new SPacketMEOutputWaitingListToClient(
                buffer.readVarInt(),
                MEOutputWaitingListTarget.STREAM_CODEC.decode(buffer),
                buffer.readUUID(),
                buffer.readVarInt(),
                MEOutputWaitingListUpdate.STREAM_CODEC.decode(buffer));
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        MEOutputWaitingListTarget.STREAM_CODEC.encode(buffer, target);
        buffer.writeUUID(openingId);
        buffer.writeVarInt(requestSequence);
        MEOutputWaitingListUpdate.STREAM_CODEC.encode(buffer, update);
    }

    /**
     * Rejects stale menus and routes the chunk through the fixed waiting-list element id.
     */
    public void execute(IPayloadContext context) {
        Player player = context.player();
        if (!player.level().isClientSide) {
            return;
        }
        MEOutputWaitingListReceiver receiver = MEOutputWaitingListRoute.resolve(
                player, containerId, target);
        if (receiver != null) {
            receiver.applyWaitingListUpdate(openingId, requestSequence, update);
        }
    }

    @Override
    public Type<SPacketMEOutputWaitingListToClient> type() {
        return TYPE;
    }
}

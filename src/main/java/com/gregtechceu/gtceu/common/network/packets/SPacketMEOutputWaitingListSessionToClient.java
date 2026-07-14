package com.gregtechceu.gtceu.common.network.packets;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputWaitingListReceiver;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputWaitingListRoute;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputWaitingListSessionReceiver;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputWaitingListTarget;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * Challenges one pending full-state action with the nonce of its exact server menu element.
 */
public record SPacketMEOutputWaitingListSessionToClient(int containerId, MEOutputWaitingListTarget target,
                                                        UUID openingId, int requestSequence, UUID menuSessionId)
        implements CustomPacketPayload {

    /** Packet identifier registered only while AE2 integration is available. */
    public static final ResourceLocation ID = GTCEu.id("me_output_waiting_list_session_to_client");

    /** Custom payload type for the waiting-list menu-session challenge. */
    public static final Type<SPacketMEOutputWaitingListSessionToClient> TYPE = new Type<>(ID);

    /** Codec for the routed opening identity and unpredictable server nonce. */
    public static final StreamCodec<RegistryFriendlyByteBuf, SPacketMEOutputWaitingListSessionToClient> CODEC = StreamCodec
            .ofMember(SPacketMEOutputWaitingListSessionToClient::encode,
                    SPacketMEOutputWaitingListSessionToClient::decode);

    /** Validates the complete challenge identity before it can be routed. */
    public SPacketMEOutputWaitingListSessionToClient {
        if (containerId < 0) {
            throw new IllegalArgumentException("ME output waiting-list session container id must be non-negative: " +
                    containerId);
        }
        if (target == null || openingId == null || menuSessionId == null) {
            throw new IllegalArgumentException("ME output waiting-list session identity must be present.");
        }
        if (requestSequence < 0) {
            throw new IllegalArgumentException("ME output waiting-list session sequence must be non-negative: " +
                    requestSequence);
        }
    }

    private static SPacketMEOutputWaitingListSessionToClient decode(RegistryFriendlyByteBuf buffer) {
        return new SPacketMEOutputWaitingListSessionToClient(
                buffer.readVarInt(),
                MEOutputWaitingListTarget.STREAM_CODEC.decode(buffer),
                buffer.readUUID(),
                buffer.readVarInt(),
                buffer.readUUID());
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        MEOutputWaitingListTarget.STREAM_CODEC.encode(buffer, target);
        buffer.writeUUID(openingId);
        buffer.writeVarInt(requestSequence);
        buffer.writeUUID(menuSessionId);
    }

    /** Routes the challenge only to the matching pending client element in the active menu. */
    public void execute(IPayloadContext context) {
        Player player = context.player();
        if (!player.level().isClientSide) {
            return;
        }
        MEOutputWaitingListReceiver receiver = MEOutputWaitingListRoute.resolve(player, containerId, target);
        if (receiver instanceof MEOutputWaitingListSessionReceiver sessionReceiver) {
            sessionReceiver.applyWaitingListMenuSession(openingId, requestSequence, menuSessionId);
        }
    }

    @Override
    public Type<SPacketMEOutputWaitingListSessionToClient> type() {
        return TYPE;
    }
}

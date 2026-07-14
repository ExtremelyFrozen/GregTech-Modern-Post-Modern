package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Receives opening-scoped waiting-list publications after the packet validates the active machine UI.
 */
public interface MEOutputWaitingListReceiver {

    /**
     * Stable LDLib2 element id used by the S2C packet to resolve the active receiver.
     */
    String ELEMENT_ID_PREFIX = "me_output_waiting_list_";

    /**
     * Returns the deterministic element id for one machine position inside a standalone or controller UI tree.
     */
    static String elementId(BlockPos pos) {
        return ELEMENT_ID_PREFIX + Long.toUnsignedString(pos.asLong(), 16);
    }

    /**
     * Returns whether this receiver still resolves the packet's persistent target identity.
     */
    boolean matchesWaitingListTarget(MEOutputWaitingListTarget target);

    /**
     * Returns whether this receiver still resolves the exact server machine that owns a publisher.
     */
    boolean matchesWaitingListTarget(MetaMachine machine);

    /**
     * Returns whether this receiver's exact output machine may register a full-state request for the player.
     */
    boolean canRequestFull(ServerPlayer player);

    /**
     * Registers an ordered full-state request against the exact output machine resolved by this receiver.
     */
    boolean requestFull(ServerPlayer player, UUID openingId, int requestSequence);

    /**
     * Applies one validated update chunk to the exact client opening that requested it.
     */
    void applyWaitingListUpdate(UUID openingId, int requestSequence, MEOutputWaitingListUpdate update);
}

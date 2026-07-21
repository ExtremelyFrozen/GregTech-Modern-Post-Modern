package com.gregtechceu.gtceu.integration.ae2.machine;

import net.minecraft.MethodsReturnNonnullByDefault;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Owns the single-use challenge that authenticates one output waiting-list request against a server menu element.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class MEOutputWaitingListMenuSession {

    /** Client opening currently permitted to answer the pending challenge. */
    private @Nullable UUID challengedOpeningId;
    /** Request generation currently permitted to answer the pending challenge. */
    private int challengedRequestSequence = -1;
    /** Unpredictable nonce invalidated immediately after one successful response. */
    private @Nullable UUID challengeId;

    /**
     * Replaces any previous challenge and returns the nonce bound to this exact opening and request generation.
     */
    public UUID issue(UUID openingId, int requestSequence) {
        if (requestSequence < 0) {
            throw new IllegalArgumentException("ME output waiting-list challenge sequence must be non-negative.");
        }
        challengedOpeningId = openingId;
        challengedRequestSequence = requestSequence;
        challengeId = UUID.randomUUID();
        return challengeId;
    }

    /**
     * Consumes the challenge only when its opening, request generation, and unpredictable nonce all match.
     */
    public boolean consume(UUID openingId, int requestSequence, UUID menuSessionId) {
        if (requestSequence < 0) {
            throw new IllegalArgumentException("ME output waiting-list response sequence must be non-negative.");
        }
        if (challengedOpeningId == null || challengeId == null ||
                !challengedOpeningId.equals(openingId) || challengedRequestSequence != requestSequence ||
                !challengeId.equals(menuSessionId)) {
            return false;
        }
        challengedOpeningId = null;
        challengedRequestSequence = -1;
        challengeId = null;
        return true;
    }
}

package com.gregtechceu.gtceu.integration.ae2.machine;

import java.util.UUID;

/**
 * Authenticates full-state actions against the exact server-created menu element that issued a challenge.
 */
public interface MEOutputWaitingListSessionReceiver extends MEOutputWaitingListReceiver {

    /**
     * Returns the single-use challenge state owned by this exact server menu element.
     */
    MEOutputWaitingListMenuSession getWaitingListMenuSession();

    /**
     * Issues a fresh challenge bound to the supplied client opening and request generation.
     */
    default UUID issueWaitingListMenuSessionChallenge(UUID openingId, int requestSequence) {
        return getWaitingListMenuSession().issue(openingId, requestSequence);
    }

    /**
     * Consumes the exact pending challenge before the request may reach its publisher.
     */
    default boolean consumeWaitingListMenuSessionChallenge(UUID openingId, int requestSequence,
                                                           UUID menuSessionId) {
        return getWaitingListMenuSession().consume(openingId, requestSequence, menuSessionId);
    }

    /**
     * Accepts a routed server challenge only for the client opening and request generation that is still pending.
     */
    void applyWaitingListMenuSession(UUID openingId, int requestSequence, UUID menuSessionId);
}

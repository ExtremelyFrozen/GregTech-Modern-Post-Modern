package com.gregtechceu.gtceu.integration.ae2.machine;

/**
 * Exposes the opening-scoped waiting-list publisher to the registered machine action handler.
 */
public interface MEOutputWaitingListActionTarget {

    /**
     * Returns the persistent machine identity used to reject actions and publications for a replaced output bus.
     */
    MEOutputWaitingListTarget getWaitingListTarget();

    /**
     * Returns the publisher owned by this exact ME output machine instance.
     */
    MEOutputWaitingListPublisher getWaitingListPublisher();
}

package com.gregtechceu.gtceu.api.gui.slot;

/**
 * Classifies one opening-scoped dynamic slot protocol transition without hiding fail-closed outcomes.
 */
public enum DynamicItemSlotTransition {

    /** The message advanced the protocol exactly once. */
    ACCEPTED,
    /** The message exactly repeated a transition that was already applied. */
    DUPLICATE,
    /** The message belongs to an older epoch or request sequence and did not mutate state. */
    STALE,
    /** The message arrived in an invalid but non-conflicting phase and did not mutate state. */
    REJECTED,
    /** The message conflicted with opening identity or immutable state and permanently closes the opening. */
    CLOSE_OPENING
}

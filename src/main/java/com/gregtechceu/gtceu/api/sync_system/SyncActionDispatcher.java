package com.gregtechceu.gtceu.api.sync_system;

/**
 * Registration and dispatch point for server sync action handlers.
 */
public interface SyncActionDispatcher {

    /**
     * Registers a handler for its action id.
     */
    void register(SyncActionHandler handler);

    /**
     * Dispatches an already-resolved server action context.
     *
     * @return {@code true} when a handler accepted and executed the action.
     */
    boolean dispatch(SyncActionContext context);
}

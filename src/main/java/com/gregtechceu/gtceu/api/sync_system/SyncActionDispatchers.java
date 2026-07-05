package com.gregtechceu.gtceu.api.sync_system;

/**
 * Shared access point for the server sync action dispatcher.
 */
public final class SyncActionDispatchers {

    /**
     * Singleton dispatcher used by packet handlers and action registration code.
     */
    private static final SyncActionDispatcher SERVER = new SyncActionDispatcherRegistry();

    private SyncActionDispatchers() {}

    /**
     * Returns the server dispatcher for registering or executing sync actions.
     */
    public static SyncActionDispatcher server() {
        return SERVER;
    }
}

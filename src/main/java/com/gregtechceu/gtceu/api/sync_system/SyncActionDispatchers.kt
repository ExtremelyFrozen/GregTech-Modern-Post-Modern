package com.gregtechceu.gtceu.api.sync_system

/**
 * Shared access point for the server sync action dispatcher.
 */
object SyncActionDispatchers {

	/**
	 * Singleton dispatcher used by packet handlers and action registration code.
	 */
	private val SERVER: SyncActionDispatcher = SyncActionDispatcherRegistry()

	/**
	 * Returns the server dispatcher for registering or executing sync actions.
	 */
	@JvmStatic
	fun server(): SyncActionDispatcher = SERVER
}

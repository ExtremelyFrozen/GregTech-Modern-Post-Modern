package com.gregtechceu.gtceu.api.sync_system

/**
 * Registration and dispatch point for server sync action handlers.
 */
interface SyncActionDispatcher {

	/**
	 * Registers a handler for its action id.
	 */
	fun register(handler: SyncActionHandler)

	/**
	 * Dispatches an already-resolved server action context.
	 *
	 * @return `true` when a handler accepted and executed the action.
	 */
	fun dispatch(context: SyncActionContext): Boolean
}

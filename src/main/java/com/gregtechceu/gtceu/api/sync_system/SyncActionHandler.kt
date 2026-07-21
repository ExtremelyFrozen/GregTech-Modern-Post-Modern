package com.gregtechceu.gtceu.api.sync_system

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/**
 * Server-side business handler for a single sync action id.
 */
interface SyncActionHandler {

	/**
	 * Returns the action identifier used by clients and the dispatcher.
	 */
	fun actionId(): ResourceLocation

	/**
	 * Checks that the resolved holder in the current context is valid for this action.
	 */
	fun acceptsHolder(context: SyncActionContext): Boolean

	/**
	 * Checks that the component payload contains the typed data required by this action.
	 */
	fun acceptsPayload(payload: DataComponentMap): Boolean

	/**
	 * Checks player permissions after the packet has resolved a valid holder.
	 */
	fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean

	/**
	 * Executes the action after id, holder, payload, and permission validation pass.
	 */
	fun execute(context: SyncActionContext)
}

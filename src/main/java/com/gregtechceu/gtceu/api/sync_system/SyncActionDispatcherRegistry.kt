package com.gregtechceu.gtceu.api.sync_system

import com.gregtechceu.gtceu.GTCEu

import net.minecraft.resources.ResourceLocation

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Default server dispatcher backed by action id registrations.
 */
open class SyncActionDispatcherRegistry : SyncActionDispatcher {

	/**
	 * Stores the registered handler for each action id.
	 */
	private val handlers: ConcurrentMap<ResourceLocation, SyncActionHandler> = ConcurrentHashMap()

	override fun register(handler: SyncActionHandler) {
		val actionId = handler.actionId()

		val previous = handlers.putIfAbsent(actionId, handler)
		if (previous != null) {
			throw IllegalArgumentException("Duplicate sync action handler for $actionId")
		}
	}

	override fun dispatch(context: SyncActionContext): Boolean {
		val actionId = context.actionId()
		val handler = handlers[actionId]
		if (handler == null) {
			GTCEu.LOGGER.warn(
				"Sync action: rejecting unknown action {} from {}",
				actionId,
				context.player.gameProfile.name,
			)
			return false
		}

		if (!handler.acceptsHolder(context)) {
			GTCEu.LOGGER.warn(
				"Sync action: rejecting invalid holder for action {} from {}",
				actionId,
				context.player.gameProfile.name,
			)
			return false
		}

		try {
			if (!handler.acceptsPayload(context.payload())) {
				GTCEu.LOGGER.warn(
					"Sync action: rejecting invalid payload for action {} from {}",
					actionId,
					context.player.gameProfile.name,
				)
				return false
			}

			if (!handler.mayExecute(context.player, context)) {
				GTCEu.LOGGER.warn(
					"Sync action: rejecting permission failure for action {} from {}",
					actionId,
					context.player.gameProfile.name,
				)
				return false
			}

			handler.execute(context)
			return true
		} catch (exception: RuntimeException) {
			GTCEu.LOGGER.error(
				"Sync action: failed to execute action {} from {}",
				actionId,
				context.player.gameProfile.name,
				exception,
			)
			return false
		}
	}
}

package com.gregtechceu.gtceu.common.cover.voiding

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/** Owns the wire protocol and server handler for item voiding cover working-enabled actions. */
object ItemVoidingCoverConfigActions {

	private val SET_ITEM_VOIDING_COVER_CONFIG_ACTION = GTCEu.id("set_item_voiding_cover_config")

	init {
		SyncActionDispatchers.server().register(ItemVoidingCoverConfigActionHandler)
	}

	/** Forces common-side handler registration when the owning cover class initializes. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one request to change an item voiding cover's working-enabled state. */
	@JvmStatic
	fun createSetWorkingEnabledAction(workingEnabled: Boolean): SyncActionData = VoidingWorkingEnabledActionProtocol.createAction(
		SET_ITEM_VOIDING_COVER_CONFIG_ACTION,
		workingEnabled,
	)

	private object ItemVoidingCoverConfigActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_ITEM_VOIDING_COVER_CONFIG_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is ItemVoidingCover

		override fun acceptsPayload(payload: DataComponentMap): Boolean = VoidingWorkingEnabledActionProtocol.read(payload) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			val cover =
				context.holder as? ItemVoidingCover
					?: throw IllegalStateException(
						"Item voiding cover config action received a non-item-voiding cover.",
					)
			cover.setWorkingEnabled(VoidingWorkingEnabledActionProtocol.require(context.payload()))
		}
	}
}

package com.gregtechceu.gtceu.common.cover.detector

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/** Owns the wire protocol and server handler for advanced item detector configuration actions. */
object AdvancedItemDetectorConfigActions {

	private val SET_ADVANCED_ITEM_DETECTOR_CONFIG_ACTION = GTCEu.id("set_advanced_item_detector_config")

	init {
		SyncActionDispatchers.server().register(AdvancedItemDetectorConfigActionHandler)
	}

	/** Forces common-side handler registration when the owning cover class initializes. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one complete client request from the current advanced item detector state. */
	@JvmStatic
	fun createSetConfigAction(min: Int, max: Int, latched: Boolean, inverted: Boolean): SyncActionData = AdvancedDetectorConfigActionProtocol.createAction(
		SET_ADVANCED_ITEM_DETECTOR_CONFIG_ACTION,
		min,
		max,
		latched,
		inverted,
	)

	private object AdvancedItemDetectorConfigActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_ADVANCED_ITEM_DETECTOR_CONFIG_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is AdvancedItemDetectorCover

		override fun acceptsPayload(payload: DataComponentMap): Boolean = AdvancedDetectorConfigActionProtocol.read(payload) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			val cover =
				context.holder as? AdvancedItemDetectorCover
					?: throw IllegalStateException(
						"Advanced item detector config action received a non-item detector.",
					)
			val config = AdvancedDetectorConfigActionProtocol.require(context.payload())
			cover.setMinValue(config.min)
			cover.setMaxValue(config.max)
			cover.setLatched(config.latched)
			cover.setInverted(config.inverted)
		}
	}
}

package com.gregtechceu.gtceu.common.cover.detector

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/** Owns the wire protocol and server handler for advanced fluid detector configuration actions. */
object AdvancedFluidDetectorConfigActions {

	private val SET_ADVANCED_FLUID_DETECTOR_CONFIG_ACTION = GTCEu.id("set_advanced_fluid_detector_config")

	init {
		SyncActionDispatchers.server().register(AdvancedFluidDetectorConfigActionHandler)
	}

	/** Forces common-side handler registration when the owning cover class initializes. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one complete client request from the current advanced fluid detector state. */
	@JvmStatic
	fun createSetConfigAction(min: Int, max: Int, latched: Boolean, inverted: Boolean): SyncActionData = AdvancedDetectorConfigActionProtocol.createAction(
		SET_ADVANCED_FLUID_DETECTOR_CONFIG_ACTION,
		min,
		max,
		latched,
		inverted,
	)

	private object AdvancedFluidDetectorConfigActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_ADVANCED_FLUID_DETECTOR_CONFIG_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is AdvancedFluidDetectorCover

		override fun acceptsPayload(payload: DataComponentMap): Boolean = AdvancedDetectorConfigActionProtocol.read(payload) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			val cover =
				context.holder as? AdvancedFluidDetectorCover
					?: throw IllegalStateException(
						"Advanced fluid detector config action received a non-fluid detector.",
					)
			val config = AdvancedDetectorConfigActionProtocol.require(context.payload())
			cover.setMinValue(config.min)
			cover.setMaxValue(config.max)
			cover.setLatched(config.latched)
			cover.setInverted(config.inverted)
		}
	}
}

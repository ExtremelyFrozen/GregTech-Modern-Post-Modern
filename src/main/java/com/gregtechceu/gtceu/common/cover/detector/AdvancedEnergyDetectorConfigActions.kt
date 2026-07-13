package com.gregtechceu.gtceu.common.cover.detector

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/** Owns the wire protocol and server handler for advanced energy detector configuration actions. */
object AdvancedEnergyDetectorConfigActions {

	private val SET_ADVANCED_ENERGY_DETECTOR_CONFIG_ACTION = GTCEu.id("set_advanced_energy_detector_config")

	init {
		SyncActionDispatchers.server().register(AdvancedEnergyDetectorConfigActionHandler)
	}

	/** Forces common-side handler registration when the owning cover class initializes. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one complete client request from the current advanced energy detector state. */
	@JvmStatic
	fun createSetConfigAction(min: Long, max: Long, usePercent: Boolean, inverted: Boolean): SyncActionData = AdvancedEnergyDetectorConfigActionProtocol.createAction(
		SET_ADVANCED_ENERGY_DETECTOR_CONFIG_ACTION,
		min,
		max,
		usePercent,
		inverted,
	)

	private object AdvancedEnergyDetectorConfigActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_ADVANCED_ENERGY_DETECTOR_CONFIG_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is AdvancedEnergyDetectorCover

		override fun acceptsPayload(payload: DataComponentMap): Boolean = AdvancedEnergyDetectorConfigActionProtocol.read(payload) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			val cover =
				context.holder as? AdvancedEnergyDetectorCover
					?: throw IllegalStateException(
						"Advanced energy detector config action received a non-energy detector.",
					)
			val config = AdvancedEnergyDetectorConfigActionProtocol.require(context.payload())
			cover.setUsePercent(config.usePercent)
			cover.setMinValue(config.min)
			cover.setMaxValue(config.max)
			cover.setInverted(config.inverted)
		}
	}
}

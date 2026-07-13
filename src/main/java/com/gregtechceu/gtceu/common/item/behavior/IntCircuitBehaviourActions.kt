package com.gregtechceu.gtceu.common.item.behavior

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/** Owns the wire protocol and server handler for programmed circuit configuration actions. */
object IntCircuitBehaviourActions {

	private val SET_CIRCUIT_CONFIGURATION_ACTION = GTCEu.id("set_circuit_configuration")

	init {
		SyncActionDispatchers.server().register(CircuitConfigurationActionHandler)
	}

	/** Forces common-side handler registration when the programmed circuit behavior initializes. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one request to change the current programmed circuit's configuration. */
	@JvmStatic
	fun createSetCircuitConfigurationAction(configuration: Int): SyncActionData {
		require(isValidCircuitConfiguration(configuration)) {
			"Given configuration number is out of range!"
		}
		val payload =
			DataComponentMap
				.builder()
				.set(GTDataComponents.CIRCUIT_CONFIG.get(), configuration)
				.build()
		return SyncActionData(SET_CIRCUIT_CONFIGURATION_ACTION, configuration, payload)
	}

	private object CircuitConfigurationActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_CIRCUIT_CONFIGURATION_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean {
			val stack = context.holder as? ItemStack ?: return false
			val openedStack = context.openedStack ?: return false
			return IntCircuitBehaviour.isIntegratedCircuit(stack) &&
				IntCircuitBehaviour.isIntegratedCircuit(openedStack) &&
				ItemStack.isSameItem(stack, openedStack)
		}

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val configuration = payload[GTDataComponents.CIRCUIT_CONFIG.get()] ?: return false
			return isValidCircuitConfiguration(configuration)
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			val stack = context.holder as? ItemStack
				?: throw IllegalStateException("Circuit configuration action received a non-item holder.")
			val configuration = context.payload()[GTDataComponents.CIRCUIT_CONFIG.get()]
				?: throw IllegalStateException("Circuit configuration action payload is missing.")
			IntCircuitBehaviour.setCircuitConfiguration(stack, configuration)
		}
	}

	private fun isValidCircuitConfiguration(configuration: Int): Boolean = configuration in 0..IntCircuitBehaviour.CIRCUIT_MAX
}

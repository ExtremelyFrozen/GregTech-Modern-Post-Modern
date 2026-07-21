package com.gregtechceu.gtceu.api.machine.fancyconfigurator

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.data.GTDataComponents
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

import com.google.gson.JsonPrimitive

/** Owns the GT action protocol for the LDLib2 machine circuit configurator. */
object LDLib2CircuitFancyConfiguratorActions {

	private const val SELECTED_SLOT = 0
	private const val NO_CONFIG = -1
	private val SET_MACHINE_CIRCUIT_CONFIGURATION_ACTION = GTCEu.id("set_machine_circuit_configuration")
	private val CIRCUIT_CONFIGURATION_FIELD = SyncFieldData.key("circuitConfig")

	init {
		SyncActionDispatchers.server().register(MachineCircuitConfigurationActionHandler)
	}

	/** Forces common-side handler registration before the configurator can send its action. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one holder-scoped request to change the machine circuit configuration. */
	@JvmStatic
	fun createSetMachineCircuitConfigurationAction(configuration: Int): SyncActionData {
		require(isValidActionConfiguration(configuration)) {
			"Machine circuit action configuration is out of range: $configuration"
		}
		val fields =
			SyncFieldData.builder()
				.put(CIRCUIT_CONFIGURATION_FIELD, JsonPrimitive(configuration))
				.build()
		return SyncActionData(
			SET_MACHINE_CIRCUIT_CONFIGURATION_ACTION,
			configuration,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private object MachineCircuitConfigurationActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_MACHINE_CIRCUIT_CONFIGURATION_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = readCircuitHolder(context) != null

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val configuration = readCircuitConfiguration(payload) ?: return false
			return isValidActionConfiguration(configuration)
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			val holder = requireCircuitHolder(context)
			LDLib2CircuitFancyConfigurator.writeMachineCircuitConfiguration(
				holder.circuitInventory,
				requireCircuitConfiguration(context.payload()),
			)
		}
	}

	private fun readCircuitHolder(context: SyncActionContext): IHasCircuitSlot? {
		val holder = context.holder
		if (holder !is IHasCircuitSlot || holder !is LDLib2FancyActionMachine) {
			return null
		}
		if (!holder.isCircuitSlotEnabled || holder.circuitInventory.slots <= SELECTED_SLOT) {
			return null
		}
		return holder
	}

	private fun requireCircuitHolder(context: SyncActionContext): IHasCircuitSlot = readCircuitHolder(context)
		?: throw IllegalStateException("Machine circuit action received an invalid holder.")

	private fun readCircuitConfiguration(payload: DataComponentMap): Int? {
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		val primitive = fields[CIRCUIT_CONFIGURATION_FIELD] as? JsonPrimitive ?: return null
		if (!primitive.isNumber) {
			return null
		}
		return try {
			primitive.asBigDecimal.intValueExact()
		} catch (exception: NumberFormatException) {
			logInvalidInteger(exception)
			null
		} catch (exception: ArithmeticException) {
			logInvalidInteger(exception)
			null
		}
	}

	private fun requireCircuitConfiguration(payload: DataComponentMap): Int {
		val configuration = readCircuitConfiguration(payload)
		if (configuration == null || !isValidActionConfiguration(configuration)) {
			throw IllegalStateException("Machine circuit action payload is missing or invalid.")
		}
		return configuration
	}

	private fun isValidActionConfiguration(configuration: Int): Boolean = configuration in NO_CONFIG..IntCircuitBehaviour.CIRCUIT_MAX

	private fun logInvalidInteger(exception: RuntimeException) {
		GTCEu.LOGGER.warn("Invalid machine circuit integer action payload.", exception)
	}
}

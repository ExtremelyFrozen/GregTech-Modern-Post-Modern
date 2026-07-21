package com.gregtechceu.gtceu.common.cover

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.cover.data.ControllerMode
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

import com.google.gson.JsonPrimitive
import org.jetbrains.annotations.ApiStatus

/** Exposes only the mode permission and ordered mutations required by the machine controller action handler. */
@ApiStatus.Internal
interface MachineControllerCoverConfigActionTarget {

	/** Returns the controller modes currently reachable from this cover. */
	fun getAllowedModes(): List<ControllerMode>

	/** Applies the validated nullable controller mode before all dependent settings. */
	fun setControllerMode(controllerMode: ControllerMode?)

	/** Applies the validated redstone threshold after the controller mode. */
	fun setMinRedstoneStrength(minRedstoneStrength: Int)

	/** Applies the validated inversion state after the redstone threshold. */
	fun setInverted(inverted: Boolean)

	/** Applies the validated power-failure behavior after the other controller settings. */
	fun setPreventPowerFail(preventPowerFail: Boolean)
}

/** Owns the wire protocol and server handler used by the machine controller cover UI. */
object MachineControllerCoverConfigActions {

	private const val ACTION_SEQUENCE = 0
	private val SET_MACHINE_CONTROLLER_COVER_CONFIG_ACTION = GTCEu.id("set_machine_controller_cover_config")
	private val CONTROLLER_MODE_FIELD = SyncFieldData.key("controllerMode")
	private val MIN_REDSTONE_STRENGTH_FIELD = SyncFieldData.key("minRedstoneStrength")
	private val INVERTED_FIELD = SyncFieldData.key("inverted")
	private val PREVENT_POWER_FAIL_FIELD = SyncFieldData.key("preventPowerFail")
	private val CONTROLLER_MODES = ControllerMode.entries

	init {
		SyncActionDispatchers.server().register(MachineControllerCoverConfigActionHandler)
	}

	/** Forces common-side handler registration when the owning cover class initializes. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one complete client request from the current machine controller state. */
	@JvmStatic
	fun createSetConfigAction(controllerMode: ControllerMode?, minRedstoneStrength: Int, inverted: Boolean, preventPowerFail: Boolean): SyncActionData {
		require(minRedstoneStrength in 1..15) {
			"Machine controller redstone strength must be between 1 and 15: $minRedstoneStrength"
		}
		val fields =
			SyncFieldData.builder()
				.put(CONTROLLER_MODE_FIELD, JsonPrimitive(controllerMode?.ordinal ?: -1))
				.put(MIN_REDSTONE_STRENGTH_FIELD, JsonPrimitive(minRedstoneStrength))
				.put(INVERTED_FIELD, JsonPrimitive(inverted))
				.put(PREVENT_POWER_FAIL_FIELD, JsonPrimitive(preventPowerFail))
				.build()
		return SyncActionData(
			SET_MACHINE_CONTROLLER_COVER_CONFIG_ACTION,
			ACTION_SEQUENCE,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private object MachineControllerCoverConfigActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_MACHINE_CONTROLLER_COVER_CONFIG_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is MachineControllerCover

		override fun acceptsPayload(payload: DataComponentMap): Boolean = readConfig(payload) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean {
			if (player.isSpectator) {
				return false
			}
			val config = requireConfig(context.payload())
			return config.controllerMode == null || target(context).getAllowedModes().contains(config.controllerMode)
		}

		override fun execute(context: SyncActionContext) {
			val target = target(context)
			val config = requireConfig(context.payload())
			target.setControllerMode(config.controllerMode)
			target.setMinRedstoneStrength(config.minRedstoneStrength)
			target.setInverted(config.inverted)
			target.setPreventPowerFail(config.preventPowerFail)
		}
	}

	private data class Config(val controllerMode: ControllerMode?, val minRedstoneStrength: Int, val inverted: Boolean, val preventPowerFail: Boolean)

	private fun readConfig(payload: DataComponentMap): Config? {
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		val controllerModeOrdinal = readExactInt(fields, CONTROLLER_MODE_FIELD) ?: return null
		val controllerMode = when {
			controllerModeOrdinal == -1 -> null
			controllerModeOrdinal in CONTROLLER_MODES.indices -> CONTROLLER_MODES[controllerModeOrdinal]
			else -> return null
		}
		val minRedstoneStrength = readExactInt(fields, MIN_REDSTONE_STRENGTH_FIELD)
			?.takeIf { strength -> strength in 1..15 }
			?: return null
		val inverted = readBoolean(fields, INVERTED_FIELD) ?: return null
		val preventPowerFail = readBoolean(fields, PREVENT_POWER_FAIL_FIELD) ?: return null
		return Config(controllerMode, minRedstoneStrength, inverted, preventPowerFail)
	}

	private fun requireConfig(payload: DataComponentMap): Config = readConfig(payload)
		?: throw IllegalStateException("Machine controller cover config action payload is invalid.")

	private fun target(context: SyncActionContext): MachineControllerCoverConfigActionTarget = context.holder as? MachineControllerCoverConfigActionTarget
		?: throw IllegalStateException(
			"Machine controller cover config action received a non-machine-controller target.",
		)

	private fun readExactInt(fields: SyncFieldData, field: ResourceLocation): Int? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		if (!primitive.isNumber) {
			return null
		}
		return try {
			primitive.asBigDecimal.intValueExact()
		} catch (exception: NumberFormatException) {
			logInvalidInteger(field, primitive, exception)
			null
		} catch (exception: ArithmeticException) {
			logInvalidInteger(field, primitive, exception)
			null
		}
	}

	private fun readBoolean(fields: SyncFieldData, field: ResourceLocation): Boolean? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		return if (primitive.isBoolean) primitive.asBoolean else null
	}

	private fun logInvalidInteger(field: ResourceLocation, primitive: JsonPrimitive, exception: RuntimeException) {
		GTCEu.LOGGER.warn(
			"Machine controller cover config action rejected invalid integer field {}: {}",
			field,
			primitive,
			exception,
		)
	}
}

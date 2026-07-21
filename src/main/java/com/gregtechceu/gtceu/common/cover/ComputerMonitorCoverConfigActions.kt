package com.gregtechceu.gtceu.common.cover

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

import com.google.gson.JsonArray
import com.google.gson.JsonPrimitive
import org.jetbrains.annotations.ApiStatus

/** Exposes only the ordered monitor configuration mutations required by its Kotlin action handler. */
@ApiStatus.Internal
interface ComputerMonitorCoverConfigActionTarget {

	/** Replaces the displayed format lines before the placeholder arguments are applied. */
	fun replaceFormatStringLines(lines: List<String>)

	/** Replaces the placeholder arguments after the displayed format lines. */
	fun replaceFormatStringArgs(args: List<String>)

	/** Applies the validated refresh interval after both format lists. */
	fun setUpdateInterval(updateInterval: Int)
}

/** Owns the wire protocol and server handler used by the computer monitor configuration UI. */
object ComputerMonitorCoverConfigActions {

	private const val ACTION_SEQUENCE = 0
	private const val UPDATE_INTERVAL_MIN = 1
	private const val UPDATE_INTERVAL_MAX = 60 * 20
	private val SET_COMPUTER_MONITOR_COVER_CONFIG_ACTION = GTCEu.id("set_computer_monitor_cover_config")
	private val FORMAT_LINES_FIELD = SyncFieldData.key("formatLines")
	private val FORMAT_ARGS_FIELD = SyncFieldData.key("formatArgs")
	private val UPDATE_INTERVAL_FIELD = SyncFieldData.key("updateInterval")

	init {
		SyncActionDispatchers.server().register(ComputerMonitorCoverConfigActionHandler)
	}

	/** Forces common-side handler registration when the owning cover class initializes. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one complete client request from the current computer monitor state. */
	@JvmStatic
	fun createSetConfigAction(lines: List<String>, args: List<String>, updateInterval: Int): SyncActionData {
		require(isValidUpdateInterval(updateInterval)) {
			"Computer monitor cover update interval is out of range: $updateInterval"
		}
		val fields =
			SyncFieldData.builder()
				.put(FORMAT_LINES_FIELD, writeStringList(lines))
				.put(FORMAT_ARGS_FIELD, writeStringList(args))
				.put(UPDATE_INTERVAL_FIELD, JsonPrimitive(updateInterval))
				.build()
		return SyncActionData(
			SET_COMPUTER_MONITOR_COVER_CONFIG_ACTION,
			ACTION_SEQUENCE,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private object ComputerMonitorCoverConfigActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_COMPUTER_MONITOR_COVER_CONFIG_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is ComputerMonitorCover

		override fun acceptsPayload(payload: DataComponentMap): Boolean = read(payload) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator && context.holder is ComputerMonitorCover

		override fun execute(context: SyncActionContext) {
			val target =
				context.holder as? ComputerMonitorCoverConfigActionTarget
					?: throw IllegalStateException(
						"Computer monitor cover config action received a non-computer-monitor target.",
					)
			val config = requireConfig(context.payload())
			target.replaceFormatStringLines(config.lines)
			target.replaceFormatStringArgs(config.args)
			target.setUpdateInterval(config.updateInterval)
		}
	}

	private data class Config(val lines: List<String>, val args: List<String>, val updateInterval: Int)

	private fun read(payload: DataComponentMap): Config? {
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		val lines = readStringList(fields, FORMAT_LINES_FIELD) ?: return null
		val args = readStringList(fields, FORMAT_ARGS_FIELD) ?: return null
		val updateInterval = readExactInt(fields, UPDATE_INTERVAL_FIELD)
			?.takeIf(::isValidUpdateInterval)
			?: return null
		return Config(lines, args, updateInterval)
	}

	private fun requireConfig(payload: DataComponentMap): Config = read(payload)
		?: throw IllegalStateException("Computer monitor cover config action payload is invalid.")

	private fun writeStringList(values: List<String>): JsonArray {
		val array = JsonArray(values.size)
		values.forEach(array::add)
		return array
	}

	private fun readStringList(fields: SyncFieldData, field: ResourceLocation): List<String>? {
		val array = fields[field] as? JsonArray ?: return null
		val values = ArrayList<String>(array.size())
		for (entry in array) {
			val primitive = entry as? JsonPrimitive ?: return null
			if (!primitive.isString) {
				return null
			}
			values.add(primitive.asString)
		}
		return values
	}

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

	private fun logInvalidInteger(field: ResourceLocation, primitive: JsonPrimitive, exception: RuntimeException) {
		GTCEu.LOGGER.warn(
			"Computer monitor cover config action rejected invalid integer field {}: {}",
			field,
			primitive,
			exception,
		)
	}

	private fun isValidUpdateInterval(value: Int): Boolean = value in UPDATE_INTERVAL_MIN..UPDATE_INTERVAL_MAX
}

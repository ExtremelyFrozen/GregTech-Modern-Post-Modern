package com.gregtechceu.gtceu.common.cover.voiding

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.cover.data.VoidingMode
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

import com.google.gson.JsonPrimitive
import org.jetbrains.annotations.ApiStatus

/** Exposes only the ordered mutations that the advanced item voiding config action may invoke. */
@ApiStatus.Internal
interface AdvancedItemVoidingCoverConfigActionTarget {

	/** Applies the validated voiding mode before the mode-dependent voiding limit. */
	fun setVoidingMode(voidingMode: VoidingMode)

	/** Applies the validated positive item voiding limit after the voiding mode. */
	fun setGlobalVoidingLimit(voidingLimit: Int)
}

/** Owns the wire protocol and server handler for advanced item voiding cover configuration. */
object AdvancedItemVoidingCoverConfigActions {

	private const val ACTION_SEQUENCE = 0
	private val SET_ADVANCED_ITEM_VOIDING_COVER_CONFIG_ACTION = GTCEu.id("set_advanced_item_voiding_cover_config")
	private val VOIDING_MODE_FIELD = SyncFieldData.key("voidingMode")
	private val VOID_SIZE_FIELD = SyncFieldData.key("voidSize")
	private val VOIDING_MODES = VoidingMode.entries

	init {
		SyncActionDispatchers.server().register(AdvancedItemVoidingCoverConfigActionHandler)
	}

	/** Forces common-side handler registration when the owning cover class initializes. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one complete client request from the current advanced item voiding state. */
	@JvmStatic
	fun createSetConfigAction(voidingMode: VoidingMode, voidingLimit: Int): SyncActionData {
		require(voidingLimit > 0) { "Advanced item voiding limit must be positive: $voidingLimit" }
		val fields =
			SyncFieldData.builder()
				.put(VOIDING_MODE_FIELD, JsonPrimitive(voidingMode.ordinal))
				.put(VOID_SIZE_FIELD, JsonPrimitive(voidingLimit))
				.build()
		return SyncActionData(
			SET_ADVANCED_ITEM_VOIDING_COVER_CONFIG_ACTION,
			ACTION_SEQUENCE,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private object AdvancedItemVoidingCoverConfigActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_ADVANCED_ITEM_VOIDING_COVER_CONFIG_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is AdvancedItemVoidingCover

		override fun acceptsPayload(payload: DataComponentMap): Boolean = readConfig(payload) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			val target =
				context.holder as? AdvancedItemVoidingCoverConfigActionTarget
					?: throw IllegalStateException(
						"Advanced item voiding config action received a non-advanced-item-voiding target.",
					)
			val config = requireConfig(context.payload())
			target.setVoidingMode(config.voidingMode)
			target.setGlobalVoidingLimit(config.voidingLimit)
		}
	}

	private fun readConfig(payload: DataComponentMap): Config? {
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		val voidingModeOrdinal = readExactInt(fields, VOIDING_MODE_FIELD)
			?.takeIf { ordinal -> ordinal in VOIDING_MODES.indices }
			?: return null
		val voidingLimit = readExactInt(fields, VOID_SIZE_FIELD)
			?.takeIf { limit -> limit > 0 }
			?: return null
		return Config(VOIDING_MODES[voidingModeOrdinal], voidingLimit)
	}

	private fun requireConfig(payload: DataComponentMap): Config = readConfig(payload)
		?: throw IllegalStateException("Advanced item voiding config action payload is invalid.")

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
			"Advanced item voiding config action rejected invalid integer field {}: {}",
			field,
			primitive,
			exception,
		)
	}

	private data class Config(val voidingMode: VoidingMode, val voidingLimit: Int)
}

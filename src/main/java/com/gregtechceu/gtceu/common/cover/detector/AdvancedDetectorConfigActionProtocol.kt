package com.gregtechceu.gtceu.common.cover.detector

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation

import com.google.gson.JsonPrimitive

/** Encodes and validates the shared scalar action payload used by advanced item and fluid detectors. */
internal object AdvancedDetectorConfigActionProtocol {

	private val MIN_FIELD = SyncFieldData.key("min")
	private val MAX_FIELD = SyncFieldData.key("max")
	private val LATCHED_FIELD = SyncFieldData.key("latched")
	private val INVERTED_FIELD = SyncFieldData.key("inverted")

	/** Represents one fully validated advanced detector configuration request. */
	data class Config(val min: Int, val max: Int, val latched: Boolean, val inverted: Boolean)

	/** Creates a complete action while preserving the existing sequence value. */
	fun createAction(actionId: ResourceLocation, min: Int, max: Int, latched: Boolean, inverted: Boolean): SyncActionData {
		require(min >= 0) { "Advanced detector minimum must be non-negative: $min" }
		require(max >= 0) { "Advanced detector maximum must be non-negative: $max" }
		val fields =
			SyncFieldData.builder()
				.put(MIN_FIELD, JsonPrimitive(min))
				.put(MAX_FIELD, JsonPrimitive(max))
				.put(LATCHED_FIELD, JsonPrimitive(latched))
				.put(INVERTED_FIELD, JsonPrimitive(inverted))
				.build()
		return SyncActionData(
			actionId,
			0,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	/** Returns a validated configuration, or `null` when any required field is absent or malformed. */
	fun read(payload: DataComponentMap): Config? {
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		return Config(
			min = readNonNegativeInt(fields, MIN_FIELD) ?: return null,
			max = readNonNegativeInt(fields, MAX_FIELD) ?: return null,
			latched = readBoolean(fields, LATCHED_FIELD) ?: return null,
			inverted = readBoolean(fields, INVERTED_FIELD) ?: return null,
		)
	}

	/** Requires a payload already accepted by [read]. */
	fun require(payload: DataComponentMap): Config = read(payload)
		?: throw IllegalStateException("Advanced detector config action payload is invalid.")

	private fun readNonNegativeInt(fields: SyncFieldData, field: ResourceLocation): Int? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		if (!primitive.isNumber) {
			return null
		}
		val value =
			try {
				primitive.asBigDecimal.intValueExact()
			} catch (exception: NumberFormatException) {
				logInvalidInteger(field, primitive, exception)
				return null
			} catch (exception: ArithmeticException) {
				logInvalidInteger(field, primitive, exception)
				return null
			}
		return value.takeIf { it >= 0 }
	}

	private fun readBoolean(fields: SyncFieldData, field: ResourceLocation): Boolean? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		return if (primitive.isBoolean) primitive.asBoolean else null
	}

	private fun logInvalidInteger(field: ResourceLocation, primitive: JsonPrimitive, exception: RuntimeException) {
		GTCEu.LOGGER.warn(
			"Advanced detector config action rejected invalid integer field {}: {}",
			field,
			primitive,
			exception,
		)
	}
}

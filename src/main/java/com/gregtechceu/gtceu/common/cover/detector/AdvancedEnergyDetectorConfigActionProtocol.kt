package com.gregtechceu.gtceu.common.cover.detector

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation

import com.google.gson.JsonPrimitive

/** Encodes and validates the long-valued action payload used by the advanced energy detector. */
internal object AdvancedEnergyDetectorConfigActionProtocol {

	private val MIN_FIELD = SyncFieldData.key("min")
	private val MAX_FIELD = SyncFieldData.key("max")
	private val USE_PERCENT_FIELD = SyncFieldData.key("usePercent")
	private val INVERTED_FIELD = SyncFieldData.key("inverted")

	/** Represents one fully validated advanced energy detector configuration request. */
	data class Config(val min: Long, val max: Long, val usePercent: Boolean, val inverted: Boolean)

	/** Creates a complete action while preserving the existing sequence value. */
	fun createAction(actionId: ResourceLocation, min: Long, max: Long, usePercent: Boolean, inverted: Boolean): SyncActionData {
		require(min >= 0L) { "Advanced energy detector minimum must be non-negative: $min" }
		require(max >= 0L) { "Advanced energy detector maximum must be non-negative: $max" }
		val fields =
			SyncFieldData.builder()
				.put(MIN_FIELD, JsonPrimitive(min))
				.put(MAX_FIELD, JsonPrimitive(max))
				.put(USE_PERCENT_FIELD, JsonPrimitive(usePercent))
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
			min = readNonNegativeLong(fields, MIN_FIELD) ?: return null,
			max = readNonNegativeLong(fields, MAX_FIELD) ?: return null,
			usePercent = readBoolean(fields, USE_PERCENT_FIELD) ?: return null,
			inverted = readBoolean(fields, INVERTED_FIELD) ?: return null,
		)
	}

	/** Requires a payload already accepted by [read]. */
	fun require(payload: DataComponentMap): Config = read(payload)
		?: throw IllegalStateException("Advanced energy detector config action payload is invalid.")

	private fun readNonNegativeLong(fields: SyncFieldData, field: ResourceLocation): Long? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		if (!primitive.isNumber) {
			return null
		}
		val value =
			try {
				primitive.asBigDecimal.longValueExact()
			} catch (exception: NumberFormatException) {
				logInvalidLong(field, primitive, exception)
				return null
			} catch (exception: ArithmeticException) {
				logInvalidLong(field, primitive, exception)
				return null
			}
		return value.takeIf { it >= 0L }
	}

	private fun readBoolean(fields: SyncFieldData, field: ResourceLocation): Boolean? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		return if (primitive.isBoolean) primitive.asBoolean else null
	}

	private fun logInvalidLong(field: ResourceLocation, primitive: JsonPrimitive, exception: RuntimeException) {
		GTCEu.LOGGER.warn(
			"Advanced energy detector config action rejected invalid long field {}: {}",
			field,
			primitive,
			exception,
		)
	}
}

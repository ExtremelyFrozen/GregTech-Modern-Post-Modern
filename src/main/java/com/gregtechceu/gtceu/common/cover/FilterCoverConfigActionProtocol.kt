package com.gregtechceu.gtceu.common.cover

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.cover.data.FilterMode
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation

import com.google.gson.JsonPrimitive

/** Shared fixed wire schema used by item and fluid filter cover configuration actions. */
internal object FilterCoverConfigActionProtocol {

	private const val ACTION_SEQUENCE = 0
	private val FILTER_MODE_FIELD = SyncFieldData.key("filterMode")
	private val MANUAL_IO_FIELD = SyncFieldData.key("manualIO")
	private val FILTER_MODES = FilterMode.entries
	private val MANUAL_IO_MODES = ManualIOMode.entries

	/** Encodes the complete shared filter configuration for the supplied concrete action id. */
	fun createAction(actionId: ResourceLocation, filterMode: FilterMode, manualIOMode: ManualIOMode): SyncActionData {
		val fields =
			SyncFieldData.builder()
				.put(FILTER_MODE_FIELD, JsonPrimitive(filterMode.ordinal))
				.put(MANUAL_IO_FIELD, JsonPrimitive(manualIOMode.ordinal))
				.build()
		return SyncActionData(
			actionId,
			ACTION_SEQUENCE,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	/** Reads an exact, in-range shared filter configuration or rejects the payload. */
	fun read(payload: DataComponentMap): Config? {
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		val filterMode = readOrdinal(fields, FILTER_MODE_FIELD, FILTER_MODES) ?: return null
		val manualIOMode = readOrdinal(fields, MANUAL_IO_FIELD, MANUAL_IO_MODES) ?: return null
		return Config(filterMode, manualIOMode)
	}

	/** Returns the validated configuration for execution and fails fast if dispatch invariants were bypassed. */
	fun requireConfig(payload: DataComponentMap): Config = read(payload)
		?: throw IllegalStateException("Filter cover config action payload is invalid.")

	private fun <T> readOrdinal(fields: SyncFieldData, field: ResourceLocation, values: List<T>): T? {
		val ordinal = readExactInt(fields, field) ?: return null
		return if (ordinal in values.indices) values[ordinal] else null
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
			"Filter cover config action rejected invalid integer field {}: {}",
			field,
			primitive,
			exception,
		)
	}

	/** Validated shared values applied by each concrete filter cover handler. */
	data class Config(val filterMode: FilterMode, val manualIOMode: ManualIOMode)
}

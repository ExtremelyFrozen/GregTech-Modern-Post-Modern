package com.gregtechceu.gtceu.common.cover

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation

import com.google.gson.JsonPrimitive

/** Encodes and validates the common transfer-mode and limit action payload. */
internal object RegulatedTransferConfigActionProtocol {

	private const val ACTION_SEQUENCE = 0
	private val TRANSFER_MODE_FIELD = SyncFieldData.key("transferMode")
	private val TRANSFER_LIMIT_FIELD = SyncFieldData.key("transferLimit")
	private val TRANSFER_BUCKET_FIELD = SyncFieldData.key("transferBucket")

	/** Defines the enum bounds and minimum limit required by one regulated-transfer action. */
	data class Schema(val transferModeValueCount: Int, val minimumTransferLimit: Int, val transferBucketValueCount: Int? = null) {

		init {
			require(transferModeValueCount > 0) { "Transfer mode value count must be positive." }
			require(minimumTransferLimit >= 0) { "Minimum transfer limit must be non-negative." }
			require(transferBucketValueCount == null || transferBucketValueCount > 0) {
				"Transfer bucket value count must be positive when the schema defines it."
			}
		}
	}

	/** Carries one fully validated payload without coupling the wire format to specific enum classes. */
	data class Config(val transferModeOrdinal: Int, val transferLimit: Int, val transferBucketOrdinal: Int?)

	/** Creates a complete action and fails fast when the caller supplies a value outside its schema. */
	fun createAction(actionId: ResourceLocation, schema: Schema, transferModeOrdinal: Int, transferLimit: Int, transferBucketOrdinal: Int? = null): SyncActionData {
		require(transferModeOrdinal in 0 until schema.transferModeValueCount) {
			"Transfer mode ordinal is out of range: $transferModeOrdinal"
		}
		require(transferLimit >= schema.minimumTransferLimit) {
			"Transfer limit must be at least ${schema.minimumTransferLimit}: $transferLimit"
		}
		validateFactoryTransferBucket(schema, transferBucketOrdinal)

		val fields =
			SyncFieldData.builder()
				.put(TRANSFER_MODE_FIELD, JsonPrimitive(transferModeOrdinal))
				.put(TRANSFER_LIMIT_FIELD, JsonPrimitive(transferLimit))
		if (transferBucketOrdinal != null) {
			fields.put(TRANSFER_BUCKET_FIELD, JsonPrimitive(transferBucketOrdinal))
		}

		return SyncActionData(
			actionId,
			ACTION_SEQUENCE,
			fields.build().toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	/** Returns a validated configuration while deliberately ignoring fields added by future protocol versions. */
	fun read(payload: DataComponentMap, schema: Schema): Config? {
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		val transferModeOrdinal = readTransferModeOrdinal(fields, schema.transferModeValueCount) ?: return null
		val transferLimit = readExactInt(fields, TRANSFER_LIMIT_FIELD)
			?.takeIf { value -> value >= schema.minimumTransferLimit }
			?: return null
		val transferBucketOrdinal = schema.transferBucketValueCount?.let { valueCount ->
			readTransferBucketOrdinal(fields, valueCount) ?: return null
		}
		return Config(transferModeOrdinal, transferLimit, transferBucketOrdinal)
	}

	/** Requires a payload that has already passed [read], keeping execution failures explicit. */
	fun requireConfig(payload: DataComponentMap, schema: Schema): Config = read(payload, schema)
		?: throw IllegalStateException("Regulated transfer config action payload is invalid.")

	private fun validateFactoryTransferBucket(schema: Schema, transferBucketOrdinal: Int?) {
		val valueCount = schema.transferBucketValueCount
		if (valueCount == null) {
			require(transferBucketOrdinal == null) {
				"Transfer bucket ordinal is not supported by this schema: $transferBucketOrdinal"
			}
			return
		}
		require(transferBucketOrdinal != null) { "Transfer bucket ordinal is required by this schema." }
		require(transferBucketOrdinal in 0 until valueCount) {
			"Transfer bucket ordinal is out of range: $transferBucketOrdinal"
		}
	}

	private fun readTransferModeOrdinal(fields: SyncFieldData, valueCount: Int): Int? = readExactInt(fields, TRANSFER_MODE_FIELD)?.takeIf { ordinal -> ordinal in 0 until valueCount }

	private fun readTransferBucketOrdinal(fields: SyncFieldData, valueCount: Int): Int? = readExactInt(fields, TRANSFER_BUCKET_FIELD)?.takeIf { ordinal -> ordinal in 0 until valueCount }

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
			"Regulated transfer config action rejected invalid integer field {}: {}",
			field,
			primitive,
			exception,
		)
	}
}

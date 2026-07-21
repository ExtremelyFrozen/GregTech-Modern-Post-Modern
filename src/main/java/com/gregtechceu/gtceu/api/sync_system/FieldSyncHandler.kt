package com.gregtechceu.gtceu.api.sync_system

import com.gregtechceu.gtceu.GTCEu

import net.minecraft.core.HolderLookup

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nullable

import java.lang.invoke.VarHandle
import java.math.BigDecimal

@ApiStatus.Internal
object FieldSyncHandler {
	@Suppress("UNCHECKED_CAST")
	@JvmStatic
	fun serializeFieldData(registries: HolderLookup.Provider, holder: Any, field: FieldSyncData, writeClientFields: Boolean, fullSync: Boolean): JsonElement {
		val currentValue = field.handle.get(holder) ?: return JsonNull.INSTANCE

		if (field.contextualCodec == null) {
			field.setContextualCodec(FieldCodecs.getContextual(field.type.rawType))
		}
		field.contextualCodec?.let {
			return try {
				(it as ContextualFieldCodec<Any>).serializeField(
					currentValue,
					ContextualFieldCodec.Context(
						holder,
						field.type,
						currentValue,
						field.fieldName,
						writeClientFields,
						fullSync,
						registries,
						SyncSerializationTarget.DATA_COMPONENTS,
					),
				)
			} catch (e: Exception) {
				GTCEu.LOGGER.error("Sync: Failed to contextual-codec serialize field {} without NBT", field.fieldName, e)
				throw e
			}
		}

		if (field.codec == null) {
			field.setCodec(FieldCodecs.get(field.type.rawType))
		}
		field.codec?.let {
			return try {
				(it as Codec<Any>)
					.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), currentValue)
					.getOrThrow()
			} catch (e: Exception) {
				GTCEu.LOGGER.error("Sync: Failed to codec-serialize field {} without NBT", field.fieldName, e)
				throw e
			}
		}

		val message = "Sync: Failed to serialize field ${field.fieldName} in class ${holder.javaClass.name}: Missing field codec for ${field.type}"
		GTCEu.LOGGER.error(message)
		throw IllegalArgumentException(message)
	}

	/**
	 * Decodes an untrusted client candidate without consulting the current field value or a contextual codec.
	 *
	 * Server field updates must remain detached until every field in the batch has decoded and normalized successfully.
	 */
	@Suppress("UNCHECKED_CAST")
	@JvmStatic
	fun decodeDetachedServerCandidate(registries: HolderLookup.Provider, holder: Any, field: FieldSyncData, savedValue: JsonElement): Any? {
		if (field.codec == null) {
			field.setCodec(FieldCodecs.get(field.type.rawType))
		}
		val codec = field.codec
			?: throw IllegalArgumentException(
				"Sync: Server update for field ${field.fieldName} of type ${field.type} requires a detached ordinary Codec; contextual-only codecs are not accepted",
			)

		return try {
			validateDefaultScalarIntegralCandidate(field, codec, savedValue)
			decodeOrdinaryFieldValue(registries, field, codec, savedValue)
		} catch (e: RuntimeException) {
			GTCEu.LOGGER.warn(
				"Sync: Failed to decode detached server candidate for field {} of type {} in {}",
				field.fieldName,
				field.type,
				holder.javaClass.name,
				e,
			)
			throw IllegalArgumentException(
				"Sync: Invalid server candidate for field ${field.fieldName} of type ${field.type}",
				e,
			)
		}
	}

	@Suppress("UNCHECKED_CAST")
	private fun decodeOrdinaryFieldValue(registries: HolderLookup.Provider, field: FieldSyncData, codec: Codec<*>, savedValue: JsonElement): Any? {
		val result = (codec as Codec<Any>)
			.parse(registries.createSerializationContext(JsonOps.INSTANCE), savedValue)
		// Codecs may define their own explicit-null value; otherwise JsonNull is the sync token for a null reference.
		if (savedValue.isJsonNull && !field.handle.varType().isPrimitive && result.error().isPresent) {
			return null
		}
		return result.getOrThrow()
	}

	private fun validateDefaultScalarIntegralCandidate(field: FieldSyncData, codec: Codec<*>, savedValue: JsonElement) {
		if (
			codec !== Codec.BYTE && codec !== Codec.SHORT &&
			codec !== Codec.INT && codec !== Codec.LONG
		) {
			return
		}

		if (!savedValue.isJsonPrimitive || !savedValue.asJsonPrimitive.isNumber) {
			throw IllegalArgumentException("Sync: Integral server candidate for field ${field.fieldName} must be a JSON number")
		}

		val candidate: BigDecimal = savedValue.asJsonPrimitive.asBigDecimal
		when {
			codec === Codec.BYTE -> candidate.byteValueExact()
			codec === Codec.SHORT -> candidate.shortValueExact()
			codec === Codec.INT -> candidate.intValueExact()
			codec === Codec.LONG -> candidate.longValueExact()
		}
	}

	/**
	 * Encodes a client-to-server field candidate with the same ordinary Codec required by detached server decoding.
	 */
	@Suppress("UNCHECKED_CAST")
	@JvmStatic
	fun encodeDetachedServerCandidate(registries: HolderLookup.Provider, holder: Any, field: FieldSyncData): JsonElement {
		if (field.codec == null) {
			field.setCodec(FieldCodecs.get(field.type.rawType))
		}
		val codec = field.codec
			?: throw IllegalArgumentException(
				"Sync: Server update for field ${field.fieldName} of type ${field.type} requires a detached ordinary Codec; contextual-only codecs are not accepted",
			)
		val currentValue = field.handle.get(holder) ?: return JsonNull.INSTANCE

		return try {
			(codec as Codec<Any>)
				.encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), currentValue)
				.getOrThrow()
		} catch (e: RuntimeException) {
			GTCEu.LOGGER.error(
				"Sync: Failed to encode detached server candidate for field {} of type {} in {}",
				field.fieldName,
				field.type,
				holder.javaClass.name,
				e,
			)
			throw IllegalArgumentException(
				"Sync: Invalid client candidate for field ${field.fieldName} of type ${field.type}",
				e,
			)
		}
	}

	@Suppress("UNCHECKED_CAST")
	@JvmOverloads
	@JvmStatic
	fun deserializeFieldData(
		registries: HolderLookup.Provider,
		holder: Any,
		field: FieldSyncData,
		savedValue: JsonElement,
		readingClientFields: Boolean,
		parseExplicitNull: Boolean = false,
		serializationTarget: SyncSerializationTarget = SyncSerializationTarget.DATA_COMPONENTS,
		fullSync: Boolean = false,
		notifyUnchangedOnFullSync: Boolean = true,
	) {
		if (savedValue.isJsonNull && !parseExplicitNull) {
			return
		}

		if (field.contextualCodec == null) {
			field.setContextualCodec(FieldCodecs.getContextual(field.type.rawType))
		}
		field.contextualCodec?.let {
			try {
				val current = field.handle.get(holder)
				val result = (it as ContextualFieldCodec<Any>).deserializeField(
					savedValue,
					ContextualFieldCodec.Context(
						holder,
						field.type,
						current,
						field.fieldName,
						readingClientFields,
						fullSync,
						registries,
						serializationTarget,
						parseExplicitNull,
						notifyUnchangedOnFullSync,
					),
				)
				applyDecodedValue(holder, field, current, result)
			} catch (e: Exception) {
				if (e is UnsupportedOperationException) {
					GTCEu.LOGGER.error(
						"Sync: failed to perform VarHandle set or non-NBT decode for {}",
						field.fieldName,
						e,
					)
					throw e
				}
				GTCEu.LOGGER.error("Sync: Failed to contextual-codec deserialize field {} without NBT", field.fieldName, e)
				throw e
			}
			return
		}

		if (field.codec == null) {
			field.setCodec(FieldCodecs.get(field.type.rawType))
		}
		field.codec?.let {
			try {
				val result = decodeOrdinaryFieldValue(registries, field, it, savedValue)
				val current = field.handle.get(holder)
				applyDecodedValue(holder, field, current, result)
			} catch (e: Exception) {
				if (e is UnsupportedOperationException) {
					GTCEu.LOGGER.error(
						"Sync: failed to perform VarHandle set: unsupported op on {} (you are probably trying to sync a final field)",
						field.fieldName,
						e,
					)
					throw e
				}
				GTCEu.LOGGER.error("Sync: Failed to codec-deserialize field {} without NBT", field.fieldName, e)
				throw e
			}
			return
		}

		val message = "Sync: Failed to deserialize field ${field.fieldName} in class ${holder.javaClass.name}: Missing field codec for ${field.type}"
		GTCEu.LOGGER.error(message)
		throw IllegalArgumentException(message)
	}

	private fun applyDecodedValue(holder: Any, field: FieldSyncData, @Nullable current: Any?, @Nullable result: Any?) {
		if (result === current) return
		if (!field.handle.isAccessModeSupported(VarHandle.AccessMode.SET) && copyIntoFinalContainer(current, result)) return
		field.handle.set(holder, result)
	}

	@Suppress("UNCHECKED_CAST")
	private fun copyIntoFinalContainer(@Nullable current: Any?, @Nullable result: Any?): Boolean {
		if (current is Collection<*> && result is Collection<*>) {
			(current as MutableCollection<Any?>).clear()
			current.addAll(result)
			return true
		}
		if (current is Map<*, *> && result is Map<*, *>) {
			(current as MutableMap<Any?, Any?>).clear()
			current.putAll(result)
			return true
		}
		return false
	}
}

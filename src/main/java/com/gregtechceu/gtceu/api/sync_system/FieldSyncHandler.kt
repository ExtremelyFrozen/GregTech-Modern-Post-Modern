package com.gregtechceu.gtceu.api.sync_system

import com.gregtechceu.gtceu.GTCEu

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.mojang.serialization.Codec
import com.mojang.serialization.JsonOps
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nullable

@ApiStatus.Internal
object FieldSyncHandler {
	@Suppress("UNCHECKED_CAST")
	@JvmStatic
	fun serializeField(registries: HolderLookup.Provider, holder: Any, field: FieldSyncData, writeClientFields: Boolean, fullSync: Boolean): Tag {
		val currentValue = field.handle.get(holder) ?: return CompoundTag().apply { putBoolean("null", true) }

		if (field.contextualCodec == null) {
			field.setContextualCodec(FieldCodecs.getContextual(field.type.rawType))
		}
		field.contextualCodec?.let {
			return try {
				(it as ContextualFieldCodec<Any>).serializeNBT(
					currentValue,
					ContextualFieldCodec.Context(
						holder,
						field.type,
						currentValue,
						field.fieldName,
						writeClientFields,
						fullSync,
						registries,
						SyncSerializationTarget.NBT,
					),
				)
			} catch (e: Exception) {
				GTCEu.LOGGER.error("Sync: Failed to contextual-codec serialize field {}", field.fieldName, e)
				CompoundTag()
			}
		}

		if (field.codec == null) {
			field.setCodec(FieldCodecs.get(field.type.rawType))
		}
		field.codec?.let {
			return try {
				(it as Codec<Any>)
					.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), currentValue)
					.getOrThrow()
			} catch (e: Exception) {
				GTCEu.LOGGER.error("Sync: Failed to codec-serialize field {}", field.fieldName, e)
				CompoundTag()
			}
		}

		GTCEu.LOGGER.error("Sync: Failed to serialize field {} in class {}: Missing field codec for {}", field.fieldName, holder.javaClass.name, field.type)
		return CompoundTag()
	}

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

	@Suppress("UNCHECKED_CAST")
	@JvmStatic
	fun deserializeField(registries: HolderLookup.Provider, holder: Any, field: FieldSyncData, @Nullable savedValue: Tag?, readingClientFields: Boolean) {
		if (savedValue == null || (savedValue is CompoundTag && savedValue.isEmpty)) return

		if (savedValue is CompoundTag && savedValue.getBoolean("null")) {
			field.handle.set(holder, null)
			return
		}

		if (field.contextualCodec == null) {
			field.setContextualCodec(FieldCodecs.getContextual(field.type.rawType))
		}
		field.contextualCodec?.let {
			try {
				val current = field.handle.get(holder)
				val result = (it as ContextualFieldCodec<Any>).deserializeNBT(
					savedValue,
					ContextualFieldCodec.Context(
						holder,
						field.type,
						current,
						field.fieldName,
						readingClientFields,
						false,
						registries,
						SyncSerializationTarget.NBT,
					),
				)
				if (copyIntoMutableCurrent(current, result)) return
				if (result !== current) {
					field.handle.set(holder, result)
				}
			} catch (e: Exception) {
				if (e is UnsupportedOperationException) {
					GTCEu.LOGGER.error(
						"Sync: failed to perform VarHandle set: unsupported op on {} (you are probably trying to sync a final field)",
						field.fieldName,
					)
					return
				}
				GTCEu.LOGGER.error("Sync: Failed to contextual-codec deserialize field {}", field.fieldName, e)
			}
			return
		}

		if (field.codec == null) {
			field.setCodec(FieldCodecs.get(field.type.rawType))
		}
		field.codec?.let {
			try {
				val result = (it as Codec<Any>)
					.parse(registries.createSerializationContext(NbtOps.INSTANCE), savedValue)
					.getOrThrow()
				val current = field.handle.get(holder)
				if (copyIntoMutableCurrent(current, result)) return
				if (result !== current) {
					field.handle.set(holder, result)
				}
			} catch (e: Exception) {
				if (e is UnsupportedOperationException) {
					GTCEu.LOGGER.error(
						"Sync: failed to perform VarHandle set: unsupported op on {} (you are probably trying to sync a final field)",
						field.fieldName,
					)
					return
				}
				GTCEu.LOGGER.error("Sync: Failed to codec-deserialize field {}", field.fieldName, e)
			}
			return
		}

		GTCEu.LOGGER.error("Sync: Failed to deserialize field {} in class {}: Missing field codec for {}", field.fieldName, holder.javaClass.name, field.type)
	}

	@Suppress("UNCHECKED_CAST")
	@JvmStatic
	fun deserializeFieldData(registries: HolderLookup.Provider, holder: Any, field: FieldSyncData, savedValue: JsonElement, readingClientFields: Boolean) {
		if (savedValue is JsonNull) {
			field.handle.set(holder, null)
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
						false,
						registries,
						SyncSerializationTarget.DATA_COMPONENTS,
					),
				)
				if (copyIntoMutableCurrent(current, result)) return
				if (result !== current) {
					field.handle.set(holder, result)
				}
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
				val result = (it as Codec<Any>)
					.parse(registries.createSerializationContext(JsonOps.INSTANCE), savedValue)
					.getOrThrow()
				val current = field.handle.get(holder)
				if (copyIntoMutableCurrent(current, result)) return
				if (result !== current) {
					field.handle.set(holder, result)
				}
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

	@Suppress("UNCHECKED_CAST")
	private fun copyIntoMutableCurrent(@Nullable current: Any?, @Nullable result: Any?): Boolean {
		if (current is MutableCollection<*> && result is Collection<*>) {
			(current as MutableCollection<Any?>).clear()
			current.addAll(result)
			return true
		}
		if (current is MutableMap<*, *> && result is Map<*, *>) {
			(current as MutableMap<Any?, Any?>).clear()
			current.putAll(result)
			return true
		}
		return false
	}
}

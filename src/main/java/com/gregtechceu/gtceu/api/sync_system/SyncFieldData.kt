package com.gregtechceu.gtceu.api.sync_system

import com.gregtechceu.gtceu.GTCEu

import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.TypedDataComponent
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.ExtraCodecs

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSyntaxException
import com.mojang.datafixers.util.Either
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.JsonOps
import com.mojang.serialization.codecs.RecordCodecBuilder

import java.util.LinkedHashMap
import java.util.Optional

import java.util.Map.copyOf as immutableCopyOf

/**
 * Field payload stored as a typed data component outside of block-entity NBT boundaries.
 *
 * Missing keys mean "unchanged"; [JsonNull] means the field value was explicitly set to `null`.
 */
class SyncFieldData(fields: Map<ResourceLocation, @JvmSuppressWildcards JsonElement?>) {
	@get:JvmName("fields")
	val fields: Map<ResourceLocation, JsonElement> = immutableFields(fields)

	fun isEmpty(): Boolean = fields.isEmpty()

	operator fun contains(key: ResourceLocation): Boolean = fields.containsKey(key)

	operator fun get(key: ResourceLocation): JsonElement? = fields[key]

	fun toJson(): JsonElement = CODEC.encodeStart(JsonOps.INSTANCE, this).getOrThrow()

	fun toComponentMap(componentType: DataComponentType<SyncFieldData>): DataComponentMap {
		if (isEmpty()) {
			return DataComponentMap.EMPTY
		}
		return DataComponentMap.builder()
			.set(componentType, this)
			.build()
	}

	override fun equals(other: Any?): Boolean = this === other || (other is SyncFieldData && fields == other.fields)

	override fun hashCode(): Int = fields.hashCode()

	override fun toString(): String = "SyncFieldData[fields=$fields]"

	class Builder {
		private val fields: MutableMap<ResourceLocation, JsonElement> = LinkedHashMap()

		fun put(key: ResourceLocation, value: JsonElement?): Builder {
			fields[key] = value ?: JsonNull.INSTANCE
			return this
		}

		fun put(key: ResourceLocation, value: SyncFieldData): Builder {
			fields[key] = value.toJson()
			return this
		}

		fun build(): SyncFieldData = if (fields.isEmpty()) EMPTY else SyncFieldData(fields)
	}

	private class FieldEntry(val key: ResourceLocation, val value: JsonElement) {
		fun valueForCodec(): Optional<JsonElement> = if (value.isJsonNull) Optional.empty() else Optional.of(value)

		companion object {
			private val JSON_STRING_CODEC: Codec<JsonElement> = Codec.STRING.comapFlatMap(
				::parseJsonString,
				GSON::toJson,
			)
			private val VALUE_CODEC: Codec<JsonElement> = Codec
				.either(JSON_STRING_CODEC, ExtraCodecs.JSON)
				.xmap(
					{ either -> either.map({ value -> value }, { value -> value }) },
					{ value -> Either.left(value) },
				)
			val CODEC: Codec<FieldEntry> = RecordCodecBuilder.create { instance ->
				instance.group(
					ResourceLocation.CODEC.fieldOf("key").forGetter(FieldEntry::key),
					VALUE_CODEC.optionalFieldOf("value").forGetter(FieldEntry::valueForCodec),
				).apply(instance, ::fromCodec)
			}

			private fun fromCodec(key: ResourceLocation, value: Optional<JsonElement>): FieldEntry = FieldEntry(key, value.orElse(JsonNull.INSTANCE))

			private fun parseJsonString(value: String): DataResult<JsonElement> = try {
				DataResult.success(JsonParser.parseString(value))
			} catch (_: JsonSyntaxException) {
				DataResult.success(JsonPrimitive(value))
			}
		}
	}

	companion object {
		private val GSON = Gson()
		private const val MAX_FIELD_JSON_LENGTH = 1_048_576

		@JvmField
		val EMPTY = SyncFieldData(emptyMap())

		@JvmField
		val CODEC: Codec<SyncFieldData> = FieldEntry.CODEC.listOf().xmap(::fromEntries, ::toEntries)

		@JvmField
		val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, SyncFieldData> =
			object : StreamCodec<RegistryFriendlyByteBuf, SyncFieldData> {
				override fun decode(buffer: RegistryFriendlyByteBuf): SyncFieldData {
					val size = buffer.readVarInt()
					val fields: MutableMap<ResourceLocation, JsonElement> = LinkedHashMap(size)
					repeat(size) {
						val key = ResourceLocation.STREAM_CODEC.decode(buffer)
						fields[key] = JsonParser.parseString(buffer.readUtf(MAX_FIELD_JSON_LENGTH))
					}
					return SyncFieldData(fields)
				}

				override fun encode(buffer: RegistryFriendlyByteBuf, value: SyncFieldData) {
					buffer.writeVarInt(value.fields.size)
					for ((key, fieldValue) in value.fields) {
						ResourceLocation.STREAM_CODEC.encode(buffer, key)
						buffer.writeUtf(GSON.toJson(fieldValue), MAX_FIELD_JSON_LENGTH)
					}
				}
			}

		@JvmField
		val DATA_COMPONENT_MAP_STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, DataComponentMap> =
			object : StreamCodec<RegistryFriendlyByteBuf, DataComponentMap> {
				override fun decode(buffer: RegistryFriendlyByteBuf): DataComponentMap {
					val size = buffer.readVarInt()
					val builder = DataComponentMap.builder()
					for (index in 0 until size) {
						setDecodedComponent(builder, TypedDataComponent.STREAM_CODEC.decode(buffer))
					}
					return builder.build()
				}

				override fun encode(buffer: RegistryFriendlyByteBuf, value: DataComponentMap) {
					buffer.writeVarInt(value.size())
					for (component in value) {
						TypedDataComponent.STREAM_CODEC.encode(buffer, component)
					}
				}
			}

		@JvmStatic
		fun builder(): Builder = Builder()

		@JvmStatic
		fun key(key: String): ResourceLocation = if (key.indexOf(':') >= 0) {
			ResourceLocation.parse(key)
		} else {
			GTCEu.id(escapePath(key))
		}

		@JvmStatic
		fun fromJson(json: JsonElement): SyncFieldData = CODEC.parse(JsonOps.INSTANCE, json).getOrThrow()

		private fun immutableFields(fields: Map<ResourceLocation, JsonElement?>): Map<ResourceLocation, JsonElement> {
			val copy: MutableMap<ResourceLocation, JsonElement> = LinkedHashMap(fields.size)
			for ((key, fieldValue) in fields) {
				copy[key] = fieldValue ?: JsonNull.INSTANCE
			}
			return immutableCopyOf(copy)
		}

		private fun fromEntries(entries: List<FieldEntry>): SyncFieldData {
			val fields: MutableMap<ResourceLocation, JsonElement> = LinkedHashMap()
			for (entry in entries) {
				fields[entry.key] = entry.value
			}
			return SyncFieldData(fields)
		}

		private fun toEntries(data: SyncFieldData): List<FieldEntry> = data.fields.entries.map { entry -> FieldEntry(entry.key, entry.value) }

		private fun <T> setDecodedComponent(builder: DataComponentMap.Builder, component: TypedDataComponent<T>) {
			val type: DataComponentType<T> = component.type()
			builder.set(type, component.value())
		}

		private fun escapePath(key: String): String {
			val path = StringBuilder(key.length)
			for (character in key) {
				if (
					character in 'a'..'z' || character in '0'..'9' ||
					character == '_' || character == '-' || character == '.' || character == '/'
				) {
					path.append(character)
				} else {
					path.append("_u")
					val hex = character.code.toString(16)
					repeat(4 - hex.length) {
						path.append('0')
					}
					path.append(hex)
				}
			}
			return path.toString()
		}
	}
}

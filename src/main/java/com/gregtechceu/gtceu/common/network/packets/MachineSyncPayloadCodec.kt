package com.gregtechceu.gtceu.common.network.packets

import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.component.TypedDataComponent
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.EncoderException

import java.io.StringReader

internal object MachineSyncPayloadCodec : StreamCodec<RegistryFriendlyByteBuf, DataComponentMap> {
	internal const val MAX_BODY_LENGTH = 32_767

	private const val MAX_COMPONENT_COUNT = 4
	private const val MAX_FIELD_COUNT = 10
	private const val MAX_FIELD_JSON_LENGTH = 32_767
	private val gson = Gson()
	private val jsonElementAdapter = gson.getAdapter(JsonElement::class.java)
	private val jsonKeywords = arrayOf("true", "false", "null")

	override fun decode(buffer: RegistryFriendlyByteBuf): DataComponentMap = try {
		decodeComponents(buffer)
	} catch (exception: DecoderException) {
		throw exception
	} catch (exception: RuntimeException) {
		throw DecoderException("Machine sync component payload is malformed", exception)
	}

	override fun encode(buffer: RegistryFriendlyByteBuf, value: DataComponentMap) {
		try {
			encodeComponents(buffer, value)
		} catch (exception: EncoderException) {
			throw exception
		} catch (exception: IndexOutOfBoundsException) {
			throw EncoderException("Machine sync payload exceeds the maximum body length of $MAX_BODY_LENGTH bytes", exception)
		} catch (exception: RuntimeException) {
			throw EncoderException("Machine sync component payload could not be encoded", exception)
		}
	}

	private fun decodeComponents(buffer: RegistryFriendlyByteBuf): DataComponentMap {
		val componentCount = readBoundedCount(buffer, "component", MAX_COMPONENT_COUNT)
		val componentTypes = HashSet<DataComponentType<*>>(componentCount)
		val components = DataComponentMap.builder()
		repeat(componentCount) {
			val type = DataComponentType.STREAM_CODEC.decode(buffer)
			if (!componentTypes.add(type)) {
				throw DecoderException("Machine sync payload contains duplicate component type $type")
			}
			decodeComponent(buffer, components, type)
		}
		return components.build()
	}

	private fun encodeComponents(buffer: RegistryFriendlyByteBuf, value: DataComponentMap) {
		val componentCount = value.size()
		if (componentCount > MAX_COMPONENT_COUNT) {
			throw EncoderException("Machine sync component count $componentCount exceeds the maximum of $MAX_COMPONENT_COUNT")
		}
		buffer.writeVarInt(componentCount)
		for (component in value) {
			encodeComponent(buffer, component)
		}
	}

	private fun <T> decodeComponent(buffer: RegistryFriendlyByteBuf, components: DataComponentMap.Builder, type: DataComponentType<T>) {
		val value = if (type === GTDataComponents.SYNC_FIELD_DATA.get()) {
			@Suppress("UNCHECKED_CAST")
			(decodeFields(buffer) as T)
		} else {
			type.streamCodec().decode(buffer)
		}
		components.set(type, value)
	}

	private fun <T> encodeComponent(buffer: RegistryFriendlyByteBuf, component: TypedDataComponent<T>) {
		val type = component.type()
		DataComponentType.STREAM_CODEC.encode(buffer, type)
		if (type === GTDataComponents.SYNC_FIELD_DATA.get()) {
			@Suppress("UNCHECKED_CAST")
			encodeFields(buffer, component.value() as SyncFieldData)
		} else {
			type.streamCodec().encode(buffer, component.value())
		}
	}

	private fun decodeFields(buffer: RegistryFriendlyByteBuf): SyncFieldData {
		val fieldCount = readBoundedCount(buffer, "sync field", MAX_FIELD_COUNT)
		val fields = LinkedHashMap<ResourceLocation, JsonElement>(fieldCount)
		repeat(fieldCount) {
			val key = ResourceLocation.STREAM_CODEC.decode(buffer)
			if (fields.containsKey(key)) {
				throw DecoderException("Machine sync payload contains duplicate sync field $key")
			}
			val rawJson = buffer.readUtf(MAX_FIELD_JSON_LENGTH)
			fields[key] = decodeJson(key, rawJson)
		}
		return SyncFieldData(fields)
	}

	private fun encodeFields(buffer: RegistryFriendlyByteBuf, value: SyncFieldData) {
		val fieldCount = value.fields().size
		if (fieldCount > MAX_FIELD_COUNT) {
			throw EncoderException("Machine sync field count $fieldCount exceeds the maximum of $MAX_FIELD_COUNT")
		}
		buffer.writeVarInt(fieldCount)
		for ((key, fieldValue) in value.fields()) {
			ResourceLocation.STREAM_CODEC.encode(buffer, key)
			val rawJson = gson.toJson(fieldValue)
			if (rawJson.length > MAX_FIELD_JSON_LENGTH) {
				throw EncoderException(
					"Machine sync field $key JSON length ${rawJson.length} exceeds the maximum of $MAX_FIELD_JSON_LENGTH UTF-16 code units",
				)
			}
			try {
				decodeJson(key, rawJson)
			} catch (exception: DecoderException) {
				throw EncoderException("Machine sync field $key could not be encoded as strict JSON", exception)
			}
			buffer.writeUtf(rawJson, MAX_FIELD_JSON_LENGTH)
		}
	}

	private fun decodeJson(key: ResourceLocation, rawJson: String): JsonElement {
		try {
			// Gson 2.10's non-lenient reader still accepts a few non-RFC string and keyword forms.
			validateJsonStrings(key, rawJson)
			val reader = JsonReader(StringReader(rawJson))
			reader.setLenient(false)
			if (reader.peek() == JsonToken.END_DOCUMENT) {
				throw DecoderException("Machine sync field $key contains empty JSON")
			}
			val value = jsonElementAdapter.read(reader)
				?: throw DecoderException("Machine sync field $key decoded to no JSON value")
			if (reader.peek() != JsonToken.END_DOCUMENT) {
				throw DecoderException("Machine sync field $key contains trailing JSON content")
			}
			return value
		} catch (exception: DecoderException) {
			throw exception
		} catch (exception: Exception) {
			throw DecoderException("Machine sync field $key contains invalid JSON", exception)
		}
	}

	private fun validateJsonStrings(key: ResourceLocation, rawJson: String) {
		if (rawJson.startsWith('\uFEFF')) {
			throw DecoderException("Machine sync field $key contains a leading byte-order mark")
		}
		var inString = false
		var index = 0
		while (index < rawJson.length) {
			val character = rawJson[index]
			if (!inString) {
				if (character == '"') {
					inString = true
				} else if (character <= '\u001F' && character != '\t' && character != '\n' && character != '\r') {
					throw DecoderException(
						"Machine sync field $key contains invalid control character U+${controlCharacterCode(character)} outside a JSON string",
					)
				} else {
					validateJsonKeywordCase(key, rawJson, index)
				}
				index++
				continue
			}

			when {
				character == '"' -> {
					inString = false
					index++
				}

				character == '\\' -> index = validateJsonEscape(key, rawJson, index + 1)

				character <= '\u001F' -> throw DecoderException(
					"Machine sync field $key contains unescaped control character U+${controlCharacterCode(character)} in a JSON string",
				)

				else -> index++
			}
		}
	}

	private fun validateJsonKeywordCase(key: ResourceLocation, rawJson: String, index: Int) {
		for (keyword in jsonKeywords) {
			if (rawJson.startsWith(keyword, index, ignoreCase = true) && !rawJson.startsWith(keyword, index)) {
				throw DecoderException("Machine sync field $key contains non-lowercase JSON keyword at index $index")
			}
		}
	}

	private fun validateJsonEscape(key: ResourceLocation, rawJson: String, escapeIndex: Int): Int {
		if (escapeIndex >= rawJson.length) {
			throw DecoderException("Machine sync field $key contains an unterminated JSON escape")
		}
		return when (val escape = rawJson[escapeIndex]) {
			'"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> escapeIndex + 1

			'u' -> validateUnicodeEscape(key, rawJson, escapeIndex)

			in '\u0000'..'\u001F' -> throw DecoderException(
				"Machine sync field $key contains a backslash followed by control character U+${controlCharacterCode(escape)}",
			)

			else -> throw DecoderException("Machine sync field $key contains invalid JSON escape \\$escape")
		}
	}

	private fun validateUnicodeEscape(key: ResourceLocation, rawJson: String, escapeIndex: Int): Int {
		val endIndex = escapeIndex + 5
		if (endIndex > rawJson.length) {
			throw DecoderException("Machine sync field $key contains an incomplete JSON unicode escape")
		}
		for (index in escapeIndex + 1 until endIndex) {
			val character = rawJson[index]
			if (character !in '0'..'9' && character !in 'a'..'f' && character !in 'A'..'F') {
				throw DecoderException("Machine sync field $key contains an invalid JSON unicode escape")
			}
		}
		return endIndex
	}

	private fun controlCharacterCode(character: Char): String = character.code.toString(16).uppercase().padStart(4, '0')

	private fun readBoundedCount(buffer: RegistryFriendlyByteBuf, name: String, maximum: Int): Int {
		val count = buffer.readVarInt()
		if (count < 0) {
			throw DecoderException("Machine sync $name count cannot be negative: $count")
		}
		if (count > maximum) {
			throw DecoderException("Machine sync $name count $count exceeds the maximum of $maximum")
		}
		return count
	}
}

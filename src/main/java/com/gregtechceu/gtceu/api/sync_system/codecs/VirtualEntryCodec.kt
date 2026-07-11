package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec

import net.minecraft.resources.ResourceLocation

import com.google.gson.JsonElement
import com.google.gson.JsonObject

class VirtualEntryCodec private constructor() : ContextualFieldCodec<VirtualEntry> {

	override fun serializeField(value: VirtualEntry, context: ContextualFieldCodec.Context<VirtualEntry>): JsonElement {
		val json = JsonObject()
		json.addProperty(TYPE_KEY, value.type.getId().toString())
		json.add(DATA_KEY, value.serializeJson(context.lookup))
		return json
	}

	override fun deserializeField(value: JsonElement, context: ContextualFieldCodec.Context<VirtualEntry>): VirtualEntry? {
		if (!value.isJsonObject) {
			throw IllegalArgumentException("Sync: virtual entry field ${context.fieldName} must be encoded as an object")
		}

		val json = value.asJsonObject
		val typeId = ResourceLocation.parse(json.get(TYPE_KEY).asString)
		val type: EntryTypes<out VirtualEntry>? = EntryTypes.fromString(typeId.toString())
		if (type == null) {
			GTCEu.LOGGER.error("Sync: unknown virtual entry type {} for field {}", typeId, context.fieldName)
			return null
		}

		val entry = context.currentValue
			?.takeIf { it.type === type }
			?: type.createInstance()
		entry.deserializeJson(context.lookup, json.get(DATA_KEY))
		return entry
	}

	companion object {
		@JvmField
		val TYPE: Class<VirtualEntry> = VirtualEntry::class.java

		@JvmField
		val INSTANCE = VirtualEntryCodec()

		private const val TYPE_KEY = "type"
		private const val DATA_KEY = "data"
	}
}

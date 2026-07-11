package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability
import com.gregtechceu.gtceu.api.registry.GTRegistries
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec

import net.minecraft.resources.ResourceLocation

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import it.unimi.dsi.fastutil.objects.Object2IntMap

import java.util.IdentityHashMap

class RecipeChanceCachesCodec private constructor() : ContextualFieldCodec<IdentityHashMap<RecipeCapability<*>, Object2IntMap<*>>> {

	override fun serializeField(
		value: IdentityHashMap<RecipeCapability<*>, Object2IntMap<*>>,
		context: ContextualFieldCodec.Context<IdentityHashMap<RecipeCapability<*>, Object2IntMap<*>>>,
	): JsonElement {
		val chanceCache = JsonObject()
		for ((capability, cache) in value) {
			val cacheJson = JsonArray()
			for (cacheEntry in cache.object2IntEntrySet()) {
				val entryJson = JsonObject()
				writeEntryJson(entryJson, capability, cacheEntry.key, cacheEntry.intValue, context)
				cacheJson.add(entryJson)
			}
			chanceCache.add(GTRegistries.RECIPE_CAPABILITIES.getKey(capability)!!.toString(), cacheJson)
		}

		return chanceCache
	}

	override fun deserializeField(
		value: JsonElement,
		context: ContextualFieldCodec.Context<IdentityHashMap<RecipeCapability<*>, Object2IntMap<*>>>,
	): IdentityHashMap<RecipeCapability<*>, Object2IntMap<*>>? {
		if (!value.isJsonObject) return context.currentValue
		val currentValue = context.currentValue ?: return null

		for ((key, cacheJson) in value.asJsonObject.entrySet()) {
			val capability = GTRegistries.RECIPE_CAPABILITIES.get(ResourceLocation.parse(key))
			if (capability == null || !cacheJson.isJsonArray) continue

			val map = getOrCreateCache(currentValue, capability)
			for (entryJson in cacheJson.asJsonArray) {
				if (!entryJson.isJsonObject) continue
				val entryObject = entryJson.asJsonObject
				val cacheKey = readEntryJson(capability, entryObject, context)
				map.put(cacheKey, entryObject.get("cached_chance").asInt)
			}
		}
		return currentValue
	}

	@Suppress("UNCHECKED_CAST")
	private fun getOrCreateCache(currentValue: IdentityHashMap<RecipeCapability<*>, Object2IntMap<*>>, capability: RecipeCapability<*>): Object2IntMap<Any?> =
		currentValue.computeIfAbsent(capability) { key ->
			(key as RecipeCapability<Any?>).makeChanceCache()
		} as Object2IntMap<Any?>

	@Suppress("UNCHECKED_CAST")
	private fun writeEntryJson(json: JsonObject, capability: RecipeCapability<*>, content: Any?, chance: Int, context: ContextualFieldCodec.Context<*>) {
		val typedCapability = capability as RecipeCapability<Any?>
		json.add("entry", typedCapability.serializer.toJson(typedCapability.of(content), context.lookup))
		json.addProperty("cached_chance", chance)
	}

	@Suppress("UNCHECKED_CAST")
	private fun readEntryJson(capability: RecipeCapability<*>, json: JsonObject, context: ContextualFieldCodec.Context<*>): Any? =
		(capability as RecipeCapability<Any?>).serializer.fromJson(json.get("entry"), context.lookup)

	companion object {
		@JvmField
		val INSTANCE: RecipeChanceCachesCodec = RecipeChanceCachesCodec()
	}
}

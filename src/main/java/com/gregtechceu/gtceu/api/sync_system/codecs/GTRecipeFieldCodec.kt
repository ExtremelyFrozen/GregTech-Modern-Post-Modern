package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.api.recipe.GTRecipe
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import org.jetbrains.annotations.Nullable

class GTRecipeFieldCodec private constructor() : ContextualFieldCodec<GTRecipe> {
	override fun serializeNBT(value: GTRecipe, context: ContextualFieldCodec.Context<GTRecipe>): Tag {
		val tag = CompoundTag()
		tag.putString("id", value.id.toString())
		val recipePayload = checkNotNull(
			GTRecipeSerializer.CODEC.encode(GTRecipeDefinition.fromRuntime(value), NbtOps.INSTANCE, NbtOps.INSTANCE.mapBuilder())
				.build(CompoundTag()).result().orElse(CompoundTag()),
		)
		tag.put("recipe", recipePayload)
		tag.putInt("parallels", value.parallels)
		tag.putInt("ocLevel", value.ocLevel)
		return tag
	}

	override fun serializeField(value: GTRecipe, context: ContextualFieldCodec.Context<GTRecipe>): JsonElement {
		val json = JsonObject()
		json.addProperty("id", value.id.toString())
		json.add(
			"recipe",
			GTRecipeSerializer.CODEC.codec()
				.encodeStart(context.lookup.createSerializationContext(JsonOps.INSTANCE), GTRecipeDefinition.fromRuntime(value))
				.getOrThrow(),
		)
		json.addProperty("parallels", value.parallels)
		json.addProperty("ocLevel", value.ocLevel)
		return json
	}

	@Nullable
	override fun deserializeNBT(tag: Tag, context: ContextualFieldCodec.Context<GTRecipe>): GTRecipe? {
		if (tag is CompoundTag && tag.isEmpty) return null
		var result: GTRecipe? = null
		if (tag is CompoundTag) {
			val recipeTag = checkNotNull(tag.get("recipe"))
			val mapResult = NbtOps.INSTANCE.getMap(recipeTag).result()
			if (mapResult.isPresent) {
				result = GTRecipeSerializer.CODEC.decode(NbtOps.INSTANCE, mapResult.get()).result().orElse(null)?.toRuntime()
			}
			if (result != null) {
				result.id = ResourceLocation.parse(tag.getString("id"))
				result.parallels = if (tag.contains("parallels")) tag.getInt("parallels") else 1
				result.ocLevel = tag.getInt("ocLevel")
			}
		}
		return result
	}

	@Nullable
	override fun deserializeField(value: JsonElement, context: ContextualFieldCodec.Context<GTRecipe>): GTRecipe? {
		if (value.isJsonNull) return null
		if (!value.isJsonObject) return null
		val json = value.asJsonObject
		val recipeJson = json.get("recipe") ?: return null
		val result = GTRecipeSerializer.CODEC.codec()
			.parse(context.lookup.createSerializationContext(JsonOps.INSTANCE), recipeJson)
			.getOrThrow()
			.toRuntime()
		result.id = ResourceLocation.parse(json.get("id").asString)
		result.parallels = if (json.has("parallels")) json.get("parallels").asInt else 1
		result.ocLevel = if (json.has("ocLevel")) json.get("ocLevel").asInt else 0
		return result
	}

	companion object {
		@JvmField
		val TYPE: Class<GTRecipe> = GTRecipe::class.java

		@JvmField
		val INSTANCE: GTRecipeFieldCodec = GTRecipeFieldCodec()
	}
}

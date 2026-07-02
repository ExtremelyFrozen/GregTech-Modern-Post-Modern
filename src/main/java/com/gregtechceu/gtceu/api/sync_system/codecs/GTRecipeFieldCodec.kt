package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.api.recipe.GTRecipe
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec

import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.connection.ConnectionType

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import io.netty.buffer.Unpooled
import org.jetbrains.annotations.Nullable

class GTRecipeFieldCodec private constructor() : ContextualFieldCodec<GTRecipe> {
	override fun serializeField(value: GTRecipe, context: ContextualFieldCodec.Context<GTRecipe>): JsonElement {
		val json = JsonObject()
		json.addProperty("id", value.id.toString())
		json.add("recipe", encodeRecipe(context, GTRecipeDefinition.fromRuntime(value)))
		json.addProperty("parallels", value.parallels)
		json.addProperty("subtickParallels", value.subtickParallels)
		json.addProperty("batchParallels", value.batchParallels)
		json.addProperty("ocLevel", value.ocLevel)
		return json
	}

	@Nullable
	override fun deserializeField(value: JsonElement, context: ContextualFieldCodec.Context<GTRecipe>): GTRecipe? {
		if (value.isJsonNull) return null
		if (!value.isJsonObject) {
			throw IllegalArgumentException("Sync: GTRecipe field ${context.fieldName} must be encoded as an object")
		}
		val json = value.asJsonObject
		val recipeJson = json.get("recipe") ?: throw IllegalArgumentException("Sync: GTRecipe field ${context.fieldName} is missing recipe payload")
		val result = decodeRecipe(context, recipeJson).toRuntime()
		result.id = ResourceLocation.parse(json.get("id").asString)
		result.parallels = if (json.has("parallels")) json.get("parallels").asInt else 1
		result.subtickParallels = if (json.has("subtickParallels")) json.get("subtickParallels").asInt else 1
		result.batchParallels = if (json.has("batchParallels")) json.get("batchParallels").asInt else 1
		result.ocLevel = if (json.has("ocLevel")) json.get("ocLevel").asInt else 0
		return result
	}

	private fun encodeRecipe(context: ContextualFieldCodec.Context<GTRecipe>, recipe: GTRecipeDefinition): JsonElement {
		val registries = registryAccess(context)
		val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.OTHER)
		try {
			GTRecipeSerializer.STREAM_CODEC.encode(buffer, recipe)
			val bytes = JsonArray(buffer.readableBytes())
			while (buffer.isReadable) {
				bytes.add(buffer.readUnsignedByte())
			}
			return bytes
		} finally {
			buffer.release()
		}
	}

	private fun decodeRecipe(context: ContextualFieldCodec.Context<GTRecipe>, value: JsonElement): GTRecipeDefinition {
		if (value is JsonNull || !value.isJsonArray) {
			throw IllegalArgumentException("Sync: GTRecipe field ${context.fieldName} recipe payload must be an array")
		}
		val registries = registryAccess(context)
		val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(value.asJsonArray.size()), registries, ConnectionType.OTHER)
		try {
			for (element in value.asJsonArray) {
				buffer.writeByte(element.asInt)
			}
			val recipe = GTRecipeSerializer.fromNetwork(buffer, false)
			if (buffer.isReadable) {
				throw IllegalArgumentException("Sync: GTRecipe field ${context.fieldName} has ${buffer.readableBytes()} trailing recipe bytes")
			}
			return recipe
		} finally {
			buffer.release()
		}
	}

	private fun registryAccess(context: ContextualFieldCodec.Context<GTRecipe>): RegistryAccess = context.lookup as? RegistryAccess
		?: throw IllegalArgumentException("Sync: GTRecipe field ${context.fieldName} requires RegistryAccess for network serialization")

	companion object {
		@JvmField
		val TYPE: Class<GTRecipe> = GTRecipe::class.java

		@JvmField
		val INSTANCE: GTRecipeFieldCodec = GTRecipeFieldCodec()
	}
}

package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.api.recipe.GTRecipe
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation

import org.jetbrains.annotations.Nullable

import java.util.Objects

class GTRecipeFieldCodec private constructor() : ContextualFieldCodec<GTRecipe> {
	override fun serializeNBT(value: GTRecipe, context: ContextualFieldCodec.Context<GTRecipe>): Tag {
		val tag = CompoundTag()
		tag.putString("id", value.id.toString())
		tag.put(
			"recipe",
			GTRecipeSerializer.CODEC.encode(value, NbtOps.INSTANCE, NbtOps.INSTANCE.mapBuilder())
				.build(CompoundTag()).result().orElse(CompoundTag()),
		)
		tag.putInt("parallels", value.parallels)
		tag.putInt("ocLevel", value.ocLevel)
		return tag
	}

	@Nullable
	override fun deserializeNBT(tag: Tag, context: ContextualFieldCodec.Context<GTRecipe>): GTRecipe? {
		if (tag is CompoundTag && tag.isEmpty) return null
		var result: GTRecipe? = null
		if (tag is CompoundTag) {
			val recipeTag = tag.get("recipe")
			val mapResult = NbtOps.INSTANCE.getMap(Objects.requireNonNull(recipeTag)).result()
			if (mapResult.isPresent) {
				result = GTRecipeSerializer.CODEC.decode(NbtOps.INSTANCE, mapResult.get()).result().orElse(null)
			}
			if (result != null) {
				result.id = ResourceLocation.parse(tag.getString("id"))
				result.parallels = if (tag.contains("parallels")) tag.getInt("parallels") else 1
				result.ocLevel = tag.getInt("ocLevel")
			}
		}
		return result
	}

	companion object {
		@JvmField
		val TYPE: Class<GTRecipe> = GTRecipe::class.java

		@JvmField
		val INSTANCE: GTRecipeFieldCodec = GTRecipeFieldCodec()
	}
}

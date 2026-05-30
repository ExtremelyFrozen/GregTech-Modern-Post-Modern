package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability
import com.gregtechceu.gtceu.api.registry.GTRegistries
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation

import it.unimi.dsi.fastutil.objects.Object2IntMap
import org.jetbrains.annotations.Nullable

import java.util.IdentityHashMap

class RecipeChanceCachesCodec private constructor() : ContextualFieldCodec<IdentityHashMap<RecipeCapability<*>, Object2IntMap<*>>> {
	override fun serializeNBT(value: IdentityHashMap<RecipeCapability<*>, Object2IntMap<*>>, context: ContextualFieldCodec.Context<IdentityHashMap<RecipeCapability<*>, Object2IntMap<*>>>): Tag {
		val chanceCache = CompoundTag()
		val currentValue = context.currentValue ?: return chanceCache

		for ((cap, cache) in currentValue) {
			val cacheTag = ListTag()
			for (entry in cache.object2IntEntrySet()) {
				val compoundTag = CompoundTag()
				val obj = cap.toNbt(entry.key, context.lookup)
				compoundTag.put("entry", obj)
				compoundTag.putInt("cached_chance", entry.intValue)
				cacheTag.add(compoundTag)
			}
			chanceCache.put(cap.name, cacheTag)
		}

		return chanceCache
	}

	@Suppress("rawtypes", "UNCHECKED_CAST")
	@Nullable
	override fun deserializeNBT(tag: Tag, context: ContextualFieldCodec.Context<IdentityHashMap<RecipeCapability<*>, Object2IntMap<*>>>): IdentityHashMap<RecipeCapability<*>, Object2IntMap<*>>? {
		if (tag !is CompoundTag) return context.currentValue
		val currentValue = context.currentValue ?: return null

		for (key in tag.allKeys) {
			val cap = GTRegistries.RECIPE_CAPABILITIES.get(ResourceLocation.parse(key)) ?: continue
			val map = currentValue.computeIfAbsent(cap) { capability -> (capability as RecipeCapability<Any>).makeChanceCache() } as Object2IntMap<Any>

			val chanceTag = tag.getList(key, Tag.TAG_COMPOUND.toInt())
			for (i in 0 until chanceTag.size) {
				val chanceKey = chanceTag.getCompound(i)
				val entry = (cap as RecipeCapability<Any>).fromNbt(chanceKey.get("entry"), context.lookup)
				val value = chanceKey.getInt("cached_chance")
				map.put(entry, value)
			}
		}
		return currentValue
	}

	companion object {
		@JvmField
		val INSTANCE: RecipeChanceCachesCodec = RecipeChanceCachesCodec()
	}
}

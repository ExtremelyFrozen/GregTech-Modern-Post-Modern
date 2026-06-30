package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHostTrait

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag

import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import org.jetbrains.annotations.Nullable

class GridNodeHostCodec private constructor() : ContextualFieldCodec<Any> {
	override fun serializeNBT(value: Any, context: ContextualFieldCodec.Context<Any>): Tag {
		val currentValue = context.currentValue
		if (GTCEu.Mods.isAE2Loaded() && currentValue is GridNodeHostTrait) {
			val compound = CompoundTag()
			currentValue.mainNode.saveToNBT(compound)
			return compound
		}
		return CompoundTag()
	}

	override fun serializeField(value: Any, context: ContextualFieldCodec.Context<Any>): JsonElement = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, serializeNBT(value, context))

	@Nullable
	override fun deserializeNBT(tag: Tag, context: ContextualFieldCodec.Context<Any>): Any? {
		val currentValue = context.currentValue
		if (GTCEu.Mods.isAE2Loaded() && currentValue is GridNodeHostTrait && tag is CompoundTag) {
			currentValue.mainNode.loadFromNBT(tag)
			return currentValue
		}
		return null
	}

	override fun deserializeField(value: JsonElement, context: ContextualFieldCodec.Context<Any>): Any? = deserializeNBT(JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, value), context)

	companion object {
		@JvmField
		val INSTANCE: GridNodeHostCodec = GridNodeHostCodec()
	}
}

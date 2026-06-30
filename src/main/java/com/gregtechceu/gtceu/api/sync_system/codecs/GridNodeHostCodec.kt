package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHostTrait

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps

import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import org.jetbrains.annotations.Nullable

class GridNodeHostCodec private constructor() : ContextualFieldCodec<Any> {
	override fun serializeField(value: Any, context: ContextualFieldCodec.Context<Any>): JsonElement {
		val currentValue = context.currentValue
		if (GTCEu.Mods.isAE2Loaded() && currentValue is GridNodeHostTrait) {
			val compound = CompoundTag()
			currentValue.mainNode.saveToNBT(compound)
			return NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, compound)
		}
		return NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, CompoundTag())
	}

	@Nullable
	override fun deserializeField(value: JsonElement, context: ContextualFieldCodec.Context<Any>): Any? {
		val currentValue = context.currentValue
		val tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, value)
		if (GTCEu.Mods.isAE2Loaded() && currentValue is GridNodeHostTrait && tag is CompoundTag) {
			currentValue.mainNode.loadFromNBT(tag)
			return currentValue
		}
		return null
	}

	companion object {
		@JvmField
		val INSTANCE: GridNodeHostCodec = GridNodeHostCodec()
	}
}

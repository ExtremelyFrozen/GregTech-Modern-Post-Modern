package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHostTrait

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag

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

	@Nullable
	override fun deserializeNBT(tag: Tag, context: ContextualFieldCodec.Context<Any>): Any? {
		val currentValue = context.currentValue
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

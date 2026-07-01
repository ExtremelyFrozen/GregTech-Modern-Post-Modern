package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHostTrait
import com.gregtechceu.gtceu.integration.ae2.utils.SerializableManagedGridNode

import net.minecraft.core.component.DataComponentMap

import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import org.jetbrains.annotations.Nullable

class GridNodeHostCodec private constructor() : ContextualFieldCodec<Any> {
	override fun serializeField(value: Any, context: ContextualFieldCodec.Context<Any>): JsonElement {
		val currentValue = context.currentValue
		if (GTCEu.Mods.isAE2Loaded() && currentValue is GridNodeHostTrait) {
			return encodeComponents(SerializableManagedGridNode.exportComponents(currentValue.mainNode), context)
		}
		return encodeComponents(DataComponentMap.EMPTY, context)
	}

	@Nullable
	override fun deserializeField(value: JsonElement, context: ContextualFieldCodec.Context<Any>): Any? {
		val currentValue = context.currentValue
		if (GTCEu.Mods.isAE2Loaded() && currentValue is GridNodeHostTrait) {
			SerializableManagedGridNode.importComponents(currentValue.mainNode, decodeComponents(value, context))
			return currentValue
		}
		return null
	}

	private fun encodeComponents(components: DataComponentMap, context: ContextualFieldCodec.Context<Any>): JsonElement = DataComponentMap.CODEC
		.encodeStart(context.lookup.createSerializationContext(JsonOps.INSTANCE), components)
		.getOrThrow()

	private fun decodeComponents(value: JsonElement, context: ContextualFieldCodec.Context<Any>): DataComponentMap = DataComponentMap.CODEC
		.parse(context.lookup.createSerializationContext(JsonOps.INSTANCE), value)
		.getOrThrow()

	companion object {
		@JvmField
		val INSTANCE: GridNodeHostCodec = GridNodeHostCodec()
	}
}

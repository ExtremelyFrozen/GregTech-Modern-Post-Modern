package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.api.machine.trait.MachineTraitHolder
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec

import net.minecraft.core.component.DataComponentMap

import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps

class MachineTraitHolderCodec private constructor() : ContextualFieldCodec<MachineTraitHolder> {
	override fun serializeField(value: MachineTraitHolder, context: ContextualFieldCodec.Context<MachineTraitHolder>): JsonElement = DataComponentMap.CODEC
		.encodeStart(
			context.lookup.createSerializationContext(JsonOps.INSTANCE),
			value.serializeSyncComponents(context.lookup, context.isClientSync, context.isClientFullSyncUpdate),
		)
		.getOrThrow()

	override fun shouldSyncField(value: MachineTraitHolder, context: ContextualFieldCodec.Context<MachineTraitHolder>, fullSync: Boolean, manuallyDirty: Boolean): Boolean {
		if (!context.isClientSync) return fullSync || manuallyDirty
		return value.scanAndMarkClientChanges(context.lookup, fullSync || manuallyDirty, context.serializationTarget)
	}

	override fun deserializeField(value: JsonElement, context: ContextualFieldCodec.Context<MachineTraitHolder>): MachineTraitHolder {
		val holder = requireNotNull(context.currentValue)
		val components = DataComponentMap.CODEC
			.parse(context.lookup.createSerializationContext(JsonOps.INSTANCE), value)
			.getOrThrow()
		holder.deserializeSyncComponents(context.lookup, components, context.isClientSync, context.parseExplicitNull)
		return holder
	}

	companion object {
		@JvmField
		val TYPE: Class<MachineTraitHolder> = MachineTraitHolder::class.java

		@JvmField
		val INSTANCE: MachineTraitHolderCodec = MachineTraitHolderCodec()
	}
}

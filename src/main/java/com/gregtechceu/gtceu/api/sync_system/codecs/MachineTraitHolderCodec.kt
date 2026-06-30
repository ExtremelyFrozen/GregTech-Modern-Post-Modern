package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.machine.trait.MachineTraitHolder
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec

import net.minecraft.core.component.DataComponentMap
import net.minecraft.nbt.Tag

import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps

class MachineTraitHolderCodec private constructor() : ContextualFieldCodec<MachineTraitHolder> {
	override fun serializeNBT(value: MachineTraitHolder, context: ContextualFieldCodec.Context<MachineTraitHolder>): Tag = throw unsupportedNbt(context.fieldName)

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

	override fun deserializeNBT(tag: Tag, context: ContextualFieldCodec.Context<MachineTraitHolder>): MachineTraitHolder = throw unsupportedNbt(context.fieldName)

	override fun deserializeField(value: JsonElement, context: ContextualFieldCodec.Context<MachineTraitHolder>): MachineTraitHolder {
		val holder = requireNotNull(context.currentValue)
		val components = DataComponentMap.CODEC
			.parse(context.lookup.createSerializationContext(JsonOps.INSTANCE), value)
			.getOrThrow()
		holder.deserializeSyncComponents(context.lookup, components, context.isClientSync)
		return holder
	}

	companion object {
		@JvmField
		val TYPE: Class<MachineTraitHolder> = MachineTraitHolder::class.java

		@JvmField
		val INSTANCE: MachineTraitHolderCodec = MachineTraitHolderCodec()

		private fun unsupportedNbt(fieldName: String): UnsupportedOperationException {
			val message = "Sync: field $fieldName uses MachineTraitHolder and must be serialized as DataComponentMap"
			GTCEu.LOGGER.error(message)
			return UnsupportedOperationException(message)
		}
	}
}

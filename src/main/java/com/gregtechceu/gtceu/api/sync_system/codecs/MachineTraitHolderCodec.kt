package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.api.machine.trait.MachineTraitHolder
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag

class MachineTraitHolderCodec private constructor() : ContextualFieldCodec<MachineTraitHolder> {
	override fun serializeNBT(value: MachineTraitHolder, context: ContextualFieldCodec.Context<MachineTraitHolder>): Tag =
		value.serializeSyncData(context.lookup, context.isClientSync, context.isClientFullSyncUpdate)

	override fun shouldSyncField(value: MachineTraitHolder, context: ContextualFieldCodec.Context<MachineTraitHolder>, fullSync: Boolean, manuallyDirty: Boolean): Boolean {
		if (!context.isClientSync) return fullSync || manuallyDirty
		return value.scanAndMarkClientChanges(context.lookup, fullSync || manuallyDirty)
	}

	override fun deserializeNBT(tag: Tag, context: ContextualFieldCodec.Context<MachineTraitHolder>): MachineTraitHolder {
		val holder = requireNotNull(context.currentValue)
		holder.deserializeSyncData(context.lookup, tag as CompoundTag, context.isClientSync)
		return holder
	}

	companion object {
		@JvmField
		val TYPE: Class<MachineTraitHolder> = MachineTraitHolder::class.java

		@JvmField
		val INSTANCE: MachineTraitHolderCodec = MachineTraitHolderCodec()
	}
}

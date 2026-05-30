package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec
import com.gregtechceu.gtceu.utils.data.TagCompatibilityFixer

import net.minecraft.nbt.Tag
import net.neoforged.neoforge.common.util.INBTSerializable

import org.jetbrains.annotations.Nullable

@Suppress("rawtypes", "UNCHECKED_CAST")
class NBTSerializableCodec private constructor() : ContextualFieldCodec<INBTSerializable<*>> {
	override fun serializeNBT(value: INBTSerializable<*>, context: ContextualFieldCodec.Context<INBTSerializable<*>>): Tag = value.serializeNBT(context.lookup)

	@Nullable
	override fun deserializeNBT(tag: Tag, context: ContextualFieldCodec.Context<INBTSerializable<*>>): INBTSerializable<*>? {
		val currentValue = context.currentValue
		if (currentValue == null) {
			GTCEu.LOGGER.warn("Sync: Deserialization of INBTSerializable objects requires an existing object, they cannot be instantiated purely from saved data.")
			return null
		}
		(currentValue as INBTSerializable<Tag>).deserializeNBT(context.lookup, TagCompatibilityFixer.stripLDLibPayloadWrapper(tag))
		return currentValue
	}

	companion object {
		@JvmField
		val TYPE: Class<INBTSerializable<*>> = INBTSerializable::class.java

		@JvmField
		val INSTANCE: NBTSerializableCodec = NBTSerializableCodec()
	}
}

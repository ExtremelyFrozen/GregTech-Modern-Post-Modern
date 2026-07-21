package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec
import com.gregtechceu.gtceu.api.transfer.DataComponentTransfer

import net.minecraft.core.component.DataComponentMap

import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps

class DataComponentTransferCodec private constructor() : ContextualFieldCodec<DataComponentTransfer> {

	override fun serializeField(value: DataComponentTransfer, context: ContextualFieldCodec.Context<DataComponentTransfer>): JsonElement = DataComponentMap.CODEC
		.encodeStart(context.lookup.createSerializationContext(JsonOps.INSTANCE), value.exportComponents())
		.getOrThrow()

	override fun deserializeField(value: JsonElement, context: ContextualFieldCodec.Context<DataComponentTransfer>): DataComponentTransfer {
		val transfer = context.currentValue
			?: throw IllegalArgumentException("Sync: data component transfer field ${context.fieldName} requires an existing field instance")
		val components = DataComponentMap.CODEC
			.parse(context.lookup.createSerializationContext(JsonOps.INSTANCE), value)
			.getOrThrow()
		transfer.importComponents(components)
		return transfer
	}

	companion object {
		@JvmField
		val TYPE: Class<DataComponentTransfer> = DataComponentTransfer::class.java

		@JvmField
		val INSTANCE = DataComponentTransferCodec()
	}
}

package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap

import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps

class CustomItemStackHandlerCodec private constructor() : ContextualFieldCodec<CustomItemStackHandler> {

	override fun serializeField(value: CustomItemStackHandler, context: ContextualFieldCodec.Context<CustomItemStackHandler>): JsonElement = DataComponentMap.CODEC
		.encodeStart(context.lookup.createSerializationContext(JsonOps.INSTANCE), value.exportComponents())
		.getOrThrow()

	override fun deserializeField(value: JsonElement, context: ContextualFieldCodec.Context<CustomItemStackHandler>): CustomItemStackHandler {
		val components = DataComponentMap.CODEC
			.parse(context.lookup.createSerializationContext(JsonOps.INSTANCE), value)
			.getOrThrow()
		val data = components.get(GTDataComponents.TRANSFER_ITEM_HANDLER.get())
			?: throw IllegalArgumentException("Sync: item handler field ${context.fieldName} is missing transfer_item_handler")
		val handler = context.currentValue ?: CustomItemStackHandler(data.slots)
		handler.importComponents(components)
		return handler
	}

	companion object {
		@JvmField
		val TYPE: Class<CustomItemStackHandler> = CustomItemStackHandler::class.java

		@JvmField
		val INSTANCE = CustomItemStackHandlerCodec()
	}
}

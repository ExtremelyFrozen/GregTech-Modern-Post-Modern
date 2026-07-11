package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponentMap

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps

class MonitorGroupCodec private constructor() : ContextualFieldCodec<MonitorGroup> {

	override fun serializeField(value: MonitorGroup, context: ContextualFieldCodec.Context<MonitorGroup>): JsonElement {
		val json = JsonObject()
		json.addProperty("name", value.name)

		val positions = JsonArray()
		value.monitorPositions.forEach { position ->
			positions.add(
				BlockPos.CODEC
					.encodeStart(context.lookup.createSerializationContext(JsonOps.INSTANCE), position)
					.getOrThrow(),
			)
		}
		json.add("positions", positions)

		val target = value.targetRaw
		if (target != null) {
			json.add(
				"targetPos",
				BlockPos.CODEC
					.encodeStart(context.lookup.createSerializationContext(JsonOps.INSTANCE), target)
					.getOrThrow(),
			)
			val targetCoverSide = value.targetCoverSide
			if (targetCoverSide != null) {
				json.add(
					"targetSide",
					Direction.CODEC
						.encodeStart(JsonOps.INSTANCE, targetCoverSide)
						.getOrThrow(),
				)
			}
		}

		json.addProperty("dataSlot", value.dataSlot)
		json.add("items", serializeItems(value.itemStackHandler, context))
		json.add("placeholderSlots", serializeItems(value.placeholderSlotsHandler, context))
		return json
	}

	override fun deserializeField(value: JsonElement, context: ContextualFieldCodec.Context<MonitorGroup>): MonitorGroup? {
		if (!value.isJsonObject) return null

		val json = value.asJsonObject
		val handler = deserializeItems(json.get("items"), context, MonitorGroup.createModuleHandler())
		val placeholderSlotsHandler = deserializeItems(
			json.get("placeholderSlots"),
			context,
			CustomItemStackHandler(8),
		)
		val group = MonitorGroup(json.get("name").asString, handler, placeholderSlotsHandler)

		val positions = json.getAsJsonArray("positions")
		for (position in positions) {
			group.add(
				BlockPos.CODEC
					.parse(context.lookup.createSerializationContext(JsonOps.INSTANCE), position)
					.getOrThrow(),
			)
		}

		if (json.has("targetPos")) {
			group.setTarget(
				BlockPos.CODEC
					.parse(context.lookup.createSerializationContext(JsonOps.INSTANCE), json.get("targetPos"))
					.getOrThrow(),
			)
			if (json.has("targetSide")) {
				group.setTargetCoverSide(
					Direction.CODEC
						.parse(JsonOps.INSTANCE, json.get("targetSide"))
						.getOrThrow(),
				)
			}
			if (json.has("dataSlot")) {
				group.setDataSlot(json.get("dataSlot").asInt)
			}
		}
		return group
	}

	private fun serializeItems(handler: CustomItemStackHandler, context: ContextualFieldCodec.Context<MonitorGroup>): JsonElement = DataComponentMap.CODEC
		.encodeStart(context.lookup.createSerializationContext(JsonOps.INSTANCE), handler.exportComponents())
		.getOrThrow()

	private fun deserializeItems(json: JsonElement?, context: ContextualFieldCodec.Context<MonitorGroup>, handler: CustomItemStackHandler): CustomItemStackHandler {
		val itemData = json ?: throw IllegalArgumentException("Sync: monitor group is missing item handler data")
		val components = DataComponentMap.CODEC
			.parse(
				context.lookup.createSerializationContext(JsonOps.INSTANCE),
				itemData,
			)
			.getOrThrow()
		handler.importComponents(components)
		return handler
	}

	companion object {
		@JvmField
		val TYPE: Class<MonitorGroup> = MonitorGroup::class.java

		@JvmField
		val INSTANCE = MonitorGroupCodec()
	}
}

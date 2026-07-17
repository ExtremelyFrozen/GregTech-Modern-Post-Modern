package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.UUIDUtil
import net.minecraft.core.component.DataComponentMap

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps

import java.util.UUID

class MonitorGroupCodec private constructor() : ContextualFieldCodec<MonitorGroup> {

	override fun serializeField(value: MonitorGroup, context: ContextualFieldCodec.Context<MonitorGroup>): JsonElement {
		val json = JsonObject()
		json.addProperty("name", value.name)
		json.add(
			"identity",
			UUIDUtil.CODEC
				.encodeStart(JsonOps.INSTANCE, value.getIdentity())
				.getOrThrow(),
		)
		json.add(
			"dynamicItemSlotIncarnation",
			UUIDUtil.CODEC
				.encodeStart(JsonOps.INSTANCE, value.getDynamicItemSlotIncarnation())
				.getOrThrow(),
		)
		json.add(
			"moduleSlotIncarnation",
			UUIDUtil.CODEC
				.encodeStart(JsonOps.INSTANCE, value.getModuleSlotIncarnation())
				.getOrThrow(),
		)
		json.addProperty("textConfigurationRevision", value.getTextConfigurationRevision())

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
		val identity = deserializeUuidOrCreateLegacy(json, "identity")
		val dynamicItemSlotIncarnation = deserializeUuidOrCreateLegacy(json, "dynamicItemSlotIncarnation")
		val moduleSlotIncarnation = deserializeUuidOrCreateLegacy(json, "moduleSlotIncarnation")
		val textConfigurationRevision = deserializeTextConfigurationRevision(json)
		val handler = deserializeItems(json.get("items"), context, MonitorGroup.createModuleHandler())
		val placeholderSlotsHandler = deserializeItems(
			json.get("placeholderSlots"),
			context,
			MonitorGroup.createPlaceholderHandler(),
		)
		val group = MonitorGroup.restore(
			identity,
			dynamicItemSlotIncarnation,
			moduleSlotIncarnation,
			textConfigurationRevision,
			json.get("name").asString,
			handler,
			placeholderSlotsHandler,
		)

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

	private fun deserializeUuidOrCreateLegacy(json: JsonObject, fieldName: String): UUID {
		if (!json.has(fieldName)) {
			return UUID.randomUUID()
		}
		return UUIDUtil.CODEC
			.parse(JsonOps.INSTANCE, json.get(fieldName))
			.getOrThrow()
	}

	private fun deserializeTextConfigurationRevision(json: JsonObject): Long {
		if (!json.has("textConfigurationRevision")) {
			return 0
		}
		val encodedRevision = json.get("textConfigurationRevision")
		if (!encodedRevision.isJsonPrimitive || !encodedRevision.asJsonPrimitive.isNumber) {
			throw IllegalArgumentException("Monitor group text configuration revision must be a number")
		}
		val revision = try {
			encodedRevision.asBigDecimal.longValueExact()
		} catch (exception: NumberFormatException) {
			throw IllegalArgumentException("Monitor group text configuration revision must be an integer", exception)
		} catch (exception: ArithmeticException) {
			throw IllegalArgumentException("Monitor group text configuration revision is outside the long range", exception)
		}
		require(revision >= 0) { "Monitor group text configuration revision must be non-negative" }
		return revision
	}

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

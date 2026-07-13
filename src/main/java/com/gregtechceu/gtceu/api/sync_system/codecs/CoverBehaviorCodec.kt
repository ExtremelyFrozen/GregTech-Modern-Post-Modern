package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.capability.ICoverable
import com.gregtechceu.gtceu.api.cover.CoverBehavior
import com.gregtechceu.gtceu.api.registry.GTRegistries
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec
import com.gregtechceu.gtceu.api.sync_system.SyncSerializationTarget

import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps

class CoverBehaviorCodec private constructor() : ContextualFieldCodec<CoverBehavior> {

	override fun serializeField(value: CoverBehavior, context: ContextualFieldCodec.Context<CoverBehavior>): JsonElement {
		val json = JsonObject()
		json.addProperty("side", value.attachedSide.ordinal)
		json.addProperty("coverType", value.coverDefinition.getId().toString())
		json.add(
			"data",
			DataComponentMap.CODEC
				.encodeStart(
					context.lookup.createSerializationContext(JsonOps.INSTANCE),
					value.getSyncDataHolder().serializeToComponents(
						context.lookup,
						context.isClientSync,
						context.isClientFullSyncUpdate,
					),
				).getOrThrow(),
		)
		return json
	}

	override fun shouldSyncField(value: CoverBehavior, context: ContextualFieldCodec.Context<CoverBehavior>, fullSync: Boolean, manuallyDirty: Boolean): Boolean {
		if (!context.isClientSync) return fullSync || manuallyDirty
		if (fullSync || manuallyDirty) {
			value.getSyncDataHolder().resyncAllFields()
			return true
		}
		return when (context.serializationTarget) {
			SyncSerializationTarget.NBT -> {
				val message = "Sync: CoverBehavior client sync NBT is disabled; use DataComponentMap serialization"
				GTCEu.LOGGER.error(message)
				throw IllegalStateException(message)
			}

			SyncSerializationTarget.DATA_COMPONENTS -> value.getSyncDataHolder().scanAndMarkChanges(context.lookup)
		}
	}

	override fun deserializeField(value: JsonElement, context: ContextualFieldCodec.Context<CoverBehavior>): CoverBehavior? {
		if (value.isJsonNull) return null

		val holder = context.holder
		if (!value.isJsonObject || holder !is ICoverable) {
			GTCEu.LOGGER.error("Sync: Object attempting to sync cover does not implement ICoverable {}", context)
			return null
		}

		val json = value.asJsonObject
		val side = Direction.values()[json.get("side").asInt]
		val coverTypeName = json.get("coverType").asString
		if (coverTypeName.isEmpty()) {
			holder.setCoverAtSide(null, side)
			return null
		}

		val coverType = ResourceLocation.parse(coverTypeName)
		val currentCover = context.currentValue
		if (currentCover == null || currentCover.coverDefinition.getId() != coverType) {
			val coverDefinition = GTRegistries.COVERS.get(coverType)
			if (coverDefinition == null) {
				GTCEu.LOGGER.error("Error during component load: unknown cover type {}", coverType)
				return null
			}
			holder.setCoverAtSide(coverDefinition.createCoverBehavior(holder, side), side)
		}

		val cover = holder.getCoverAtSide(side) ?: return null
		val components = DataComponentMap.CODEC
			.parse(context.lookup.createSerializationContext(JsonOps.INSTANCE), json.get("data"))
			.getOrThrow()
		cover.getSyncDataHolder().deserializeComponents(context.lookup, components, context.isClientSync)
		return cover
	}

	companion object {

		@JvmField
		val TYPE: Class<CoverBehavior> = CoverBehavior::class.java

		@JvmField
		val INSTANCE = CoverBehaviorCodec()
	}
}

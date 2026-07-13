package com.gregtechceu.gtceu.common.cover.voiding

import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation

import com.google.gson.JsonPrimitive

/** Encodes and validates the shared working-enabled payload used by item and fluid voiding covers. */
internal object VoidingWorkingEnabledActionProtocol {

	private val WORKING_ENABLED_FIELD = SyncFieldData.key("workingEnabled")

	/** Creates a working-enabled action while preserving the existing boolean-derived sequence. */
	fun createAction(actionId: ResourceLocation, workingEnabled: Boolean): SyncActionData {
		val fields =
			SyncFieldData.builder()
				.put(WORKING_ENABLED_FIELD, JsonPrimitive(workingEnabled))
				.build()
		return SyncActionData(
			actionId,
			if (workingEnabled) 1 else 0,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	/** Returns the requested state, or `null` when the required field is absent or not a JSON boolean. */
	fun read(payload: DataComponentMap): Boolean? {
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		val primitive = fields[WORKING_ENABLED_FIELD] as? JsonPrimitive ?: return null
		return if (primitive.isBoolean) primitive.asBoolean else null
	}

	/** Requires a payload already accepted by [read]. */
	fun require(payload: DataComponentMap): Boolean = read(payload)
		?: throw IllegalStateException("Voiding cover working-enabled action payload is invalid.")
}

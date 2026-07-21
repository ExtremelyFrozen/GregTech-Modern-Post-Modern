package com.gregtechceu.gtceu.api.sync_system.codecs

import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap

import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps

class CustomFluidTankCodec private constructor() : ContextualFieldCodec<CustomFluidTank> {

	override fun serializeField(value: CustomFluidTank, context: ContextualFieldCodec.Context<CustomFluidTank>): JsonElement = DataComponentMap.CODEC
		.encodeStart(context.lookup.createSerializationContext(JsonOps.INSTANCE), value.exportComponents())
		.getOrThrow()

	override fun deserializeField(value: JsonElement, context: ContextualFieldCodec.Context<CustomFluidTank>): CustomFluidTank {
		val components = DataComponentMap.CODEC
			.parse(context.lookup.createSerializationContext(JsonOps.INSTANCE), value)
			.getOrThrow()
		val data = components.get(GTDataComponents.TRANSFER_FLUID_TANK.get())
		val tank = context.currentValue ?: CustomFluidTank(
			data?.capacity ?: throw IllegalArgumentException("Sync: fluid tank field ${context.fieldName} is missing transfer_fluid_tank"),
		)
		tank.importComponents(components)
		return tank
	}

	companion object {
		@JvmField
		val TYPE: Class<CustomFluidTank> = CustomFluidTank::class.java

		@JvmField
		val INSTANCE = CustomFluidTankCodec()
	}
}

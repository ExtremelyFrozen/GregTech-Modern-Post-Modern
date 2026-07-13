package com.gregtechceu.gtceu.common.machine.storage

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

import com.google.gson.JsonPrimitive
import org.jetbrains.annotations.ApiStatus

/** Exposes the buffer fluid-slot operation that its Kotlin action handler may invoke. */
@ApiStatus.Internal
interface BufferFluidSlotActionTarget : LDLib2FancyActionMachine {

	/** Executes one validated fluid-container click against [tankIndex]. */
	fun clickBufferFluidSlot(player: ServerPlayer, tankIndex: Int, shiftDown: Boolean)
}

/** Owns the wire protocol and server handler for buffer fluid-slot clicks. */
object BufferMachineActions {

	private val CLICK_BUFFER_FLUID_SLOT_ACTION = GTCEu.id("click_buffer_fluid_slot")
	private val TANK_FIELD = SyncFieldData.key("tank")
	private val SHIFT_FIELD = SyncFieldData.key("shift")

	init {
		SyncActionDispatchers.server().register(BufferFluidSlotActionHandler)
	}

	/** Forces this object's static registration from the owning machine's class initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one client action for a real buffer tank container interaction. */
	@JvmStatic
	fun createClickFluidSlotAction(tankIndex: Int, shiftDown: Boolean): SyncActionData {
		val fields =
			SyncFieldData.builder()
				.put(TANK_FIELD, JsonPrimitive(tankIndex))
				.put(SHIFT_FIELD, JsonPrimitive(shiftDown))
				.build()
		return SyncActionData(
			CLICK_BUFFER_FLUID_SLOT_ACTION,
			tankIndex * 2 + if (shiftDown) 1 else 0,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private object BufferFluidSlotActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = CLICK_BUFFER_FLUID_SLOT_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is BufferFluidSlotActionTarget

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return readNonNegativeInteger(fields, TANK_FIELD) != null && readBoolean(fields, SHIFT_FIELD) != null
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			target(context).clickBufferFluidSlot(
				context.player,
				requireNonNegativeInteger(context.payload(), TANK_FIELD),
				requireBoolean(context.payload(), SHIFT_FIELD),
			)
		}
	}

	private fun target(context: SyncActionContext): BufferFluidSlotActionTarget = context.holder as? BufferFluidSlotActionTarget
		?: throw IllegalStateException("Buffer fluid slot action received a non-buffer machine.")

	private fun requireFieldData(payload: DataComponentMap): SyncFieldData = payload[GTDataComponents.SYNC_FIELD_DATA.get()]
		?: throw IllegalStateException("Buffer fluid slot action payload is missing field data.")

	private fun requireNonNegativeInteger(payload: DataComponentMap, field: ResourceLocation): Int = readNonNegativeInteger(requireFieldData(payload), field)
		?: throw IllegalStateException("Buffer fluid slot action payload is missing $field.")

	private fun requireBoolean(payload: DataComponentMap, field: ResourceLocation): Boolean = readBoolean(requireFieldData(payload), field)
		?: throw IllegalStateException("Buffer fluid slot action payload is missing $field.")

	private fun readNonNegativeInteger(fields: SyncFieldData, field: ResourceLocation): Int? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		if (!primitive.isNumber) {
			return null
		}
		val value = primitive.asLong
		return if (value in 0L..Int.MAX_VALUE.toLong()) value.toInt() else null
	}

	private fun readBoolean(fields: SyncFieldData, field: ResourceLocation): Boolean? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		return if (primitive.isBoolean) primitive.asBoolean else null
	}
}

package com.gregtechceu.gtceu.common.machine.electric

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

/** Exposes the electric pump operation that its Kotlin action handler may invoke. */
@ApiStatus.Internal
interface PumpFluidSlotActionTarget : LDLib2FancyActionMachine {

	/** Executes one validated fluid-container click against the pump's fixed cache tank. */
	fun clickPumpFluidSlot(player: ServerPlayer, shiftDown: Boolean)
}

/** Owns the wire protocol and server handler for electric pump fluid-slot clicks. */
object PumpMachineActions {

	private val CLICK_PUMP_FLUID_SLOT_ACTION = GTCEu.id("click_pump_machine_fluid_slot")
	private val SHIFT_FIELD = SyncFieldData.key("shift")

	init {
		SyncActionDispatchers.server().register(PumpFluidSlotActionHandler)
	}

	/** Forces this object's static registration from the owning machine's class initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one client action for the electric pump's real fluid-container interaction. */
	@JvmStatic
	fun createClickPumpFluidSlotAction(shiftDown: Boolean): SyncActionData {
		val fields =
			SyncFieldData.builder()
				.put(SHIFT_FIELD, JsonPrimitive(shiftDown))
				.build()
		return SyncActionData(
			CLICK_PUMP_FLUID_SLOT_ACTION,
			if (shiftDown) 1 else 0,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private object PumpFluidSlotActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = CLICK_PUMP_FLUID_SLOT_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is PumpFluidSlotActionTarget

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return readShift(fields) != null
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			target(context).clickPumpFluidSlot(context.player, requireShift(context.payload()))
		}
	}

	private fun target(context: SyncActionContext): PumpFluidSlotActionTarget = context.holder as? PumpFluidSlotActionTarget
		?: throw IllegalStateException("Pump fluid slot action received a non-pump machine.")

	private fun requireFieldData(payload: DataComponentMap): SyncFieldData = payload[GTDataComponents.SYNC_FIELD_DATA.get()]
		?: throw IllegalStateException("Pump action payload is missing field data.")

	private fun requireShift(payload: DataComponentMap): Boolean = readShift(requireFieldData(payload))
		?: throw IllegalStateException("Pump action payload is missing $SHIFT_FIELD.")

	private fun readShift(fields: SyncFieldData): Boolean? {
		val primitive = fields[SHIFT_FIELD] as? JsonPrimitive ?: return null
		return if (primitive.isBoolean) primitive.asBoolean else null
	}
}

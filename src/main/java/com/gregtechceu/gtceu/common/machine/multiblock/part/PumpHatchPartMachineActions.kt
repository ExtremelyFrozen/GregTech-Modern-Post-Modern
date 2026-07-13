package com.gregtechceu.gtceu.common.machine.multiblock.part

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

/** Exposes the pump hatch operation that its Kotlin action handler may invoke. */
@ApiStatus.Internal
interface PumpHatchFluidSlotActionTarget : LDLib2FancyActionMachine {

	/** Executes one validated fluid-container click against the pump hatch's fixed tank. */
	fun clickPumpHatchFluidSlot(player: ServerPlayer, shiftDown: Boolean)
}

/** Owns the wire protocol and server handler for pump hatch fluid-slot clicks. */
object PumpHatchPartMachineActions {

	private val CLICK_PUMP_HATCH_FLUID_SLOT_ACTION = GTCEu.id("click_pump_hatch_fluid_slot")
	private val SHIFT_FIELD = SyncFieldData.key("shift")

	init {
		SyncActionDispatchers.server().register(PumpHatchFluidSlotActionHandler)
	}

	/** Forces this object's static registration from the owning machine's class initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one client action for the pump hatch's real fluid-container interaction. */
	@JvmStatic
	fun createClickPumpHatchFluidSlotAction(shiftDown: Boolean): SyncActionData {
		val fields =
			SyncFieldData.builder()
				.put(SHIFT_FIELD, JsonPrimitive(shiftDown))
				.build()
		return SyncActionData(
			CLICK_PUMP_HATCH_FLUID_SLOT_ACTION,
			if (shiftDown) 1 else 0,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private object PumpHatchFluidSlotActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = CLICK_PUMP_HATCH_FLUID_SLOT_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is PumpHatchFluidSlotActionTarget

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return readShift(fields) != null
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			target(context).clickPumpHatchFluidSlot(context.player, requireShift(context.payload()))
		}
	}

	private fun target(context: SyncActionContext): PumpHatchFluidSlotActionTarget = context.holder as? PumpHatchFluidSlotActionTarget
		?: throw IllegalStateException("Pump hatch fluid slot action received a non-pump-hatch machine.")

	private fun requireFieldData(payload: DataComponentMap): SyncFieldData = payload[GTDataComponents.SYNC_FIELD_DATA.get()]
		?: throw IllegalStateException("Pump hatch action payload is missing field data.")

	private fun requireShift(payload: DataComponentMap): Boolean = readShift(requireFieldData(payload))
		?: throw IllegalStateException("Pump hatch action payload is missing $SHIFT_FIELD.")

	private fun readShift(fields: SyncFieldData): Boolean? {
		val primitive = fields[SHIFT_FIELD] as? JsonPrimitive ?: return null
		return if (primitive.isBoolean) primitive.asBoolean else null
	}
}

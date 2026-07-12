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

/**
 * Exposes only the dual hatch operations that its Kotlin server action handler is allowed to invoke.
 */
@ApiStatus.Internal
interface DualHatchFluidSlotActionTarget : LDLib2FancyActionMachine {

	/** Returns the number of independently addressable fluid tanks. */
	fun getFluidTankCount(): Int

	/** Executes one validated fluid-container click against [tankIndex]. */
	fun clickFluidSlot(player: ServerPlayer, tankIndex: Int, shiftDown: Boolean)
}

/** Owns the wire protocol and server handler for dual hatch fluid-slot clicks. */
object DualHatchPartMachineActions {

	private val CLICK_DUAL_HATCH_FLUID_SLOT_ACTION = GTCEu.id("click_dual_hatch_fluid_slot")
	private val TANK_FIELD = SyncFieldData.key("tank")
	private val SHIFT_FIELD = SyncFieldData.key("shift")

	init {
		SyncActionDispatchers.server().register(DualHatchFluidSlotActionHandler)
	}

	/** Forces this object's static registration from the owning machine's class initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates the client action payload for one dual hatch fluid-slot click. */
	@JvmStatic
	fun createClickDualHatchFluidSlotAction(tankIndex: Int, shiftDown: Boolean): SyncActionData {
		val fields =
			SyncFieldData.builder()
				.put(TANK_FIELD, JsonPrimitive(tankIndex))
				.put(SHIFT_FIELD, JsonPrimitive(shiftDown))
				.build()
		val payload = fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get())
		return SyncActionData(
			CLICK_DUAL_HATCH_FLUID_SLOT_ACTION,
			tankIndex * 2 + if (shiftDown) 1 else 0,
			payload,
		)
	}

	private object DualHatchFluidSlotActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = CLICK_DUAL_HATCH_FLUID_SLOT_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is DualHatchFluidSlotActionTarget

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return readTankIndex(fields) != null && readShift(fields) != null
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean {
			if (player.isSpectator) {
				return false
			}
			val target = context.holder as DualHatchFluidSlotActionTarget
			val fields = context.payload()[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			val tankIndex = readTankIndex(fields) ?: return false
			return tankIndex < target.getFluidTankCount()
		}

		override fun execute(context: SyncActionContext) {
			val target =
				context.holder as? DualHatchFluidSlotActionTarget
					?: throw IllegalStateException(
						"Dual hatch fluid slot action received a non-dual-hatch machine.",
					)
			target.clickFluidSlot(
				context.player,
				requireTankIndex(context.payload()),
				requireShift(context.payload()),
			)
		}
	}

	private fun requireFieldData(payload: DataComponentMap): SyncFieldData = payload[GTDataComponents.SYNC_FIELD_DATA.get()]
		?: throw IllegalStateException(
			"Dual hatch fluid slot action payload is missing field data.",
		)

	private fun requireTankIndex(payload: DataComponentMap): Int = readTankIndex(requireFieldData(payload))
		?: throw IllegalStateException(
			"Dual hatch fluid slot action payload is missing $TANK_FIELD.",
		)

	private fun requireShift(payload: DataComponentMap): Boolean = readShift(requireFieldData(payload))
		?: throw IllegalStateException(
			"Dual hatch fluid slot action payload is missing $SHIFT_FIELD.",
		)

	private fun readTankIndex(fields: SyncFieldData): Int? {
		val primitive = fields[TANK_FIELD] as? JsonPrimitive ?: return null
		if (!primitive.isNumber) {
			return null
		}
		val value =
			try {
				primitive.asBigDecimal.longValueExact()
			} catch (exception: NumberFormatException) {
				logInvalidTankIndex(primitive, exception)
				return null
			} catch (exception: ArithmeticException) {
				logInvalidTankIndex(primitive, exception)
				return null
			}
		return if (value in 0L..Int.MAX_VALUE.toLong()) value.toInt() else null
	}

	private fun logInvalidTankIndex(primitive: JsonPrimitive, exception: RuntimeException) {
		GTCEu.LOGGER.warn(
			"Dual hatch fluid slot action rejected invalid tank index {}",
			primitive,
			exception,
		)
	}

	private fun readShift(fields: SyncFieldData): Boolean? {
		val primitive = fields[SHIFT_FIELD] as? JsonPrimitive ?: return null
		return if (primitive.isBoolean) primitive.asBoolean else null
	}
}

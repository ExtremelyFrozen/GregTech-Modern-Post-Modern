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
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.SimpleFluidContent

import com.google.gson.JsonPrimitive
import org.jetbrains.annotations.ApiStatus

/**
 * Exposes only standalone fluid hatch operations that its Kotlin server action handlers may invoke.
 */
@ApiStatus.Internal
interface FluidHatchFluidSlotActionTarget : LDLib2FancyActionMachine {

	/** Returns whether this definition uses the base fluid hatch LDLib2 page and action protocol. */
	fun supportsFluidHatchActions(): Boolean

	/** Returns whether this hatch exposes its single-tank locked-fluid controls. */
	fun supportsFluidHatchLocking(): Boolean

	/** Returns the number of independently addressable tanks. */
	fun getFluidHatchTankCount(): Int

	/** Executes one validated fluid-container click against [tankIndex]. */
	fun clickFluidHatchSlot(player: ServerPlayer, tankIndex: Int, shiftDown: Boolean)

	/** Applies one validated phantom locked-fluid selection. */
	fun setFluidHatchLockedFluid(fluid: FluidStack)

	/** Applies one validated lock-toggle command. */
	fun setFluidHatchLocked(locked: Boolean)
}

/** Owns the wire protocol and server handlers for the standalone fluid hatch page. */
object FluidHatchPartMachineActions {

	private val CLICK_FLUID_SLOT_ACTION = GTCEu.id("click_fluid_hatch_fluid_slot")
	private val SET_LOCKED_FLUID_ACTION = GTCEu.id("set_fluid_hatch_locked_fluid")
	private val SET_LOCKED_ACTION = GTCEu.id("set_fluid_hatch_locked")
	private val TANK_FIELD = SyncFieldData.key("tank")
	private val SHIFT_FIELD = SyncFieldData.key("shift")
	private val LOCKED_FIELD = SyncFieldData.key("locked")

	init {
		SyncActionDispatchers.server().register(FluidSlotActionHandler)
		SyncActionDispatchers.server().register(LockedFluidActionHandler)
		SyncActionDispatchers.server().register(LockedActionHandler)
	}

	/** Forces this object's static registration from the owning machine's class initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one client action for a real tank slot container interaction. */
	@JvmStatic
	fun createClickFluidSlotAction(tankIndex: Int, shiftDown: Boolean): SyncActionData {
		require(tankIndex >= 0) { "Fluid hatch tank index must be non-negative: $tankIndex" }
		val fields =
			SyncFieldData.builder()
				.put(TANK_FIELD, JsonPrimitive(tankIndex))
				.put(SHIFT_FIELD, JsonPrimitive(shiftDown))
				.build()
		return SyncActionData(
			CLICK_FLUID_SLOT_ACTION,
			tankIndex * 2 + if (shiftDown) 1 else 0,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	/** Creates one client action for a phantom locked-fluid selection. */
	@JvmStatic
	fun createSetLockedFluidAction(fluid: FluidStack): SyncActionData {
		val lockedFluid = if (fluid.isEmpty) FluidStack.EMPTY else fluid.copyWithAmount(1)
		val payload =
			DataComponentMap
				.builder()
				.set(GTDataComponents.FLUID_CONTENT.get(), SimpleFluidContent.copyOf(lockedFluid))
				.build()
		val sequence = FluidStack.hashFluidAndComponents(lockedFluid) * 31 + lockedFluid.amount
		return SyncActionData(SET_LOCKED_FLUID_ACTION, sequence, payload)
	}

	/** Creates one client action for the lock toggle. */
	@JvmStatic
	fun createSetLockedAction(locked: Boolean): SyncActionData {
		val fields =
			SyncFieldData.builder()
				.put(LOCKED_FIELD, JsonPrimitive(locked))
				.build()
		return SyncActionData(
			SET_LOCKED_ACTION,
			if (locked) 1 else 0,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private abstract class FluidHatchActionHandler : SyncActionHandler {

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is FluidHatchFluidSlotActionTarget

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator && target(context).supportsFluidHatchActions()

		protected fun target(context: SyncActionContext): FluidHatchFluidSlotActionTarget = context.holder as? FluidHatchFluidSlotActionTarget
			?: throw IllegalStateException(
				"Fluid hatch action received a non-fluid-hatch machine.",
			)
	}

	private object FluidSlotActionHandler : FluidHatchActionHandler() {

		override fun actionId(): ResourceLocation = CLICK_FLUID_SLOT_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return readTankIndex(fields) != null && readBoolean(fields, SHIFT_FIELD) != null
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean {
			if (!super.mayExecute(player, context)) {
				return false
			}
			val tankIndex = readTankIndex(requireFieldData(context.payload())) ?: return false
			return tankIndex < target(context).getFluidHatchTankCount()
		}

		override fun execute(context: SyncActionContext) {
			target(context).clickFluidHatchSlot(
				context.player,
				requireTankIndex(context.payload()),
				requireBoolean(context.payload(), SHIFT_FIELD),
			)
		}
	}

	private object LockedFluidActionHandler : FluidHatchActionHandler() {

		override fun actionId(): ResourceLocation = SET_LOCKED_FLUID_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean = payload.has(GTDataComponents.FLUID_CONTENT.get())

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = super.mayExecute(player, context) && target(context).supportsFluidHatchLocking()

		override fun execute(context: SyncActionContext) {
			target(context).setFluidHatchLockedFluid(requireFluid(context.payload()))
		}
	}

	private object LockedActionHandler : FluidHatchActionHandler() {

		override fun actionId(): ResourceLocation = SET_LOCKED_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return readBoolean(fields, LOCKED_FIELD) != null
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = super.mayExecute(player, context) && target(context).supportsFluidHatchLocking()

		override fun execute(context: SyncActionContext) {
			target(context).setFluidHatchLocked(requireBoolean(context.payload(), LOCKED_FIELD))
		}
	}

	private fun requireFieldData(payload: DataComponentMap): SyncFieldData = payload[GTDataComponents.SYNC_FIELD_DATA.get()]
		?: throw IllegalStateException("Fluid hatch action payload is missing field data.")

	private fun requireTankIndex(payload: DataComponentMap): Int = readTankIndex(requireFieldData(payload))
		?: throw IllegalStateException("Fluid hatch action payload is missing $TANK_FIELD.")

	private fun requireBoolean(payload: DataComponentMap, field: ResourceLocation): Boolean = readBoolean(requireFieldData(payload), field)
		?: throw IllegalStateException("Fluid hatch action payload is missing $field.")

	private fun requireFluid(payload: DataComponentMap): FluidStack {
		if (!payload.has(GTDataComponents.FLUID_CONTENT.get())) {
			throw IllegalStateException("Fluid hatch action payload is missing fluid content.")
		}
		return payload.getOrDefault(GTDataComponents.FLUID_CONTENT.get(), SimpleFluidContent.EMPTY).copy()
	}

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
			"Fluid hatch action rejected invalid tank index {}",
			primitive,
			exception,
		)
	}

	private fun readBoolean(fields: SyncFieldData, field: ResourceLocation): Boolean? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		return if (primitive.isBoolean) primitive.asBoolean else null
	}
}

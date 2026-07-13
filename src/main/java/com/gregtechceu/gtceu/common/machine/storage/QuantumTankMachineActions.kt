package com.gregtechceu.gtceu.common.machine.storage

import com.gregtechceu.gtceu.GTCEu
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
import net.neoforged.neoforge.fluids.FluidType
import net.neoforged.neoforge.fluids.SimpleFluidContent

import com.google.gson.JsonPrimitive
import org.jetbrains.annotations.ApiStatus

/** Exposes the three quantum tank UI operations that their Kotlin action handlers may invoke. */
@ApiStatus.Internal
interface QuantumTankActionTarget {

	/** Executes one validated fluid-container click against the quantum tank's only fluid slot. */
	fun clickQuantumTankFluidSlot(player: ServerPlayer, shiftDown: Boolean)

	/** Applies one validated phantom locked-fluid selection. */
	fun setQuantumTankLockedFluid(fluid: FluidStack)

	/** Applies one validated lock-toggle command. */
	fun setQuantumTankLocked(locked: Boolean)
}

/** Owns the wire protocol and server handlers for the quantum tank UI state. */
object QuantumTankMachineActions {

	private val CLICK_QUANTUM_TANK_FLUID_SLOT_ACTION = GTCEu.id("click_quantum_tank_fluid_slot")
	private val SET_QUANTUM_TANK_LOCKED_FLUID_ACTION = GTCEu.id("set_quantum_tank_locked_fluid")
	private val SET_QUANTUM_TANK_LOCKED_ACTION = GTCEu.id("set_quantum_tank_locked")
	private val SHIFT_FIELD = SyncFieldData.key("shift")
	private val LOCKED_FIELD = SyncFieldData.key("locked")

	init {
		SyncActionDispatchers.server().register(QuantumTankFluidSlotActionHandler)
		SyncActionDispatchers.server().register(QuantumTankLockedFluidActionHandler)
		SyncActionDispatchers.server().register(QuantumTankLockedActionHandler)
	}

	/** Forces this object's static registration from the owning machine's class initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one client action for a real quantum tank fluid-container interaction. */
	@JvmStatic
	fun createClickQuantumTankFluidSlotAction(shiftDown: Boolean): SyncActionData {
		val fields =
			SyncFieldData.builder()
				.put(SHIFT_FIELD, JsonPrimitive(shiftDown))
				.build()
		return SyncActionData(
			CLICK_QUANTUM_TANK_FLUID_SLOT_ACTION,
			if (shiftDown) 1 else 0,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	/** Creates one client action for a phantom locked-fluid selection. */
	@JvmStatic
	fun createSetQuantumTankLockedFluidAction(fluid: FluidStack): SyncActionData {
		val locked = if (fluid.isEmpty) FluidStack.EMPTY else fluid.copyWithAmount(FluidType.BUCKET_VOLUME)
		val payload =
			DataComponentMap
				.builder()
				.set(GTDataComponents.FLUID_CONTENT.get(), SimpleFluidContent.copyOf(locked))
				.build()
		val sequence = FluidStack.hashFluidAndComponents(locked) * 31 + locked.amount
		return SyncActionData(SET_QUANTUM_TANK_LOCKED_FLUID_ACTION, sequence, payload)
	}

	/** Creates one client action for the lock toggle. */
	@JvmStatic
	fun createSetQuantumTankLockedAction(locked: Boolean): SyncActionData {
		val fields =
			SyncFieldData.builder()
				.put(LOCKED_FIELD, JsonPrimitive(locked))
				.build()
		return SyncActionData(
			SET_QUANTUM_TANK_LOCKED_ACTION,
			if (locked) 1 else 0,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private abstract class QuantumTankActionHandler : SyncActionHandler {

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is QuantumTankActionTarget

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		protected fun target(context: SyncActionContext): QuantumTankActionTarget = context.holder as? QuantumTankActionTarget
			?: throw IllegalStateException("Quantum tank action received a non-quantum-tank machine.")
	}

	private object QuantumTankFluidSlotActionHandler : QuantumTankActionHandler() {

		override fun actionId(): ResourceLocation = CLICK_QUANTUM_TANK_FLUID_SLOT_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return readBoolean(fields, SHIFT_FIELD) != null
		}

		override fun execute(context: SyncActionContext) {
			target(context).clickQuantumTankFluidSlot(
				context.player,
				requireBoolean(context.payload(), SHIFT_FIELD),
			)
		}
	}

	private object QuantumTankLockedFluidActionHandler : QuantumTankActionHandler() {

		override fun actionId(): ResourceLocation = SET_QUANTUM_TANK_LOCKED_FLUID_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean = payload.has(GTDataComponents.FLUID_CONTENT.get())

		override fun execute(context: SyncActionContext) {
			target(context).setQuantumTankLockedFluid(requireFluidStack(context.payload()))
		}
	}

	private object QuantumTankLockedActionHandler : QuantumTankActionHandler() {

		override fun actionId(): ResourceLocation = SET_QUANTUM_TANK_LOCKED_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return readBoolean(fields, LOCKED_FIELD) != null
		}

		override fun execute(context: SyncActionContext) {
			target(context).setQuantumTankLocked(requireBoolean(context.payload(), LOCKED_FIELD))
		}
	}

	private fun requireFieldData(payload: DataComponentMap): SyncFieldData = payload[GTDataComponents.SYNC_FIELD_DATA.get()]
		?: throw IllegalStateException("Quantum tank action payload is missing field data.")

	private fun requireFluidStack(payload: DataComponentMap): FluidStack {
		if (!payload.has(GTDataComponents.FLUID_CONTENT.get())) {
			throw IllegalStateException("Quantum tank fluid action payload is missing fluid stack.")
		}
		return payload.getOrDefault(GTDataComponents.FLUID_CONTENT.get(), SimpleFluidContent.EMPTY).copy()
	}

	private fun requireBoolean(payload: DataComponentMap, field: ResourceLocation): Boolean = readBoolean(requireFieldData(payload), field)
		?: throw IllegalStateException("Quantum tank action payload is missing $field.")

	private fun readBoolean(fields: SyncFieldData, field: ResourceLocation): Boolean? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		return if (primitive.isBoolean) primitive.asBoolean else null
	}
}

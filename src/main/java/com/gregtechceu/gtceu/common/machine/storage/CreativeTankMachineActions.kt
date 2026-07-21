package com.gregtechceu.gtceu.common.machine.storage

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.FluidType
import net.neoforged.neoforge.fluids.SimpleFluidContent

import org.jetbrains.annotations.ApiStatus

/** Exposes only the creative tank mutation that its Kotlin server action handler may invoke. */
@ApiStatus.Internal
interface CreativeTankFluidActionTarget {

	/** Applies one validated phantom fluid selection, normalizing its stored amount. */
	fun setCreativeTankFluid(fluid: FluidStack)
}

/** Owns the wire protocol and server handler for the creative tank phantom fluid slot. */
object CreativeTankMachineActions {

	private val SET_CREATIVE_TANK_FLUID_ACTION = GTCEu.id("set_creative_tank_fluid")

	init {
		SyncActionDispatchers.server().register(CreativeTankFluidActionHandler)
	}

	/** Forces this object's static registration from the owning machine's class initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one client action for a creative tank phantom fluid selection. */
	@JvmStatic
	fun createSetFluidAction(fluid: FluidStack): SyncActionData {
		val storedFluid = if (fluid.isEmpty) FluidStack.EMPTY else fluid.copyWithAmount(FluidType.BUCKET_VOLUME)
		val payload =
			DataComponentMap
				.builder()
				.set(GTDataComponents.FLUID_CONTENT.get(), SimpleFluidContent.copyOf(storedFluid))
				.build()
		val sequence = FluidStack.hashFluidAndComponents(storedFluid) * 31 + storedFluid.amount
		return SyncActionData(SET_CREATIVE_TANK_FLUID_ACTION, sequence, payload)
	}

	private object CreativeTankFluidActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_CREATIVE_TANK_FLUID_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is CreativeTankFluidActionTarget

		override fun acceptsPayload(payload: DataComponentMap): Boolean = payload.has(GTDataComponents.FLUID_CONTENT.get())

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			target(context).setCreativeTankFluid(requireFluidStack(context.payload()))
		}
	}

	private fun target(context: SyncActionContext): CreativeTankFluidActionTarget = context.holder as? CreativeTankFluidActionTarget
		?: throw IllegalStateException("Creative tank action received a non-creative-tank target.")

	private fun requireFluidStack(payload: DataComponentMap): FluidStack = payload[GTDataComponents.FLUID_CONTENT.get()]?.copy()
		?: throw IllegalStateException("Creative tank fluid action payload is missing fluid stack.")
}

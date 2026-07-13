package com.gregtechceu.gtceu.common.cover.voiding

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

import org.jetbrains.annotations.ApiStatus

/** Exposes only the working-enabled mutation that the fluid voiding action handler may invoke. */
@ApiStatus.Internal
interface FluidVoidingWorkingEnabledActionTarget {

	/** Applies one validated working-enabled state through the cover's normal setter. */
	fun setWorkingEnabled(isWorkingAllowed: Boolean)
}

/** Owns the wire protocol and server handler for fluid voiding cover working-enabled actions. */
object FluidVoidingCoverConfigActions {

	private val SET_FLUID_VOIDING_COVER_CONFIG_ACTION = GTCEu.id("set_fluid_voiding_cover_config")

	init {
		SyncActionDispatchers.server().register(FluidVoidingCoverConfigActionHandler)
	}

	/** Forces common-side handler registration when the owning cover class initializes. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one request to change a fluid voiding cover's working-enabled state. */
	@JvmStatic
	fun createSetWorkingEnabledAction(workingEnabled: Boolean): SyncActionData = VoidingWorkingEnabledActionProtocol.createAction(
		SET_FLUID_VOIDING_COVER_CONFIG_ACTION,
		workingEnabled,
	)

	private object FluidVoidingCoverConfigActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_FLUID_VOIDING_COVER_CONFIG_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is FluidVoidingCover

		override fun acceptsPayload(payload: DataComponentMap): Boolean = VoidingWorkingEnabledActionProtocol.read(payload) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			val target =
				context.holder as? FluidVoidingWorkingEnabledActionTarget
					?: throw IllegalStateException(
						"Fluid voiding cover config action received a non-fluid-voiding cover.",
					)
			target.setWorkingEnabled(VoidingWorkingEnabledActionProtocol.require(context.payload()))
		}
	}
}

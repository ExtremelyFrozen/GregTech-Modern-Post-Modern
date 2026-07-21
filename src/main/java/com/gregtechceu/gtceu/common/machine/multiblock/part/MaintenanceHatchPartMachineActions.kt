package com.gregtechceu.gtceu.common.machine.multiblock.part

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

import com.google.gson.JsonPrimitive
import org.jetbrains.annotations.ApiStatus

/**
 * Exposes only the maintenance hatch operations that its Kotlin UI action handlers may invoke.
 *
 * This contract keeps holder authorization and the action protocol independent from the concrete hatch while
 * preserving the existing Java implementation of duration adjustment and player-driven repairs.
 */
@ApiStatus.Internal
interface MaintenanceHatchActionTarget {

	/** Returns whether this holder exposes the configurable duration controls. */
	fun supportsMaintenanceDurationAdjustment(): Boolean

	/** Applies one validated `-1` or `1` duration step through the holder's existing clamp logic. */
	fun adjustMaintenanceDuration(direction: Int)

	/** Runs the existing creative, duct-tape, and tool repair sequence for [player]. */
	fun fixMaintenanceProblemsForAction(player: ServerPlayer)
}

/** Owns the wire protocols and server handlers for maintenance hatch duration and repair controls. */
object MaintenanceHatchPartMachineActions {

	private val ADJUST_MAINTENANCE_DURATION_ACTION = GTCEu.id("adjust_maintenance_duration_multiplier")
	private val FIX_MAINTENANCE_PROBLEMS_ACTION = GTCEu.id("fix_maintenance_problems")
	private val DURATION_DIRECTION_FIELD = SyncFieldData.key("direction")

	init {
		SyncActionDispatchers.server().register(MaintenanceDurationActionHandler)
		SyncActionDispatchers.server().register(MaintenanceFixActionHandler)
	}

	/** Forces this object's static registration from the owning machine's class initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one client action for a maintenance duration decrement or increment. */
	@JvmStatic
	fun createAdjustMaintenanceDurationAction(direction: Int): SyncActionData {
		val fields =
			SyncFieldData.builder()
				.put(DURATION_DIRECTION_FIELD, JsonPrimitive(direction))
				.build()
		return SyncActionData(
			ADJUST_MAINTENANCE_DURATION_ACTION,
			if (direction > 0) 1 else 0,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	/** Creates one client action for the existing player-driven maintenance repair sequence. */
	@JvmStatic
	fun createFixMaintenanceProblemsAction(): SyncActionData = SyncActionData(FIX_MAINTENANCE_PROBLEMS_ACTION, 0, DataComponentMap.EMPTY)

	private object MaintenanceDurationActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = ADJUST_MAINTENANCE_DURATION_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean {
			val target = context.holder as? MaintenanceHatchActionTarget ?: return false
			return target.supportsMaintenanceDurationAdjustment()
		}

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return readDirection(fields) != null
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			durationTarget(context).adjustMaintenanceDuration(requireDirection(context.payload()))
		}
	}

	private object MaintenanceFixActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = FIX_MAINTENANCE_PROBLEMS_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is MaintenanceHatchActionTarget

		override fun acceptsPayload(payload: DataComponentMap): Boolean = payload.isEmpty

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			fixTarget(context).fixMaintenanceProblemsForAction(context.player)
		}
	}

	private fun durationTarget(context: SyncActionContext): MaintenanceHatchActionTarget = context.holder as? MaintenanceHatchActionTarget
		?: throw IllegalStateException(
			"Maintenance duration action received a non-maintenance-hatch machine.",
		)

	private fun fixTarget(context: SyncActionContext): MaintenanceHatchActionTarget = context.holder as? MaintenanceHatchActionTarget
		?: throw IllegalStateException("Maintenance fix action received a non-maintenance-hatch machine.")

	private fun requireFieldData(payload: DataComponentMap): SyncFieldData = payload[GTDataComponents.SYNC_FIELD_DATA.get()]
		?: throw IllegalStateException("Maintenance duration action payload is missing field data.")

	private fun requireDirection(payload: DataComponentMap): Int = readDirection(requireFieldData(payload))
		?: throw IllegalStateException(
			"Maintenance duration action payload is missing $DURATION_DIRECTION_FIELD.",
		)

	private fun readDirection(fields: SyncFieldData): Int? {
		val primitive = fields[DURATION_DIRECTION_FIELD] as? JsonPrimitive ?: return null
		if (!primitive.isNumber) {
			return null
		}
		val direction = primitive.asInt
		return if (direction == -1 || direction == 1) direction else null
	}
}

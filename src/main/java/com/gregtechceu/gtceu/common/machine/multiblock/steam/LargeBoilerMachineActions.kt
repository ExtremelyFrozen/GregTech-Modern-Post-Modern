package com.gregtechceu.gtceu.common.machine.multiblock.steam

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

/** Exposes only the validated throttle mutation used by the large boiler action handler. */
@ApiStatus.Internal
interface LargeBoilerThrottleActionTarget {

	/** Applies one validated decrement or increment and updates the active fuel burn time. */
	fun adjustLargeBoilerThrottle(direction: Int)
}

/** Owns the wire protocol and server handler for large boiler throttle buttons. */
object LargeBoilerMachineActions {

	private val ADJUST_LARGE_BOILER_THROTTLE_ACTION = GTCEu.id("adjust_large_boiler_throttle")
	private val THROTTLE_DIRECTION_FIELD = SyncFieldData.key("direction")

	init {
		SyncActionDispatchers.server().register(LargeBoilerThrottleActionHandler)
	}

	/** Forces common-side handler registration when the owning machine class initializes. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one client request for a validated five-percent throttle step. */
	@JvmStatic
	fun createAdjustLargeBoilerThrottleAction(direction: Int): SyncActionData {
		require(isThrottleDirection(direction)) {
			"Large boiler throttle direction must be -1 or 1: $direction"
		}
		val fields =
			SyncFieldData
				.builder()
				.put(THROTTLE_DIRECTION_FIELD, JsonPrimitive(direction))
				.build()
		return SyncActionData(
			ADJUST_LARGE_BOILER_THROTTLE_ACTION,
			if (direction > 0) 1 else 0,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private object LargeBoilerThrottleActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = ADJUST_LARGE_BOILER_THROTTLE_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is LargeBoilerMachine

		override fun acceptsPayload(payload: DataComponentMap): Boolean = readThrottleDirection(payload) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			target(context).adjustLargeBoilerThrottle(requireThrottleDirection(context.payload()))
		}
	}

	private fun target(context: SyncActionContext): LargeBoilerThrottleActionTarget = context.holder as? LargeBoilerThrottleActionTarget
		?: throw IllegalStateException(
			"Large boiler throttle action received a non-large-boiler machine.",
		)

	private fun requireThrottleDirection(payload: DataComponentMap): Int = readThrottleDirection(payload)
		?: throw IllegalStateException(
			"Large boiler throttle action payload is missing or invalid for $THROTTLE_DIRECTION_FIELD.",
		)

	private fun readThrottleDirection(payload: DataComponentMap): Int? {
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		val primitive = fields[THROTTLE_DIRECTION_FIELD] as? JsonPrimitive ?: return null
		if (!primitive.isNumber) {
			return null
		}
		return primitive.asInt.takeIf(::isThrottleDirection)
	}

	private fun isThrottleDirection(direction: Int): Boolean = direction == -1 || direction == 1
}

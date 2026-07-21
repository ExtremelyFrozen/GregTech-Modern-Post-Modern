package com.gregtechceu.gtceu.common.machine.multiblock.electric

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

import org.jetbrains.annotations.ApiStatus

/** Exposes only the two idle-only Large Miner mode mutations used by its UI action handlers. */
@ApiStatus.Internal
interface LargeMinerModeActionTarget {

	/** Returns whether live mining currently blocks both mode changes. */
	fun isLargeMinerModeChangeBlocked(): Boolean

	/** Toggles silk touch through the existing Large Miner logic restriction. */
	fun toggleLargeMinerSilkTouch()

	/** Toggles chunk mode through the existing Large Miner logic and its single area reset. */
	fun toggleLargeMinerChunkMode()
}

/** Owns the empty-payload GT actions for the two clickable Large Miner display modes. */
object LargeMinerMachineActions {

	private val TOGGLE_SILK_TOUCH_ACTION = GTCEu.id("toggle_large_miner_silk_touch")
	private val TOGGLE_CHUNK_MODE_ACTION = GTCEu.id("toggle_large_miner_chunk_mode")

	init {
		SyncActionDispatchers.server().register(ToggleSilkTouchActionHandler)
		SyncActionDispatchers.server().register(ToggleChunkModeActionHandler)
	}

	/** Forces common-side handler registration when the owning machine class initializes. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one holder-scoped request to toggle silk touch while the miner is idle. */
	@JvmStatic
	fun createToggleSilkTouchAction(): SyncActionData = SyncActionData(TOGGLE_SILK_TOUCH_ACTION, 0, DataComponentMap.EMPTY)

	/** Creates one holder-scoped request to toggle chunk mode while the miner is idle. */
	@JvmStatic
	fun createToggleChunkModeAction(): SyncActionData = SyncActionData(TOGGLE_CHUNK_MODE_ACTION, 0, DataComponentMap.EMPTY)

	private object ToggleSilkTouchActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = TOGGLE_SILK_TOUCH_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is LargeMinerMachine

		override fun acceptsPayload(payload: DataComponentMap): Boolean = payload.isEmpty

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator && !target(context).isLargeMinerModeChangeBlocked()

		override fun execute(context: SyncActionContext) {
			target(context).toggleLargeMinerSilkTouch()
		}
	}

	private object ToggleChunkModeActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = TOGGLE_CHUNK_MODE_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is LargeMinerMachine

		override fun acceptsPayload(payload: DataComponentMap): Boolean = payload.isEmpty

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator && !target(context).isLargeMinerModeChangeBlocked()

		override fun execute(context: SyncActionContext) {
			target(context).toggleLargeMinerChunkMode()
		}
	}

	private fun target(context: SyncActionContext): LargeMinerModeActionTarget = context.holder as? LargeMinerModeActionTarget
		?: throw IllegalStateException("Large Miner mode action received a non-Large-Miner machine.")
}

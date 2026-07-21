package com.gregtechceu.gtceu.common.cover

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.common.cover.data.TransferMode

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

/** Owns the wire action and server handler used by the robot arm configuration UI. */
object RobotArmCoverConfigActions {

	private val SET_ROBOT_ARM_COVER_CONFIG_ACTION = GTCEu.id("set_robot_arm_cover_config")
	private val TRANSFER_MODES = TransferMode.values()
	private val PROTOCOL_SCHEMA = RegulatedTransferConfigActionProtocol.Schema(
		transferModeValueCount = TRANSFER_MODES.size,
		minimumTransferLimit = 1,
	)

	init {
		SyncActionDispatchers.server().register(RobotArmCoverConfigActionHandler)
	}

	/** Forces common-side handler registration when the owning cover class initializes. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one complete client request from the current robot arm state. */
	@JvmStatic
	fun createSetConfigAction(transferMode: TransferMode, transferLimit: Int): SyncActionData = RegulatedTransferConfigActionProtocol.createAction(
		SET_ROBOT_ARM_COVER_CONFIG_ACTION,
		PROTOCOL_SCHEMA,
		transferMode.ordinal,
		transferLimit,
	)

	private object RobotArmCoverConfigActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_ROBOT_ARM_COVER_CONFIG_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is RobotArmCover

		override fun acceptsPayload(payload: DataComponentMap): Boolean = RegulatedTransferConfigActionProtocol.read(payload, PROTOCOL_SCHEMA) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			val cover = context.holder as? RobotArmCover
				?: throw IllegalStateException("Robot arm cover config action received a non-robot-arm cover.")
			val config = RegulatedTransferConfigActionProtocol.requireConfig(context.payload(), PROTOCOL_SCHEMA)
			cover.setTransferMode(TRANSFER_MODES[config.transferModeOrdinal])
			cover.setGlobalTransferLimit(config.transferLimit)
		}
	}
}

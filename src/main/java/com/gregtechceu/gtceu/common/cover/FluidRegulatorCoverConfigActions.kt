package com.gregtechceu.gtceu.common.cover

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.common.cover.data.BucketMode
import com.gregtechceu.gtceu.common.cover.data.TransferMode

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

import org.jetbrains.annotations.ApiStatus

/** Exposes only the ordered fluid regulator mutations that its Kotlin config action handler may invoke. */
@ApiStatus.Internal
interface FluidRegulatorCoverConfigActionTarget {

	/** Applies the validated transfer mode before values whose UI and normalization depend on that mode. */
	fun setTransferMode(transferMode: TransferMode)

	/** Applies the validated non-negative transfer limit in the regulator's base milli-bucket unit. */
	fun setGlobalTransferLimit(transferLimit: Int)

	/** Applies the validated display bucket mode after the base-unit transfer limit has been stored. */
	fun setTransferBucketMode(transferBucketMode: BucketMode)
}

/** Owns the wire action and server handler used by the fluid regulator configuration UI. */
object FluidRegulatorCoverConfigActions {

	private val SET_FLUID_REGULATOR_COVER_CONFIG_ACTION = GTCEu.id("set_fluid_regulator_cover_config")
	private val TRANSFER_MODES = TransferMode.entries
	private val TRANSFER_BUCKET_MODES = BucketMode.entries
	private val PROTOCOL_SCHEMA =
		RegulatedTransferConfigActionProtocol.Schema(
			transferModeValueCount = TRANSFER_MODES.size,
			minimumTransferLimit = 0,
			transferBucketValueCount = TRANSFER_BUCKET_MODES.size,
		)

	init {
		SyncActionDispatchers.server().register(FluidRegulatorCoverConfigActionHandler)
	}

	/** Forces common-side handler registration when the owning cover class initializes. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one complete client request from the current fluid regulator state. */
	@JvmStatic
	fun createSetConfigAction(transferMode: TransferMode, transferLimit: Int, transferBucketMode: BucketMode): SyncActionData = RegulatedTransferConfigActionProtocol.createAction(
		SET_FLUID_REGULATOR_COVER_CONFIG_ACTION,
		PROTOCOL_SCHEMA,
		transferMode.ordinal,
		transferLimit,
		transferBucketMode.ordinal,
	)

	private object FluidRegulatorCoverConfigActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_FLUID_REGULATOR_COVER_CONFIG_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is FluidRegulatorCoverConfigActionTarget

		override fun acceptsPayload(payload: DataComponentMap): Boolean = RegulatedTransferConfigActionProtocol.read(payload, PROTOCOL_SCHEMA) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			val target = context.holder as? FluidRegulatorCoverConfigActionTarget
				?: throw IllegalStateException(
					"Fluid regulator cover config action received a non-fluid-regulator target.",
				)
			val config = RegulatedTransferConfigActionProtocol.requireConfig(context.payload(), PROTOCOL_SCHEMA)
			val transferBucketOrdinal = config.transferBucketOrdinal
				?: throw IllegalStateException(
					"Fluid regulator cover config action schema omitted the transfer bucket.",
				)
			target.setTransferMode(TRANSFER_MODES[config.transferModeOrdinal])
			target.setGlobalTransferLimit(config.transferLimit)
			target.setTransferBucketMode(TRANSFER_BUCKET_MODES[transferBucketOrdinal])
		}
	}
}

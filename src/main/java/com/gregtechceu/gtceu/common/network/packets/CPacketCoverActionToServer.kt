package com.gregtechceu.gtceu.common.network.packets

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper
import com.gregtechceu.gtceu.api.cover.CoverBehavior
import com.gregtechceu.gtceu.api.gui.factory.GTCoverUIContainerMenu
import com.gregtechceu.gtceu.api.machine.MetaMachine
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.neoforged.neoforge.network.handling.IPayloadContext

import java.util.UUID

open class CPacketCoverActionToServer(
	private val pos: BlockPos,
	private val side: Direction,
	private val coverDefinitionId: ResourceLocation,
	private val actionSessionId: UUID,
	private val action: SyncActionData,
) : CustomPacketPayload {

	constructor(buffer: RegistryFriendlyByteBuf) : this(
		buffer.readBlockPos(),
		buffer.readEnum(Direction::class.java),
		buffer.readResourceLocation(),
		buffer.readUUID(),
		SyncActionData.STREAM_CODEC.decode(buffer),
	)

	open fun encode(buffer: RegistryFriendlyByteBuf) {
		buffer.writeBlockPos(pos)
		buffer.writeEnum(side)
		buffer.writeResourceLocation(coverDefinitionId)
		buffer.writeUUID(actionSessionId)
		SyncActionData.STREAM_CODEC.encode(buffer, action)
	}

	open fun execute(context: IPayloadContext) {
		val player = context.player()
		if (player !is ServerPlayer) {
			GTCEu.LOGGER.warn("Sync action: rejecting cover action {} without server player", action.actionId)
			return
		}

		val menu = player.containerMenu
		if (menu !is GTCoverUIContainerMenu) {
			GTCEu.LOGGER.warn(
				"Sync action: rejecting cover action {} from {} because no cover UI menu is open",
				action.actionId,
				player.gameProfile.name,
			)
			return
		}
		val interactionAnchor = try {
			menu.getInteractionAnchorForAction(player, pos, side, coverDefinitionId, actionSessionId)
		} catch (exception: RuntimeException) {
			GTCEu.LOGGER.error(
				"Sync action: failed to validate cover action {} from {} for {} {} {}",
				action.actionId,
				player.gameProfile.name,
				pos,
				side,
				coverDefinitionId,
				exception,
			)
			return
		}
		if (interactionAnchor == null) {
			GTCEu.LOGGER.warn(
				"Sync action: rejecting cover action {} from {} because the active cover UI session or interaction anchor for {} {} {} is invalid",
				action.actionId,
				player.gameProfile.name,
				pos,
				side,
				coverDefinitionId,
			)
			return
		}

		val level: Level = player.level()
		if (!level.isLoaded(pos)) {
			GTCEu.LOGGER.warn(
				"Sync action: rejecting cover action {} from {} because {} is not loaded",
				action.actionId,
				player.gameProfile.name,
				pos,
			)
			return
		}

		if (!level.isLoaded(interactionAnchor)) {
			GTCEu.LOGGER.warn(
				"Sync action: rejecting cover action {} from {} because interaction anchor {} is not loaded",
				action.actionId,
				player.gameProfile.name,
				interactionAnchor,
			)
			return
		}

		if (!canInteract(player, interactionAnchor)) {
			GTCEu.LOGGER.warn(
				"Sync action: rejecting cover action {} from {} because interaction at anchor {} is not allowed",
				action.actionId,
				player.gameProfile.name,
				interactionAnchor,
			)
			return
		}

		val coverable = GTCapabilityHelper.getCoverable(level, pos, side)
		if (coverable == null) {
			GTCEu.LOGGER.warn(
				"Sync action: rejecting cover action {} from {} because cover holder at {} is invalid",
				action.actionId,
				player.gameProfile.name,
				pos,
			)
			return
		}

		val cover: CoverBehavior? = coverable.getCoverAtSide(side)
		if (cover == null) {
			GTCEu.LOGGER.warn(
				"Sync action: rejecting cover action {} from {} because side {} has no cover",
				action.actionId,
				player.gameProfile.name,
				side,
			)
			return
		}

		if (!cover.coverDefinition.getId().equals(coverDefinitionId)) {
			GTCEu.LOGGER.warn(
				"Sync action: rejecting cover action {} from {} because cover at {} {} changed",
				action.actionId,
				player.gameProfile.name,
				pos,
				side,
			)
			return
		}

		val machine: MetaMachine? = MetaMachine.getMachine(level, pos)
		if (machine != null && !MachineOwner.canOpenOwnerMachine(player, machine)) {
			GTCEu.LOGGER.warn(
				"Sync action: rejecting cover action {} from {} because owner permission failed",
				action.actionId,
				player.gameProfile.name,
			)
			return
		}

		if (!menu.matchesActionSession(player, pos, side, coverDefinitionId, actionSessionId)) {
			GTCEu.LOGGER.warn(
				"Sync action: rejecting cover action {} from {} because the cover UI session or interaction anchor became invalid",
				action.actionId,
				player.gameProfile.name,
			)
			return
		}

		SyncActionDispatchers.server().dispatch(SyncActionContext.cover(player, cover, action, pos, side))
	}

	private fun canInteract(player: ServerPlayer, pos: BlockPos): Boolean {
		if (player.isSpectator) {
			return false
		}
		val additionalDistance = MAX_INTERACTION_DISTANCE - player.blockInteractionRange()
		return player.canInteractWithBlock(pos, additionalDistance)
	}

	override fun type(): Type<CPacketCoverActionToServer> = TYPE

	companion object {
		@JvmField
		val ID: ResourceLocation = GTCEu.id("cover_action_to_server")

		@JvmField
		val TYPE: Type<CPacketCoverActionToServer> = Type(ID)

		@JvmField
		val CODEC: StreamCodec<RegistryFriendlyByteBuf, CPacketCoverActionToServer> =
			StreamCodec.ofMember(CPacketCoverActionToServer::encode, ::CPacketCoverActionToServer)

		private const val MAX_INTERACTION_DISTANCE = 8.0
	}
}

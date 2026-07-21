package com.gregtechceu.gtceu.common.network.packets

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.machine.MetaMachine
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.managed.ManagedSyncBlockEntity
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner

import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.neoforge.network.handling.IPayloadContext

open class CPacketMachineActionToServer(private val pos: BlockPos, private val machineDefinitionId: ResourceLocation, private val action: SyncActionData) : CustomPacketPayload {

	constructor(buffer: RegistryFriendlyByteBuf) : this(
		buffer.readBlockPos(),
		buffer.readResourceLocation(),
		SyncActionData.STREAM_CODEC.decode(buffer),
	)

	open fun encode(buffer: RegistryFriendlyByteBuf) {
		buffer.writeBlockPos(pos)
		buffer.writeResourceLocation(machineDefinitionId)
		SyncActionData.STREAM_CODEC.encode(buffer, action)
	}

	open fun execute(context: IPayloadContext) {
		val player = context.player()
		if (player !is ServerPlayer) {
			GTCEu.LOGGER.warn("Sync action: rejecting machine action {} without server player", action.actionId)
			return
		}

		val level: Level = player.level()
		if (!level.isLoaded(pos)) {
			GTCEu.LOGGER.warn(
				"Sync action: rejecting machine action {} from {} because {} is not loaded",
				action.actionId,
				player.gameProfile.name,
				pos,
			)
			return
		}

		if (!canInteract(player, pos)) {
			GTCEu.LOGGER.warn(
				"Sync action: rejecting machine action {} from {} because interaction is not allowed",
				action.actionId,
				player.gameProfile.name,
			)
			return
		}

		val blockEntity: BlockEntity? = level.getBlockEntity(pos)
		if (blockEntity !is ManagedSyncBlockEntity) {
			GTCEu.LOGGER.warn(
				"Sync action: rejecting machine action {} from {} because holder at {} is invalid",
				action.actionId,
				player.gameProfile.name,
				pos,
			)
			return
		}

		if (blockEntity is MetaMachine) {
			if (!blockEntity.definition.getId().equals(machineDefinitionId)) {
				GTCEu.LOGGER.warn(
					"Sync action: rejecting machine action {} from {} because machine at {} changed",
					action.actionId,
					player.gameProfile.name,
					pos,
				)
				return
			}

			if (!MachineOwner.canOpenOwnerMachine(player, blockEntity)) {
				GTCEu.LOGGER.warn(
					"Sync action: rejecting machine action {} from {} because owner permission failed",
					action.actionId,
					player.gameProfile.name,
				)
				return
			}
		}

		SyncActionDispatchers.server().dispatch(SyncActionContext.machine(player, blockEntity, action, pos))
	}

	private fun canInteract(player: ServerPlayer, pos: BlockPos): Boolean = !player.isSpectator && player.canInteractWithBlock(pos, MAX_INTERACTION_DISTANCE)

	override fun type(): Type<CPacketMachineActionToServer> = TYPE

	companion object {
		@JvmField
		val ID: ResourceLocation = GTCEu.id("machine_action_to_server")

		@JvmField
		val TYPE: Type<CPacketMachineActionToServer> = Type(ID)

		@JvmField
		val CODEC: StreamCodec<RegistryFriendlyByteBuf, CPacketMachineActionToServer> =
			StreamCodec.ofMember(CPacketMachineActionToServer::encode, ::CPacketMachineActionToServer)

		private const val MAX_INTERACTION_DISTANCE = 8.0
	}
}

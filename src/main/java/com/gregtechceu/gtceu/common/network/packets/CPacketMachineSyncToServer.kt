package com.gregtechceu.gtceu.common.network.packets

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.machine.MetaMachine
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.api.sync_system.managed.ManagedSyncBlockEntity
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner

import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.neoforge.network.handling.IPayloadContext

open class CPacketMachineSyncToServer(private val pos: BlockPos, private val blockEntityTypeId: ResourceLocation, private val data: DataComponentMap) : CustomPacketPayload {

	constructor(buffer: RegistryFriendlyByteBuf) : this(
		buffer.readBlockPos(),
		buffer.readResourceLocation(),
		SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.decode(buffer),
	)

	open fun encode(buffer: RegistryFriendlyByteBuf) {
		buffer.writeBlockPos(pos)
		buffer.writeResourceLocation(blockEntityTypeId)
		SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.encode(buffer, data)
	}

	open fun execute(context: IPayloadContext) {
		val player = context.player()
		if (player !is ServerPlayer) {
			GTCEu.LOGGER.warn("Sync: rejecting block entity field update without server player")
			return
		}

		if (data.isEmpty) {
			GTCEu.LOGGER.warn(
				"Sync: rejecting block entity field update from {} because payload is empty",
				player.gameProfile.name,
			)
			return
		}

		val level: Level = player.level()
		if (!level.isLoaded(pos)) {
			GTCEu.LOGGER.warn(
				"Sync: rejecting block entity field update from {} because {} is not loaded",
				player.gameProfile.name,
				pos,
			)
			return
		}

		if (!canInteract(player, pos)) {
			GTCEu.LOGGER.warn(
				"Sync: rejecting block entity field update from {} because interaction is not allowed",
				player.gameProfile.name,
			)
			return
		}

		val blockEntity: BlockEntity? = level.getBlockEntity(pos)
		if (blockEntity !is ManagedSyncBlockEntity) {
			GTCEu.LOGGER.warn(
				"Sync: rejecting block entity field update from {} because holder at {} is invalid",
				player.gameProfile.name,
				pos,
			)
			return
		}

		val currentBlockEntityTypeId: ResourceLocation = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.type)!!
		if (!currentBlockEntityTypeId.equals(blockEntityTypeId)) {
			GTCEu.LOGGER.warn(
				"Sync: rejecting block entity field update from {} because holder at {} changed",
				player.gameProfile.name,
				pos,
			)
			return
		}

		if (blockEntity is MetaMachine && !MachineOwner.canOpenOwnerMachine(player, blockEntity)) {
			GTCEu.LOGGER.warn(
				"Sync: rejecting block entity field update from {} because owner permission failed",
				player.gameProfile.name,
			)
			return
		}

		blockEntity.getSyncDataHolder().applyServerNetworkUpdate(level.registryAccess(), data)
		blockEntity.markAsChanged()
		blockEntity.setChanged()
	}

	private fun canInteract(player: ServerPlayer, pos: BlockPos): Boolean = !player.isSpectator && player.canInteractWithBlock(pos, MAX_INTERACTION_DISTANCE)

	override fun type(): Type<CPacketMachineSyncToServer> = TYPE

	companion object {
		@JvmField
		val ID: ResourceLocation = GTCEu.id("machine_sync_to_server")

		@JvmField
		val TYPE: Type<CPacketMachineSyncToServer> = Type(ID)

		@JvmField
		val CODEC: StreamCodec<RegistryFriendlyByteBuf, CPacketMachineSyncToServer> =
			StreamCodec.ofMember(CPacketMachineSyncToServer::encode, ::CPacketMachineSyncToServer)

		private const val MAX_INTERACTION_DISTANCE = 8.0
	}
}

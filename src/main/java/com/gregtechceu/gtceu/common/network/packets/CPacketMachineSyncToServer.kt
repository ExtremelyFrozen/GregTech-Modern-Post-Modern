package com.gregtechceu.gtceu.common.network.packets

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.machine.MetaMachine
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

import io.netty.handler.codec.DecoderException
import io.netty.handler.codec.EncoderException

open class CPacketMachineSyncToServer(private val pos: BlockPos, private val blockEntityTypeId: ResourceLocation, private val data: DataComponentMap) : CustomPacketPayload {

	constructor(buffer: RegistryFriendlyByteBuf) : this(decode(buffer))

	private constructor(decoded: DecodedPacket) : this(decoded.pos, decoded.blockEntityTypeId, decoded.data)

	open fun encode(buffer: RegistryFriendlyByteBuf) {
		val temporary = RegistryFriendlyByteBuf(
			buffer.alloc().buffer(INITIAL_BUFFER_CAPACITY, MachineSyncPayloadCodec.MAX_BODY_LENGTH),
			buffer.registryAccess(),
			buffer.getConnectionType(),
		)
		try {
			try {
				temporary.writeBlockPos(pos)
				temporary.writeResourceLocation(blockEntityTypeId)
				MachineSyncPayloadCodec.encode(temporary, data)
			} catch (exception: EncoderException) {
				throw exception
			} catch (exception: IndexOutOfBoundsException) {
				throw EncoderException(
					"Machine sync payload exceeds the maximum body length of ${MachineSyncPayloadCodec.MAX_BODY_LENGTH} bytes",
					exception,
				)
			} catch (exception: RuntimeException) {
				throw EncoderException("Machine sync payload could not be encoded", exception)
			}

			val writerIndex = buffer.writerIndex()
			try {
				buffer.writeBytes(temporary, temporary.readerIndex(), temporary.readableBytes())
			} catch (exception: RuntimeException) {
				buffer.writerIndex(writerIndex)
				throw EncoderException("Machine sync payload could not be copied to the destination buffer", exception)
			}
		} finally {
			temporary.release()
		}
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

		val updateResult = try {
			blockEntity.getSyncDataHolder().tryApplyServerNetworkUpdate(level.registryAccess(), data)
		} catch (exception: RuntimeException) {
			GTCEu.LOGGER.error(
				"Sync: unexpected failure while applying block entity field update from {} at {} to {}",
				player.gameProfile.name,
				pos,
				blockEntity.javaClass.name,
				exception,
			)
			return
		}
		if (!updateResult.accepted) {
			GTCEu.LOGGER.warn(
				"Sync: rejecting block entity field update from {} at {} because {}",
				player.gameProfile.name,
				pos,
				updateResult.rejectionReason,
			)
			return
		}
		if (!updateResult.changed) {
			return
		}
		blockEntity.markAsChanged()
		blockEntity.setChanged()
	}

	private fun canInteract(player: ServerPlayer, pos: BlockPos): Boolean = !player.isSpectator && player.canInteractWithBlock(pos, MAX_INTERACTION_DISTANCE)

	override fun type(): Type<CPacketMachineSyncToServer> = TYPE

	companion object {
		private const val INITIAL_BUFFER_CAPACITY = 256

		@JvmField
		val ID: ResourceLocation = GTCEu.id("machine_sync_to_server")

		@JvmField
		val TYPE: Type<CPacketMachineSyncToServer> = Type(ID)

		@JvmField
		val CODEC: StreamCodec<RegistryFriendlyByteBuf, CPacketMachineSyncToServer> =
			StreamCodec.ofMember(CPacketMachineSyncToServer::encode, ::CPacketMachineSyncToServer)

		private const val MAX_INTERACTION_DISTANCE = 8.0

		private fun decode(buffer: RegistryFriendlyByteBuf): DecodedPacket {
			val bodyLength = buffer.readableBytes()
			if (bodyLength > MachineSyncPayloadCodec.MAX_BODY_LENGTH) {
				throw DecoderException(
					"Machine sync payload body length $bodyLength exceeds the maximum of ${MachineSyncPayloadCodec.MAX_BODY_LENGTH} bytes",
				)
			}
			try {
				val decoded = DecodedPacket(
					buffer.readBlockPos(),
					buffer.readResourceLocation(),
					MachineSyncPayloadCodec.decode(buffer),
				)
				if (buffer.isReadable) {
					throw DecoderException("Machine sync payload contains ${buffer.readableBytes()} trailing bytes")
				}
				return decoded
			} catch (exception: DecoderException) {
				throw exception
			} catch (exception: RuntimeException) {
				throw DecoderException("Machine sync payload is malformed", exception)
			}
		}
	}

	private class DecodedPacket(val pos: BlockPos, val blockEntityTypeId: ResourceLocation, val data: DataComponentMap)
}

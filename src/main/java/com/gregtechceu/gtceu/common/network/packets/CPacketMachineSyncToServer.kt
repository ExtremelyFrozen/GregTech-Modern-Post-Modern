package com.gregtechceu.gtceu.common.network.packets

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.machine.MetaMachine
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait
import com.gregtechceu.gtceu.api.sync_system.managed.ISyncManaged
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

open class CPacketMachineSyncToServer private constructor(
	private val pos: BlockPos,
	private val blockEntityTypeId: ResourceLocation,
	private val data: DataComponentMap,
	private val traitTarget: MachineTraitTarget?,
) : CustomPacketPayload {

	constructor(pos: BlockPos, blockEntityTypeId: ResourceLocation, data: DataComponentMap) : this(
		pos,
		blockEntityTypeId,
		data,
		null,
	)

	constructor(buffer: RegistryFriendlyByteBuf) : this(decode(buffer))

	private constructor(decoded: DecodedPacket) : this(decoded.pos, decoded.blockEntityTypeId, decoded.data, decoded.traitTarget)

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
				traitTarget?.encode(temporary)
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

		val syncTarget = resolveSyncTarget(player, blockEntity) ?: return
		val updateResult = try {
			syncTarget.getSyncDataHolder().tryApplyServerNetworkUpdate(level.registryAccess(), data)
		} catch (exception: RuntimeException) {
			GTCEu.LOGGER.error(
				"Sync: unexpected failure while applying block entity field update from {} at {} to {}",
				player.gameProfile.name,
				pos,
				syncTarget.javaClass.name,
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
		syncTarget.markAsChanged()
		blockEntity.setChanged()
	}

	private fun resolveSyncTarget(player: ServerPlayer, blockEntity: ManagedSyncBlockEntity): ISyncManaged? {
		val target = traitTarget ?: return blockEntity
		if (blockEntity !is MetaMachine) {
			GTCEu.LOGGER.warn(
				"Sync: rejecting machine trait field update from {} because holder at {} is not a machine",
				player.gameProfile.name,
				pos,
			)
			return null
		}
		if (blockEntity.definition.id != target.machineDefinitionId) {
			GTCEu.LOGGER.warn(
				"Sync: rejecting machine trait field update from {} because machine definition at {} changed from {} to {}",
				player.gameProfile.name,
				pos,
				target.machineDefinitionId,
				blockEntity.definition.id,
			)
			return null
		}

		val traits = blockEntity.getSyncTraits()
		if (target.index !in traits.indices) {
			GTCEu.LOGGER.warn(
				"Sync: rejecting machine trait field update from {} because trait index {} is missing at {}",
				player.gameProfile.name,
				target.index,
				pos,
			)
			return null
		}

		val trait = traits[target.index]
		if (trait.javaClass.name != target.className) {
			GTCEu.LOGGER.warn(
				"Sync: rejecting machine trait field update from {} because trait {} at index {} changed to {} at {}",
				player.gameProfile.name,
				target.className,
				target.index,
				trait.javaClass.name,
				pos,
			)
			return null
		}
		if (trait.machine !== blockEntity) {
			GTCEu.LOGGER.error(
				"Sync: rejecting machine trait field update from {} because trait {} at index {} does not belong to machine at {}",
				player.gameProfile.name,
				target.className,
				target.index,
				pos,
			)
			return null
		}
		return trait
	}

	private fun canInteract(player: ServerPlayer, pos: BlockPos): Boolean = !player.isSpectator && player.canInteractWithBlock(pos, MAX_INTERACTION_DISTANCE)

	override fun type(): Type<CPacketMachineSyncToServer> = TYPE

	companion object {
		private const val INITIAL_BUFFER_CAPACITY = 256
		private const val MACHINE_TRAIT_TARGET_MARKER = 0x47545452
		private const val MAX_TRAIT_CLASS_NAME_LENGTH = 512

		@JvmField
		val ID: ResourceLocation = GTCEu.id("machine_sync_to_server")

		@JvmField
		val TYPE: Type<CPacketMachineSyncToServer> = Type(ID)

		@JvmField
		val CODEC: StreamCodec<RegistryFriendlyByteBuf, CPacketMachineSyncToServer> =
			StreamCodec.ofMember(CPacketMachineSyncToServer::encode, ::CPacketMachineSyncToServer)

		@JvmStatic
		fun forMachineTrait(machine: MetaMachine, trait: MachineTrait, data: DataComponentMap): CPacketMachineSyncToServer {
			val traitIndex = machine.getSyncTraits().indexOfFirst { current -> current === trait }
			require(traitIndex >= 0) { "Machine trait does not belong to the packet's machine" }
			require(trait.machine === machine) { "Machine trait parent does not match the packet's machine" }
			return CPacketMachineSyncToServer(
				machine.blockPos,
				BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(machine.type)!!,
				data,
				MachineTraitTarget(machine.definition.id, traitIndex, trait.javaClass.name),
			)
		}

		private const val MAX_INTERACTION_DISTANCE = 8.0

		private fun decode(buffer: RegistryFriendlyByteBuf): DecodedPacket {
			val bodyLength = buffer.readableBytes()
			if (bodyLength > MachineSyncPayloadCodec.MAX_BODY_LENGTH) {
				throw DecoderException(
					"Machine sync payload body length $bodyLength exceeds the maximum of ${MachineSyncPayloadCodec.MAX_BODY_LENGTH} bytes",
				)
			}
			try {
				val pos = buffer.readBlockPos()
				val blockEntityTypeId = buffer.readResourceLocation()
				val data = MachineSyncPayloadCodec.decode(buffer)
				val traitTarget = if (buffer.isReadable) MachineTraitTarget.decode(buffer) else null
				if (buffer.isReadable) {
					throw DecoderException("Machine sync payload contains ${buffer.readableBytes()} trailing bytes")
				}
				return DecodedPacket(pos, blockEntityTypeId, data, traitTarget)
			} catch (exception: DecoderException) {
				throw exception
			} catch (exception: RuntimeException) {
				throw DecoderException("Machine sync payload is malformed", exception)
			}
		}
	}

	private class DecodedPacket(val pos: BlockPos, val blockEntityTypeId: ResourceLocation, val data: DataComponentMap, val traitTarget: MachineTraitTarget?)

	private class MachineTraitTarget(val machineDefinitionId: ResourceLocation, val index: Int, val className: String) {
		fun encode(buffer: RegistryFriendlyByteBuf) {
			buffer.writeInt(MACHINE_TRAIT_TARGET_MARKER)
			buffer.writeResourceLocation(machineDefinitionId)
			buffer.writeVarInt(index)
			buffer.writeUtf(className, MAX_TRAIT_CLASS_NAME_LENGTH)
		}

		companion object {
			fun decode(buffer: RegistryFriendlyByteBuf): MachineTraitTarget {
				val marker = buffer.readInt()
				if (marker != MACHINE_TRAIT_TARGET_MARKER) {
					throw DecoderException("Machine sync payload contains an unknown target extension")
				}
				val machineDefinitionId = buffer.readResourceLocation()
				val index = buffer.readVarInt()
				if (index < 0) {
					throw DecoderException("Machine trait target index cannot be negative: $index")
				}
				val className = buffer.readUtf(MAX_TRAIT_CLASS_NAME_LENGTH)
				if (className.isBlank()) {
					throw DecoderException("Machine trait target class name cannot be blank")
				}
				return MachineTraitTarget(machineDefinitionId, index, className)
			}
		}
	}
}

package com.gregtechceu.gtceu.common.network.packets

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.api.sync_system.managed.ManagedSyncBlockEntity

import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponentMap
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.neoforged.neoforge.network.handling.IPayloadContext

open class SPacketMachineSyncToClient(private val pos: BlockPos, private val data: DataComponentMap) : CustomPacketPayload {

	constructor(buffer: RegistryFriendlyByteBuf) : this(
		buffer.readBlockPos(),
		SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.decode(buffer),
	)

	open fun encode(buffer: RegistryFriendlyByteBuf) {
		buffer.writeBlockPos(pos)
		SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.encode(buffer, data)
	}

	open fun execute(context: IPayloadContext) {
		if (data.isEmpty) {
			return
		}

		val level: Level = context.player().level()
		if (!level.isClientSide || !level.isLoaded(pos)) {
			return
		}

		val blockEntity: BlockEntity? = level.getBlockEntity(pos)
		if (blockEntity !is ManagedSyncBlockEntity) {
			return
		}

		blockEntity.getSyncDataHolder().applyClientNetworkUpdate(level.registryAccess(), data)
	}

	override fun type(): Type<SPacketMachineSyncToClient> = TYPE

	companion object {
		@JvmField
		val ID: ResourceLocation = GTCEu.id("machine_sync_to_client")

		@JvmField
		val TYPE: Type<SPacketMachineSyncToClient> = Type(ID)

		@JvmField
		val CODEC: StreamCodec<RegistryFriendlyByteBuf, SPacketMachineSyncToClient> =
			StreamCodec.ofMember(SPacketMachineSyncToClient::encode, ::SPacketMachineSyncToClient)
	}
}

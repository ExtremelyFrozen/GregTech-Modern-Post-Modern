package com.gregtechceu.gtceu.common.network.packets

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferProxyOpeningIdentity
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferProxyUIContainerMenu
import com.gregtechceu.gtceu.integration.ae2.machine.MEPatternBufferProxyViewSnapshot

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.network.handling.IPayloadContext

import java.util.UUID

/** Delivers a linked-buffer GT field delta only to the Proxy menu session that requested it. */
class SPacketMEPatternBufferProxyViewToClient(
	private val containerId: Int,
	private val menuSessionId: UUID,
	private val opening: MEPatternBufferProxyOpeningIdentity,
	private val update: MEPatternBufferProxyViewSnapshot,
) : CustomPacketPayload {

	constructor(buffer: RegistryFriendlyByteBuf) : this(
		buffer.readVarInt(),
		buffer.readUUID(),
		MEPatternBufferProxyOpeningIdentity.STREAM_CODEC.decode(buffer),
		MEPatternBufferProxyViewSnapshot.STREAM_CODEC.decode(buffer),
	)

	fun encode(buffer: RegistryFriendlyByteBuf) {
		buffer.writeVarInt(containerId)
		buffer.writeUUID(menuSessionId)
		MEPatternBufferProxyOpeningIdentity.STREAM_CODEC.encode(buffer, opening)
		MEPatternBufferProxyViewSnapshot.STREAM_CODEC.encode(buffer, update)
	}

	fun execute(context: IPayloadContext) {
		val player = context.player()
		if (!player.level().isClientSide) return
		val menu = player.containerMenu as? MEPatternBufferProxyUIContainerMenu ?: return
		menu.applyClientUpdate(player, containerId, menuSessionId, opening, update)
	}

	override fun type(): Type<SPacketMEPatternBufferProxyViewToClient> = TYPE

	companion object {
		@JvmField
		val ID: ResourceLocation = GTCEu.id("me_pattern_buffer_proxy_view_to_client")

		@JvmField
		val TYPE: Type<SPacketMEPatternBufferProxyViewToClient> = Type(ID)

		@JvmField
		val CODEC: StreamCodec<RegistryFriendlyByteBuf, SPacketMEPatternBufferProxyViewToClient> =
			StreamCodec.ofMember(SPacketMEPatternBufferProxyViewToClient::encode, ::SPacketMEPatternBufferProxyViewToClient)
	}
}

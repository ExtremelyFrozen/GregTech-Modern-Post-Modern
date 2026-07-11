package com.gregtechceu.gtceu.common.network.packets

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.network.handling.IPayloadContext

open class CPacketItemActionToServer(private val hand: InteractionHand, private val openedStack: ItemStack, private val action: SyncActionData) : CustomPacketPayload {

	constructor(buffer: RegistryFriendlyByteBuf) : this(
		buffer.readEnum(InteractionHand::class.java),
		ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
		SyncActionData.STREAM_CODEC.decode(buffer),
	)

	open fun encode(buffer: RegistryFriendlyByteBuf) {
		buffer.writeEnum(hand)
		ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, openedStack)
		SyncActionData.STREAM_CODEC.encode(buffer, action)
	}

	open fun execute(context: IPayloadContext) {
		val player = context.player()
		if (player !is ServerPlayer) {
			GTCEu.LOGGER.warn("Sync action: rejecting item action {} without server player", action.actionId)
			return
		}

		if (player.isSpectator) {
			GTCEu.LOGGER.warn(
				"Sync action: rejecting item action {} from {} because interaction is not allowed",
				action.actionId,
				player.gameProfile.name,
			)
			return
		}

		val stack: ItemStack = player.getItemInHand(hand)
		if (stack.isEmpty) {
			GTCEu.LOGGER.warn(
				"Sync action: rejecting item action {} from {} because held item is missing",
				action.actionId,
				player.gameProfile.name,
			)
			return
		}

		if (!ItemStack.isSameItem(stack, openedStack)) {
			GTCEu.LOGGER.warn(
				"Sync action: rejecting item action {} from {} because held item changed",
				action.actionId,
				player.gameProfile.name,
			)
			return
		}

		SyncActionDispatchers.server().dispatch(SyncActionContext.item(player, stack, openedStack, action, hand))
	}

	override fun type(): Type<CPacketItemActionToServer> = TYPE

	companion object {
		@JvmField
		val ID: ResourceLocation = GTCEu.id("item_action_to_server")

		@JvmField
		val TYPE: Type<CPacketItemActionToServer> = Type(ID)

		@JvmField
		val CODEC: StreamCodec<RegistryFriendlyByteBuf, CPacketItemActionToServer> =
			StreamCodec.ofMember(CPacketItemActionToServer::encode, ::CPacketItemActionToServer)
	}
}

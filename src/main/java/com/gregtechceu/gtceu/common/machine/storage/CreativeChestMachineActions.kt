package com.gregtechceu.gtceu.common.machine.storage

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

import org.jetbrains.annotations.ApiStatus

/** Exposes only the creative chest mutation that its Kotlin server action handler may invoke. */
@ApiStatus.Internal
interface CreativeChestItemActionTarget {

	/** Applies one validated phantom item selection, normalizing its stored count. */
	fun setCreativeChestItem(item: ItemStack)
}

/** Owns the wire protocol and server handler for the creative chest phantom item slot. */
object CreativeChestMachineActions {

	private val SET_CREATIVE_CHEST_ITEM_ACTION = GTCEu.id("set_creative_chest_item")

	init {
		SyncActionDispatchers.server().register(CreativeChestItemActionHandler)
	}

	/** Forces this object's static registration from the owning machine's class initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one client action for a creative chest phantom item selection. */
	@JvmStatic
	fun createSetItemAction(item: ItemStack): SyncActionData {
		val storedItem = if (item.isEmpty) ItemStack.EMPTY else item.copyWithCount(1)
		val payload =
			DataComponentMap
				.builder()
				.set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), storedItem)
				.build()
		return SyncActionData(
			SET_CREATIVE_CHEST_ITEM_ACTION,
			ItemStack.hashItemAndComponents(storedItem),
			payload,
		)
	}

	private object CreativeChestItemActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_CREATIVE_CHEST_ITEM_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is CreativeChestItemActionTarget

		override fun acceptsPayload(payload: DataComponentMap): Boolean = payload.has(GTDataComponents.PLACEHOLDER_ITEM_STACK.get())

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			target(context).setCreativeChestItem(requireItemStack(context.payload()))
		}
	}

	private fun target(context: SyncActionContext): CreativeChestItemActionTarget = context.holder as? CreativeChestItemActionTarget
		?: throw IllegalStateException("Creative chest action received a non-creative-chest target.")

	private fun requireItemStack(payload: DataComponentMap): ItemStack = payload[GTDataComponents.PLACEHOLDER_ITEM_STACK.get()]?.copy()
		?: throw IllegalStateException("Creative chest item action payload is missing item stack.")
}

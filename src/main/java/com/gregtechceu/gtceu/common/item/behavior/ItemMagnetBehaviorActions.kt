package com.gregtechceu.gtceu.common.item.behavior

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.item.IComponentItem
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.common.data.GTDataComponents
import com.gregtechceu.gtceu.common.item.behavior.ItemMagnetBehavior.Filter
import com.gregtechceu.gtceu.common.item.behavior.ItemMagnetBehavior.MagnetComponent

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/** Owns the wire protocol and server handler for held-item magnet filter selection. */
object ItemMagnetBehaviorActions {

	private val SET_MAGNET_FILTER_ACTION = GTCEu.id("set_magnet_filter")

	init {
		SyncActionDispatchers.server().register(MagnetFilterActionHandler)
	}

	/** Forces common-side handler registration when the owning magnet behavior initializes. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one client request while preserving the current stack's active state in the payload. */
	@JvmStatic
	fun createSetMagnetFilterAction(stack: ItemStack, filter: Filter): SyncActionData {
		val current = stack[GTDataComponents.MAGNET]
			?: throw IllegalStateException("Cannot create a magnet filter action without a magnet component.")
		val payload =
			DataComponentMap
				.builder()
				.set(GTDataComponents.MAGNET.get(), MagnetComponent(current.active(), filter))
				.build()
		return SyncActionData(SET_MAGNET_FILTER_ACTION, filter.ordinal, payload)
	}

	private object MagnetFilterActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_MAGNET_FILTER_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean {
			val stack = context.holder as? ItemStack ?: return false
			val openedStack = context.openedStack ?: return false
			return isMagnet(stack) &&
				isMagnet(openedStack) &&
				ItemStack.isSameItem(stack, openedStack)
		}

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val requested = payload[GTDataComponents.MAGNET.get()] ?: return false
			return isKnownFilter(requested.filterType())
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			val stack = context.holder as? ItemStack
				?: throw IllegalStateException("Magnet filter action received a non-item holder.")
			val requested = context.payload()[GTDataComponents.MAGNET.get()]
				?: throw IllegalStateException("Magnet filter action payload is missing.")
			val filter = requested.filterType()
				?: throw IllegalStateException("Magnet filter action payload has no filter.")
			require(isKnownFilter(filter)) { "Magnet filter action payload has an unknown filter." }
			val current = stack[GTDataComponents.MAGNET]
				?: throw IllegalStateException("Magnet filter action holder is missing its magnet component.")
			stack.set(GTDataComponents.MAGNET, MagnetComponent(current.active(), filter))
		}
	}

	private fun isMagnet(stack: ItemStack): Boolean {
		val item = stack.item as? IComponentItem ?: return false
		return item.components.any { component -> component is ItemMagnetBehavior }
	}

	private fun isKnownFilter(filter: Filter?): Boolean = Filter.entries.any { known -> known === filter }
}

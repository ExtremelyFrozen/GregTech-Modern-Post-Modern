package com.gregtechceu.gtceu.api.sync_system

import com.gregtechceu.gtceu.api.cover.CoverBehavior
import com.gregtechceu.gtceu.api.sync_system.managed.ManagedSyncBlockEntity

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

import net.minecraft.core.BlockPos as Pos
import net.minecraft.core.Direction as Side
import net.minecraft.world.InteractionHand as Hand
import net.minecraft.world.item.ItemStack as Stack

/**
 * Server-side context passed to a registered sync action handler.
 *
 * @property player the server player that sent the action request.
 * @property holder the resolved machine, cover, or item holder for the request.
 * @property action the decoded action data.
 * @property pos the block position for block-backed holders, or `null` for item actions.
 * @property side the cover side for cover holders, or `null` for other holders.
 * @property hand the interaction hand for held item holders, or `null` for block-backed holders.
 * @property openedStack the held item stack snapshot captured when the UI was opened, or `null` for block-backed
 * holders.
 */
@JvmRecord
data class SyncActionContext(val player: ServerPlayer, val holder: Any, val action: SyncActionData, val pos: Pos?, val side: Side?, val hand: Hand?, val openedStack: Stack?) {
	/**
	 * Returns the action id without forcing handlers to dereference the action record.
	 */
	fun actionId(): ResourceLocation = action.actionId

	/**
	 * Returns the client sequence number without forcing handlers to dereference the action record.
	 */
	fun sequence(): Int = action.sequence

	/**
	 * Returns the component payload that action handlers must validate before use.
	 */
	fun payload(): DataComponentMap = action.payload

	override fun toString(): String = "SyncActionContext[player=$player, holder=$holder, action=$action, pos=$pos, side=$side, hand=$hand, openedStack=$openedStack]"

	companion object {
		/**
		 * Creates context for a managed block entity action.
		 */
		@JvmStatic
		fun machine(player: ServerPlayer, holder: ManagedSyncBlockEntity, action: SyncActionData, pos: Pos): SyncActionContext = SyncActionContext(player, holder, action, pos, null, null, null)

		/**
		 * Creates context for an attached cover action.
		 */
		@JvmStatic
		fun cover(player: ServerPlayer, holder: CoverBehavior, action: SyncActionData, pos: Pos, side: Side): SyncActionContext = SyncActionContext(player, holder, action, pos, side, null, null)

		/** Creates context for a held item action. */
		@JvmStatic
		fun item(player: ServerPlayer, holder: Stack, openedStack: Stack, action: SyncActionData, hand: Hand) = SyncActionContext(player, holder, action, null, null, hand, openedStack)
	}
}

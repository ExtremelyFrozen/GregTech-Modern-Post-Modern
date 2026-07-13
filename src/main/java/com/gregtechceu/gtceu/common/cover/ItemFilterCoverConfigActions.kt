package com.gregtechceu.gtceu.common.cover

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.common.cover.data.FilterMode
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

import org.jetbrains.annotations.ApiStatus

/** Exposes only the ordered item-filter mutations required by its Kotlin action handler. */
@ApiStatus.Internal
interface ItemFilterCoverConfigActionTarget {

	/** Applies the validated filter direction before the manual-flow policy. */
	fun setFilterMode(filterMode: FilterMode)

	/** Applies the validated manual-flow policy after the filter direction. */
	fun setAllowFlow(manualIOMode: ManualIOMode)
}

/** Owns the concrete action id, holder policy, and execution for item filter cover configuration. */
object ItemFilterCoverConfigActions {

	private val SET_ITEM_FILTER_COVER_CONFIG_ACTION = GTCEu.id("set_item_filter_cover_config")

	init {
		SyncActionDispatchers.server().register(ItemFilterCoverConfigActionHandler)
	}

	/** Forces common-side handler registration when the owning cover class initializes. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one complete client request from the current item filter state. */
	@JvmStatic
	fun createSetConfigAction(filterMode: FilterMode, manualIOMode: ManualIOMode): SyncActionData = FilterCoverConfigActionProtocol.createAction(
		SET_ITEM_FILTER_COVER_CONFIG_ACTION,
		filterMode,
		manualIOMode,
	)

	private object ItemFilterCoverConfigActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_ITEM_FILTER_COVER_CONFIG_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is ItemFilterCover

		override fun acceptsPayload(payload: DataComponentMap): Boolean = FilterCoverConfigActionProtocol.read(payload) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			val target =
				context.holder as? ItemFilterCoverConfigActionTarget
					?: throw IllegalStateException(
						"Item filter cover config action received a non-item-filter target.",
					)
			val config = FilterCoverConfigActionProtocol.requireConfig(context.payload())
			target.setFilterMode(config.filterMode)
			target.setAllowFlow(config.manualIOMode)
		}
	}
}

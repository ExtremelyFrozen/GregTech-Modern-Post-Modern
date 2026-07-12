package com.gregtechceu.gtceu.api.machine.fancyconfigurator

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.capability.ICoverable
import com.gregtechceu.gtceu.api.gui.factory.CoverUIHelper
import com.gregtechceu.gtceu.api.machine.MachineCoverContainer
import com.gregtechceu.gtceu.api.machine.MetaMachine
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.data.GTDataComponents
import com.gregtechceu.gtceu.common.item.behavior.CoverPlaceBehavior

import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive

/**
 * Creates and handles GT machine actions for covers selected from an LDLib2 directional page.
 *
 * The server resolves the cover definition exclusively from the player's current carried stack. Client payloads only
 * identify a machine side and therefore cannot select or synthesize a cover item.
 */
class LDLib2DirectionalCoverActions private constructor() {

	companion object {
		private val PLACE_DIRECTIONAL_COVER_ACTION: ResourceLocation = GTCEu.id("place_directional_cover")
		private val REMOVE_DIRECTIONAL_COVER_ACTION: ResourceLocation = GTCEu.id("remove_directional_cover")
		private val OPEN_DIRECTIONAL_COVER_ACTION: ResourceLocation = GTCEu.id("open_directional_cover")
		private val SIDE_FIELD: ResourceLocation = SyncFieldData.key("side")

		init {
			SyncActionDispatchers.server().register(PlaceCoverActionHandler())
			SyncActionDispatchers.server().register(RemoveCoverActionHandler())
			SyncActionDispatchers.server().register(OpenCoverActionHandler())
		}

		/**
		 * Triggers common-side class initialization so the three server handlers are registered before UI actions arrive.
		 */
		@JvmStatic
		fun initialize() = Unit

		/**
		 * Creates a request to place or replace a cover on [side] using the server player's carried stack.
		 */
		@JvmStatic
		fun createPlaceCoverAction(side: Direction): SyncActionData = createAction(PLACE_DIRECTIONAL_COVER_ACTION, side)

		/**
		 * Creates a request to remove the cover currently attached to [side].
		 */
		@JvmStatic
		fun createRemoveCoverAction(side: Direction): SyncActionData = createAction(REMOVE_DIRECTIONAL_COVER_ACTION, side)

		/**
		 * Creates a request to open the configurable cover currently attached to [side].
		 */
		@JvmStatic
		fun createOpenCoverAction(side: Direction): SyncActionData = createAction(OPEN_DIRECTIONAL_COVER_ACTION, side)

		private fun createAction(actionId: ResourceLocation, side: Direction): SyncActionData {
			val sideId = side.get3DDataValue()
			val payload = DataComponentMap.builder()
				.set(
					GTDataComponents.SYNC_FIELD_DATA.get(),
					SyncFieldData.builder()
						.put(SIDE_FIELD, JsonPrimitive(sideId))
						.build(),
				)
				.build()
			return SyncActionData(actionId, sideId, payload)
		}

		private abstract class DirectionalCoverActionHandler : SyncActionHandler {

			override fun acceptsHolder(context: SyncActionContext): Boolean = readMachine(context) != null

			override fun acceptsPayload(payload: DataComponentMap): Boolean = readSide(payload) != null

			override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean {
				if (player.isSpectator) {
					return false
				}
				val machine = readMachine(context) ?: return false
				val side = readSide(context.payload()) ?: return false
				val coverable = machine.getTrait(MachineCoverContainer.TYPE) ?: return false
				return canExecute(player, coverable, side)
			}

			override fun execute(context: SyncActionContext) {
				val machine = requireMachine(context)
				val side = requireSide(context.payload())
				val coverable = machine.getTrait(MachineCoverContainer.TYPE)
					?: throw IllegalStateException("Directional cover action machine has no cover container trait.")
				executeValidated(context.player, coverable, side)
			}

			protected abstract fun canExecute(player: ServerPlayer, coverable: ICoverable, side: Direction): Boolean

			protected abstract fun executeValidated(player: ServerPlayer, coverable: ICoverable, side: Direction)
		}

		private class PlaceCoverActionHandler : DirectionalCoverActionHandler() {

			override fun actionId(): ResourceLocation = PLACE_DIRECTIONAL_COVER_ACTION

			override fun canExecute(player: ServerPlayer, coverable: ICoverable, side: Direction): Boolean {
				val definition = CoverPlaceBehavior.findCoverDefinition(player.containerMenu.carried) ?: return false
				return coverable.canPlaceCoverOnSide(definition, side) &&
					definition.createCoverBehavior(coverable, side).canAttach()
			}

			override fun executeValidated(player: ServerPlayer, coverable: ICoverable, side: Direction) {
				val carried = player.containerMenu.carried
				val definition = CoverPlaceBehavior.findCoverDefinition(carried)
					?: throw IllegalStateException("Directional cover placement lost its server-carried cover item.")
				if (!coverable.placeCoverOnSide(side, carried, definition, player)) {
					throw IllegalStateException("Validated directional cover placement was rejected during execution.")
				}
				if (!player.isCreative) {
					carried.shrink(1)
				}
				player.containerMenu.broadcastChanges()
			}
		}

		private class RemoveCoverActionHandler : DirectionalCoverActionHandler() {

			override fun actionId(): ResourceLocation = REMOVE_DIRECTIONAL_COVER_ACTION

			override fun canExecute(player: ServerPlayer, coverable: ICoverable, side: Direction): Boolean = coverable.getCoverAtSide(side) != null

			override fun executeValidated(player: ServerPlayer, coverable: ICoverable, side: Direction) {
				if (!coverable.removeCover(side, player)) {
					throw IllegalStateException("Validated directional cover removal was rejected during execution.")
				}
				player.containerMenu.broadcastChanges()
			}
		}

		private class OpenCoverActionHandler : DirectionalCoverActionHandler() {

			override fun actionId(): ResourceLocation = OPEN_DIRECTIONAL_COVER_ACTION

			override fun canExecute(player: ServerPlayer, coverable: ICoverable, side: Direction): Boolean {
				val cover = coverable.getCoverAtSide(side) ?: return false
				return CoverUIHelper.canOpenLDLib2(cover, player)
			}

			override fun executeValidated(player: ServerPlayer, coverable: ICoverable, side: Direction) {
				val cover = coverable.getCoverAtSide(side)
					?: throw IllegalStateException("Directional cover open action lost its selected cover.")
				if (!CoverUIHelper.open(cover, player)) {
					throw IllegalStateException("Validated directional cover UI failed to open.")
				}
			}
		}

		private fun readMachine(context: SyncActionContext): MetaMachine? {
			val holder = context.holder
			if (holder !is MetaMachine || holder !is LDLib2FancyActionMachine) {
				return null
			}
			return holder
		}

		private fun requireMachine(context: SyncActionContext): MetaMachine = readMachine(context)
			?: throw IllegalStateException("Directional cover action received an unauthorized machine holder.")

		private fun requireSide(payload: DataComponentMap): Direction = readSide(payload) ?: throw IllegalStateException("Directional cover action payload has no valid side.")

		private fun readSide(payload: DataComponentMap): Direction? {
			val fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get()) ?: return null
			val element: JsonElement = fields[SIDE_FIELD] ?: return null
			if (element !is JsonPrimitive || !element.isNumber) {
				return null
			}

			val sideId = try {
				element.asBigDecimal.intValueExact()
			} catch (exception: ArithmeticException) {
				logInvalidNumericSide(element, exception)
				return null
			} catch (exception: NumberFormatException) {
				logInvalidNumericSide(element, exception)
				return null
			}
			if (sideId !in Direction.values().indices) {
				return null
			}
			return Direction.from3DDataValue(sideId)
		}

		private fun logInvalidNumericSide(element: JsonElement, exception: RuntimeException) {
			GTCEu.LOGGER.warn(
				"Directional cover action received a side outside the exact integer range {}",
				element,
				exception,
			)
		}
	}
}

package com.gregtechceu.gtceu.api.machine.steam

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

import com.google.gson.JsonPrimitive
import org.jetbrains.annotations.ApiStatus

/**
 * Exposes only the steam boiler fluid-slot operation that its Kotlin server action handler may invoke.
 *
 * This narrow contract keeps the wire protocol independent from the concrete boiler hierarchy while requiring
 * every accepted holder to opt in explicitly.
 */
@ApiStatus.Internal
interface SteamBoilerFluidSlotActionTarget {

	/**
	 * Executes one validated fluid-container click against [fluidSlot], preserving the concrete boiler's tank
	 * permissions and [shiftDown] cursor semantics.
	 */
	fun clickSteamBoilerFluidSlot(player: ServerPlayer, fluidSlot: Int, shiftDown: Boolean)
}

/** Owns the wire protocol and server handler for steam boiler fluid-slot clicks. */
object SteamBoilerMachineActions {

	private val CLICK_STEAM_BOILER_FLUID_SLOT_ACTION = GTCEu.id("click_steam_boiler_fluid_slot")
	private val FLUID_SLOT_FIELD = SyncFieldData.key("fluidSlot")
	private val SHIFT_FIELD = SyncFieldData.key("shift")
	private const val WATER_FLUID_SLOT = 0
	private const val FUEL_FLUID_SLOT = 2

	init {
		SyncActionDispatchers.server().register(SteamBoilerFluidSlotActionHandler)
	}

	/** Forces this object's static registration from the owning machine's class initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates the client action payload for one steam boiler fluid-slot click. */
	@JvmStatic
	fun createClickSteamBoilerFluidSlotAction(fluidSlot: Int, shiftDown: Boolean): SyncActionData {
		val fields =
			SyncFieldData.builder()
				.put(FLUID_SLOT_FIELD, JsonPrimitive(fluidSlot))
				.put(SHIFT_FIELD, JsonPrimitive(shiftDown))
				.build()
		return SyncActionData(
			CLICK_STEAM_BOILER_FLUID_SLOT_ACTION,
			fluidSlot,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private object SteamBoilerFluidSlotActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = CLICK_STEAM_BOILER_FLUID_SLOT_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is SteamBoilerFluidSlotActionTarget

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return readFluidSlot(fields) != null && readShift(fields) != null
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			target(context).clickSteamBoilerFluidSlot(
				context.player,
				requireFluidSlot(context.payload()),
				requireShift(context.payload()),
			)
		}
	}

	private fun target(context: SyncActionContext): SteamBoilerFluidSlotActionTarget = context.holder as? SteamBoilerFluidSlotActionTarget
		?: throw IllegalStateException("Steam boiler fluid slot action received a non-boiler machine.")

	private fun requireFieldData(payload: DataComponentMap): SyncFieldData = payload[GTDataComponents.SYNC_FIELD_DATA.get()]
		?: throw IllegalStateException("Steam boiler fluid slot action payload is missing field data.")

	private fun requireFluidSlot(payload: DataComponentMap): Int = readFluidSlot(requireFieldData(payload))
		?: throw IllegalStateException("Steam boiler fluid slot action payload is missing $FLUID_SLOT_FIELD.")

	private fun requireShift(payload: DataComponentMap): Boolean = readShift(requireFieldData(payload))
		?: throw IllegalStateException("Steam boiler fluid slot action payload is missing $SHIFT_FIELD.")

	private fun readFluidSlot(fields: SyncFieldData): Int? {
		val primitive = fields[FLUID_SLOT_FIELD] as? JsonPrimitive ?: return null
		if (!primitive.isNumber) {
			return null
		}
		val value = primitive.asInt
		return if (value in WATER_FLUID_SLOT..FUEL_FLUID_SLOT) value else null
	}

	private fun readShift(fields: SyncFieldData): Boolean? {
		val primitive = fields[SHIFT_FIELD] as? JsonPrimitive ?: return null
		return if (primitive.isBoolean) primitive.asBoolean else null
	}
}

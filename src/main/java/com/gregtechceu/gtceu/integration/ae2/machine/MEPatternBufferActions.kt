package com.gregtechceu.gtceu.integration.ae2.machine

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine
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
 * Exposes only the authoritative Pattern Buffer operations used by its holder-scoped LDLib2 actions.
 */
@ApiStatus.Internal
interface MEPatternBufferActionTarget : LDLib2FancyActionMachine {

	/** Returns whether the exact machine definition owns the Pattern Buffer action protocol. */
	fun supportsMEPatternBufferActions(): Boolean

	/** Replaces the terminal group name without trimming or otherwise normalizing it. */
	fun setMEPatternBufferName(name: String)

	/** Attempts to return every buffered crafting input to the connected ME network. */
	fun refundMEPatternBufferContents()

	/** Returns the number of independently addressable shared fluid tanks. */
	fun getMEPatternBufferShareTankCount(): Int

	/** Executes one validated fluid-container interaction against a shared tank. */
	fun clickMEPatternBufferShareTank(player: ServerPlayer, tankIndex: Int, shiftDown: Boolean)
}

/** Owns the Pattern Buffer name, refund, and shared-tank wire protocols. */
object MEPatternBufferActions {

	private val SET_NAME_ACTION = GTCEu.id("set_me_pattern_buffer_name")
	private val REFUND_ALL_ACTION = GTCEu.id("refund_me_pattern_buffer")
	private val CLICK_SHARE_TANK_ACTION = GTCEu.id("click_me_pattern_buffer_share_tank")
	private val NAME_FIELD = SyncFieldData.key("name")
	private val TANK_FIELD = SyncFieldData.key("tank")
	private val SHIFT_FIELD = SyncFieldData.key("shift")

	init {
		SyncActionDispatchers.server().register(SetNameHandler)
		SyncActionDispatchers.server().register(RefundAllHandler)
		SyncActionDispatchers.server().register(ClickShareTankHandler)
	}

	/** Forces action registration from the owning machine's static initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one exact Pattern Buffer terminal name update, including an empty clearing value. */
	@JvmStatic
	fun createSetNameAction(name: String): SyncActionData {
		val fields = SyncFieldData.builder().put(NAME_FIELD, JsonPrimitive(name)).build()
		return SyncActionData(
			SET_NAME_ACTION,
			name.hashCode(),
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	/** Creates one command that refunds all currently buffered crafting inputs. */
	@JvmStatic
	fun createRefundAllAction(): SyncActionData = SyncActionData(REFUND_ALL_ACTION, 0, DataComponentMap.EMPTY)

	/** Creates one shared-tank fluid-container interaction. */
	@JvmStatic
	fun createClickShareTankAction(tankIndex: Int, shiftDown: Boolean): SyncActionData {
		require(tankIndex >= 0) { "Pattern Buffer shared tank index must be non-negative: $tankIndex" }
		val fields =
			SyncFieldData.builder()
				.put(TANK_FIELD, JsonPrimitive(tankIndex))
				.put(SHIFT_FIELD, JsonPrimitive(shiftDown))
				.build()
		return SyncActionData(
			CLICK_SHARE_TANK_ACTION,
			tankIndex * 2 + if (shiftDown) 1 else 0,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private abstract class PatternBufferActionHandler : SyncActionHandler {

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is MEPatternBufferActionTarget

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator && target(context).supportsMEPatternBufferActions()

		protected fun target(context: SyncActionContext): MEPatternBufferActionTarget = context.holder as? MEPatternBufferActionTarget
			?: throw IllegalStateException("Pattern Buffer action received a non-Pattern-Buffer holder.")
	}

	private object SetNameHandler : PatternBufferActionHandler() {

		override fun actionId(): ResourceLocation = SET_NAME_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			if (payload.size() != 1) return false
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return fields.fields.size == 1 && readName(fields) != null
		}

		override fun execute(context: SyncActionContext) {
			target(context).setMEPatternBufferName(requireName(context.payload()))
		}
	}

	private object RefundAllHandler : PatternBufferActionHandler() {

		override fun actionId(): ResourceLocation = REFUND_ALL_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean = payload.isEmpty

		override fun execute(context: SyncActionContext) {
			target(context).refundMEPatternBufferContents()
		}
	}

	private object ClickShareTankHandler : PatternBufferActionHandler() {

		override fun actionId(): ResourceLocation = CLICK_SHARE_TANK_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			if (payload.size() != 1) return false
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return fields.fields.size == 2 &&
				readTankIndex(fields) != null && readShift(fields) != null
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean {
			if (!super.mayExecute(player, context)) return false
			val tankIndex = readTankIndex(requireFields(context.payload())) ?: return false
			return tankIndex < target(context).getMEPatternBufferShareTankCount()
		}

		override fun execute(context: SyncActionContext) {
			target(context).clickMEPatternBufferShareTank(
				context.player,
				requireTankIndex(context.payload()),
				requireShift(context.payload()),
			)
		}
	}

	private fun requireFields(payload: DataComponentMap): SyncFieldData = payload[GTDataComponents.SYNC_FIELD_DATA.get()]
		?: throw IllegalStateException("Pattern Buffer action payload is missing field data.")

	private fun requireName(payload: DataComponentMap): String = readName(requireFields(payload))
		?: throw IllegalStateException("Pattern Buffer action payload is missing string field $NAME_FIELD.")

	private fun requireTankIndex(payload: DataComponentMap): Int = readTankIndex(requireFields(payload))
		?: throw IllegalStateException(
			"Pattern Buffer action payload is missing non-negative integer field $TANK_FIELD.",
		)

	private fun requireShift(payload: DataComponentMap): Boolean = readShift(requireFields(payload))
		?: throw IllegalStateException("Pattern Buffer action payload is missing boolean field $SHIFT_FIELD.")

	private fun readName(fields: SyncFieldData): String? {
		val primitive = fields[NAME_FIELD] as? JsonPrimitive ?: return null
		return if (primitive.isString) primitive.asString else null
	}

	private fun readTankIndex(fields: SyncFieldData): Int? {
		val primitive = fields[TANK_FIELD] as? JsonPrimitive ?: return null
		if (!primitive.isNumber) return null
		val value =
			try {
				primitive.asBigDecimal.intValueExact()
			} catch (exception: NumberFormatException) {
				logInvalidTankIndex(primitive, exception)
				return null
			} catch (exception: ArithmeticException) {
				logInvalidTankIndex(primitive, exception)
				return null
			}
		return value.takeIf { it >= 0 }
	}

	private fun readShift(fields: SyncFieldData): Boolean? {
		val primitive = fields[SHIFT_FIELD] as? JsonPrimitive ?: return null
		return if (primitive.isBoolean) primitive.asBoolean else null
	}

	private fun logInvalidTankIndex(value: JsonPrimitive, exception: RuntimeException) {
		GTCEu.LOGGER.warn(
			"Pattern Buffer action rejected invalid integer {} for field {}",
			value,
			TANK_FIELD,
			exception,
		)
	}
}

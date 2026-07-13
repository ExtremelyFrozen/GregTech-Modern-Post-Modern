package com.gregtechceu.gtceu.common.machine.storage

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.GTValues
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

/** Exposes only the creative energy mutations that Kotlin server action handlers may invoke. */
@ApiStatus.Internal
interface CreativeEnergyActionTarget {

	/** Applies one validated voltage and updates its derived tier and client sync state. */
	fun setCreativeEnergyVoltage(voltage: Long)

	/** Applies one validated tier and updates its derived voltage and client sync state. */
	fun setCreativeEnergyTier(tier: Int)
}

/** Owns the wire protocol and server handlers for creative energy voltage and tier actions. */
object CreativeEnergyContainerMachineActions {

	private val SET_CREATIVE_ENERGY_VOLTAGE_ACTION = GTCEu.id("set_creative_energy_voltage")
	private val SET_CREATIVE_ENERGY_TIER_ACTION = GTCEu.id("set_creative_energy_tier")
	private val VOLTAGE_FIELD = SyncFieldData.key("voltage")
	private val TIER_FIELD = SyncFieldData.key("setTier")

	init {
		SyncActionDispatchers.server().register(CreativeEnergyVoltageActionHandler)
		SyncActionDispatchers.server().register(CreativeEnergyTierActionHandler)
	}

	/** Forces this object's static registration from the owning machine's class initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one client request for a validated non-negative creative energy voltage. */
	@JvmStatic
	fun createSetVoltageAction(voltage: Long): SyncActionData {
		require(voltage >= 0L) { "Creative energy voltage cannot be negative: $voltage" }
		return createAction(
			SET_CREATIVE_ENERGY_VOLTAGE_ACTION,
			VOLTAGE_FIELD,
			JsonPrimitive(voltage),
			voltage.hashCode(),
		)
	}

	/** Creates one client request for a validated creative energy tier index. */
	@JvmStatic
	fun createSetTierAction(tier: Int): SyncActionData {
		require(tier in GTValues.VNF.indices) { "Creative energy tier is out of range: $tier" }
		return createAction(SET_CREATIVE_ENERGY_TIER_ACTION, TIER_FIELD, JsonPrimitive(tier), tier)
	}

	private fun createAction(actionId: ResourceLocation, field: ResourceLocation, value: JsonPrimitive, sequence: Int): SyncActionData {
		val fields =
			SyncFieldData
				.builder()
				.put(field, value)
				.build()
		return SyncActionData(
			actionId,
			sequence,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private abstract class CreativeEnergyActionHandler : SyncActionHandler {

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is CreativeEnergyActionTarget

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		protected fun target(context: SyncActionContext): CreativeEnergyActionTarget = context.holder as? CreativeEnergyActionTarget
			?: throw IllegalStateException("Creative energy action received a non-creative-energy target.")
	}

	private object CreativeEnergyVoltageActionHandler : CreativeEnergyActionHandler() {

		override fun actionId(): ResourceLocation = SET_CREATIVE_ENERGY_VOLTAGE_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return readNonNegativeLong(fields, VOLTAGE_FIELD) != null
		}

		override fun execute(context: SyncActionContext) {
			target(context).setCreativeEnergyVoltage(requireNonNegativeLong(context.payload(), VOLTAGE_FIELD))
		}
	}

	private object CreativeEnergyTierActionHandler : CreativeEnergyActionHandler() {

		override fun actionId(): ResourceLocation = SET_CREATIVE_ENERGY_TIER_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return readTierIndex(fields, TIER_FIELD) != null
		}

		override fun execute(context: SyncActionContext) {
			target(context).setCreativeEnergyTier(requireTierIndex(context.payload(), TIER_FIELD))
		}
	}

	private fun requireFieldData(payload: DataComponentMap): SyncFieldData = payload[GTDataComponents.SYNC_FIELD_DATA.get()]
		?: throw IllegalStateException("Creative energy action payload is missing field data.")

	private fun requireNonNegativeLong(payload: DataComponentMap, field: ResourceLocation): Long = readNonNegativeLong(requireFieldData(payload), field)
		?: throw IllegalStateException("Creative energy action payload is missing or invalid for $field.")

	private fun requireTierIndex(payload: DataComponentMap, field: ResourceLocation): Int = readTierIndex(requireFieldData(payload), field)
		?: throw IllegalStateException("Creative energy action payload is missing or invalid for $field.")

	private fun readNonNegativeLong(fields: SyncFieldData, field: ResourceLocation): Long? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		if (!primitive.isNumber) {
			return null
		}
		val value =
			try {
				primitive.asBigDecimal.longValueExact()
			} catch (exception: NumberFormatException) {
				logInvalidNumber(field, primitive, exception)
				return null
			} catch (exception: ArithmeticException) {
				logInvalidNumber(field, primitive, exception)
				return null
			}
		return value.takeIf { it >= 0L }
	}

	private fun readTierIndex(fields: SyncFieldData, field: ResourceLocation): Int? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		if (!primitive.isNumber) {
			return null
		}
		val value =
			try {
				primitive.asBigDecimal.intValueExact()
			} catch (exception: NumberFormatException) {
				logInvalidNumber(field, primitive, exception)
				return null
			} catch (exception: ArithmeticException) {
				logInvalidNumber(field, primitive, exception)
				return null
			}
		return value.takeIf { it in GTValues.VNF.indices }
	}

	private fun logInvalidNumber(field: ResourceLocation, primitive: JsonPrimitive, exception: RuntimeException) {
		GTCEu.LOGGER.warn(
			"Creative energy action rejected invalid numeric field {}: {}",
			field,
			primitive,
			exception,
		)
	}
}

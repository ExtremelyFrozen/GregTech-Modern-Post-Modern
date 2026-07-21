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
import net.neoforged.neoforge.fluids.FluidStack
import net.neoforged.neoforge.fluids.SimpleFluidContent

import com.google.gson.JsonPrimitive
import org.jetbrains.annotations.ApiStatus

/**
 * Exposes only the authoritative ME fluid configuration operations used by holder-scoped actions.
 */
@ApiStatus.Internal
interface MEFluidConfigActionTarget : LDLib2FancyActionMachine {

	/** Returns whether the exact machine definition owns the ME fluid configuration protocol. */
	fun supportsMEFluidConfigActions(): Boolean

	/** Returns the fixed number of addressable configuration slots. */
	fun getMEFluidConfigSlotCount(): Int

	/** Returns whether auto-pull currently owns all configuration slots. */
	fun isMEFluidConfigAutoPull(): Boolean

	/** Returns whether this target is the stocking hatch variant. */
	fun isMEFluidStocking(): Boolean

	/** Validates a complete set or clear operation before any authoritative mutation occurs. */
	fun canSetMEFluidConfig(slot: Int, fluid: FluidStack): Boolean

	/** Applies one previously validated set or clear operation. */
	fun setMEFluidConfig(slot: Int, fluid: FluidStack)

	/** Validates that [slot] still owns [expectedFluid] before changing its positive target amount. */
	fun canSetMEFluidConfigAmount(slot: Int, expectedFluid: FluidStack, amount: Int): Boolean

	/** Applies one amount update after revalidating the expected fluid identity. */
	fun setMEFluidConfigAmount(slot: Int, expectedFluid: FluidStack, amount: Int)

	/** Applies one previously validated stocking auto-pull state. */
	fun setMEFluidAutoPull(autoPull: Boolean)
}

/** Owns the ME fluid configuration wire protocol and its fail-fast server handlers. */
object MEFluidConfigActions {

	private val SET_CONFIG_ACTION = GTCEu.id("set_me_fluid_config")
	private val SET_AMOUNT_ACTION = GTCEu.id("set_me_fluid_config_amount")
	private val SET_AUTO_PULL_ACTION = GTCEu.id("set_me_fluid_auto_pull")
	private val SLOT_FIELD = SyncFieldData.key("slot")
	private val AMOUNT_FIELD = SyncFieldData.key("amount")
	private val AUTO_PULL_FIELD = SyncFieldData.key("autoPull")

	init {
		SyncActionDispatchers.server().register(SetConfigHandler)
		SyncActionDispatchers.server().register(SetAmountHandler)
		SyncActionDispatchers.server().register(SetAutoPullHandler)
	}

	/** Forces action registration from the owning machine's static initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one exact set or clear config action for [slot]. */
	@JvmStatic
	fun createSetConfigAction(slot: Int, fluid: FluidStack): SyncActionData {
		require(slot in 0 until AEFluidConfigSnapshot.SLOT_COUNT) {
			"ME fluid configuration slot is out of range: $slot"
		}
		val fields = SyncFieldData.builder().put(SLOT_FIELD, JsonPrimitive(slot)).build()
		val copiedFluid = if (fluid.isEmpty) FluidStack.EMPTY else fluid.copy()
		val payload =
			DataComponentMap
				.builder()
				.set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
				.set(GTDataComponents.FLUID_CONTENT.get(), SimpleFluidContent.copyOf(copiedFluid))
				.build()
		val sequence = slot * 31 + FluidStack.hashFluidAndComponents(copiedFluid) * 31 + copiedFluid.amount
		return SyncActionData(SET_CONFIG_ACTION, sequence, payload)
	}

	/** Creates one positive integer amount update bound to the fluid currently expected in [slot]. */
	@JvmStatic
	fun createSetAmountAction(slot: Int, expectedFluid: FluidStack, amount: Int): SyncActionData {
		require(slot in 0 until AEFluidConfigSnapshot.SLOT_COUNT) {
			"ME fluid configuration slot is out of range: $slot"
		}
		require(!expectedFluid.isEmpty) { "ME fluid configuration amount requires an expected fluid." }
		require(amount > 0) { "ME fluid configuration amount must be positive: $amount" }
		val fields =
			SyncFieldData
				.builder()
				.put(SLOT_FIELD, JsonPrimitive(slot))
				.put(AMOUNT_FIELD, JsonPrimitive(amount))
				.build()
		val normalizedFluid = expectedFluid.copyWithAmount(1)
		val payload =
			DataComponentMap
				.builder()
				.set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
				.set(GTDataComponents.FLUID_CONTENT.get(), SimpleFluidContent.copyOf(normalizedFluid))
				.build()
		val sequence = slot * 31 + FluidStack.hashFluidAndComponents(normalizedFluid) * 31 + amount
		return SyncActionData(SET_AMOUNT_ACTION, sequence, payload)
	}

	/** Creates one stocking auto-pull toggle action. */
	@JvmStatic
	fun createSetAutoPullAction(autoPull: Boolean): SyncActionData {
		val fields = SyncFieldData.builder().put(AUTO_PULL_FIELD, JsonPrimitive(autoPull)).build()
		return SyncActionData(
			SET_AUTO_PULL_ACTION,
			if (autoPull) 1 else 0,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private abstract class MEFluidActionHandler : SyncActionHandler {

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is MEFluidConfigActionTarget

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator && target(context).supportsMEFluidConfigActions()

		protected fun target(context: SyncActionContext): MEFluidConfigActionTarget = context.holder as? MEFluidConfigActionTarget
			?: throw IllegalStateException("ME fluid action received a non-ME-fluid holder.")

		protected fun validSlot(context: SyncActionContext): Int? {
			val slot = readExactInt(requireFieldData(context.payload()), SLOT_FIELD) ?: return null
			return if (slot in 0 until target(context).getMEFluidConfigSlotCount()) slot else null
		}
	}

	private object SetConfigHandler : MEFluidActionHandler() {

		override fun actionId(): ResourceLocation = SET_CONFIG_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return fields.fields.size == 1 && readExactInt(fields, SLOT_FIELD) != null &&
				payload.has(GTDataComponents.FLUID_CONTENT.get())
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean {
			if (!super.mayExecute(player, context)) return false
			val slot = validSlot(context) ?: return false
			val target = target(context)
			val fluid = requireFluid(context.payload())
			return !target.isMEFluidConfigAutoPull() && target.canSetMEFluidConfig(slot, fluid)
		}

		override fun execute(context: SyncActionContext) {
			target(context).setMEFluidConfig(requireSlot(context), requireFluid(context.payload()))
		}
	}

	private object SetAmountHandler : MEFluidActionHandler() {

		override fun actionId(): ResourceLocation = SET_AMOUNT_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			val amount = readExactInt(fields, AMOUNT_FIELD)
			return fields.fields.size == 2 && readExactInt(fields, SLOT_FIELD) != null && amount != null && amount > 0 &&
				readExpectedFluid(payload) != null
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean {
			if (!super.mayExecute(player, context)) return false
			val slot = validSlot(context) ?: return false
			val fields = requireFieldData(context.payload())
			return target(context).canSetMEFluidConfigAmount(
				slot,
				requireExpectedFluid(context.payload()),
				requireExactInt(fields, AMOUNT_FIELD),
			)
		}

		override fun execute(context: SyncActionContext) {
			val fields = requireFieldData(context.payload())
			target(context).setMEFluidConfigAmount(
				requireSlot(context),
				requireExpectedFluid(context.payload()),
				requireExactInt(fields, AMOUNT_FIELD),
			)
		}
	}

	private object SetAutoPullHandler : MEFluidActionHandler() {

		override fun actionId(): ResourceLocation = SET_AUTO_PULL_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return fields.fields.size == 1 && readBoolean(fields, AUTO_PULL_FIELD) != null
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = super.mayExecute(player, context) && target(context).isMEFluidStocking()

		override fun execute(context: SyncActionContext) {
			target(context).setMEFluidAutoPull(requireBoolean(requireFieldData(context.payload()), AUTO_PULL_FIELD))
		}
	}

	private fun requireFieldData(payload: DataComponentMap): SyncFieldData = payload[GTDataComponents.SYNC_FIELD_DATA.get()]
		?: throw IllegalStateException("ME fluid action payload is missing field data.")

	private fun requireSlot(context: SyncActionContext): Int = readExactInt(requireFieldData(context.payload()), SLOT_FIELD)
		?: throw IllegalStateException("ME fluid action payload is missing an exact integer slot.")

	private fun requireExactInt(fields: SyncFieldData, field: ResourceLocation): Int = readExactInt(fields, field)
		?: throw IllegalStateException("ME fluid action payload is missing exact integer field $field.")

	private fun requireBoolean(fields: SyncFieldData, field: ResourceLocation): Boolean = readBoolean(fields, field)
		?: throw IllegalStateException("ME fluid action payload is missing boolean field $field.")

	private fun requireExpectedFluid(payload: DataComponentMap): FluidStack = readExpectedFluid(payload)
		?: throw IllegalStateException("ME fluid amount action payload is missing its expected fluid.")

	private fun requireFluid(payload: DataComponentMap): FluidStack {
		if (!payload.has(GTDataComponents.FLUID_CONTENT.get())) {
			throw IllegalStateException("ME fluid action payload is missing fluid content.")
		}
		return payload.getOrDefault(GTDataComponents.FLUID_CONTENT.get(), SimpleFluidContent.EMPTY).copy()
	}

	private fun readExpectedFluid(payload: DataComponentMap): FluidStack? {
		val content = payload[GTDataComponents.FLUID_CONTENT.get()] ?: return null
		val fluid = content.copy()
		return if (fluid.isEmpty) null else fluid.copyWithAmount(1)
	}

	private fun readExactInt(fields: SyncFieldData, field: ResourceLocation): Int? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		if (!primitive.isNumber) return null
		return try {
			primitive.asBigDecimal.intValueExact()
		} catch (exception: NumberFormatException) {
			logInvalidInteger(field, primitive, exception)
			null
		} catch (exception: ArithmeticException) {
			logInvalidInteger(field, primitive, exception)
			null
		}
	}

	private fun logInvalidInteger(field: ResourceLocation, primitive: JsonPrimitive, exception: RuntimeException) {
		GTCEu.LOGGER.warn("ME fluid action rejected invalid integer {} for field {}", primitive, field, exception)
	}

	private fun readBoolean(fields: SyncFieldData, field: ResourceLocation): Boolean? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		return if (primitive.isBoolean) primitive.asBoolean else null
	}
}

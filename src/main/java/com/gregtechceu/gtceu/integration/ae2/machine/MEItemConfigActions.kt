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
import net.minecraft.world.item.ItemStack

import com.google.gson.JsonPrimitive
import org.jetbrains.annotations.ApiStatus

/**
 * Exposes only the authoritative ME item configuration operations used by holder-scoped actions.
 */
@ApiStatus.Internal
interface MEItemConfigActionTarget : LDLib2FancyActionMachine {

	/** Returns whether the exact machine definition owns the ME item configuration protocol. */
	fun supportsMEItemConfigActions(): Boolean

	/** Returns the fixed number of addressable configuration slots. */
	fun getMEItemConfigSlotCount(): Int

	/** Returns whether auto-pull currently owns all configuration slots. */
	fun isMEItemConfigAutoPull(): Boolean

	/** Returns whether this target is the stocking bus variant. */
	fun isMEItemStocking(): Boolean

	/** Validates a complete set or clear operation before any authoritative mutation occurs. */
	fun canSetMEItemConfig(slot: Int, item: ItemStack): Boolean

	/** Applies one previously validated set or clear operation. */
	fun setMEItemConfig(slot: Int, item: ItemStack)

	/** Validates that [slot] still owns [expectedItem] before changing its positive target amount. */
	fun canSetMEItemConfigAmount(slot: Int, expectedItem: ItemStack, amount: Int): Boolean

	/** Applies one amount update after revalidating the expected item identity. */
	fun setMEItemConfigAmount(slot: Int, expectedItem: ItemStack, amount: Int)

	/** Validates the cursor, slot, and expected stock identity before one pickup. */
	fun canPickupMEItemConfigStock(player: ServerPlayer, slot: Int, expectedItem: ItemStack): Boolean

	/** Moves one validated saturated stock amount to [player]'s empty cursor. */
	fun pickupMEItemConfigStock(player: ServerPlayer, slot: Int, expectedItem: ItemStack)

	/** Applies one previously validated stocking auto-pull state. */
	fun setMEItemAutoPull(autoPull: Boolean)
}

/** Owns the ME item configuration wire protocol and its fail-fast server handlers. */
object MEItemConfigActions {

	private val SET_CONFIG_ACTION = GTCEu.id("set_me_item_config")
	private val SET_AMOUNT_ACTION = GTCEu.id("set_me_item_config_amount")
	private val PICKUP_STOCK_ACTION = GTCEu.id("pickup_me_item_config_stock")
	private val SET_AUTO_PULL_ACTION = GTCEu.id("set_me_item_auto_pull")
	private val SLOT_FIELD = SyncFieldData.key("slot")
	private val AMOUNT_FIELD = SyncFieldData.key("amount")
	private val AUTO_PULL_FIELD = SyncFieldData.key("autoPull")

	init {
		SyncActionDispatchers.server().register(SetConfigHandler)
		SyncActionDispatchers.server().register(SetAmountHandler)
		SyncActionDispatchers.server().register(PickupStockHandler)
		SyncActionDispatchers.server().register(SetAutoPullHandler)
	}

	/** Forces action registration from the owning machine's static initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one exact set or clear config action for [slot]. */
	@JvmStatic
	fun createSetConfigAction(slot: Int, item: ItemStack): SyncActionData {
		requireSlotRange(slot)
		val copiedItem = if (item.isEmpty) ItemStack.EMPTY else item.copy()
		val fields = SyncFieldData.builder().put(SLOT_FIELD, JsonPrimitive(slot)).build()
		val payload = itemPayload(fields, copiedItem)
		val sequence = slot * 31 + ItemStack.hashItemAndComponents(copiedItem) * 31 + copiedItem.count
		return SyncActionData(SET_CONFIG_ACTION, sequence, payload)
	}

	/** Creates one positive integer amount update bound to the item currently expected in [slot]. */
	@JvmStatic
	fun createSetAmountAction(slot: Int, expectedItem: ItemStack, amount: Int): SyncActionData {
		requireSlotRange(slot)
		require(!expectedItem.isEmpty) { "ME item configuration amount requires an expected item." }
		require(amount > 0) { "ME item configuration amount must be positive: $amount" }
		val fields =
			SyncFieldData
				.builder()
				.put(SLOT_FIELD, JsonPrimitive(slot))
				.put(AMOUNT_FIELD, JsonPrimitive(amount))
				.build()
		val normalizedItem = expectedItem.copyWithCount(1)
		val payload = itemPayload(fields, normalizedItem)
		val sequence = slot * 31 + ItemStack.hashItemAndComponents(normalizedItem) * 31 + amount
		return SyncActionData(SET_AMOUNT_ACTION, sequence, payload)
	}

	/** Creates one stock pickup bound to the item currently expected in [slot]. */
	@JvmStatic
	fun createPickupStockAction(slot: Int, expectedItem: ItemStack): SyncActionData {
		requireSlotRange(slot)
		require(!expectedItem.isEmpty) { "ME item stock pickup requires an expected item." }
		val fields = SyncFieldData.builder().put(SLOT_FIELD, JsonPrimitive(slot)).build()
		val normalizedItem = expectedItem.copyWithCount(1)
		val payload = itemPayload(fields, normalizedItem)
		val sequence = slot * 31 + ItemStack.hashItemAndComponents(normalizedItem)
		return SyncActionData(PICKUP_STOCK_ACTION, sequence, payload)
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

	private abstract class MEItemActionHandler : SyncActionHandler {

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is MEItemConfigActionTarget

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator && target(context).supportsMEItemConfigActions()

		protected fun target(context: SyncActionContext): MEItemConfigActionTarget = context.holder as? MEItemConfigActionTarget
			?: throw IllegalStateException("ME item action received a non-ME-item holder.")

		protected fun validSlot(context: SyncActionContext): Int? {
			val slot = readExactInt(requireFieldData(context.payload()), SLOT_FIELD) ?: return null
			return if (slot in 0 until target(context).getMEItemConfigSlotCount()) slot else null
		}
	}

	private object SetConfigHandler : MEItemActionHandler() {

		override fun actionId(): ResourceLocation = SET_CONFIG_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return fields.fields.size == 1 && readExactInt(fields, SLOT_FIELD) != null &&
				payload.has(GTDataComponents.PLACEHOLDER_ITEM_STACK.get())
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean {
			if (!super.mayExecute(player, context)) return false
			val slot = validSlot(context) ?: return false
			val target = target(context)
			return !target.isMEItemConfigAutoPull() &&
				target.canSetMEItemConfig(slot, requireConfigItem(context.payload()))
		}

		override fun execute(context: SyncActionContext) {
			target(context).setMEItemConfig(requireSlot(context), requireConfigItem(context.payload()))
		}
	}

	private object SetAmountHandler : MEItemActionHandler() {

		override fun actionId(): ResourceLocation = SET_AMOUNT_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			val amount = readExactInt(fields, AMOUNT_FIELD)
			return fields.fields.size == 2 && readExactInt(fields, SLOT_FIELD) != null && amount != null && amount > 0 &&
				readExpectedItem(payload) != null
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean {
			if (!super.mayExecute(player, context)) return false
			val slot = validSlot(context) ?: return false
			val target = target(context)
			val fields = requireFieldData(context.payload())
			return !target.isMEItemStocking() && !target.isMEItemConfigAutoPull() &&
				target.canSetMEItemConfigAmount(
					slot,
					requireExpectedItem(context.payload()),
					requireExactInt(fields, AMOUNT_FIELD),
				)
		}

		override fun execute(context: SyncActionContext) {
			val fields = requireFieldData(context.payload())
			target(context).setMEItemConfigAmount(
				requireSlot(context),
				requireExpectedItem(context.payload()),
				requireExactInt(fields, AMOUNT_FIELD),
			)
		}
	}

	private object PickupStockHandler : MEItemActionHandler() {

		override fun actionId(): ResourceLocation = PICKUP_STOCK_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return fields.fields.size == 1 && readExactInt(fields, SLOT_FIELD) != null &&
				readExpectedItem(payload) != null
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean {
			if (!super.mayExecute(player, context)) return false
			val slot = validSlot(context) ?: return false
			val target = target(context)
			return !target.isMEItemStocking() && !target.isMEItemConfigAutoPull() &&
				target.canPickupMEItemConfigStock(player, slot, requireExpectedItem(context.payload()))
		}

		override fun execute(context: SyncActionContext) {
			target(context).pickupMEItemConfigStock(
				context.player,
				requireSlot(context),
				requireExpectedItem(context.payload()),
			)
		}
	}

	private object SetAutoPullHandler : MEItemActionHandler() {

		override fun actionId(): ResourceLocation = SET_AUTO_PULL_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return fields.fields.size == 1 && readBoolean(fields, AUTO_PULL_FIELD) != null
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = super.mayExecute(player, context) && target(context).isMEItemStocking()

		override fun execute(context: SyncActionContext) {
			target(context).setMEItemAutoPull(requireBoolean(requireFieldData(context.payload()), AUTO_PULL_FIELD))
		}
	}

	private fun itemPayload(fields: SyncFieldData, item: ItemStack): DataComponentMap = DataComponentMap
		.builder()
		.set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
		.set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), item)
		.build()

	private fun requireSlotRange(slot: Int) {
		require(slot in 0 until AEItemConfigSnapshot.SLOT_COUNT) {
			"ME item configuration slot is out of range: $slot"
		}
	}

	private fun requireFieldData(payload: DataComponentMap): SyncFieldData = payload[GTDataComponents.SYNC_FIELD_DATA.get()]
		?: throw IllegalStateException("ME item action payload is missing field data.")

	private fun requireSlot(context: SyncActionContext): Int = readExactInt(requireFieldData(context.payload()), SLOT_FIELD)
		?: throw IllegalStateException("ME item action payload is missing an exact integer slot.")

	private fun requireExactInt(fields: SyncFieldData, field: ResourceLocation): Int = readExactInt(fields, field)
		?: throw IllegalStateException("ME item action payload is missing exact integer field $field.")

	private fun requireBoolean(fields: SyncFieldData, field: ResourceLocation): Boolean = readBoolean(fields, field)
		?: throw IllegalStateException("ME item action payload is missing boolean field $field.")

	private fun requireConfigItem(payload: DataComponentMap): ItemStack {
		if (!payload.has(GTDataComponents.PLACEHOLDER_ITEM_STACK.get())) {
			throw IllegalStateException("ME item config action payload is missing item content.")
		}
		val item = payload.getOrDefault(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), ItemStack.EMPTY)
		return if (item.isEmpty) ItemStack.EMPTY else item.copy()
	}

	private fun requireExpectedItem(payload: DataComponentMap): ItemStack = readExpectedItem(payload)
		?: throw IllegalStateException("ME item action payload is missing its expected item.")

	private fun readExpectedItem(payload: DataComponentMap): ItemStack? {
		val item = payload[GTDataComponents.PLACEHOLDER_ITEM_STACK.get()] ?: return null
		return if (item.isEmpty || item.count != 1) null else item.copy()
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
		GTCEu.LOGGER.warn("ME item action rejected invalid integer {} for field {}", primitive, field, exception)
	}

	private fun readBoolean(fields: SyncFieldData, field: ResourceLocation): Boolean? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		return if (primitive.isBoolean) primitive.asBoolean else null
	}
}

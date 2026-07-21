package com.gregtechceu.gtceu.common.machine.storage

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
import net.minecraft.world.item.ItemStack

import com.google.gson.JsonPrimitive
import org.jetbrains.annotations.ApiStatus

/** Exposes the four quantum chest UI operations that their Kotlin action handlers may invoke. */
@ApiStatus.Internal
interface QuantumChestActionTarget {

	/** Imports a validated requested item count from the opening player's cursor. */
	fun clickQuantumChestImportSlot(player: ServerPlayer, requestedItem: ItemStack, rightClick: Boolean)

	/** Exports one validated stack of stored items to the opening player. */
	fun exportQuantumChestItem(player: ServerPlayer)

	/** Applies one validated phantom locked-item selection or rejects an incompatible selection. */
	fun setQuantumChestLockedItem(item: ItemStack)

	/** Applies one validated lock-toggle command. */
	fun setQuantumChestLocked(locked: Boolean)
}

/** Owns the wire protocol and server handlers for the quantum chest UI state. */
object QuantumChestMachineActions {

	private val CLICK_QUANTUM_CHEST_IMPORT_SLOT_ACTION = GTCEu.id("click_quantum_chest_import_slot")
	private val EXPORT_QUANTUM_CHEST_ITEM_ACTION = GTCEu.id("export_quantum_chest_item")
	private val SET_QUANTUM_CHEST_LOCKED_ITEM_ACTION = GTCEu.id("set_quantum_chest_locked_item")
	private val SET_QUANTUM_CHEST_LOCKED_ACTION = GTCEu.id("set_quantum_chest_locked")
	private val RIGHT_CLICK_FIELD = SyncFieldData.key("rightClick")
	private val LOCKED_FIELD = SyncFieldData.key("locked")

	init {
		SyncActionDispatchers.server().register(QuantumChestImportSlotActionHandler)
		SyncActionDispatchers.server().register(QuantumChestExportItemActionHandler)
		SyncActionDispatchers.server().register(QuantumChestLockedItemActionHandler)
		SyncActionDispatchers.server().register(QuantumChestLockedActionHandler)
	}

	/** Forces this object's static registration from the owning machine's class initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one client action for an import-slot cursor interaction. */
	@JvmStatic
	fun createClickQuantumChestImportSlotAction(carried: ItemStack, rightClick: Boolean): SyncActionData {
		val item = carried.copy()
		val fields =
			SyncFieldData.builder()
				.put(RIGHT_CLICK_FIELD, JsonPrimitive(rightClick))
				.build()
		val payload =
			DataComponentMap
				.builder()
				.set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
				.set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), item)
				.build()
		val sequence = ItemStack.hashItemAndComponents(item) * 31 + item.count
		return SyncActionData(
			CLICK_QUANTUM_CHEST_IMPORT_SLOT_ACTION,
			sequence * 31 + if (rightClick) 1 else 0,
			payload,
		)
	}

	/** Creates the zero-argument client action that exports one stored stack. */
	@JvmStatic
	fun createExportQuantumChestItemAction(): SyncActionData = SyncActionData(EXPORT_QUANTUM_CHEST_ITEM_ACTION, 0, DataComponentMap.EMPTY)

	/** Creates one client action for a phantom locked-item selection. */
	@JvmStatic
	fun createSetQuantumChestLockedItemAction(item: ItemStack): SyncActionData {
		val locked = if (item.isEmpty) ItemStack.EMPTY else item.copyWithCount(1)
		val payload =
			DataComponentMap
				.builder()
				.set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), locked)
				.build()
		return SyncActionData(
			SET_QUANTUM_CHEST_LOCKED_ITEM_ACTION,
			ItemStack.hashItemAndComponents(locked),
			payload,
		)
	}

	/** Creates one client action for the lock toggle. */
	@JvmStatic
	fun createSetQuantumChestLockedAction(locked: Boolean): SyncActionData {
		val fields =
			SyncFieldData.builder()
				.put(LOCKED_FIELD, JsonPrimitive(locked))
				.build()
		return SyncActionData(
			SET_QUANTUM_CHEST_LOCKED_ACTION,
			if (locked) 1 else 0,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private abstract class QuantumChestActionHandler : SyncActionHandler {

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is QuantumChestActionTarget

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		protected fun target(context: SyncActionContext): QuantumChestActionTarget = context.holder as? QuantumChestActionTarget
			?: throw IllegalStateException("Quantum chest action received a non-quantum-chest machine.")
	}

	private object QuantumChestImportSlotActionHandler : QuantumChestActionHandler() {

		override fun actionId(): ResourceLocation = CLICK_QUANTUM_CHEST_IMPORT_SLOT_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			if (!payload.has(GTDataComponents.PLACEHOLDER_ITEM_STACK.get())) {
				return false
			}
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return readBoolean(fields, RIGHT_CLICK_FIELD) != null
		}

		override fun execute(context: SyncActionContext) {
			target(context).clickQuantumChestImportSlot(
				context.player,
				requireItemStack(context.payload()),
				requireBoolean(context.payload(), RIGHT_CLICK_FIELD),
			)
		}
	}

	private object QuantumChestExportItemActionHandler : QuantumChestActionHandler() {

		override fun actionId(): ResourceLocation = EXPORT_QUANTUM_CHEST_ITEM_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean = payload.isEmpty

		override fun execute(context: SyncActionContext) {
			target(context).exportQuantumChestItem(context.player)
		}
	}

	private object QuantumChestLockedItemActionHandler : QuantumChestActionHandler() {

		override fun actionId(): ResourceLocation = SET_QUANTUM_CHEST_LOCKED_ITEM_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean = payload.has(GTDataComponents.PLACEHOLDER_ITEM_STACK.get())

		override fun execute(context: SyncActionContext) {
			target(context).setQuantumChestLockedItem(requireItemStack(context.payload()))
		}
	}

	private object QuantumChestLockedActionHandler : QuantumChestActionHandler() {

		override fun actionId(): ResourceLocation = SET_QUANTUM_CHEST_LOCKED_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return readBoolean(fields, LOCKED_FIELD) != null
		}

		override fun execute(context: SyncActionContext) {
			target(context).setQuantumChestLocked(requireBoolean(context.payload(), LOCKED_FIELD))
		}
	}

	private fun requireFieldData(payload: DataComponentMap): SyncFieldData = payload[GTDataComponents.SYNC_FIELD_DATA.get()]
		?: throw IllegalStateException("Quantum chest action payload is missing field data.")

	private fun requireItemStack(payload: DataComponentMap): ItemStack {
		if (!payload.has(GTDataComponents.PLACEHOLDER_ITEM_STACK.get())) {
			throw IllegalStateException("Quantum chest item action payload is missing item stack.")
		}
		return payload.getOrDefault(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), ItemStack.EMPTY)
	}

	private fun requireBoolean(payload: DataComponentMap, field: ResourceLocation): Boolean = readBoolean(requireFieldData(payload), field)
		?: throw IllegalStateException("Quantum chest action payload is missing $field.")

	private fun readBoolean(fields: SyncFieldData, field: ResourceLocation): Boolean? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		return if (primitive.isBoolean) primitive.asBoolean else null
	}
}

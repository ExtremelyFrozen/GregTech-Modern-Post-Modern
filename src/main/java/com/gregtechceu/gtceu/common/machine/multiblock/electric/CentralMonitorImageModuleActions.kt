package com.gregtechceu.gtceu.common.machine.multiblock.electric

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.item.IComponentItem
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.data.GTDataComponents
import com.gregtechceu.gtceu.common.item.modules.ImageModuleBehaviour

import net.minecraft.core.UUIDUtil
import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import com.mojang.serialization.JsonOps
import org.jetbrains.annotations.ApiStatus

import java.util.UUID

/**
 * Exposes the compare-and-set operation required to change one Central Monitor image module URL.
 *
 * The handler passes both the opening module snapshot and the slot incarnation so a replaced module can never receive
 * configuration intended for the item that previously occupied the slot.
 */
@ApiStatus.Internal
interface CentralMonitorImageModuleActionHost {

	/** Returns the incarnation that identifies this exact placed Central Monitor to an opened client page. */
	fun getCentralMonitorActionIncarnation(): UUID

	/** Checks the complete image module request against the current group, slot, item, and URL state. */
	fun canSetCentralMonitorImageModuleUrl(groupIdentity: UUID, moduleSlotIncarnation: UUID, expectedModule: ItemStack, expectedUrl: String?, requestedUrl: String): Boolean

	/** Applies the image module request if every current-state check still succeeds. */
	fun setCentralMonitorImageModuleUrl(groupIdentity: UUID, moduleSlotIncarnation: UUID, expectedModule: ItemStack, expectedUrl: String?, requestedUrl: String): Boolean

	/** Publishes the authoritative groups again after a parsed action no longer matches current state. */
	fun resyncCentralMonitorImageModuleState()
}

/** Owns the strict wire protocol and server handler for Central Monitor image module URL changes. */
object CentralMonitorImageModuleActions {

	private val SET_IMAGE_MODULE_URL_ACTION = GTCEu.id("set_central_monitor_image_module_url")
	private val HOLDER_INCARNATION_FIELD = SyncFieldData.key("holder_incarnation")
	private val GROUP_IDENTITY_FIELD = SyncFieldData.key("group_identity")
	private val MODULE_SLOT_INCARNATION_FIELD = SyncFieldData.key("module_slot_incarnation")
	private val EXPECTED_URL_FIELD = SyncFieldData.key("expected_url")
	private val REQUESTED_URL_FIELD = SyncFieldData.key("requested_url")
	private const val MAX_URL_LENGTH = 32_767

	init {
		SyncActionDispatchers.server().register(SetImageModuleUrlHandler)
	}

	/** Forces handler registration from the owning Central Monitor's static initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one ordered compare-and-set request for the image module currently occupying a group slot. */
	@JvmStatic
	fun createSetImageModuleUrlAction(holderIncarnation: UUID, groupIdentity: UUID, moduleSlotIncarnation: UUID, expectedModule: ItemStack, requestedUrl: String, sequence: Int): SyncActionData {
		require(sequence >= 0) { "Central Monitor image module sequence must be non-negative: $sequence" }
		require(isValidUrl(requestedUrl)) {
			"Central Monitor image module URL exceeds $MAX_URL_LENGTH characters."
		}
		val expectedSnapshot = captureExpectedModule(expectedModule)
		require(isImageModule(expectedSnapshot)) {
			"Central Monitor image module action requires an image module snapshot."
		}
		val expectedUrl = expectedSnapshot[GTDataComponents.IMAGE_MODULE_URL.get()]
		require(expectedUrl != requestedUrl) {
			"Central Monitor image module action must change the URL."
		}

		val fields =
			SyncFieldData.builder()
				.put(HOLDER_INCARNATION_FIELD, encodeUuid(holderIncarnation))
				.put(GROUP_IDENTITY_FIELD, encodeUuid(groupIdentity))
				.put(MODULE_SLOT_INCARNATION_FIELD, encodeUuid(moduleSlotIncarnation))
				.put(EXPECTED_URL_FIELD, expectedUrl?.let(::JsonPrimitive) ?: JsonNull.INSTANCE)
				.put(REQUESTED_URL_FIELD, JsonPrimitive(requestedUrl))
				.build()
		val payload =
			DataComponentMap.builder()
				.set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
				.set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), expectedSnapshot)
				.build()
		return SyncActionData(SET_IMAGE_MODULE_URL_ACTION, sequence, payload)
	}

	/** Captures the stable item state used by module configuration compare-and-set requests. */
	@JvmStatic
	fun captureExpectedModule(module: ItemStack): ItemStack {
		require(hasValidStackCount(module)) {
			"Central Monitor module snapshot must contain a valid non-empty item stack."
		}
		return module.copy().also { snapshot -> snapshot.remove(GTDataComponents.TEXT_LINE_LIST.get()) }
	}

	/** Compares a current slot occupant with a previously captured stable module snapshot. */
	@JvmStatic
	fun matchesExpectedModule(current: ItemStack, expected: ItemStack): Boolean {
		if (!hasValidStackCount(current) || !hasValidStackCount(expected)) return false
		return ItemStack.matches(captureExpectedModule(current), expected)
	}

	/** Returns whether the stack exposes the image monitor module behaviour targeted by this action. */
	@JvmStatic
	fun isImageModule(stack: ItemStack): Boolean {
		val componentItem = stack.item as? IComponentItem ?: return false
		return componentItem.components.any { component -> component is ImageModuleBehaviour }
	}

	private object SetImageModuleUrlHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_IMAGE_MODULE_URL_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is CentralMonitorImageModuleActionHost

		override fun acceptsPayload(payload: DataComponentMap): Boolean = readCommand(payload) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean {
			val command = readCommand(context.payload()) ?: return false
			val target = target(context)
			if (context.sequence() < 0 || player.isSpectator) return false
			if (target.getCentralMonitorActionIncarnation() != command.holderIncarnation) {
				target.resyncCentralMonitorImageModuleState()
				return false
			}
			val accepted = target.canSetCentralMonitorImageModuleUrl(
				command.groupIdentity,
				command.moduleSlotIncarnation,
				command.expectedModule,
				command.expectedUrl,
				command.requestedUrl,
			)
			if (!accepted) {
				target.resyncCentralMonitorImageModuleState()
			}
			return accepted
		}

		override fun execute(context: SyncActionContext) {
			val command = requireCommand(context.payload())
			val target = target(context)
			val applied = target.setCentralMonitorImageModuleUrl(
				command.groupIdentity,
				command.moduleSlotIncarnation,
				command.expectedModule,
				command.expectedUrl,
				command.requestedUrl,
			)
			if (!applied) {
				target.resyncCentralMonitorImageModuleState()
			}
			check(applied) {
				"Central Monitor image module change became invalid after permission validation."
			}
		}

		private fun target(context: SyncActionContext): CentralMonitorImageModuleActionHost = context.holder as? CentralMonitorImageModuleActionHost
			?: throw IllegalStateException("Central Monitor image module action received a non-monitor holder.")
	}

	private fun requireCommand(payload: DataComponentMap): ImageModuleCommand = readCommand(payload)
		?: throw IllegalStateException("Central Monitor image module action omitted a valid command payload.")

	private fun readCommand(payload: DataComponentMap): ImageModuleCommand? {
		if (payload.size() != 2) return null
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		val expectedModule = payload[GTDataComponents.PLACEHOLDER_ITEM_STACK.get()]?.copy() ?: return null
		if (fields.fields.size != 5 || !hasValidStackCount(expectedModule)) return null
		if (!ItemStack.matches(
				captureExpectedModule(expectedModule),
				expectedModule,
			) || !isImageModule(expectedModule)
		) {
			return null
		}

		val holderIncarnation = readUuid(fields[HOLDER_INCARNATION_FIELD], HOLDER_INCARNATION_FIELD) ?: return null
		val groupIdentity = readUuid(fields[GROUP_IDENTITY_FIELD], GROUP_IDENTITY_FIELD) ?: return null
		val moduleSlotIncarnation =
			readUuid(fields[MODULE_SLOT_INCARNATION_FIELD], MODULE_SLOT_INCARNATION_FIELD) ?: return null
		val expectedUrl = readNullableUrl(fields[EXPECTED_URL_FIELD]) ?: return null
		val requestedUrl = readUrl(fields[REQUESTED_URL_FIELD]) ?: return null
		val snapshotUrl = expectedModule[GTDataComponents.IMAGE_MODULE_URL.get()]
		if (snapshotUrl != expectedUrl.value || expectedUrl.value == requestedUrl) return null
		return ImageModuleCommand(
			holderIncarnation,
			groupIdentity,
			moduleSlotIncarnation,
			expectedModule,
			expectedUrl.value,
			requestedUrl,
		)
	}

	private fun encodeUuid(value: UUID): JsonElement = UUIDUtil.CODEC
		.encodeStart(JsonOps.INSTANCE, value)
		.getOrThrow()

	private fun readUuid(value: JsonElement?, field: ResourceLocation): UUID? {
		if (value == null) return null
		return try {
			UUIDUtil.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow()
		} catch (exception: RuntimeException) {
			GTCEu.LOGGER.warn("Central Monitor image module action rejected invalid UUID field {}", field, exception)
			null
		}
	}

	private fun readNullableUrl(value: JsonElement?): NullableUrl? {
		if (value == null) return null
		if (value.isJsonNull) return NullableUrl(null)
		return readUrl(value)?.let(::NullableUrl)
	}

	private fun readUrl(value: JsonElement?): String? {
		val primitive = value as? JsonPrimitive ?: return null
		if (!primitive.isString) return null
		return primitive.asString.takeIf(::isValidUrl)
	}

	/** Returns whether a URL fits the network codec used by the image module data component. */
	@JvmStatic
	fun isValidUrl(value: String): Boolean = value.length <= MAX_URL_LENGTH

	private fun hasValidStackCount(stack: ItemStack): Boolean = !stack.isEmpty && stack.count <= stack.maxStackSize

	private data class NullableUrl(val value: String?)

	private data class ImageModuleCommand(
		val holderIncarnation: UUID,
		val groupIdentity: UUID,
		val moduleSlotIncarnation: UUID,
		val expectedModule: ItemStack,
		val expectedUrl: String?,
		val requestedUrl: String,
	)
}

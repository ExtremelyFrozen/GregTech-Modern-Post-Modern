package com.gregtechceu.gtceu.common.machine.multiblock.electric

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.item.IComponentItem
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.data.GTDataComponents
import com.gregtechceu.gtceu.common.item.datacomponents.TextLineList
import com.gregtechceu.gtceu.common.item.modules.TextModuleBehaviour

import net.minecraft.core.UUIDUtil
import net.minecraft.core.component.DataComponentMap
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mojang.serialization.JsonOps
import org.jetbrains.annotations.ApiStatus

import java.math.BigDecimal
import java.util.UUID

/**
 * Exposes the compare-and-set operation required to change one Central Monitor text module configuration.
 *
 * The expected module snapshot retains editable text and every other stable component while excluding only values
 * derived by the server tick. This prevents one editor from overwriting a concurrent configuration change.
 */
@ApiStatus.Internal
interface CentralMonitorTextModuleActionHost {

	/** Returns the incarnation that identifies this exact placed Central Monitor to an opened client page. */
	fun getCentralMonitorActionIncarnation(): UUID

	/** Checks the complete request against the current group, slot, revision, module snapshot, and requested configuration. */
	fun canSetCentralMonitorTextModuleConfiguration(
		groupIdentity: UUID,
		moduleSlotIncarnation: UUID,
		expectedConfigurationRevision: Long,
		expectedModule: ItemStack,
		requestedConfiguration: TextLineList,
	): Boolean

	/** Applies the text configuration if every current-state check still succeeds. */
	fun setCentralMonitorTextModuleConfiguration(
		groupIdentity: UUID,
		moduleSlotIncarnation: UUID,
		expectedConfigurationRevision: Long,
		expectedModule: ItemStack,
		requestedConfiguration: TextLineList,
	): Boolean

	/** Publishes the authoritative groups again after a parsed action no longer matches current state. */
	fun resyncCentralMonitorTextModuleState()
}

/** Owns the strict wire protocol and server handler for Central Monitor text module configuration changes. */
object CentralMonitorTextModuleActions {

	private val SET_TEXT_MODULE_CONFIGURATION_ACTION = GTCEu.id("set_central_monitor_text_module_configuration")
	private val HOLDER_INCARNATION_FIELD = SyncFieldData.key("holder_incarnation")
	private val GROUP_IDENTITY_FIELD = SyncFieldData.key("group_identity")
	private val MODULE_SLOT_INCARNATION_FIELD = SyncFieldData.key("module_slot_incarnation")
	private val EXPECTED_CONFIGURATION_REVISION_FIELD = SyncFieldData.key("expected_configuration_revision")
	private val REQUESTED_CONFIGURATION_FIELD = SyncFieldData.key("requested_configuration")
	private const val CONFIGURATION_LINES_FIELD = "lines"
	private const val CONFIGURATION_SCALE_FIELD = "scale"
	private const val MIN_TEXT_SCALE = 0.0001f
	private const val MAX_TEXT_SCALE = 1000.0f
	private val MIN_TEXT_SCALE_DECIMAL = BigDecimal("0.0001")
	private val MAX_TEXT_SCALE_DECIMAL = BigDecimal("1000")

	init {
		SyncActionDispatchers.server().register(SetTextModuleConfigurationHandler)
	}

	/** Forces handler registration from the owning Central Monitor's static initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one ordered compare-and-set request for the text module currently occupying a group slot. */
	@JvmStatic
	fun createSetTextModuleConfigurationAction(
		holderIncarnation: UUID,
		groupIdentity: UUID,
		moduleSlotIncarnation: UUID,
		expectedConfigurationRevision: Long,
		expectedModule: ItemStack,
		requestedConfiguration: TextLineList,
		sequence: Int,
	): SyncActionData {
		require(sequence >= 0) { "Central Monitor text module sequence must be non-negative: $sequence" }
		require(expectedConfigurationRevision in 0 until Long.MAX_VALUE) {
			"Central Monitor text configuration revision must be non-negative and incrementable: $expectedConfigurationRevision"
		}
		val expectedSnapshot = captureExpectedModule(expectedModule)
		require(isTextModule(expectedSnapshot)) {
			"Central Monitor text module action requires a text module snapshot."
		}
		val canonicalRequest = canonicalizeConfiguration(requestedConfiguration)
		require(expectedSnapshot[GTDataComponents.FORMAT_STRING_LIST.get()] != canonicalRequest) {
			"Central Monitor text module action must change the editable configuration."
		}
		val encodedRequest = encodeConfiguration(canonicalRequest)
		SyncFieldData.requireFieldValueWithinNetworkLimit(REQUESTED_CONFIGURATION_FIELD, encodedRequest)

		val fields =
			SyncFieldData.builder()
				.put(HOLDER_INCARNATION_FIELD, encodeUuid(holderIncarnation))
				.put(GROUP_IDENTITY_FIELD, encodeUuid(groupIdentity))
				.put(MODULE_SLOT_INCARNATION_FIELD, encodeUuid(moduleSlotIncarnation))
				.put(EXPECTED_CONFIGURATION_REVISION_FIELD, JsonPrimitive(expectedConfigurationRevision))
				.put(REQUESTED_CONFIGURATION_FIELD, encodedRequest)
				.build()
		val payload =
			DataComponentMap.builder()
				.set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
				.set(GTDataComponents.PLACEHOLDER_ITEM_STACK.get(), expectedSnapshot)
				.build()
		return SyncActionData(SET_TEXT_MODULE_CONFIGURATION_ACTION, sequence, payload)
	}

	/** Creates the plain-literal configuration sent by either Central Monitor editor implementation. */
	@JvmStatic
	fun createConfiguration(lines: List<String>, scale: Float): TextLineList {
		require(isValidScale(scale)) {
			"Central Monitor text module scale must be finite and between $MIN_TEXT_SCALE and $MAX_TEXT_SCALE: $scale"
		}
		return TextLineList(lines.map(Component::literal), scale)
	}

	/** Captures stable module state while excluding only values derived by the server tick. */
	@JvmStatic
	fun captureExpectedModule(module: ItemStack): ItemStack {
		require(hasValidStackCount(module)) {
			"Central Monitor text module snapshot must contain a valid non-empty item stack."
		}
		return module.copy().also { snapshot ->
			snapshot.remove(GTDataComponents.TEXT_LINE_LIST.get())
			snapshot.remove(GTDataComponents.PLACEHOLDER_UUID.get())
		}
	}

	/** Compares a current slot occupant with a previously captured stable text module snapshot. */
	@JvmStatic
	fun matchesExpectedModule(current: ItemStack, expected: ItemStack): Boolean {
		if (!hasValidStackCount(current) || !hasValidStackCount(expected)) return false
		return ItemStack.matches(captureExpectedModule(current), expected)
	}

	/** Returns whether the stack exposes the text monitor module behaviour targeted by this action. */
	@JvmStatic
	fun isTextModule(stack: ItemStack): Boolean {
		val componentItem = stack.item as? IComponentItem ?: return false
		return componentItem.components.any { component -> component is TextModuleBehaviour }
	}

	/** Returns whether the scale can be persisted without constructor clamping or non-finite values. */
	@JvmStatic
	fun isValidScale(scale: Float): Boolean = scale.isFinite() && scale in MIN_TEXT_SCALE..MAX_TEXT_SCALE

	private object SetTextModuleConfigurationHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_TEXT_MODULE_CONFIGURATION_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is CentralMonitorTextModuleActionHost

		override fun acceptsPayload(payload: DataComponentMap): Boolean = readCommand(payload) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean {
			val command = readCommand(context.payload()) ?: return false
			val target = target(context)
			if (context.sequence() < 0 || player.isSpectator) return false
			if (target.getCentralMonitorActionIncarnation() != command.holderIncarnation) {
				target.resyncCentralMonitorTextModuleState()
				return false
			}
			val accepted = target.canSetCentralMonitorTextModuleConfiguration(
				command.groupIdentity,
				command.moduleSlotIncarnation,
				command.expectedConfigurationRevision,
				command.expectedModule,
				command.requestedConfiguration,
			)
			if (!accepted) {
				target.resyncCentralMonitorTextModuleState()
			}
			return accepted
		}

		override fun execute(context: SyncActionContext) {
			val command = requireCommand(context.payload())
			val target = target(context)
			val applied = target.setCentralMonitorTextModuleConfiguration(
				command.groupIdentity,
				command.moduleSlotIncarnation,
				command.expectedConfigurationRevision,
				command.expectedModule,
				command.requestedConfiguration,
			)
			if (!applied) {
				target.resyncCentralMonitorTextModuleState()
			}
			check(applied) {
				"Central Monitor text module change became invalid after permission validation."
			}
		}

		private fun target(context: SyncActionContext): CentralMonitorTextModuleActionHost = context.holder as? CentralMonitorTextModuleActionHost
			?: throw IllegalStateException("Central Monitor text module action received a non-monitor holder.")
	}

	private fun requireCommand(payload: DataComponentMap): TextModuleCommand = readCommand(payload)
		?: throw IllegalStateException("Central Monitor text module action omitted a valid command payload.")

	private fun readCommand(payload: DataComponentMap): TextModuleCommand? {
		if (payload.size() != 2) return null
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		val expectedModule = payload[GTDataComponents.PLACEHOLDER_ITEM_STACK.get()]?.copy() ?: return null
		if (fields.fields.size != 5 || !hasValidStackCount(expectedModule)) return null
		if (!ItemStack.matches(
				captureExpectedModule(expectedModule),
				expectedModule,
			) || !isTextModule(expectedModule)
		) {
			return null
		}

		val holderIncarnation = readUuid(fields[HOLDER_INCARNATION_FIELD], HOLDER_INCARNATION_FIELD) ?: return null
		val groupIdentity = readUuid(fields[GROUP_IDENTITY_FIELD], GROUP_IDENTITY_FIELD) ?: return null
		val moduleSlotIncarnation =
			readUuid(fields[MODULE_SLOT_INCARNATION_FIELD], MODULE_SLOT_INCARNATION_FIELD) ?: return null
		val expectedConfigurationRevision =
			readNonNegativeLong(fields[EXPECTED_CONFIGURATION_REVISION_FIELD], EXPECTED_CONFIGURATION_REVISION_FIELD)
				?: return null
		if (expectedConfigurationRevision == Long.MAX_VALUE) return null
		val requestedConfiguration = readConfiguration(fields[REQUESTED_CONFIGURATION_FIELD]) ?: return null
		if (expectedModule[GTDataComponents.FORMAT_STRING_LIST.get()] == requestedConfiguration) return null
		return TextModuleCommand(
			holderIncarnation,
			groupIdentity,
			moduleSlotIncarnation,
			expectedConfigurationRevision,
			expectedModule,
			requestedConfiguration,
		)
	}

	private fun canonicalizeConfiguration(configuration: TextLineList): TextLineList {
		require(isValidScale(configuration.scale)) {
			"Central Monitor text module scale must be finite and between $MIN_TEXT_SCALE and $MAX_TEXT_SCALE: ${configuration.scale}"
		}
		return TextLineList(configuration.lines.map { line -> Component.literal(line.string) }, configuration.scale)
	}

	private fun encodeUuid(value: UUID): JsonElement = UUIDUtil.CODEC
		.encodeStart(JsonOps.INSTANCE, value)
		.getOrThrow()

	private fun readUuid(value: JsonElement?, field: ResourceLocation): UUID? {
		if (value == null) return null
		return try {
			UUIDUtil.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow()
		} catch (exception: RuntimeException) {
			GTCEu.LOGGER.warn("Central Monitor text module action rejected invalid UUID field {}", field, exception)
			null
		}
	}

	private fun readNonNegativeLong(value: JsonElement?, field: ResourceLocation): Long? {
		val primitive = value as? JsonPrimitive ?: return null
		if (!primitive.isNumber) return null
		val decimal = try {
			primitive.asBigDecimal
		} catch (exception: NumberFormatException) {
			GTCEu.LOGGER.warn("Central Monitor text module action rejected invalid integer field {}", field, exception)
			return null
		}
		val number = try {
			decimal.longValueExact()
		} catch (exception: ArithmeticException) {
			GTCEu.LOGGER.warn(
				"Central Monitor text module action rejected non-integral or overflowing field {}",
				field,
				exception,
			)
			return null
		}
		return number.takeIf { it >= 0 }
	}

	private fun encodeConfiguration(configuration: TextLineList): JsonElement = TextLineList.CODEC
		.encodeStart(JsonOps.INSTANCE, configuration)
		.getOrThrow()

	private fun readConfiguration(value: JsonElement?): TextLineList? {
		val encoded = value as? JsonObject ?: return null
		if (!SyncFieldData.isFieldValueWithinNetworkLimit(encoded)) return null
		if (
			encoded.size() != 2 ||
			!encoded.has(CONFIGURATION_LINES_FIELD) ||
			!encoded.has(CONFIGURATION_SCALE_FIELD)
		) {
			return null
		}
		val scale = readRawScale(encoded[CONFIGURATION_SCALE_FIELD]) ?: return null
		return try {
			val decoded = TextLineList.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow()
			if (decoded.scale != scale) null else canonicalizeConfiguration(decoded)
		} catch (exception: RuntimeException) {
			GTCEu.LOGGER.warn("Central Monitor text module action rejected invalid configuration", exception)
			null
		}
	}

	private fun readRawScale(value: JsonElement?): Float? {
		val primitive = value as? JsonPrimitive ?: return null
		if (!primitive.isNumber) return null
		return try {
			val decimal = primitive.asBigDecimal
			if (decimal !in MIN_TEXT_SCALE_DECIMAL..MAX_TEXT_SCALE_DECIMAL) return null
			decimal.toFloat().takeIf(::isValidScale)
		} catch (exception: NumberFormatException) {
			GTCEu.LOGGER.warn("Central Monitor text module action rejected an invalid scale", exception)
			null
		}
	}

	private fun hasValidStackCount(stack: ItemStack): Boolean = !stack.isEmpty && stack.count <= stack.maxStackSize

	private data class TextModuleCommand(
		val holderIncarnation: UUID,
		val groupIdentity: UUID,
		val moduleSlotIncarnation: UUID,
		val expectedConfigurationRevision: Long,
		val expectedModule: ItemStack,
		val requestedConfiguration: TextLineList,
	)
}

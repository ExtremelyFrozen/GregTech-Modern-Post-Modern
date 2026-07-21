package com.gregtechceu.gtceu.common.cover.ender

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.capability.recipe.IO
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

import com.google.gson.JsonPrimitive
import org.jetbrains.annotations.ApiStatus

import java.util.Locale

/** Exposes only the Ender Link mutations and owner-scoped channel access required by its action handlers. */
@ApiStatus.Internal
interface EnderLinkCoverActionTarget {

	/** Applies the normalized channel color before all other cover configuration. */
	fun setChannelName(channelColor: String)

	/** Applies the validated public or private registry scope after the channel color. */
	fun setPermission(permission: AbstractEnderLinkCover.Permissions)

	/** Applies the validated import or export direction after the registry scope. */
	fun setIo(io: IO)

	/** Applies the validated manual I/O policy after the transfer direction. */
	fun setManualIOMode(manualIOMode: ManualIOMode)

	/** Applies the working state after all transfer configuration. */
	fun setWorkingEnabled(workingEnabled: Boolean)

	/** Marks the Ender Link UI dirty after a configuration or description mutation. */
	fun markEnderLinkUIChanged()

	/** Returns channel names visible in this cover's public or owner-scoped private registry. */
	fun getEnderLinkActionChannelNames(): List<String>

	/** Finds one channel only within this cover's public or owner-scoped private registry. */
	fun findEnderLinkActionChannel(channelName: String): VirtualEntry?

	/** Replaces the description through the Java-owned virtual entry API. */
	fun setEnderLinkActionChannelDescription(entry: VirtualEntry, description: String)

	/** Reads the sortable color key through the Java-owned virtual entry API. */
	fun getEnderLinkActionChannelColor(entry: VirtualEntry): String

	/** Sends the sorted channel snapshot back to the requesting cover UI. */
	fun sendEnderLinkActionChannelList(player: ServerPlayer, pos: BlockPos, side: Direction, entries: List<VirtualEntry>)
}

/** Owns all four wire actions used by the Ender Link cover UI. */
object EnderLinkCoverActions {

	private const val ACTION_SEQUENCE = 0
	private val SET_ENDER_LINK_COVER_CONFIG_ACTION = GTCEu.id("set_ender_link_cover_config")
	private val SET_ENDER_LINK_CHANNEL_DESCRIPTION_ACTION = GTCEu.id("set_ender_link_channel_description")
	private val REQUEST_ENDER_LINK_CHANNELS_ACTION = GTCEu.id("request_ender_link_channels")
	private val CLEAR_ENDER_LINK_CHANNEL_DESCRIPTION_ACTION = GTCEu.id("clear_ender_link_channel_description")
	private val CHANNEL_COLOR_FIELD = SyncFieldData.key("channelColor")
	private val PERMISSION_FIELD = SyncFieldData.key("permission")
	private val IO_FIELD = SyncFieldData.key("io")
	private val MANUAL_IO_FIELD = SyncFieldData.key("manualIO")
	private val WORKING_ENABLED_FIELD = SyncFieldData.key("workingEnabled")
	private val DESCRIPTION_FIELD = SyncFieldData.key("description")
	private val REQUEST_CHANNELS_FIELD = SyncFieldData.key("requestChannels")
	private val PERMISSIONS = AbstractEnderLinkCover.Permissions.entries
	private val MANUAL_IO_MODES = ManualIOMode.entries

	init {
		SyncActionDispatchers.server().register(EnderLinkCoverConfigActionHandler)
		SyncActionDispatchers.server().register(EnderLinkDescriptionActionHandler)
		SyncActionDispatchers.server().register(EnderLinkChannelListActionHandler)
		SyncActionDispatchers.server().register(EnderLinkClearDescriptionActionHandler)
	}

	/** Forces common-side handler registration when the owning cover class initializes. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one complete request from the current Ender Link cover state. */
	@JvmStatic
	fun createSetConfigAction(channelColor: String, permission: AbstractEnderLinkCover.Permissions, io: IO, manualIOMode: ManualIOMode, workingEnabled: Boolean): SyncActionData {
		require(isValidColorInput(channelColor)) { "Ender link cover color is invalid: $channelColor" }
		require(isImportExport(io)) { "Ender link cover IO mode must be import or export: $io" }
		val fields =
			SyncFieldData.builder()
				.put(CHANNEL_COLOR_FIELD, JsonPrimitive(channelColor))
				.put(PERMISSION_FIELD, JsonPrimitive(permission.ordinal))
				.put(IO_FIELD, JsonPrimitive(io.ordinal))
				.put(MANUAL_IO_FIELD, JsonPrimitive(manualIOMode.ordinal))
				.put(WORKING_ENABLED_FIELD, JsonPrimitive(workingEnabled))
				.build()
		return action(SET_ENDER_LINK_COVER_CONFIG_ACTION, fields)
	}

	/** Creates a request to replace one owner-scoped channel description. */
	@JvmStatic
	fun createSetDescriptionAction(channelName: String, description: String): SyncActionData {
		val fields =
			SyncFieldData.builder()
				.put(CHANNEL_COLOR_FIELD, JsonPrimitive(channelName))
				.put(DESCRIPTION_FIELD, JsonPrimitive(description))
				.build()
		return action(SET_ENDER_LINK_CHANNEL_DESCRIPTION_ACTION, fields)
	}

	/** Creates a request for the channel list visible to the current cover. */
	@JvmStatic
	fun createRequestChannelsAction(): SyncActionData {
		val fields =
			SyncFieldData.builder()
				.put(REQUEST_CHANNELS_FIELD, JsonPrimitive(true))
				.build()
		return action(REQUEST_ENDER_LINK_CHANNELS_ACTION, fields)
	}

	/** Creates a request to clear one non-blank owner-scoped channel description. */
	@JvmStatic
	fun createClearDescriptionAction(channelName: String): SyncActionData {
		val fields =
			SyncFieldData.builder()
				.put(CHANNEL_COLOR_FIELD, JsonPrimitive(channelName))
				.build()
		return action(CLEAR_ENDER_LINK_CHANNEL_DESCRIPTION_ACTION, fields)
	}

	private object EnderLinkCoverConfigActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_ENDER_LINK_COVER_CONFIG_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is AbstractEnderLinkCover<*>

		override fun acceptsPayload(payload: DataComponentMap): Boolean = readConfig(payload) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = mayExecuteEnderLinkAction(player, context)

		override fun execute(context: SyncActionContext) {
			val target = target(context)
			val config = requireConfig(context.payload())
			target.setChannelName(normalizeColorInput(config.channelColor))
			target.setPermission(config.permission)
			target.setIo(config.io)
			target.setManualIOMode(config.manualIOMode)
			target.setWorkingEnabled(config.workingEnabled)
			target.markEnderLinkUIChanged()
		}
	}

	private object EnderLinkChannelListActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = REQUEST_ENDER_LINK_CHANNELS_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is AbstractEnderLinkCover<*>

		override fun acceptsPayload(payload: DataComponentMap): Boolean = readRequest(payload)

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = mayExecuteEnderLinkAction(player, context)

		override fun execute(context: SyncActionContext) {
			sendChannelList(context, target(context))
		}
	}

	private object EnderLinkDescriptionActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_ENDER_LINK_CHANNEL_DESCRIPTION_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is AbstractEnderLinkCover<*>

		override fun acceptsPayload(payload: DataComponentMap): Boolean = readDescription(payload) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = mayExecuteEnderLinkAction(player, context)

		override fun execute(context: SyncActionContext) {
			val target = target(context)
			val config = requireDescription(context.payload())
			val entry = requireRegistryEntry(target, config.channelName)
			target.setEnderLinkActionChannelDescription(entry, config.description)
			target.markEnderLinkUIChanged()
		}
	}

	private object EnderLinkClearDescriptionActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = CLEAR_ENDER_LINK_CHANNEL_DESCRIPTION_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is AbstractEnderLinkCover<*>

		override fun acceptsPayload(payload: DataComponentMap): Boolean = readClearChannel(payload) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = mayExecuteEnderLinkAction(player, context)

		override fun execute(context: SyncActionContext) {
			val location = requireCoverLocation(context)
			val target = target(context)
			val channelName = requireClearChannel(context.payload())
			val entry = requireRegistryEntry(target, channelName)
			target.setEnderLinkActionChannelDescription(entry, "")
			sendChannelList(context.player, target, location)
		}
	}

	private data class Config(val channelColor: String, val permission: AbstractEnderLinkCover.Permissions, val io: IO, val manualIOMode: ManualIOMode, val workingEnabled: Boolean)

	private data class DescriptionConfig(val channelName: String, val description: String)
	private data class CoverLocation(val pos: BlockPos, val side: Direction)

	private fun readConfig(payload: DataComponentMap): Config? {
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		val channelColor = readString(fields, CHANNEL_COLOR_FIELD)?.takeIf(::isValidColorInput) ?: return null
		val permission = readOrdinal(fields, PERMISSION_FIELD, PERMISSIONS) ?: return null
		val io = readIO(fields) ?: return null
		val manualIOMode = readOrdinal(fields, MANUAL_IO_FIELD, MANUAL_IO_MODES) ?: return null
		val workingEnabled = readBoolean(fields, WORKING_ENABLED_FIELD) ?: return null
		return Config(channelColor, permission, io, manualIOMode, workingEnabled)
	}

	private fun requireConfig(payload: DataComponentMap): Config = readConfig(payload)
		?: throw IllegalStateException("Ender link cover config action payload is invalid.")

	private fun readDescription(payload: DataComponentMap): DescriptionConfig? {
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		val channelName = readString(fields, CHANNEL_COLOR_FIELD) ?: return null
		val description = readString(fields, DESCRIPTION_FIELD) ?: return null
		return DescriptionConfig(channelName, description)
	}

	private fun requireDescription(payload: DataComponentMap): DescriptionConfig = readDescription(payload)
		?: throw IllegalStateException("Ender link description action payload is invalid.")

	private fun readRequest(payload: DataComponentMap): Boolean {
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
		return readBoolean(fields, REQUEST_CHANNELS_FIELD) == true
	}

	private fun readClearChannel(payload: DataComponentMap): String? {
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		return readString(fields, CHANNEL_COLOR_FIELD)?.takeIf(::isJavaNonBlank)
	}

	private fun requireClearChannel(payload: DataComponentMap): String = readClearChannel(payload)
		?: throw IllegalStateException("Ender link clear description action payload is invalid.")

	private fun action(actionId: ResourceLocation, fields: SyncFieldData): SyncActionData = SyncActionData(
		actionId,
		ACTION_SEQUENCE,
		fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
	)

	private fun mayExecuteEnderLinkAction(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator && context.holder is AbstractEnderLinkCover<*>

	private fun target(context: SyncActionContext): EnderLinkCoverActionTarget = context.holder as? EnderLinkCoverActionTarget
		?: throw IllegalStateException("Ender link action received a non-ender-link target.")

	private fun requireRegistryEntry(target: EnderLinkCoverActionTarget, channelName: String): VirtualEntry = target.findEnderLinkActionChannel(channelName)
		?: throw IllegalStateException("Ender link channel is missing: $channelName")

	private fun requireCoverLocation(context: SyncActionContext): CoverLocation = CoverLocation(
		context.pos ?: throw IllegalStateException("Ender link channel list action is missing cover position."),
		context.side ?: throw IllegalStateException("Ender link channel list action is missing cover side."),
	)

	private fun sendChannelList(context: SyncActionContext, target: EnderLinkCoverActionTarget) {
		sendChannelList(context.player, target, requireCoverLocation(context))
	}

	private fun sendChannelList(player: ServerPlayer, target: EnderLinkCoverActionTarget, location: CoverLocation) {
		val entries = target.getEnderLinkActionChannelNames()
			.map { channelName -> requireRegistryEntry(target, channelName) }
			.sortedBy { entry -> target.getEnderLinkActionChannelColor(entry) }
		target.sendEnderLinkActionChannelList(player, location.pos, location.side, entries)
	}

	private fun readIO(fields: SyncFieldData): IO? {
		val ordinal = readExactInt(fields, IO_FIELD) ?: return null
		return when (ordinal) {
			IO.IN.ordinal -> IO.IN
			IO.OUT.ordinal -> IO.OUT
			else -> null
		}
	}

	private fun <T> readOrdinal(fields: SyncFieldData, field: ResourceLocation, values: List<T>): T? {
		val ordinal = readExactInt(fields, field) ?: return null
		return if (ordinal in values.indices) values[ordinal] else null
	}

	private fun readString(fields: SyncFieldData, field: ResourceLocation): String? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		return if (primitive.isString) primitive.asString else null
	}

	private fun readBoolean(fields: SyncFieldData, field: ResourceLocation): Boolean? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		return if (primitive.isBoolean) primitive.asBoolean else null
	}

	private fun readExactInt(fields: SyncFieldData, field: ResourceLocation): Int? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		if (!primitive.isNumber) {
			return null
		}
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
		GTCEu.LOGGER.warn(
			"Ender link cover action rejected invalid integer field {}: {}",
			field,
			primitive,
			exception,
		)
	}

	private fun normalizeColorInput(color: String): String {
		var normalized = color
		if (normalized.length < 8) {
			normalized += "F".repeat(8 - normalized.length)
		}
		return normalized.uppercase(Locale.ROOT)
	}

	private fun isValidColorInput(color: String): Boolean = AbstractEnderLinkCover.COLOR_INPUT_PATTERN.matcher(color).matches()

	private fun isJavaNonBlank(value: String): Boolean = value.codePoints().anyMatch { codePoint -> !Character.isWhitespace(codePoint) }

	private fun isImportExport(io: IO): Boolean = io == IO.IN || io == IO.OUT
}

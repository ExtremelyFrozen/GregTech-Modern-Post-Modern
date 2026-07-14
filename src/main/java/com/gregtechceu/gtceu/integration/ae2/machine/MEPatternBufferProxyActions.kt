package com.gregtechceu.gtceu.integration.ae2.machine

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2CircuitFancyConfiguratorActions
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2DirectionalCoverActions
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.data.GTDataComponents
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponentMap
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

import com.google.gson.JsonPrimitive
import org.jetbrains.annotations.ApiStatus

import java.util.UUID

/**
 * Immutable identity of the Proxy instance and linked Pattern Buffer captured by one menu opening.
 *
 * @property proxyIncarnation persistent Proxy identity that rejects packets queued for a replacement Proxy.
 * @property bufferPos linked Pattern Buffer position captured when the page opened.
 * @property linkRevision monotonically changing link generation captured when the page opened.
 */
@JvmRecord
data class MEPatternBufferProxyOpeningIdentity(val proxyIncarnation: UUID, val bufferPos: BlockPos, val linkRevision: Long) {
	private constructor(buffer: RegistryFriendlyByteBuf) : this(
		buffer.readUUID(),
		buffer.readBlockPos(),
		buffer.readLong(),
	)

	private fun encode(buffer: RegistryFriendlyByteBuf) {
		buffer.writeUUID(proxyIncarnation)
		buffer.writeBlockPos(bufferPos)
		buffer.writeLong(linkRevision)
	}

	companion object {

		/** Encodes the immutable Proxy and linked-buffer generation captured by the server opening. */
		@JvmField
		val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MEPatternBufferProxyOpeningIdentity> =
			StreamCodec.ofMember(MEPatternBufferProxyOpeningIdentity::encode, ::MEPatternBufferProxyOpeningIdentity)
	}
}

/** Exposes only Proxy operations that revalidate an immutable opening before touching its linked buffer. */
@ApiStatus.Internal
interface MEPatternBufferProxyActionTarget : LDLib2FancyActionMachine {

	/** Returns whether the player may still use the exact Proxy and linked-buffer opening. */
	fun canExecuteMEPatternBufferProxyAction(player: ServerPlayer, opening: MEPatternBufferProxyOpeningIdentity): Boolean

	/** Replaces the linked buffer name after revalidating [opening]. */
	fun setLinkedMEPatternBufferName(opening: MEPatternBufferProxyOpeningIdentity, name: String)

	/** Refunds the linked buffer contents after revalidating [opening]. */
	fun refundLinkedMEPatternBufferContents(opening: MEPatternBufferProxyOpeningIdentity)

	/** Returns the linked buffer tank count after revalidating [opening]. */
	fun getLinkedMEPatternBufferShareTankCount(opening: MEPatternBufferProxyOpeningIdentity): Int

	/** Executes one linked shared-tank interaction after revalidating [opening]. */
	fun clickLinkedMEPatternBufferShareTank(player: ServerPlayer, opening: MEPatternBufferProxyOpeningIdentity, tankIndex: Int, shiftDown: Boolean)

	/** Dispatches one allowlisted circuit action against the revalidated linked buffer. */
	fun configureLinkedMEPatternBufferCircuit(player: ServerPlayer, opening: MEPatternBufferProxyOpeningIdentity, configuration: Int)

	/** Dispatches one allowlisted cover action against the revalidated linked buffer. */
	fun configureLinkedMEPatternBufferCover(player: ServerPlayer, opening: MEPatternBufferProxyOpeningIdentity, side: Direction, operation: MEPatternBufferProxyCoverOperation)
}

/** Exact cover commands that a Proxy directional page may forward to its linked buffer. */
enum class MEPatternBufferProxyCoverOperation {
	PLACE,
	REMOVE,
	OPEN,
}

/** Owns the opening-bound wire protocol for every interactive Pattern Buffer Proxy control. */
object MEPatternBufferProxyActions {

	private val SET_NAME_ACTION = GTCEu.id("set_me_pattern_buffer_proxy_name")
	private val REFUND_ALL_ACTION = GTCEu.id("refund_me_pattern_buffer_proxy")
	private val CLICK_SHARE_TANK_ACTION = GTCEu.id("click_me_pattern_buffer_proxy_share_tank")
	private val SET_CIRCUIT_ACTION = GTCEu.id("set_me_pattern_buffer_proxy_circuit")
	private val CONFIGURE_COVER_ACTION = GTCEu.id("configure_me_pattern_buffer_proxy_cover")
	private val PROXY_INCARNATION_FIELD = SyncFieldData.key("proxy_incarnation")
	private val BUFFER_POS_FIELD = SyncFieldData.key("buffer_pos")
	private val LINK_REVISION_FIELD = SyncFieldData.key("link_revision")
	private val NAME_FIELD = SyncFieldData.key("name")
	private val TANK_FIELD = SyncFieldData.key("tank")
	private val SHIFT_FIELD = SyncFieldData.key("shift")
	private val CIRCUIT_FIELD = SyncFieldData.key("circuit")
	private val SIDE_FIELD = SyncFieldData.key("side")
	private val COVER_OPERATION_FIELD = SyncFieldData.key("cover_operation")

	init {
		LDLib2CircuitFancyConfiguratorActions.initialize()
		LDLib2DirectionalCoverActions.initialize()
		SyncActionDispatchers.server().register(SetNameHandler)
		SyncActionDispatchers.server().register(RefundAllHandler)
		SyncActionDispatchers.server().register(ClickShareTankHandler)
		SyncActionDispatchers.server().register(SetCircuitHandler)
		SyncActionDispatchers.server().register(ConfigureCoverHandler)
	}

	/** Forces action registration from the owning Proxy's static initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one opening-bound linked-buffer name update. */
	@JvmStatic
	fun createSetNameAction(opening: MEPatternBufferProxyOpeningIdentity, name: String): SyncActionData = createAction(SET_NAME_ACTION, name.hashCode(), opening) { builder ->
		builder.put(NAME_FIELD, JsonPrimitive(name))
	}

	/** Creates one opening-bound linked-buffer refund command. */
	@JvmStatic
	fun createRefundAllAction(opening: MEPatternBufferProxyOpeningIdentity): SyncActionData = createAction(REFUND_ALL_ACTION, 0, opening) { }

	/** Creates one opening-bound linked-buffer shared-tank interaction. */
	@JvmStatic
	fun createClickShareTankAction(opening: MEPatternBufferProxyOpeningIdentity, tankIndex: Int, shiftDown: Boolean): SyncActionData {
		require(tankIndex >= 0) { "Pattern Buffer Proxy shared tank index must be non-negative: $tankIndex" }
		return createAction(CLICK_SHARE_TANK_ACTION, tankIndex * 2 + if (shiftDown) 1 else 0, opening) { builder ->
			builder
				.put(TANK_FIELD, JsonPrimitive(tankIndex))
				.put(SHIFT_FIELD, JsonPrimitive(shiftDown))
		}
	}

	/** Creates one opening-bound linked-buffer programmed-circuit update. */
	@JvmStatic
	fun createSetCircuitConfigurationAction(opening: MEPatternBufferProxyOpeningIdentity, configuration: Int): SyncActionData {
		require(isValidCircuitConfiguration(configuration)) {
			"Pattern Buffer Proxy circuit configuration is out of range: $configuration"
		}
		return createAction(SET_CIRCUIT_ACTION, configuration, opening) { builder ->
			builder.put(CIRCUIT_FIELD, JsonPrimitive(configuration))
		}
	}

	/** Creates one opening-bound linked-buffer cover placement. */
	@JvmStatic
	fun createPlaceCoverAction(opening: MEPatternBufferProxyOpeningIdentity, side: Direction): SyncActionData = createCoverAction(opening, side, MEPatternBufferProxyCoverOperation.PLACE)

	/** Creates one opening-bound linked-buffer cover removal. */
	@JvmStatic
	fun createRemoveCoverAction(opening: MEPatternBufferProxyOpeningIdentity, side: Direction): SyncActionData = createCoverAction(opening, side, MEPatternBufferProxyCoverOperation.REMOVE)

	/** Creates one opening-bound linked-buffer cover UI request. */
	@JvmStatic
	fun createOpenCoverAction(opening: MEPatternBufferProxyOpeningIdentity, side: Direction): SyncActionData = createCoverAction(opening, side, MEPatternBufferProxyCoverOperation.OPEN)

	private fun createCoverAction(opening: MEPatternBufferProxyOpeningIdentity, side: Direction, operation: MEPatternBufferProxyCoverOperation): SyncActionData =
		createAction(CONFIGURE_COVER_ACTION, side.get3DDataValue(), opening) { builder ->
			builder
				.put(SIDE_FIELD, JsonPrimitive(side.get3DDataValue()))
				.put(COVER_OPERATION_FIELD, JsonPrimitive(operation.name.lowercase()))
		}

	private fun createAction(actionId: ResourceLocation, sequence: Int, opening: MEPatternBufferProxyOpeningIdentity, addFields: (SyncFieldData.Builder) -> Unit): SyncActionData {
		val builder =
			SyncFieldData.builder()
				.put(PROXY_INCARNATION_FIELD, JsonPrimitive(opening.proxyIncarnation.toString()))
				.put(BUFFER_POS_FIELD, JsonPrimitive(opening.bufferPos.asLong().toString()))
				.put(LINK_REVISION_FIELD, JsonPrimitive(opening.linkRevision.toString()))
		addFields(builder)
		return SyncActionData(
			actionId,
			sequence,
			builder.build().toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private abstract class ProxyActionHandler(private val fieldCount: Int) : SyncActionHandler {

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is MEPatternBufferProxyActionTarget

		protected fun acceptsFields(payload: DataComponentMap, validate: (SyncFieldData) -> Boolean): Boolean {
			if (payload.size() != 1) return false
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			return fields.fields.size == fieldCount && readOpening(fields) != null && validate(fields)
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean {
			if (player.isSpectator) return false
			return target(context).canExecuteMEPatternBufferProxyAction(
				player,
				requireOpening(context.payload()),
			)
		}

		protected fun target(context: SyncActionContext): MEPatternBufferProxyActionTarget = context.holder as? MEPatternBufferProxyActionTarget
			?: throw IllegalStateException("Pattern Buffer Proxy action received an invalid holder.")
	}

	private object SetNameHandler : ProxyActionHandler(4) {

		override fun actionId(): ResourceLocation = SET_NAME_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean = acceptsFields(payload) { fields -> readName(fields) != null }

		override fun execute(context: SyncActionContext) {
			target(context).setLinkedMEPatternBufferName(
				requireOpening(context.payload()),
				requireName(context.payload()),
			)
		}
	}

	private object RefundAllHandler : ProxyActionHandler(3) {

		override fun actionId(): ResourceLocation = REFUND_ALL_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean = acceptsFields(payload) { true }

		override fun execute(context: SyncActionContext) {
			target(context).refundLinkedMEPatternBufferContents(requireOpening(context.payload()))
		}
	}

	private object ClickShareTankHandler : ProxyActionHandler(5) {

		override fun actionId(): ResourceLocation = CLICK_SHARE_TANK_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean = acceptsFields(payload) { fields -> readTankIndex(fields) != null && readShift(fields) != null }

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean {
			if (!super.mayExecute(player, context)) return false
			val opening = requireOpening(context.payload())
			val tankIndex = requireTankIndex(context.payload())
			return tankIndex < target(context).getLinkedMEPatternBufferShareTankCount(opening)
		}

		override fun execute(context: SyncActionContext) {
			target(context).clickLinkedMEPatternBufferShareTank(
				context.player,
				requireOpening(context.payload()),
				requireTankIndex(context.payload()),
				requireShift(context.payload()),
			)
		}
	}

	private object SetCircuitHandler : ProxyActionHandler(4) {

		override fun actionId(): ResourceLocation = SET_CIRCUIT_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean = acceptsFields(payload) { fields ->
			val configuration = readCircuitConfiguration(fields)
			configuration != null && isValidCircuitConfiguration(configuration)
		}

		override fun execute(context: SyncActionContext) {
			target(context).configureLinkedMEPatternBufferCircuit(
				context.player,
				requireOpening(context.payload()),
				requireCircuitConfiguration(context.payload()),
			)
		}
	}

	private object ConfigureCoverHandler : ProxyActionHandler(5) {

		override fun actionId(): ResourceLocation = CONFIGURE_COVER_ACTION

		override fun acceptsPayload(payload: DataComponentMap): Boolean = acceptsFields(payload) { fields -> readSide(fields) != null && readCoverOperation(fields) != null }

		override fun execute(context: SyncActionContext) {
			target(context).configureLinkedMEPatternBufferCover(
				context.player,
				requireOpening(context.payload()),
				requireSide(context.payload()),
				requireCoverOperation(context.payload()),
			)
		}
	}

	private fun requireFields(payload: DataComponentMap): SyncFieldData = payload[GTDataComponents.SYNC_FIELD_DATA.get()]
		?: throw IllegalStateException("Pattern Buffer Proxy action payload is missing field data.")

	private fun requireOpening(payload: DataComponentMap): MEPatternBufferProxyOpeningIdentity = readOpening(requireFields(payload))
		?: throw IllegalStateException("Pattern Buffer Proxy action payload has no valid opening identity.")

	private fun readOpening(fields: SyncFieldData): MEPatternBufferProxyOpeningIdentity? {
		val incarnation = readUuid(fields, PROXY_INCARNATION_FIELD) ?: return null
		val bufferPos = readBlockPos(fields, BUFFER_POS_FIELD) ?: return null
		val linkRevision = readLong(fields, LINK_REVISION_FIELD) ?: return null
		return MEPatternBufferProxyOpeningIdentity(incarnation, bufferPos, linkRevision)
	}

	private fun requireName(payload: DataComponentMap): String = readName(requireFields(payload))
		?: throw IllegalStateException("Pattern Buffer Proxy action payload has no string name.")

	private fun readName(fields: SyncFieldData): String? {
		val primitive = fields[NAME_FIELD] as? JsonPrimitive ?: return null
		return if (primitive.isString) primitive.asString else null
	}

	private fun requireTankIndex(payload: DataComponentMap): Int = readTankIndex(requireFields(payload))
		?: throw IllegalStateException("Pattern Buffer Proxy action payload has no non-negative tank index.")

	private fun readTankIndex(fields: SyncFieldData): Int? = readExactInt(fields, TANK_FIELD)?.takeIf { it >= 0 }

	private fun requireShift(payload: DataComponentMap): Boolean = readShift(requireFields(payload))
		?: throw IllegalStateException("Pattern Buffer Proxy action payload has no boolean Shift value.")

	private fun readShift(fields: SyncFieldData): Boolean? {
		val primitive = fields[SHIFT_FIELD] as? JsonPrimitive ?: return null
		return if (primitive.isBoolean) primitive.asBoolean else null
	}

	private fun requireCircuitConfiguration(payload: DataComponentMap): Int {
		val configuration = readCircuitConfiguration(requireFields(payload))
		if (configuration == null || !isValidCircuitConfiguration(configuration)) {
			throw IllegalStateException("Pattern Buffer Proxy action payload has no valid circuit configuration.")
		}
		return configuration
	}

	private fun readCircuitConfiguration(fields: SyncFieldData): Int? = readExactInt(fields, CIRCUIT_FIELD)

	private fun isValidCircuitConfiguration(configuration: Int): Boolean = configuration in -1..IntCircuitBehaviour.CIRCUIT_MAX

	private fun requireSide(payload: DataComponentMap): Direction = readSide(requireFields(payload))
		?: throw IllegalStateException("Pattern Buffer Proxy action payload has no valid side.")

	private fun readSide(fields: SyncFieldData): Direction? {
		val sideId = readExactInt(fields, SIDE_FIELD) ?: return null
		return if (sideId in Direction.values().indices) Direction.from3DDataValue(sideId) else null
	}

	private fun requireCoverOperation(payload: DataComponentMap): MEPatternBufferProxyCoverOperation = readCoverOperation(requireFields(payload))
		?: throw IllegalStateException("Pattern Buffer Proxy action payload has no valid cover operation.")

	private fun readCoverOperation(fields: SyncFieldData): MEPatternBufferProxyCoverOperation? {
		val primitive = fields[COVER_OPERATION_FIELD] as? JsonPrimitive ?: return null
		if (!primitive.isString) return null
		return when (primitive.asString) {
			"place" -> MEPatternBufferProxyCoverOperation.PLACE
			"remove" -> MEPatternBufferProxyCoverOperation.REMOVE
			"open" -> MEPatternBufferProxyCoverOperation.OPEN
			else -> null
		}
	}

	private fun readUuid(fields: SyncFieldData, field: ResourceLocation): UUID? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		if (!primitive.isString) return null
		return try {
			UUID.fromString(primitive.asString)
		} catch (exception: IllegalArgumentException) {
			GTCEu.LOGGER.warn("Pattern Buffer Proxy action rejected invalid UUID for field {}", field, exception)
			null
		}
	}

	private fun readBlockPos(fields: SyncFieldData, field: ResourceLocation): BlockPos? = readLong(fields, field)?.let(BlockPos::of)

	private fun readLong(fields: SyncFieldData, field: ResourceLocation): Long? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		return if (primitive.isString) primitive.asString.toLongOrNull() else null
	}

	private fun readExactInt(fields: SyncFieldData, field: ResourceLocation): Int? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		if (!primitive.isNumber) return null
		return try {
			primitive.asBigDecimal.intValueExact()
		} catch (exception: NumberFormatException) {
			logInvalidInteger(field, exception)
			null
		} catch (exception: ArithmeticException) {
			logInvalidInteger(field, exception)
			null
		}
	}

	private fun logInvalidInteger(field: ResourceLocation, exception: RuntimeException) {
		GTCEu.LOGGER.warn("Pattern Buffer Proxy action rejected invalid integer for field {}", field, exception)
	}
}

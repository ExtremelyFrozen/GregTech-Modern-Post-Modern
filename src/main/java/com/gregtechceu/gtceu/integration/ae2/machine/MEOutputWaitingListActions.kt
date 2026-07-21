package com.gregtechceu.gtceu.integration.ae2.machine

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.machine.MetaMachine
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.data.GTDataComponents
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner
import com.gregtechceu.gtceu.common.network.packets.SPacketMEOutputWaitingListSessionToClient

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu

import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponentMap
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor

import com.google.gson.JsonPrimitive

import java.util.UUID

/**
 * Persistent identity of one ME output machine used by menu actions and waiting-list publications.
 *
 * @property pos output machine position captured by the contextual page.
 * @property machineDefinitionId exact machine definition captured by the contextual page.
 * @property incarnation persistent identity that changes when a machine at the same position is replaced.
 */
@JvmRecord
data class MEOutputWaitingListTarget(val pos: BlockPos, val machineDefinitionId: ResourceLocation, val incarnation: UUID) {
	private constructor(buffer: RegistryFriendlyByteBuf) : this(
		buffer.readBlockPos(),
		buffer.readResourceLocation(),
		buffer.readUUID(),
	)

	private fun encode(buffer: RegistryFriendlyByteBuf) {
		buffer.writeBlockPos(pos)
		buffer.writeResourceLocation(machineDefinitionId)
		buffer.writeUUID(incarnation)
	}

	companion object {

		/** Encodes the exact output bus identity shared by the action and packet protocols. */
		@JvmField
		val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MEOutputWaitingListTarget> =
			StreamCodec.ofMember(MEOutputWaitingListTarget::encode, ::MEOutputWaitingListTarget)
	}
}

/** Owns the client request that registers one opening and sends its full waiting-list state. */
object MEOutputWaitingListActions {

	private val REQUEST_FULL_ACTION = GTCEu.id("request_me_output_waiting_list_full")
	private val TARGET_POS_FIELD = SyncFieldData.key("target_pos")
	private val TARGET_DEFINITION_FIELD = SyncFieldData.key("target_definition")
	private val TARGET_INCARNATION_FIELD = SyncFieldData.key("target_incarnation")
	private val OPENING_ID_FIELD = SyncFieldData.key("opening_id")
	private val MENU_SESSION_ID_FIELD = SyncFieldData.key("menu_session_id")

	init {
		SyncActionDispatchers.server().register(RequestFullHandler)
	}

	/** Forces handler registration from the owning ME output machine's static initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one ordered full-state request bound to the target bus and client element opening UUID. */
	@JvmStatic
	fun createRequestFullAction(target: MEOutputWaitingListTarget, openingId: UUID, requestSequence: Int): SyncActionData = createRequestFullActionPayload(target, openingId, requestSequence, null)

	/** Creates the authenticated retry after the exact server menu element challenges this opening. */
	@JvmStatic
	fun createRequestFullAction(target: MEOutputWaitingListTarget, openingId: UUID, requestSequence: Int, menuSessionId: UUID): SyncActionData =
		createRequestFullActionPayload(target, openingId, requestSequence, menuSessionId)

	private fun createRequestFullActionPayload(target: MEOutputWaitingListTarget, openingId: UUID, requestSequence: Int, menuSessionId: UUID?): SyncActionData {
		require(requestSequence >= 0) { "ME output waiting-list request sequence must be non-negative." }
		val fields = SyncFieldData.builder()
			.put(TARGET_POS_FIELD, JsonPrimitive(target.pos.asLong().toString()))
			.put(TARGET_DEFINITION_FIELD, JsonPrimitive(target.machineDefinitionId.toString()))
			.put(TARGET_INCARNATION_FIELD, JsonPrimitive(target.incarnation.toString()))
			.put(OPENING_ID_FIELD, JsonPrimitive(openingId.toString()))
		if (menuSessionId != null) {
			fields.put(MENU_SESSION_ID_FIELD, JsonPrimitive(menuSessionId.toString()))
		}
		return SyncActionData(
			REQUEST_FULL_ACTION,
			requestSequence,
			fields.build().toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private object RequestFullHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = REQUEST_FULL_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is MetaMachine && context.holder is LDLib2FancyActionMachine

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			if (payload.size() != 1) return false
			val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return false
			val hasMenuSession = MENU_SESSION_ID_FIELD in fields
			val expectedFieldCount = if (hasMenuSession) 5 else 4
			if (fields.fields.size != expectedFieldCount || readTarget(fields) == null || readOpeningId(fields) == null) {
				return false
			}
			return !hasMenuSession || readMenuSessionId(fields) != null
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = context.sequence() >= 0 && !player.isSpectator && canOpenRoot(player, context) &&
			receiver(context)?.canRequestFull(player) == true

		override fun execute(context: SyncActionContext) {
			val receiver = receiver(context)
			if (receiver == null) {
				GTCEu.LOGGER.warn(
					"ME output waiting-list full request lost its target menu for {}",
					context.player.gameProfile.name,
				)
				return
			}
			val target = requireTarget(context.payload())
			val openingId = requireOpeningId(context.payload())
			val menuSessionId = readMenuSessionId(context.payload())
			if (menuSessionId == null || !receiver.consumeWaitingListMenuSessionChallenge(
					openingId,
					context.sequence(),
					menuSessionId,
				)
			) {
				if (menuSessionId != null) {
					GTCEu.LOGGER.warn(
						"ME output waiting-list rejected an expired or mismatched menu challenge from {}",
						context.player.gameProfile.name,
					)
				}
				sendMenuSessionChallenge(context, receiver, target, openingId)
				return
			}
			if (!receiver.requestFull(context.player, openingId, context.sequence())) {
				GTCEu.LOGGER.warn(
					"ME output waiting-list full request lost its active menu for {}",
					context.player.gameProfile.name,
				)
			}
		}
	}

	private fun sendMenuSessionChallenge(context: SyncActionContext, receiver: MEOutputWaitingListSessionReceiver, target: MEOutputWaitingListTarget, openingId: UUID) {
		val menu = context.player.containerMenu as? ModularUIContainerMenu
		if (menu == null) {
			GTCEu.LOGGER.warn(
				"ME output waiting-list could not challenge a missing LDLib2 menu for {}",
				context.player.gameProfile.name,
			)
			return
		}
		val menuSessionId = receiver.issueWaitingListMenuSessionChallenge(openingId, context.sequence())
		try {
			PacketDistributor.sendToPlayer(
				context.player,
				SPacketMEOutputWaitingListSessionToClient(
					menu.containerId,
					target,
					openingId,
					context.sequence(),
					menuSessionId,
				),
			)
		} catch (exception: RuntimeException) {
			GTCEu.LOGGER.error(
				"ME output waiting-list failed to challenge menu {} for {}",
				menu.containerId,
				context.player.gameProfile.name,
				exception,
			)
		}
	}

	private fun canOpenRoot(player: ServerPlayer, context: SyncActionContext): Boolean {
		val rootMachine = context.holder as? MetaMachine ?: return false
		return MachineOwner.canOpenOwnerMachine(player, rootMachine)
	}

	private fun receiver(context: SyncActionContext): MEOutputWaitingListSessionReceiver? {
		val rootMachine = context.holder as? MetaMachine ?: return null
		val target = readTarget(context.payload()) ?: return null
		return MEOutputWaitingListRoute.resolveSessionForAction(context.player, rootMachine, target)
	}

	private fun requireTarget(payload: DataComponentMap): MEOutputWaitingListTarget = readTarget(payload)
		?: throw IllegalStateException("ME output waiting-list action omitted a valid target.")

	private fun requireOpeningId(payload: DataComponentMap): UUID {
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()]
			?: throw IllegalStateException("ME output waiting-list action omitted sync field data.")
		return readOpeningId(fields)
			?: throw IllegalStateException("ME output waiting-list action omitted a valid opening UUID.")
	}

	private fun readTarget(payload: DataComponentMap): MEOutputWaitingListTarget? {
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		return readTarget(fields)
	}

	private fun readTarget(fields: SyncFieldData): MEOutputWaitingListTarget? {
		val pos = readString(fields, TARGET_POS_FIELD)?.toLongOrNull()?.let(BlockPos::of) ?: return null
		val definition =
			readString(fields, TARGET_DEFINITION_FIELD)?.let { ResourceLocation.tryParse(it) } ?: return null
		val incarnation = readUuid(fields, TARGET_INCARNATION_FIELD) ?: return null
		return MEOutputWaitingListTarget(pos, definition, incarnation)
	}

	private fun readOpeningId(fields: SyncFieldData): UUID? = readUuid(fields, OPENING_ID_FIELD)

	private fun readMenuSessionId(payload: DataComponentMap): UUID? {
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		return readMenuSessionId(fields)
	}

	private fun readMenuSessionId(fields: SyncFieldData): UUID? = readUuid(fields, MENU_SESSION_ID_FIELD)

	private fun readUuid(fields: SyncFieldData, field: ResourceLocation): UUID? {
		val value = readString(fields, field) ?: return null
		return try {
			UUID.fromString(value)
		} catch (exception: IllegalArgumentException) {
			GTCEu.LOGGER.warn("ME output waiting-list action rejected invalid UUID for field {}", field, exception)
			null
		}
	}

	private fun readString(fields: SyncFieldData, field: ResourceLocation): String? {
		val primitive = fields[field] as? JsonPrimitive ?: return null
		return if (primitive.isString) primitive.asString else null
	}
}

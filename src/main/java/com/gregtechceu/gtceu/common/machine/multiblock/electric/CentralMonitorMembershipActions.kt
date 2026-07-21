package com.gregtechceu.gtceu.common.machine.multiblock.electric

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.BlockPos
import net.minecraft.core.UUIDUtil
import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import com.mojang.serialization.JsonOps
import org.jetbrains.annotations.ApiStatus

import java.util.LinkedHashSet
import java.util.UUID

/**
 * Exposes only the Central Monitor membership operations that server action handlers may validate and apply.
 *
 * The split validation and mutation methods keep packet decoding outside the machine while allowing the machine to
 * re-check its current structure and group collection immediately before each atomic change.
 */
@ApiStatus.Internal
@JvmSuppressWildcards
interface CentralMonitorMembershipActionTarget {

	/** Returns the incarnation that identifies this exact placed Central Monitor to an opened client page. */
	fun getCentralMonitorActionIncarnation(): UUID

	/** Returns the maximum number of positions addressable by the currently formed monitor grid. */
	fun getCentralMonitorMembershipCapacity(): Int

	/** Returns the persisted collection revision used to reject stale or replayed membership actions. */
	fun getCentralMonitorMembershipRevision(): Long

	/** Checks whether [positions] may form a new group with [groupIdentity] in the current structure. */
	fun canCreateCentralMonitorGroup(expectedRevision: Long, groupIdentity: UUID, positions: Set<BlockPos>): Boolean

	/** Creates the validated group and returns whether the current state still allowed the complete mutation. */
	fun createCentralMonitorGroup(expectedRevision: Long, groupIdentity: UUID, positions: Set<BlockPos>): Boolean

	/** Checks whether every requested position can be removed from the unique group identified by [groupIdentity]. */
	fun canRemoveCentralMonitorGroupMembers(expectedRevision: Long, groupIdentity: UUID, positions: Set<BlockPos>): Boolean

	/** Removes all validated positions atomically and returns whether the complete mutation was applied. */
	fun removeCentralMonitorGroupMembers(expectedRevision: Long, groupIdentity: UUID, positions: Set<BlockPos>): Boolean
}

/** Owns the strict wire protocol and server handlers for Central Monitor membership changes. */
object CentralMonitorMembershipActions {

	private val CREATE_GROUP_ACTION = GTCEu.id("create_central_monitor_group")
	private val REMOVE_GROUP_MEMBERS_ACTION = GTCEu.id("remove_central_monitor_group_members")
	private val HOLDER_INCARNATION_FIELD = SyncFieldData.key("holder_incarnation")
	private val MEMBERSHIP_REVISION_FIELD = SyncFieldData.key("membership_revision")
	private val GROUP_IDENTITY_FIELD = SyncFieldData.key("group_identity")
	private val POSITIONS_FIELD = SyncFieldData.key("positions")
	private const val MAX_MEMBERSHIP_POSITIONS = 65_536

	init {
		SyncActionDispatchers.server().register(CreateGroupHandler)
		SyncActionDispatchers.server().register(RemoveGroupMembersHandler)
	}

	/** Forces handler registration from the owning Central Monitor's class initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one ordered request to add a new group with a client-proposed replay-safe UUID. */
	@JvmStatic
	fun createGroupAction(holderIncarnation: UUID, expectedRevision: Long, groupIdentity: UUID, positions: Set<BlockPos>, sequence: Int): SyncActionData =
		createAction(CREATE_GROUP_ACTION, holderIncarnation, expectedRevision, groupIdentity, positions, sequence)

	/** Creates one ordered request to remove a non-empty set of members from an existing group. */
	@JvmStatic
	fun createRemoveGroupMembersAction(holderIncarnation: UUID, expectedRevision: Long, groupIdentity: UUID, positions: Set<BlockPos>, sequence: Int): SyncActionData = createAction(
		REMOVE_GROUP_MEMBERS_ACTION,
		holderIncarnation,
		expectedRevision,
		groupIdentity,
		positions,
		sequence,
	)

	private fun createAction(actionId: ResourceLocation, holderIncarnation: UUID, expectedRevision: Long, groupIdentity: UUID, positions: Set<BlockPos>, sequence: Int): SyncActionData {
		require(sequence >= 0) { "Central Monitor membership sequence must be non-negative: $sequence" }
		require(expectedRevision >= 0) { "Central Monitor membership revision must be non-negative: $expectedRevision" }
		require(positions.isNotEmpty()) { "Central Monitor membership positions must not be empty." }
		require(positions.size <= MAX_MEMBERSHIP_POSITIONS) {
			"Central Monitor membership positions exceed the protocol limit: ${positions.size}"
		}

		val encodedPositions = JsonArray()
		positions
			.sortedWith(
				compareBy(
					{ position: BlockPos -> position.x },
					{ position -> position.y },
					{ position -> position.z },
				),
			)
			.forEach { position -> encodedPositions.add(encodePosition(position)) }
		val fields =
			SyncFieldData.builder()
				.put(HOLDER_INCARNATION_FIELD, encodeUuid(holderIncarnation))
				.put(MEMBERSHIP_REVISION_FIELD, JsonPrimitive(expectedRevision))
				.put(GROUP_IDENTITY_FIELD, encodeUuid(groupIdentity))
				.put(POSITIONS_FIELD, encodedPositions)
				.build()
		return SyncActionData(
			actionId,
			sequence,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private abstract class MembershipHandler : SyncActionHandler {

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is CentralMonitorMembershipActionTarget

		override fun acceptsPayload(payload: DataComponentMap): Boolean = readCommand(payload) != null

		protected fun mayExecuteBase(player: ServerPlayer, context: SyncActionContext, command: MembershipCommand): Boolean {
			val target = target(context)
			return context.sequence() >= 0 &&
				!player.isSpectator &&
				target.getCentralMonitorActionIncarnation() == command.holderIncarnation &&
				target.getCentralMonitorMembershipRevision() == command.expectedRevision &&
				command.positions.size <= target.getCentralMonitorMembershipCapacity()
		}

		protected fun target(context: SyncActionContext): CentralMonitorMembershipActionTarget = context.holder as? CentralMonitorMembershipActionTarget
			?: throw IllegalStateException("Central Monitor membership action received a non-monitor holder.")
	}

	private object CreateGroupHandler : MembershipHandler() {

		override fun actionId(): ResourceLocation = CREATE_GROUP_ACTION

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean {
			val command = readCommand(context.payload()) ?: return false
			return mayExecuteBase(player, context, command) &&
				target(context).canCreateCentralMonitorGroup(
					command.expectedRevision,
					command.groupIdentity,
					command.positions,
				)
		}

		override fun execute(context: SyncActionContext) {
			val command = requireCommand(context.payload())
			check(
				target(context).createCentralMonitorGroup(
					command.expectedRevision,
					command.groupIdentity,
					command.positions,
				),
			) {
				"Central Monitor group creation became invalid after permission validation."
			}
		}
	}

	private object RemoveGroupMembersHandler : MembershipHandler() {

		override fun actionId(): ResourceLocation = REMOVE_GROUP_MEMBERS_ACTION

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean {
			val command = readCommand(context.payload()) ?: return false
			return mayExecuteBase(player, context, command) &&
				target(context).canRemoveCentralMonitorGroupMembers(
					command.expectedRevision,
					command.groupIdentity,
					command.positions,
				)
		}

		override fun execute(context: SyncActionContext) {
			val command = requireCommand(context.payload())
			check(
				target(context).removeCentralMonitorGroupMembers(
					command.expectedRevision,
					command.groupIdentity,
					command.positions,
				),
			) {
				"Central Monitor membership removal became invalid after permission validation."
			}
		}
	}

	private fun requireCommand(payload: DataComponentMap): MembershipCommand = readCommand(payload)
		?: throw IllegalStateException("Central Monitor membership action omitted a valid command payload.")

	private fun readCommand(payload: DataComponentMap): MembershipCommand? {
		if (payload.size() != 1) return null
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		if (fields.fields.size != 4) return null
		val holderIncarnation = readUuid(fields[HOLDER_INCARNATION_FIELD]) ?: return null
		val expectedRevision = readNonNegativeLong(fields[MEMBERSHIP_REVISION_FIELD]) ?: return null
		val groupIdentity = readUuid(fields[GROUP_IDENTITY_FIELD]) ?: return null
		val positions = readPositions(fields[POSITIONS_FIELD]) ?: return null
		return MembershipCommand(holderIncarnation, expectedRevision, groupIdentity, positions)
	}

	private fun encodeUuid(value: UUID): JsonElement = UUIDUtil.CODEC
		.encodeStart(JsonOps.INSTANCE, value)
		.getOrThrow()

	private fun readUuid(value: JsonElement?): UUID? = value
		?.let { encoded -> UUIDUtil.CODEC.parse(JsonOps.INSTANCE, encoded).result().orElse(null) }

	private fun readNonNegativeLong(value: JsonElement?): Long? {
		val primitive = value as? JsonPrimitive ?: return null
		if (!primitive.isNumber) return null
		return try {
			primitive.asBigDecimal.longValueExact().takeIf { revision -> revision >= 0 }
		} catch (exception: NumberFormatException) {
			logInvalidRevision(exception)
			null
		} catch (exception: ArithmeticException) {
			logInvalidRevision(exception)
			null
		}
	}

	private fun logInvalidRevision(exception: RuntimeException) {
		GTCEu.LOGGER.warn("Central Monitor membership action rejected an inexact revision", exception)
	}

	private fun encodePosition(position: BlockPos): JsonElement = BlockPos.CODEC
		.encodeStart(JsonOps.INSTANCE, position)
		.getOrThrow()

	private fun readPositions(value: JsonElement?): Set<BlockPos>? {
		if (value !is JsonArray || value.isEmpty || value.size() > MAX_MEMBERSHIP_POSITIONS) return null
		val positions = LinkedHashSet<BlockPos>(value.size())
		for (encodedPosition in value) {
			val position = BlockPos.CODEC.parse(JsonOps.INSTANCE, encodedPosition).result().orElse(null)
				?: return null
			if (!positions.add(position)) return null
		}
		return positions
	}

	private data class MembershipCommand(val holderIncarnation: UUID, val expectedRevision: Long, val groupIdentity: UUID, val positions: Set<BlockPos>)
}

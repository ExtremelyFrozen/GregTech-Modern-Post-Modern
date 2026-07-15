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

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonPrimitive
import com.mojang.serialization.JsonOps
import org.jetbrains.annotations.ApiStatus

import java.util.UUID

/**
 * Current or requested raw target selection for one Central Monitor group.
 *
 * @property targetPos raw position of the selected grid component, or `null` when the target is cleared.
 * @property dataSlot zero-based data-item slot associated with [targetPos].
 */
@JvmRecord
data class CentralMonitorGroupTargetState(val targetPos: BlockPos?, val dataSlot: Int)

/** Exposes only the Central Monitor target operation that its server action handler may validate and apply. */
@ApiStatus.Internal
interface CentralMonitorGroupTargetActionHost {

	/** Returns the incarnation that identifies this exact placed Central Monitor to an opened client page. */
	fun getCentralMonitorActionIncarnation(): UUID

	/** Checks the complete compare-and-set request against the current formed grid and group state. */
	fun canSetCentralMonitorGroupTarget(groupIdentity: UUID, expected: CentralMonitorGroupTargetState, requested: CentralMonitorGroupTargetState): Boolean

	/** Applies the complete compare-and-set request if every current-state check still succeeds. */
	fun setCentralMonitorGroupTarget(groupIdentity: UUID, expected: CentralMonitorGroupTargetState, requested: CentralMonitorGroupTargetState): Boolean
}

/** Owns the strict wire protocol and server handler for Central Monitor group target changes. */
object CentralMonitorGroupTargetActions {

	private val SET_GROUP_TARGET_ACTION = GTCEu.id("set_central_monitor_group_target")
	private val HOLDER_INCARNATION_FIELD = SyncFieldData.key("holder_incarnation")
	private val GROUP_IDENTITY_FIELD = SyncFieldData.key("group_identity")
	private val EXPECTED_TARGET_POS_FIELD = SyncFieldData.key("expected_target_pos")
	private val EXPECTED_DATA_SLOT_FIELD = SyncFieldData.key("expected_data_slot")
	private val REQUESTED_TARGET_POS_FIELD = SyncFieldData.key("requested_target_pos")
	private val REQUESTED_DATA_SLOT_FIELD = SyncFieldData.key("requested_data_slot")

	init {
		SyncActionDispatchers.server().register(SetGroupTargetHandler)
	}

	/** Forces handler registration from the owning Central Monitor's static initializer. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one ordered compare-and-set request for a group's raw target and zero-based data slot. */
	@JvmStatic
	fun createSetGroupTargetAction(holderIncarnation: UUID, groupIdentity: UUID, expected: CentralMonitorGroupTargetState, requested: CentralMonitorGroupTargetState, sequence: Int): SyncActionData {
		require(sequence >= 0) { "Central Monitor target sequence must be non-negative: $sequence" }
		require(expected.dataSlot >= 0) {
			"Central Monitor expected target data slot must be non-negative: ${expected.dataSlot}"
		}
		require(requested.dataSlot >= 0) {
			"Central Monitor requested target data slot must be non-negative: ${requested.dataSlot}"
		}
		require(requested.targetPos != null || requested.dataSlot == 0) {
			"Central Monitor cleared target must use data slot zero."
		}
		require(expected != requested) { "Central Monitor target action must change the target state." }

		val fields =
			SyncFieldData.builder()
				.put(HOLDER_INCARNATION_FIELD, encodeUuid(holderIncarnation))
				.put(GROUP_IDENTITY_FIELD, encodeUuid(groupIdentity))
				.put(EXPECTED_TARGET_POS_FIELD, encodeNullablePosition(expected.targetPos))
				.put(EXPECTED_DATA_SLOT_FIELD, JsonPrimitive(expected.dataSlot))
				.put(REQUESTED_TARGET_POS_FIELD, encodeNullablePosition(requested.targetPos))
				.put(REQUESTED_DATA_SLOT_FIELD, JsonPrimitive(requested.dataSlot))
				.build()
		return SyncActionData(
			SET_GROUP_TARGET_ACTION,
			sequence,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private object SetGroupTargetHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_GROUP_TARGET_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is CentralMonitorGroupTargetActionHost

		override fun acceptsPayload(payload: DataComponentMap): Boolean = readCommand(payload) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean {
			val command = readCommand(context.payload()) ?: return false
			val target = target(context)
			return context.sequence() >= 0 &&
				!player.isSpectator &&
				target.getCentralMonitorActionIncarnation() == command.holderIncarnation &&
				target.canSetCentralMonitorGroupTarget(
					command.groupIdentity,
					command.expected,
					command.requested,
				)
		}

		override fun execute(context: SyncActionContext) {
			val command = requireCommand(context.payload())
			check(
				target(context).setCentralMonitorGroupTarget(
					command.groupIdentity,
					command.expected,
					command.requested,
				),
			) {
				"Central Monitor target change became invalid after permission validation."
			}
		}

		private fun target(context: SyncActionContext): CentralMonitorGroupTargetActionHost = context.holder as? CentralMonitorGroupTargetActionHost
			?: throw IllegalStateException("Central Monitor target action received a non-monitor holder.")
	}

	private fun requireCommand(payload: DataComponentMap): TargetCommand = readCommand(payload)
		?: throw IllegalStateException("Central Monitor target action omitted a valid command payload.")

	private fun readCommand(payload: DataComponentMap): TargetCommand? {
		if (payload.size() != 1) return null
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		if (fields.fields.size != 6) return null
		val holderIncarnation = readUuid(fields[HOLDER_INCARNATION_FIELD], HOLDER_INCARNATION_FIELD) ?: return null
		val groupIdentity = readUuid(fields[GROUP_IDENTITY_FIELD], GROUP_IDENTITY_FIELD) ?: return null
		val expectedPosition = readNullablePosition(
			fields[EXPECTED_TARGET_POS_FIELD],
			EXPECTED_TARGET_POS_FIELD,
		) ?: return null
		val expectedSlot = readNonNegativeInt(fields[EXPECTED_DATA_SLOT_FIELD], EXPECTED_DATA_SLOT_FIELD) ?: return null
		val requestedPosition = readNullablePosition(
			fields[REQUESTED_TARGET_POS_FIELD],
			REQUESTED_TARGET_POS_FIELD,
		) ?: return null
		val requestedSlot = readNonNegativeInt(fields[REQUESTED_DATA_SLOT_FIELD], REQUESTED_DATA_SLOT_FIELD) ?: return null
		val expected = CentralMonitorGroupTargetState(expectedPosition.value, expectedSlot)
		val requested = CentralMonitorGroupTargetState(requestedPosition.value, requestedSlot)
		if ((requested.targetPos == null && requested.dataSlot != 0) || expected == requested) return null
		return TargetCommand(holderIncarnation, groupIdentity, expected, requested)
	}

	private fun encodeUuid(value: UUID): JsonElement = UUIDUtil.CODEC
		.encodeStart(JsonOps.INSTANCE, value)
		.getOrThrow()

	private fun readUuid(value: JsonElement?, field: ResourceLocation): UUID? {
		if (value == null) return null
		return try {
			UUIDUtil.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow()
		} catch (exception: RuntimeException) {
			GTCEu.LOGGER.warn("Central Monitor target action rejected invalid UUID field {}", field, exception)
			null
		}
	}

	private fun encodeNullablePosition(position: BlockPos?): JsonElement = position?.let(::encodePosition)
		?: JsonNull.INSTANCE

	private fun encodePosition(position: BlockPos): JsonElement = BlockPos.CODEC
		.encodeStart(JsonOps.INSTANCE, position)
		.getOrThrow()

	private fun readNullablePosition(value: JsonElement?, field: ResourceLocation): NullablePosition? {
		if (value == null) return null
		if (value.isJsonNull) return NullablePosition(null)
		return try {
			NullablePosition(BlockPos.CODEC.parse(JsonOps.INSTANCE, value).getOrThrow())
		} catch (exception: RuntimeException) {
			GTCEu.LOGGER.warn("Central Monitor target action rejected invalid BlockPos field {}", field, exception)
			null
		}
	}

	private fun readNonNegativeInt(value: JsonElement?, field: ResourceLocation): Int? {
		val primitive = value as? JsonPrimitive ?: return null
		if (!primitive.isNumber) return null
		return try {
			primitive.asBigDecimal.intValueExact().takeIf { slot -> slot >= 0 }
		} catch (exception: NumberFormatException) {
			logInvalidSlot(field, exception)
			null
		} catch (exception: ArithmeticException) {
			logInvalidSlot(field, exception)
			null
		}
	}

	private fun logInvalidSlot(field: ResourceLocation, exception: RuntimeException) {
		GTCEu.LOGGER.warn("Central Monitor target action rejected an inexact data slot for field {}", field, exception)
	}

	private data class NullablePosition(val value: BlockPos?)

	private data class TargetCommand(val holderIncarnation: UUID, val groupIdentity: UUID, val expected: CentralMonitorGroupTargetState, val requested: CentralMonitorGroupTargetState)
}

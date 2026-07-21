package com.gregtechceu.gtceu.common.cover

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.capability.recipe.IO
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.cover.data.BucketMode
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

import com.google.gson.JsonPrimitive
import org.jetbrains.annotations.ApiStatus

/** Exposes only the pump configuration state and ordered mutations required by its Kotlin action handler. */
@ApiStatus.Internal
interface PumpCoverConfigActionTarget {

	/** Returns the target pump's maximum milli-bucket transfer rate for bucket-mode permission checks. */
	fun getMaxFluidTransferRate(): Int

	/** Applies the validated non-negative milli-bucket transfer rate before the remaining configuration. */
	fun setTransferRate(transferRate: Int)

	/** Applies the validated import or export direction after the transfer rate. */
	fun setIo(io: IO)

	/** Applies the validated bucket display mode after the transfer direction. */
	fun setBucketMode(bucketMode: BucketMode)

	/** Applies the validated manual I/O mode after the other pump settings. */
	fun setManualIOMode(manualIOMode: ManualIOMode)
}

/** Owns the wire protocol and server handler used by the pump configuration UI. */
object PumpCoverConfigActions {

	private const val ACTION_SEQUENCE = 0
	private val SET_PUMP_COVER_CONFIG_ACTION = GTCEu.id("set_pump_cover_config")
	private val TRANSFER_RATE_FIELD = SyncFieldData.key("transferRate")
	private val IO_FIELD = SyncFieldData.key("io")
	private val BUCKET_MODE_FIELD = SyncFieldData.key("bucketMode")
	private val MANUAL_IO_FIELD = SyncFieldData.key("manualIO")
	private val BUCKET_MODES = BucketMode.entries
	private val MANUAL_IO_MODES = ManualIOMode.entries

	init {
		SyncActionDispatchers.server().register(PumpCoverConfigActionHandler)
	}

	/** Forces common-side handler registration when the owning cover class initializes. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one complete client request from the current pump state. */
	@JvmStatic
	fun createSetConfigAction(transferRate: Int, io: IO, bucketMode: BucketMode, manualIOMode: ManualIOMode): SyncActionData {
		require(transferRate >= 0) { "Pump transfer rate must be non-negative: $transferRate" }
		require(isImportExport(io)) { "Pump IO mode must be import or export: $io" }
		val fields =
			SyncFieldData.builder()
				.put(TRANSFER_RATE_FIELD, JsonPrimitive(transferRate))
				.put(IO_FIELD, JsonPrimitive(io.ordinal))
				.put(BUCKET_MODE_FIELD, JsonPrimitive(bucketMode.ordinal))
				.put(MANUAL_IO_FIELD, JsonPrimitive(manualIOMode.ordinal))
				.build()
		return SyncActionData(
			SET_PUMP_COVER_CONFIG_ACTION,
			ACTION_SEQUENCE,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private object PumpCoverConfigActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_PUMP_COVER_CONFIG_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is PumpCoverConfigActionTarget

		override fun acceptsPayload(payload: DataComponentMap): Boolean = read(payload) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean {
			if (player.isSpectator) {
				return false
			}
			val config = requireConfig(context.payload())
			return config.bucketMode.multiplier <= target(context).getMaxFluidTransferRate()
		}

		override fun execute(context: SyncActionContext) {
			val target = target(context)
			val config = requireConfig(context.payload())
			target.setTransferRate(config.transferRate)
			target.setIo(config.io)
			target.setBucketMode(config.bucketMode)
			target.setManualIOMode(config.manualIOMode)
		}
	}

	private data class Config(val transferRate: Int, val io: IO, val bucketMode: BucketMode, val manualIOMode: ManualIOMode)

	private fun read(payload: DataComponentMap): Config? {
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		val transferRate = readExactInt(fields, TRANSFER_RATE_FIELD)?.takeIf { value -> value >= 0 } ?: return null
		val io = readIO(fields) ?: return null
		val bucketMode = readOrdinal(fields, BUCKET_MODE_FIELD, BUCKET_MODES) ?: return null
		val manualIOMode = readOrdinal(fields, MANUAL_IO_FIELD, MANUAL_IO_MODES) ?: return null
		return Config(transferRate, io, bucketMode, manualIOMode)
	}

	private fun requireConfig(payload: DataComponentMap): Config = read(payload)
		?: throw IllegalStateException("Pump cover config action payload is invalid.")

	private fun target(context: SyncActionContext): PumpCoverConfigActionTarget = context.holder as? PumpCoverConfigActionTarget
		?: throw IllegalStateException("Pump cover config action received a non-pump target.")

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
			"Pump cover config action rejected invalid integer field {}: {}",
			field,
			primitive,
			exception,
		)
	}

	private fun isImportExport(io: IO): Boolean = io == IO.IN || io == IO.OUT
}

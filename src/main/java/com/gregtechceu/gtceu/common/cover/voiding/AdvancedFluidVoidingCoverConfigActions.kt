package com.gregtechceu.gtceu.common.cover.voiding

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.cover.data.BucketMode
import com.gregtechceu.gtceu.common.cover.data.VoidingMode
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

import com.google.gson.JsonPrimitive
import org.jetbrains.annotations.ApiStatus

/** Exposes only the ordered mutations that the advanced fluid voiding config action may invoke. */
@ApiStatus.Internal
interface AdvancedFluidVoidingCoverConfigActionTarget {

	/** Applies the validated voiding mode before mode-dependent bucket and size state. */
	fun setVoidingMode(voidingMode: VoidingMode)

	/** Applies the validated display bucket mode before storing the base-unit transfer size. */
	fun setTransferBucketMode(transferBucketMode: BucketMode)

	/** Applies the validated positive transfer size in milli-buckets. */
	fun setGlobalTransferSizeMillibuckets(transferSize: Int)
}

/** Owns the wire protocol and server handler for advanced fluid voiding cover configuration. */
object AdvancedFluidVoidingCoverConfigActions {

	private val SET_ADVANCED_FLUID_VOIDING_COVER_CONFIG_ACTION = GTCEu.id("set_advanced_fluid_voiding_cover_config")
	private val VOIDING_MODE_FIELD = SyncFieldData.key("voidingMode")
	private val VOID_SIZE_FIELD = SyncFieldData.key("voidSize")
	private val BUCKET_MODE_FIELD = SyncFieldData.key("bucketMode")
	private val VOIDING_MODES = VoidingMode.entries
	private val BUCKET_MODES = BucketMode.entries

	init {
		SyncActionDispatchers.server().register(AdvancedFluidVoidingCoverConfigActionHandler)
	}

	/** Forces common-side handler registration when the owning cover class initializes. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one complete client request from the current advanced fluid voiding state. */
	@JvmStatic
	fun createSetConfigAction(voidingMode: VoidingMode, transferSize: Int, bucketMode: BucketMode): SyncActionData {
		require(transferSize > 0) { "Advanced fluid voiding transfer size must be positive: $transferSize" }
		val fields =
			SyncFieldData.builder()
				.put(VOIDING_MODE_FIELD, JsonPrimitive(voidingMode.ordinal))
				.put(VOID_SIZE_FIELD, JsonPrimitive(transferSize))
				.put(BUCKET_MODE_FIELD, JsonPrimitive(bucketMode.ordinal))
				.build()
		return SyncActionData(
			SET_ADVANCED_FLUID_VOIDING_COVER_CONFIG_ACTION,
			0,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private object AdvancedFluidVoidingCoverConfigActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_ADVANCED_FLUID_VOIDING_COVER_CONFIG_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = context.holder is AdvancedFluidVoidingCover

		override fun acceptsPayload(payload: DataComponentMap): Boolean = readConfig(payload) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			val target =
				context.holder as? AdvancedFluidVoidingCoverConfigActionTarget
					?: throw IllegalStateException(
						"Advanced fluid voiding config action received a non-advanced-fluid-voiding target.",
					)
			val config = requireConfig(context.payload())
			target.setVoidingMode(config.voidingMode)
			target.setTransferBucketMode(config.bucketMode)
			target.setGlobalTransferSizeMillibuckets(config.transferSize)
		}
	}

	private fun readConfig(payload: DataComponentMap): Config? {
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		val voidingModeOrdinal = readExactInt(fields, VOIDING_MODE_FIELD)
			?.takeIf { ordinal -> ordinal in VOIDING_MODES.indices }
			?: return null
		val transferSize = readExactInt(fields, VOID_SIZE_FIELD)
			?.takeIf { size -> size > 0 }
			?: return null
		val bucketModeOrdinal = readExactInt(fields, BUCKET_MODE_FIELD)
			?.takeIf { ordinal -> ordinal in BUCKET_MODES.indices }
			?: return null
		return Config(VOIDING_MODES[voidingModeOrdinal], transferSize, BUCKET_MODES[bucketModeOrdinal])
	}

	private fun requireConfig(payload: DataComponentMap): Config = readConfig(payload)
		?: throw IllegalStateException("Advanced fluid voiding config action payload is invalid.")

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
			"Advanced fluid voiding config action rejected invalid integer field {}: {}",
			field,
			primitive,
			exception,
		)
	}

	private data class Config(val voidingMode: VoidingMode, val transferSize: Int, val bucketMode: BucketMode)
}

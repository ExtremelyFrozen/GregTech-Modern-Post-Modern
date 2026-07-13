package com.gregtechceu.gtceu.api.machine.fancyconfigurator

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.capability.IControllable
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer

import com.google.gson.JsonPrimitive

/** Owns the GT action protocol for the LDLib2 working-enabled configurator. */
object LDLib2WorkingEnabledFancyConfiguratorActions {

	private val SET_WORKING_ENABLED_ACTION = GTCEu.id("set_working_enabled")
	private val WORKING_ENABLED_FIELD = SyncFieldData.key("workingEnabled")

	init {
		SyncActionDispatchers.server().register(WorkingEnabledActionHandler)
	}

	/** Forces common-side handler registration before the configurator can send its action. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one holder-scoped request to change the working-enabled state. */
	@JvmStatic
	fun createSetWorkingEnabledAction(workingEnabled: Boolean): SyncActionData {
		val fields =
			SyncFieldData.builder()
				.put(WORKING_ENABLED_FIELD, JsonPrimitive(workingEnabled))
				.build()
		return SyncActionData(
			SET_WORKING_ENABLED_ACTION,
			if (workingEnabled) 1 else 0,
			fields.toComponentMap(GTDataComponents.SYNC_FIELD_DATA.get()),
		)
	}

	private object WorkingEnabledActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_WORKING_ENABLED_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean = readControllable(context) != null

		override fun acceptsPayload(payload: DataComponentMap): Boolean = readWorkingEnabled(payload) != null

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			requireControllable(context).setWorkingEnabled(requireWorkingEnabled(context.payload()))
		}
	}

	private fun readControllable(context: SyncActionContext): IControllable? {
		val holder = context.holder
		if (holder !is IControllable || holder !is LDLib2FancyActionMachine) {
			return null
		}
		return holder
	}

	private fun requireControllable(context: SyncActionContext): IControllable = readControllable(context)
		?: throw IllegalStateException("Working-enabled action received an unauthorized holder.")

	private fun requireWorkingEnabled(payload: DataComponentMap): Boolean = readWorkingEnabled(payload)
		?: throw IllegalStateException("Working-enabled action payload is missing enabled state.")

	private fun readWorkingEnabled(payload: DataComponentMap): Boolean? {
		val fields = payload[GTDataComponents.SYNC_FIELD_DATA.get()] ?: return null
		val primitive = fields[WORKING_ENABLED_FIELD] as? JsonPrimitive ?: return null
		return if (primitive.isBoolean) primitive.asBoolean else null
	}
}

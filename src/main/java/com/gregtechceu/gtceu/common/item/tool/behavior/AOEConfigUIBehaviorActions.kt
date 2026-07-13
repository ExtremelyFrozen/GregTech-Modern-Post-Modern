package com.gregtechceu.gtceu.common.item.tool.behavior

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.item.IGTTool
import com.gregtechceu.gtceu.api.item.datacomponents.AoESymmetrical
import com.gregtechceu.gtceu.api.item.tool.ToolHelper
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext
import com.gregtechceu.gtceu.api.sync_system.SyncActionData
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.component.DataComponentMap
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/** Owns the wire protocol and server handler for the held-tool AOE configuration UI. */
object AOEConfigUIBehaviorActions {

	private const val ACTION_SEQUENCE = 0
	private val SET_TOOL_AOE_ACTION = GTCEu.id("set_tool_aoe")

	init {
		SyncActionDispatchers.server().register(AOEConfigurationActionHandler)
	}

	/** Forces common-side handler registration when the owning behavior class initializes. */
	@JvmStatic
	fun initialize() = Unit

	/** Creates one client request for a complete, locally valid AOE definition. */
	@JvmStatic
	fun createSetToolAOEAction(definition: AoESymmetrical): SyncActionData {
		require(isValidAOEDefinition(definition)) { "Given AOE definition is out of range." }
		val payload =
			DataComponentMap
				.builder()
				.set(GTDataComponents.AOE.get(), definition)
				.build()
		return SyncActionData(SET_TOOL_AOE_ACTION, ACTION_SEQUENCE, payload)
	}

	private object AOEConfigurationActionHandler : SyncActionHandler {

		override fun actionId(): ResourceLocation = SET_TOOL_AOE_ACTION

		override fun acceptsHolder(context: SyncActionContext): Boolean {
			val stack = context.holder as? ItemStack ?: return false
			val openedStack = context.openedStack ?: return false
			return ItemStack.isSameItem(stack, openedStack) &&
				isAOEConfigTool(stack) &&
				hasConfigurableAOE(stack) &&
				hasConfigurableAOE(openedStack)
		}

		override fun acceptsPayload(payload: DataComponentMap): Boolean {
			val definition = payload[GTDataComponents.AOE.get()] ?: return false
			return isValidAOEDefinition(definition)
		}

		override fun mayExecute(player: ServerPlayer, context: SyncActionContext): Boolean = !player.isSpectator

		override fun execute(context: SyncActionContext) {
			val stack = context.holder as? ItemStack
				?: throw IllegalStateException("AOE configuration action received a non-item holder.")
			val requested = context.payload()[GTDataComponents.AOE.get()]
				?: throw IllegalStateException("AOE configuration action payload is missing.")
			val current = ToolHelper.getAoEDefinition(stack)
			require(canApplyAOEDefinition(current, requested)) {
				"AOE configuration action payload does not match the current tool."
			}
			stack.set(GTDataComponents.AOE, requested)
		}
	}

	private fun hasConfigurableAOE(stack: ItemStack): Boolean = !ToolHelper.getAoEDefinition(stack).isZero()

	private fun isAOEConfigTool(stack: ItemStack): Boolean {
		val tool = stack.item as? IGTTool ?: return false
		return tool.toolStats.behaviors.any { behavior -> behavior is AOEConfigUIBehavior }
	}

	private fun isValidAOEDefinition(definition: AoESymmetrical): Boolean = definition.maxColumn() >= 0 &&
		definition.maxRow() >= 0 &&
		definition.maxLayer() >= 0 &&
		definition.column() >= 0 &&
		definition.column() <= definition.maxColumn() &&
		definition.row() >= 0 &&
		definition.row() <= definition.maxRow() &&
		definition.layer() >= 0 &&
		definition.layer() <= definition.maxLayer() &&
		!definition.isZero()

	private fun canApplyAOEDefinition(current: AoESymmetrical, requested: AoESymmetrical): Boolean = isValidAOEDefinition(current) &&
		isValidAOEDefinition(requested) &&
		current.maxColumn() == requested.maxColumn() &&
		current.maxRow() == requested.maxRow() &&
		current.maxLayer() == requested.maxLayer()
}

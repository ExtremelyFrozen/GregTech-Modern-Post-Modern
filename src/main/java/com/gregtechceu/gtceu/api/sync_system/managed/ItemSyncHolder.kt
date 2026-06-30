package com.gregtechceu.gtceu.api.sync_system.managed

import com.gregtechceu.gtceu.api.sync_system.SyncDataHolder
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.HolderLookup
import net.minecraft.world.item.ItemStack

import org.jetbrains.annotations.Nullable

class ItemSyncHolder(owner: ISyncManaged) : ISyncManaged {
	private val syncDataHolder: SyncDataHolder = SyncDataHolder(owner)

	override fun getSyncDataHolder(): SyncDataHolder = syncDataHolder

	fun saveToStack(stack: ItemStack, registries: HolderLookup.Provider) {
		val data = syncDataHolder.serializeToItemComponents(registries)
		if (!data.isEmpty) {
			stack.set(GTDataComponents.BLOCK_ITEM_DATA, data)
		}
	}

	fun loadFromStack(stack: ItemStack, registries: HolderLookup.Provider, clientSide: Boolean) {
		val data = stack.get(GTDataComponents.BLOCK_ITEM_DATA)
		if (data == null || data.isEmpty) {
			return
		}

		syncDataHolder.deserializeItemComponents(registries, data)
	}

	@Nullable
	override fun getParentSyncObject(): ISyncManaged? = null

	override fun scheduleRenderUpdate() {}

	override fun markAsChanged() {}
}

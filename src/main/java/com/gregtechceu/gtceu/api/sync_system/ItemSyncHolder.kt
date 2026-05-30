package com.gregtechceu.gtceu.api.sync_system

import com.gregtechceu.gtceu.common.data.item.GTDataComponents

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.item.ItemStack

import org.jetbrains.annotations.Nullable

class ItemSyncHolder(owner: ISyncManaged) : ISyncManaged {
	private val syncDataHolder: SyncDataHolder = SyncDataHolder(owner)

	override fun getSyncDataHolder(): SyncDataHolder = syncDataHolder

	fun saveToStack(stack: ItemStack, registries: HolderLookup.Provider) {
		val data = syncDataHolder.serializeToItemNBT(registries)
		if (!data.isEmpty) {
			stack.set(GTDataComponents.BLOCK_ITEM_DATA, data)
		}
	}

	fun loadFromStack(stack: ItemStack, registries: HolderLookup.Provider, clientSide: Boolean) {
		val data = stack.get(GTDataComponents.BLOCK_ITEM_DATA)
		if (data == null || data.isEmpty) {
			return
		}

		syncDataHolder.deserializeItemNBT(registries, data)
		if (clientSide) {
			syncDataHolder.deserializeNBT(registries, data, true)
		}
	}

	fun scanChanges(registries: HolderLookup.Provider): Boolean = syncDataHolder.scanAndMarkChanges(registries)

	fun flushToStack(stack: ItemStack) {
		val pending = syncDataHolder.getPendingChanges()
		if (pending.isEmpty) {
			return
		}

		val existing = stack.get(GTDataComponents.BLOCK_ITEM_DATA)
		stack.set(GTDataComponents.BLOCK_ITEM_DATA, existing?.merge(pending) ?: pending)
	}

	fun applyServerUpdate(registries: HolderLookup.Provider, tag: CompoundTag) {
		syncDataHolder.applyServerUpdate(registries, tag)
	}

	@Nullable
	override fun getParentSyncObject(): ISyncManaged? = null

	override fun scheduleRenderUpdate() {}

	override fun markAsChanged() {}
}

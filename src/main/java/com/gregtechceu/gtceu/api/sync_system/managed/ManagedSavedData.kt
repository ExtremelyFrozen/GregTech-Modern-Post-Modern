package com.gregtechceu.gtceu.api.sync_system.managed

import com.gregtechceu.gtceu.api.sync_system.SyncDataHolder

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.saveddata.SavedData

/**
 * A [SavedData] object that stores and loads its data via the sync system.<br></br>
 * [ManagedSavedData] is not synced to clients.
 */
abstract class ManagedSavedData :
	SavedData,
	ISyncManaged {

	@JvmField
	protected val syncDataHolder: SyncDataHolder = SyncDataHolder(this)

	constructor()

	constructor(tag: CompoundTag, registries: HolderLookup.Provider) {
		getSyncDataHolder().deserializeNBT(registries, tag, false)
	}

	override fun getSyncDataHolder(): SyncDataHolder = syncDataHolder

	override fun getParentSyncObject(): ISyncManaged? = null

	// No functionality, not synced to clients
	override fun markAsChanged() {}

	// No functionality, not synced to clients
	override fun scheduleRenderUpdate() {}

	override fun isDirty(): Boolean = true

	override fun save(compoundTag: CompoundTag, registries: HolderLookup.Provider): CompoundTag = getSyncDataHolder().serializeNBT(registries, false)
}

package com.gregtechceu.gtceu.api.sync_system.managed

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.SyncDataHolder

import net.minecraft.core.HolderLookup
import net.minecraft.core.component.DataComponentMap
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.world.level.saveddata.SavedData

/**
 * A [SavedData] object that stores and loads its data via the sync system.<br></br>
 * [ManagedSavedData] is not synced to clients.
 */
abstract class ManagedSavedData :
	SavedData,
	ISyncManaged {
	protected var savedSyncDataKey: String = DEFAULT_SYNC_DATA_KEY

	@JvmField
	protected val syncDataHolder: SyncDataHolder = SyncDataHolder(this)

	constructor()

	protected constructor(savedSyncDataKey: String) {
		this.savedSyncDataKey = savedSyncDataKey
	}

	constructor(tag: CompoundTag, registries: HolderLookup.Provider) : this(tag, registries, DEFAULT_SYNC_DATA_KEY)

	protected constructor(tag: CompoundTag, registries: HolderLookup.Provider, savedSyncDataKey: String) {
		this.savedSyncDataKey = savedSyncDataKey
		loadSavedSyncData(tag, registries)
	}

	private fun loadSavedSyncData(tag: CompoundTag, registries: HolderLookup.Provider) {
		if (tag.contains(savedSyncDataKey)) {
			val savedData = DataComponentMap.CODEC
				.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get(savedSyncDataKey))
				.getOrThrow()
			getSyncDataHolder().deserializeComponents(registries, savedData, false)
		}
	}

	override fun getSyncDataHolder(): SyncDataHolder = syncDataHolder

	override fun getParentSyncObject(): ISyncManaged? = null

	// No functionality, not synced to clients
	override fun markAsChanged() {}

	// No functionality, not synced to clients
	override fun scheduleRenderUpdate() {}

	override fun isDirty(): Boolean = true

	override fun save(compoundTag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
		val savedData = getSyncDataHolder().serializeToComponents(registries, writeClientFields = false, fullSync = false)
		if (!savedData.isEmpty) {
			compoundTag.put(
				savedSyncDataKey,
				DataComponentMap.CODEC
					.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), savedData)
					.getOrThrow(),
			)
		}
		return compoundTag
	}

	private companion object {

		private val DEFAULT_SYNC_DATA_KEY = "${GTCEu.MOD_ID}_sync_data"
	}
}

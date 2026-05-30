package com.gregtechceu.gtceu.api.sync_system

import net.neoforged.neoforge.common.util.INBTSerializable

import org.jetbrains.annotations.Nullable

import kotlin.jvm.JvmDefaultWithCompatibility

/**
 * Represents a class with fields that have sync annotations. <br></br>
 * Differs from [ISyncAnnotated] in that more control is provided over syncing. <br></br>
 * An [ISyncManaged] class manages the sync status of itself and its fields,
 * while a [ISyncAnnotated] must be managed by a field in an [ISyncManaged] class.
 *
 *
 * A field of type `T` can be marked with sync annotations if:
 * <ul>
 * <li>`T` is primitive</li>
 * <li>`T` has a [FieldCodecs] codec registered</li>
 * <li>`T` implements [INBTSerializable]</li>
 * <li>`T` is an [ISyncManaged] or [ISyncAnnotated] class</li>
 * </ul>
 *
 * @see SyncDataHolder
 * @see ISyncAnnotated
 */
@JvmDefaultWithCompatibility
interface ISyncManaged {
	fun getSyncDataHolder(): SyncDataHolder

	/**
	 * Gets the parent sync object of this sync object
	 *
	 * @return The parent sync object, can only return null if this object does not have a parent sync object and both
	 * [scheduleRenderUpdate] and [markAsChanged] are overriden
	 */
	@Nullable
	fun getParentSyncObject(): ISyncManaged?

	/**
	 * Function called when a synced field requests a rerender
	 */
	fun scheduleRenderUpdate() {
		getParentSyncObject()?.scheduleRenderUpdate()
	}

	/**
	 * Function called to notify the server that this object has been updated and must be synced to clients
	 */
	fun markAsChanged() {
		getParentSyncObject()?.markAsChanged()
	}
}

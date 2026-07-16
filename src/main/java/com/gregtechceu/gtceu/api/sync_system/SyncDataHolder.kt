package com.gregtechceu.gtceu.api.sync_system

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.managed.ISyncManaged
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.HolderLookup
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponentMap

import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.mojang.serialization.JsonOps
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectSet
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap
import org.jetbrains.annotations.Nullable

import java.lang.invoke.MethodHandle
import java.lang.invoke.WrongMethodTypeException

/**
 * Result of validating and transactionally applying one client-to-server field update batch.
 */
data class ServerFieldUpdateResult(val accepted: Boolean, val changed: Boolean, @field:Nullable val rejectionReason: String?) {
	companion object {
		@JvmStatic
		fun accepted(changed: Boolean): ServerFieldUpdateResult = ServerFieldUpdateResult(true, changed, null)

		@JvmStatic
		fun rejected(reason: String): ServerFieldUpdateResult = ServerFieldUpdateResult(false, false, reason)
	}
}

/**
 * Class that holds all sync info for an [ISyncManaged] object.
 */
class SyncDataHolder(private val holder: ISyncManaged) {
	private val syncData: ClassSyncData = ClassSyncData.getClassData(holder.javaClass)
	private val cachedClientValues: MutableMap<FieldSyncData, Any?> = Reference2ReferenceOpenHashMap()
	private val cachedServerValues: MutableMap<FieldSyncData, Any?> = Reference2ReferenceOpenHashMap()
	private val dirtySyncFields: ObjectSet<String> = ObjectOpenHashSet()

	@field:Nullable
	private var pendingClientChanges: DataComponentMap? = null
	private var resyncAll = true

	init {
		for (field in syncData.getClientSyncFields()) {
			cachedClientValues[field] = field.handle.get(holder)
		}
		for (field in syncData.getServerUpdateFields()) {
			cachedServerValues[field] = field.handle.get(holder)
		}
	}

	/**
	 * Instructs the sync system that this field has been updated and must be synced with clients.
	 *
	 * @param fieldName The field that has changed.
	 */
	fun markClientSyncFieldDirty(fieldName: String) {
		dirtySyncFields.add(fieldName)
		holder.markAsChanged()
	}

	fun resyncAllFields() {
		resyncAll = true
		holder.markAsChanged()
	}

	fun serializeToItemComponents(registries: HolderLookup.Provider): DataComponentMap = componentsOf(serializeToItemFieldData(registries))

	fun serializeToItemFieldData(registries: HolderLookup.Provider): SyncFieldData {
		val builder = SyncFieldData.builder()
		for (field in syncData.getItemSaveFields()) {
			val value = FieldSyncHandler.serializeFieldData(
				registries,
				holder,
				field,
				writeClientFields = false,
				fullSync = false,
			)
			if (!value.isJsonNull) {
				builder.put(itemFieldKey(field), value)
			}
		}
		return builder.build()
	}

	fun serializeFullClientSyncData(registries: HolderLookup.Provider): SyncFieldData {
		val builder = SyncFieldData.builder()
		for (field in syncData.getClientSyncFields()) {
			builder.put(field.componentKey, FieldSyncHandler.serializeFieldData(registries, holder, field, true, fullSync = true))
			cachedClientValues[field] = field.handle.get(holder)
		}
		resyncAll = false
		dirtySyncFields.clear()
		pendingClientChanges = null
		return builder.build()
	}

	fun serializeFullClientSyncComponents(registries: HolderLookup.Provider): DataComponentMap = componentsOf(serializeFullClientSyncData(registries))

	/**
	 * Serializes one client field for validation without consuming pending changes or updating synchronization caches.
	 */
	fun serializeClientFieldSnapshot(registries: HolderLookup.Provider, fieldName: String): JsonElement {
		val field = syncData.getClientSyncFields().singleOrNull { candidate -> candidate.fieldName == fieldName }
			?: throw IllegalArgumentException("Unknown or ambiguous client sync field: $fieldName")
		return FieldSyncHandler.serializeFieldData(
			registries,
			holder,
			field,
			writeClientFields = true,
			fullSync = true,
		)
	}

	fun serializeToFieldData(registries: HolderLookup.Provider, writeClientFields: Boolean, fullSync: Boolean): SyncFieldData = if (writeClientFields) {
		if (fullSync) {
			serializeFullClientSyncData(registries)
		} else {
			if (pendingClientChanges == null) {
				scanAndMarkChanges(registries)
			}
			getPendingFieldData()
		}
	} else {
		serializeToSaveFieldData(registries)
	}

	fun serializeToComponents(registries: HolderLookup.Provider, writeClientFields: Boolean, fullSync: Boolean): DataComponentMap = if (writeClientFields) {
		if (fullSync) {
			serializeFullClientSyncComponents(registries)
		} else {
			if (pendingClientChanges == null) {
				scanAndMarkChanges(registries)
			}
			getPendingChanges()
		}
	} else {
		componentsOf(serializeToSaveFieldData(registries))
	}

	fun serializeToSaveFieldData(registries: HolderLookup.Provider): SyncFieldData {
		val builder = SyncFieldData.builder()
		for (field in syncData.getServerSaveFields()) {
			val value = FieldSyncHandler.serializeFieldData(
				registries,
				holder,
				field,
				writeClientFields = false,
				fullSync = false,
			)
			if (!value.isJsonNull) {
				builder.put(field.componentKey, value)
			}
		}
		return builder.build()
	}

	fun scanAndMarkChanges(registries: HolderLookup.Provider): Boolean {
		val changes = SyncFieldData.builder()
		var hasChanges = false
		val fullSync = resyncAll

		for (field in syncData.getClientSyncFields()) {
			val currentValue = field.handle.get(holder)
			val previousValue = cachedClientValues[field]
			val manuallyDirty = dirtySyncFields.contains(field.fieldName)
			val changed = fullSync ||
				manuallyDirty ||
				currentValue != previousValue ||
				shouldSyncContextualField(registries, field, currentValue, fullSync, manuallyDirty)
			if (changed) {
				changes.put(field.componentKey, FieldSyncHandler.serializeFieldData(registries, holder, field, true, fullSync))
				cachedClientValues[field] = currentValue
				hasChanges = true
			}
		}

		resyncAll = false
		dirtySyncFields.clear()
		if (hasChanges) {
			pendingClientChanges = componentsOf(changes.build())
			return true
		}
		return false
	}

	private fun shouldSyncContextualField(registries: HolderLookup.Provider, field: FieldSyncData, @Nullable currentValue: Any?, fullSync: Boolean, manuallyDirty: Boolean): Boolean =
		shouldSyncContextualField(
			registries,
			field,
			currentValue,
			fullSync,
			manuallyDirty,
			SyncSerializationTarget.DATA_COMPONENTS,
		)

	@Suppress("UNCHECKED_CAST")
	private fun shouldSyncContextualField(
		registries: HolderLookup.Provider,
		field: FieldSyncData,
		@Nullable currentValue: Any?,
		fullSync: Boolean,
		manuallyDirty: Boolean,
		serializationTarget: SyncSerializationTarget,
	): Boolean {
		if (currentValue == null) {
			return false
		}
		if (field.contextualCodec == null) {
			field.setContextualCodec(FieldCodecs.getContextual(field.type.rawType))
		}
		if (field.contextualCodec == null) {
			return false
		}
		return (field.contextualCodec as ContextualFieldCodec<Any>).shouldSyncField(
			currentValue,
			ContextualFieldCodec.Context(
				holder,
				field.type,
				currentValue,
				field.fieldName,
				true,
				fullSync,
				registries,
				serializationTarget,
			),
			fullSync,
			manuallyDirty,
		)
	}

	fun getPendingChanges(): DataComponentMap {
		val changes = pendingClientChanges
		pendingClientChanges = null
		return changes ?: DataComponentMap.EMPTY
	}

	fun getPendingFieldData(): SyncFieldData {
		val changes = getPendingChanges()
		return changes.get(GTDataComponents.SYNC_FIELD_DATA.get()) ?: SyncFieldData.EMPTY
	}

	fun collectClientNetworkChanges(registries: RegistryAccess, force: Boolean): DataComponentMap {
		if (force) {
			pendingClientChanges = serializeFullClientSyncComponents(registries)
		}

		return getPendingChanges()
	}

	fun collectServerNetworkChanges(registries: RegistryAccess): DataComponentMap {
		val changes = SyncFieldData.builder()
		var wroteAny = false
		val fields = syncData.getOrderedServerUpdateFields()
		for (field in fields) {
			val currentValue = field.handle.get(holder)
			val previousValue = cachedServerValues[field]
			if (currentValue == previousValue) {
				continue
			}
			changes.put(
				field.componentKey,
				FieldSyncHandler.encodeDetachedServerCandidate(registries, holder, field),
			)
			cachedServerValues[field] = currentValue
			wroteAny = true
		}

		if (!wroteAny) {
			return DataComponentMap.EMPTY
		}
		return componentsOf(changes.build())
	}

	fun deserializeItemComponents(registries: HolderLookup.Provider, components: DataComponentMap) {
		val fieldData = components.get(GTDataComponents.SYNC_FIELD_DATA.get())
			?: return
		deserializeItemFieldData(registries, fieldData)
	}

	@JvmOverloads
	fun deserializeComponents(registries: HolderLookup.Provider, components: DataComponentMap, readingClientFields: Boolean, parseExplicitNull: Boolean = false) {
		val fieldData = components.get(GTDataComponents.SYNC_FIELD_DATA.get())
			?: return
		deserializeFieldData(registries, fieldData, readingClientFields, parseExplicitNull)
	}

	fun deserializeItemFieldData(registries: HolderLookup.Provider, fieldData: SyncFieldData) {
		for (field in syncData.getItemSaveFields()) {
			if (!fieldData.fields.containsKey(itemFieldKey(field))) {
				continue
			}
			val savedValue = fieldData.get(itemFieldKey(field)) ?: JsonNull.INSTANCE
			FieldSyncHandler.deserializeFieldData(registries, holder, field, savedValue, false)
		}
	}

	@JvmOverloads
	fun deserializeFieldData(registries: HolderLookup.Provider, fieldData: SyncFieldData, readingClientFields: Boolean, parseExplicitNull: Boolean = false) {
		val fieldsToCheck = if (readingClientFields) syncData.getClientSyncFields() else syncData.getServerSaveFields()
		for (field in fieldsToCheck) {
			if (!fieldData.fields.containsKey(field.componentKey)) {
				continue
			}
			val savedValue = fieldData.get(field.componentKey) ?: JsonNull.INSTANCE
			FieldSyncHandler.deserializeFieldData(registries, holder, field, savedValue, readingClientFields, parseExplicitNull)

			if (readingClientFields) {
				val currentValue = field.handle.get(holder)
				cachedClientValues[field] = currentValue
				if (field.hasSyncBoth) {
					cachedServerValues[field] = currentValue
				}
				invokeClientChangeListeners(field)

				if (field.triggerClientRerender) holder.scheduleRenderUpdate()
			}
		}
	}

	fun applyServerNetworkUpdate(registries: RegistryAccess, components: DataComponentMap) {
		val result = tryApplyServerNetworkUpdate(registries, components)
		if (!result.accepted) {
			throw IllegalArgumentException(result.rejectionReason ?: "Sync: Server field update was rejected")
		}
	}

	/**
	 * Applies a client field-update batch without exposing partially decoded or normalized values to the holder.
	 */
	fun tryApplyServerNetworkUpdate(registries: RegistryAccess, components: DataComponentMap): ServerFieldUpdateResult {
		if (components.isEmpty) {
			return ServerFieldUpdateResult.accepted(false)
		}

		val changes = components.get(GTDataComponents.SYNC_FIELD_DATA.get())
			?: return ServerFieldUpdateResult.rejected("Sync: Server field update is missing sync field data")
		if (changes.isEmpty()) {
			return ServerFieldUpdateResult.rejected("Sync: Server field update contains empty sync field data")
		}

		val serverFields = syncData.getOrderedServerUpdateFields()
		val selectedFields = serverFields.filter { field -> changes.fields.containsKey(field.componentKey) }
		val selectedKeys = selectedFields.mapTo(HashSet()) { field -> field.componentKey }
		val rejectedKeys = changes.fields.keys.filterNot(selectedKeys::contains)
		if (rejectedKeys.isNotEmpty()) {
			requestAuthoritativeServerAcks(selectedFields)
			return ServerFieldUpdateResult.rejected(
				"Sync: Server field update contains unknown or non-server-updatable fields: ${rejectedKeys.sortedBy { key -> key.toString() }}",
			)
		}
		if (selectedFields.isEmpty()) {
			return ServerFieldUpdateResult.rejected("Sync: Server field update contains no server-updatable fields")
		}

		val pendingUpdates = ArrayList<PendingServerFieldUpdate>(selectedFields.size)
		try {
			// Decode every candidate without consulting or mutating the holder.
			for (field in selectedFields) {
				val encodedValue = changes.get(field.componentKey) ?: JsonNull.INSTANCE
				val candidate = FieldSyncHandler.decodeDetachedServerCandidate(registries, holder, field, encodedValue)
				pendingUpdates.add(PendingServerFieldUpdate(field, candidate))
			}

			// Snapshot every old value before a normalizer is allowed to run.
			for (update in pendingUpdates) {
				update.oldValue = update.field.handle.get(holder)
				validateServerCandidateType(update.field, update.decodedCandidate)
			}

			// Normalize the complete decoded batch before committing any field.
			for (update in pendingUpdates) {
				update.normalizedCandidate = normalizeServerCandidate(update)
			}
			for (update in pendingUpdates) {
				validateServerCandidateType(update.field, update.normalizedCandidate)
			}

			commitServerUpdates(pendingUpdates)
		} catch (e: RuntimeException) {
			requestAuthoritativeServerAcks(selectedFields)
			return ServerFieldUpdateResult.rejected(
				e.message ?: "Sync: Server field update failed with ${e.javaClass.simpleName}",
			)
		}

		requestAuthoritativeServerAcks(selectedFields)
		val changed = pendingUpdates.any { update -> update.oldValue != update.normalizedCandidate }
		for (update in pendingUpdates) {
			if (update.oldValue != update.normalizedCandidate) {
				invokeServerChangeListener(update)
			}
		}
		return ServerFieldUpdateResult.accepted(changed)
	}

	fun applyClientNetworkUpdate(registries: RegistryAccess, components: DataComponentMap) {
		if (components.isEmpty) {
			return
		}

		val changes = components.get(GTDataComponents.SYNC_FIELD_DATA.get()) ?: return
		for (field in syncData.getClientSyncFields()) {
			if (!changes.fields.containsKey(field.componentKey)) {
				continue
			}
			val value = changes.get(field.componentKey) ?: JsonNull.INSTANCE
			FieldSyncHandler.deserializeFieldData(registries, holder, field, value, true, parseExplicitNull = true)
			val currentValue = field.handle.get(holder)
			cachedClientValues[field] = currentValue
			if (field.hasSyncBoth) {
				cachedServerValues[field] = currentValue
			}
			invokeClientChangeListeners(field)
			if (field.triggerClientRerender) holder.scheduleRenderUpdate()
		}
	}

	private fun invokeClientChangeListeners(field: FieldSyncData) {
		try {
			for (changeListenerHandle: MethodHandle in field.changeListenerHandles) {
				changeListenerHandle.invoke(holder)
			}
		} catch (e: Throwable) {
			if (e is WrongMethodTypeException) {
				throw IllegalArgumentException("Invalid method signature for change listener for field ${field.fieldName} ${holder.javaClass.name}")
			}
			GTCEu.LOGGER.error("Sync: Error while invoking change listener for field {}", field.fieldName, e)
		}
	}

	private fun normalizeServerCandidate(update: PendingServerFieldUpdate): Any? {
		val normalizer = update.field.serverNormalizerHandle ?: return update.decodedCandidate
		return try {
			normalizer.invoke(holder, update.decodedCandidate)
		} catch (e: Throwable) {
			throw IllegalArgumentException(
				"Sync: Server normalizer rejected field ${update.field.fieldName} of type ${update.field.type}",
				e,
			)
		}
	}

	private fun validateServerCandidateType(field: FieldSyncData, @Nullable candidate: Any?) {
		val fieldType = field.handle.varType()
		if (candidate == null) {
			if (fieldType.isPrimitive) {
				throw IllegalArgumentException(
					"Sync: Server candidate for primitive field ${field.fieldName} of type ${field.type} was null",
				)
			}
			return
		}
		if (!boxedType(fieldType).isInstance(candidate)) {
			throw IllegalArgumentException(
				"Sync: Server candidate for field ${field.fieldName} expected ${fieldType.typeName} but decoded ${candidate.javaClass.name}",
			)
		}
	}

	private fun commitServerUpdates(updates: List<PendingServerFieldUpdate>) {
		var committed = 0
		try {
			for (update in updates) {
				update.field.handle.set(holder, update.normalizedCandidate)
				committed++
			}
		} catch (e: Throwable) {
			for (index in committed - 1 downTo 0) {
				val update = updates[index]
				try {
					update.field.handle.set(holder, update.oldValue)
				} catch (rollbackFailure: Throwable) {
					GTCEu.LOGGER.error(
						"Sync: Failed to roll back field {} of type {} after server batch commit failure",
						update.field.fieldName,
						update.field.type,
						rollbackFailure,
					)
				}
			}
			throw IllegalStateException("Sync: Failed to commit server field update batch", e)
		}
	}

	private fun requestAuthoritativeServerAcks(fields: Collection<FieldSyncData>) {
		var marked = false
		for (field in fields) {
			if (field.hasSyncBoth) {
				dirtySyncFields.add(field.fieldName)
				marked = true
			}
		}
		if (marked) {
			holder.markAsChanged()
		}
	}

	private fun invokeServerChangeListener(update: PendingServerFieldUpdate) {
		val listener = update.field.serverChangeListenerHandle ?: return
		try {
			listener.invoke(holder, update.oldValue, update.normalizedCandidate)
		} catch (e: Throwable) {
			GTCEu.LOGGER.error(
				"Sync: Error while invoking server change listener for field {} of type {}",
				update.field.fieldName,
				update.field.type,
				e,
			)
		}
	}

	private fun boxedType(type: Class<*>): Class<*> = when (type) {
		Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType
		Byte::class.javaPrimitiveType -> Byte::class.javaObjectType
		Char::class.javaPrimitiveType -> Char::class.javaObjectType
		Short::class.javaPrimitiveType -> Short::class.javaObjectType
		Int::class.javaPrimitiveType -> Int::class.javaObjectType
		Long::class.javaPrimitiveType -> Long::class.javaObjectType
		Float::class.javaPrimitiveType -> Float::class.javaObjectType
		Double::class.javaPrimitiveType -> Double::class.javaObjectType
		else -> type
	}

	private fun itemFieldKey(field: FieldSyncData) = field.itemDataKey
		?: throw IllegalArgumentException("Sync: @ItemSave field ${field.fieldName} has no item data component key")

	private fun componentsOf(fieldData: SyncFieldData): DataComponentMap {
		if (fieldData.isEmpty()) {
			return DataComponentMap.EMPTY
		}
		return DataComponentMap.builder()
			.set(GTDataComponents.SYNC_FIELD_DATA.get(), fieldData)
			.build()
	}

	private data class PendingServerFieldUpdate(
		val field: FieldSyncData,
		@field:Nullable val decodedCandidate: Any?,
		@field:Nullable var oldValue: Any? = null,
		@field:Nullable var normalizedCandidate: Any? = null,
	)

	companion object {
		@JvmField
		val SYNC_MANAGED_CODEC: ContextualFieldCodec<ISyncManaged> = object : ContextualFieldCodec<ISyncManaged> {
			override fun serializeField(value: ISyncManaged, context: ContextualFieldCodec.Context<ISyncManaged>) = DataComponentMap.CODEC
				.encodeStart(
					context.lookup.createSerializationContext(JsonOps.INSTANCE),
					value.getSyncDataHolder().serializeToComponents(context.lookup, context.isClientSync, context.isClientFullSyncUpdate),
				)
				.getOrThrow()

			override fun shouldSyncField(value: ISyncManaged, context: ContextualFieldCodec.Context<ISyncManaged>, fullSync: Boolean, manuallyDirty: Boolean): Boolean {
				if (!context.isClientSync) return fullSync || manuallyDirty
				if (fullSync || manuallyDirty) {
					value.getSyncDataHolder().resyncAllFields()
					return true
				}
				return when (context.serializationTarget) {
					SyncSerializationTarget.NBT -> {
						val message = "Sync: client sync NBT is disabled for ${context.fieldName}; use DataComponentMap serialization"
						GTCEu.LOGGER.error(message)
						throw IllegalStateException(message)
					}

					SyncSerializationTarget.DATA_COMPONENTS -> value.getSyncDataHolder().scanAndMarkChanges(context.lookup)
				}
			}

			@Nullable
			override fun deserializeField(value: JsonElement, context: ContextualFieldCodec.Context<ISyncManaged>): ISyncManaged? {
				val syncManaged = context.currentValue
				if (syncManaged == null) {
					GTCEu.LOGGER.error("Sync: ISyncManaged field was null, cannot instantiate {}", context.fieldName)
					return null
				}
				val components = DataComponentMap.CODEC
					.parse(context.lookup.createSerializationContext(JsonOps.INSTANCE), value)
					.getOrThrow()
				syncManaged.getSyncDataHolder().deserializeComponents(context.lookup, components, context.isClientSync, context.parseExplicitNull)
				return syncManaged
			}
		}
	}
}

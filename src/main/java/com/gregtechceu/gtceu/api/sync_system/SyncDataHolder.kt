package com.gregtechceu.gtceu.api.sync_system

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.managed.ISyncManaged
import com.gregtechceu.gtceu.common.data.GTDataComponents

import net.minecraft.core.HolderLookup
import net.minecraft.core.RegistryAccess
import net.minecraft.core.component.DataComponentMap

import com.google.gson.JsonElement
import com.mojang.serialization.JsonOps
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectSet
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap
import org.jetbrains.annotations.Nullable

import java.lang.invoke.MethodHandle
import java.lang.invoke.WrongMethodTypeException
import java.util.Objects

/**
 * Class that holds all sync info for an [com.gregtechceu.gtceu.api.sync_system.managed.ISyncManaged] object.
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
			builder.put(
				itemFieldKey(field),
				FieldSyncHandler.serializeFieldData(
					registries,
					holder,
					field,
					writeClientFields = false,
					fullSync = false,
				),
			)
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
			builder.put(
				field.componentKey,
				FieldSyncHandler.serializeFieldData(
					registries,
					holder,
					field,
					writeClientFields = false,
					fullSync = false,
				),
			)
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
			if (Objects.equals(currentValue, previousValue)) {
				continue
			}
			changes.put(
				field.componentKey,
				FieldSyncHandler.serializeFieldData(
					registries,
					holder,
					field,
					writeClientFields = false,
					fullSync = false,
				),
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

	fun deserializeComponents(registries: HolderLookup.Provider, components: DataComponentMap, readingClientFields: Boolean) {
		val fieldData = components.get(GTDataComponents.SYNC_FIELD_DATA.get())
			?: return
		deserializeFieldData(registries, fieldData, readingClientFields)
	}

	fun deserializeItemFieldData(registries: HolderLookup.Provider, fieldData: SyncFieldData) {
		for (field in syncData.getItemSaveFields()) {
			val savedValue = fieldData.get(itemFieldKey(field)) ?: continue
			FieldSyncHandler.deserializeFieldData(registries, holder, field, savedValue, false)
		}
	}

	fun deserializeFieldData(registries: HolderLookup.Provider, fieldData: SyncFieldData, readingClientFields: Boolean) {
		val fieldsToCheck = if (readingClientFields) syncData.getClientSyncFields() else syncData.getServerSaveFields()
		for (field in fieldsToCheck) {
			val savedValue = fieldData.get(field.componentKey) ?: continue
			FieldSyncHandler.deserializeFieldData(registries, holder, field, savedValue, readingClientFields)

			if (readingClientFields) {
				cachedClientValues[field] = field.handle.get(holder)
				invokeClientChangeListeners(field)

				if (field.triggerClientRerender) holder.scheduleRenderUpdate()
			}
		}
	}

	fun applyServerNetworkUpdate(registries: RegistryAccess, components: DataComponentMap) {
		if (components.isEmpty) {
			return
		}

		val changes = components.get(GTDataComponents.SYNC_FIELD_DATA.get()) ?: return
		for (field in syncData.getServerUpdateFields()) {
			val value = changes.get(field.componentKey) ?: continue
			FieldSyncHandler.deserializeFieldData(registries, holder, field, value, false)
		}
	}

	fun applyClientNetworkUpdate(registries: RegistryAccess, components: DataComponentMap) {
		if (components.isEmpty) {
			return
		}

		val changes = components.get(GTDataComponents.SYNC_FIELD_DATA.get()) ?: return
		for (field in syncData.getClientSyncFields()) {
			val value = changes.get(field.componentKey) ?: continue
			FieldSyncHandler.deserializeFieldData(registries, holder, field, value, true)
			cachedClientValues[field] = field.handle.get(holder)
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

	private fun itemFieldKey(field: FieldSyncData) = field.itemDataKey
		?: throw IllegalArgumentException("Sync: @ItemSave field ${field.fieldName} has no item data component key")

	private fun componentsOf(fieldData: SyncFieldData): DataComponentMap {
		if (fieldData.isEmpty) {
			return DataComponentMap.EMPTY
		}
		return DataComponentMap.builder()
			.set(GTDataComponents.SYNC_FIELD_DATA.get(), fieldData)
			.build()
	}

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
				syncManaged.getSyncDataHolder().deserializeComponents(context.lookup, components, context.isClientSync)
				return syncManaged
			}
		}
	}
}

package com.gregtechceu.gtceu.api.sync_system

import com.gregtechceu.gtceu.GTCEu

import net.minecraft.core.HolderLookup
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.Tag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.neoforged.neoforge.network.connection.ConnectionType

import io.netty.buffer.Unpooled
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
import it.unimi.dsi.fastutil.objects.ObjectSet
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap
import org.jetbrains.annotations.Nullable

import java.lang.invoke.MethodHandle
import java.lang.invoke.WrongMethodTypeException
import java.util.Objects

/**
 * Class that holds all sync info for an [ISyncManaged] object.
 */
class SyncDataHolder(private val holder: ISyncManaged) {
	private val syncData: ClassSyncData = ClassSyncData.getClassData(holder.javaClass)
	private val cachedClientValues: MutableMap<FieldSyncData, Any?> = Reference2ReferenceOpenHashMap()
	private val cachedServerValues: MutableMap<FieldSyncData, Any?> = Reference2ReferenceOpenHashMap()
	private val dirtySyncFields: ObjectSet<String> = ObjectOpenHashSet()

	@field:Nullable
	private var pendingClientChanges: CompoundTag? = null
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

	fun serializeNBT(registries: HolderLookup.Provider, writeClientFields: Boolean): CompoundTag =
		if (writeClientFields) getOrCreateClientSyncNBT(registries, resyncAll) else serializeToSaveNBT(registries)

	fun serializeNBT(registries: HolderLookup.Provider, writeClientFields: Boolean, fullSync: Boolean): CompoundTag =
		if (writeClientFields) getOrCreateClientSyncNBT(registries, fullSync) else serializeToSaveNBT(registries)

	fun serializeToSaveNBT(registries: HolderLookup.Provider): CompoundTag {
		val tag = CompoundTag()
		for (field in syncData.getServerSaveFields()) {
			val nbtValue = FieldSyncHandler.serializeField(
				registries,
				holder,
				field,
				writeClientFields = false,
				fullSync = false,
			)
			tag.put(field.nbtSaveKey, nbtValue)
		}
		return tag
	}

	fun serializeToItemNBT(registries: HolderLookup.Provider): CompoundTag {
		val tag = CompoundTag()
		for (field in syncData.getItemSaveFields()) {
			val nbtValue = FieldSyncHandler.serializeField(
				registries,
				holder,
				field,
				writeClientFields = false,
				fullSync = false,
			)
			tag.put(field.itemNbtKey, nbtValue)
		}
		return tag
	}

	fun serializeFullClientSyncNBT(registries: HolderLookup.Provider): CompoundTag {
		val tag = CompoundTag()
		for (field in syncData.getClientSyncFields()) {
			val nbtValue = FieldSyncHandler.serializeField(registries, holder, field, true, fullSync = true)
			tag.put(field.nbtSaveKey, nbtValue)
			cachedClientValues[field] = field.handle.get(holder)
		}
		resyncAll = false
		dirtySyncFields.clear()
		pendingClientChanges = null
		return tag
	}

	fun scanAndMarkChanges(registries: HolderLookup.Provider): Boolean {
		val changes = CompoundTag()
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
				val nbtValue = FieldSyncHandler.serializeField(registries, holder, field, true, fullSync)
				changes.put(field.nbtSaveKey, nbtValue)
				cachedClientValues[field] = currentValue
			}
		}

		resyncAll = false
		dirtySyncFields.clear()
		if (!changes.isEmpty) {
			pendingClientChanges = changes
			return true
		}
		return false
	}

	@Suppress("UNCHECKED_CAST")
	private fun shouldSyncContextualField(registries: HolderLookup.Provider, field: FieldSyncData, @Nullable currentValue: Any?, fullSync: Boolean, manuallyDirty: Boolean): Boolean {
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
			ContextualFieldCodec.Context(holder, field.type, currentValue, field.fieldName, true, fullSync, registries),
			fullSync,
			manuallyDirty,
		)
	}

	fun getPendingChanges(): CompoundTag {
		val changes = pendingClientChanges
		pendingClientChanges = null
		return changes ?: CompoundTag()
	}

	fun collectClientNetworkChanges(registries: RegistryAccess, force: Boolean): ByteArray {
		if (force) {
			pendingClientChanges = serializeFullClientSyncNBT(registries)
		}

		val pendingChanges = getPendingChanges()
		if (pendingChanges.isEmpty) {
			return ByteArray(0)
		}

		val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.OTHER)
		try {
			val fields = syncData.getOrderedClientSyncFields()
			for (i in fields.indices) {
				val field = fields[i]
				val value = pendingChanges.get(field.nbtSaveKey) ?: continue
				buf.writeVarInt(i)
				buf.writeNbt(value)
			}
			val data = ByteArray(buf.readableBytes())
			buf.getBytes(0, data)
			return data
		} finally {
			buf.release()
		}
	}

	fun collectServerChanges(registries: HolderLookup.Provider): CompoundTag {
		val changes = CompoundTag()
		for (field in syncData.getServerUpdateFields()) {
			val currentValue = field.handle.get(holder)
			val previousValue = cachedServerValues[field]
			if (currentValue != previousValue) {
				val nbtValue = FieldSyncHandler.serializeField(
					registries,
					holder,
					field,
					writeClientFields = false,
					fullSync = false,
				)
				changes.put(field.fieldName, nbtValue)
				cachedServerValues[field] = currentValue
			}
		}
		return changes
	}

	fun collectServerNetworkChanges(registries: RegistryAccess): ByteArray {
		val buf = RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.OTHER)
		try {
			var wroteAny = false
			val fields = syncData.getOrderedServerUpdateFields()
			for (i in fields.indices) {
				val field = fields[i]
				val currentValue = field.handle.get(holder)
				val previousValue = cachedServerValues[field]
				if (Objects.equals(currentValue, previousValue)) {
					continue
				}
				buf.writeVarInt(i)
				buf.writeNbt(
					FieldSyncHandler.serializeField(
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
				return ByteArray(0)
			}

			val data = ByteArray(buf.readableBytes())
			buf.getBytes(0, data)
			return data
		} finally {
			buf.release()
		}
	}

	private fun getOrCreateClientSyncNBT(registries: HolderLookup.Provider, fullSync: Boolean): CompoundTag {
		if (fullSync) {
			return serializeFullClientSyncNBT(registries)
		}
		if (pendingClientChanges == null) {
			scanAndMarkChanges(registries)
		}
		return getPendingChanges()
	}

	fun deserializeNBT(registries: HolderLookup.Provider, tag: CompoundTag, readingClientFields: Boolean) {
		val fieldsToCheck = if (readingClientFields) syncData.getClientSyncFields() else syncData.getServerSaveFields()

		for (field in fieldsToCheck) {
			val savedValue = tag.get(field.nbtSaveKey)
			if (savedValue != null) {
				FieldSyncHandler.deserializeField(registries, holder, field, savedValue, readingClientFields)
			}

			if (readingClientFields) {
				cachedClientValues[field] = field.handle.get(holder)
				invokeClientChangeListeners(field)

				if (field.triggerClientRerender) holder.scheduleRenderUpdate()
			}
		}
	}

	fun deserializeItemNBT(registries: HolderLookup.Provider, tag: CompoundTag) {
		for (field in syncData.getItemSaveFields()) {
			val savedValue = tag.get(field.itemNbtKey)
			FieldSyncHandler.deserializeField(registries, holder, field, savedValue, false)
		}
	}

	fun applyServerUpdate(registries: HolderLookup.Provider, tag: CompoundTag) {
		for (field in syncData.getServerUpdateFields()) {
			val savedValue = tag.get(field.fieldName)
			FieldSyncHandler.deserializeField(registries, holder, field, savedValue, false)
		}
	}

	fun applyServerNetworkUpdate(registries: RegistryAccess, data: ByteArray) {
		if (data.isEmpty()) {
			return
		}

		val buf = RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), registries, ConnectionType.OTHER)
		try {
			val fields = syncData.getOrderedServerUpdateFields()
			while (buf.isReadable) {
				val index = buf.readVarInt()
				if (index < 0 || index >= fields.size) {
					throw IllegalArgumentException("Invalid server sync field index: $index")
				}
				val value = buf.readNbt(NbtAccounter.unlimitedHeap())
				if (value != null) {
					FieldSyncHandler.deserializeField(registries, holder, fields[index], value, false)
				}
			}
		} finally {
			buf.release()
		}
	}

	fun applyClientNetworkUpdate(registries: RegistryAccess, data: ByteArray) {
		if (data.isEmpty()) {
			return
		}

		val buf = RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), registries, ConnectionType.OTHER)
		try {
			val fields = syncData.getOrderedClientSyncFields()
			while (buf.isReadable) {
				val index = buf.readVarInt()
				if (index < 0 || index >= fields.size) {
					throw IllegalArgumentException("Invalid client sync field index: $index")
				}

				val field = fields[index]
				val value = buf.readNbt(NbtAccounter.unlimitedHeap()) ?: continue

				FieldSyncHandler.deserializeField(registries, holder, field, value, true)
				cachedClientValues[field] = field.handle.get(holder)
				invokeClientChangeListeners(field)
				if (field.triggerClientRerender) holder.scheduleRenderUpdate()
			}
		} finally {
			buf.release()
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

	companion object {
		@JvmField
		val SYNC_MANAGED_CODEC: ContextualFieldCodec<ISyncManaged> = object : ContextualFieldCodec<ISyncManaged> {
			override fun serializeNBT(value: ISyncManaged, context: ContextualFieldCodec.Context<ISyncManaged>): Tag =
				value.getSyncDataHolder().serializeNBT(context.lookup, context.isClientSync, context.isClientFullSyncUpdate)

			override fun shouldSyncField(value: ISyncManaged, context: ContextualFieldCodec.Context<ISyncManaged>, fullSync: Boolean, manuallyDirty: Boolean): Boolean {
				if (!context.isClientSync) return fullSync || manuallyDirty
				if (fullSync || manuallyDirty) {
					value.getSyncDataHolder().resyncAllFields()
					return true
				}
				return value.getSyncDataHolder().scanAndMarkChanges(context.lookup)
			}

			@Nullable
			override fun deserializeNBT(tag: Tag, context: ContextualFieldCodec.Context<ISyncManaged>): ISyncManaged? {
				val syncManaged = context.currentValue
				if (syncManaged == null) {
					GTCEu.LOGGER.error("Sync: ISyncManaged field was null, cannot instantiate {}", context.fieldName)
					return null
				}
				syncManaged.getSyncDataHolder().deserializeNBT(context.lookup, tag as CompoundTag, context.isClientSync)
				return syncManaged
			}
		}
	}
}

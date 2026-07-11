package com.gregtechceu.gtceu.api.sync_system

import com.gregtechceu.gtceu.GTCEu
import com.gregtechceu.gtceu.api.sync_system.annotations.ClientFieldChangeListener
import com.gregtechceu.gtceu.api.sync_system.annotations.ItemSave
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToServer
import com.gregtechceu.gtceu.api.sync_system.managed.ISyncAnnotated
import com.gregtechceu.gtceu.api.sync_system.managed.ISyncManaged

import com.mojang.serialization.Codec
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.Modifier
import java.util.Comparator

/**
 * Static data for [com.gregtechceu.gtceu.api.sync_system.managed.ISyncManaged] classes.
 */
class ClassSyncData private constructor(clazz: Class<*>) {
	private val managedFields: MutableList<FieldSyncData> = ObjectArrayList()
	private val clientSyncFields: MutableSet<FieldSyncData> = ObjectOpenHashSet()
	private val serverSaveFields: MutableSet<FieldSyncData> = ObjectOpenHashSet()
	private val itemSaveFields: MutableSet<FieldSyncData> = ObjectOpenHashSet()
	private val serverSyncFields: MutableSet<FieldSyncData> = ObjectOpenHashSet()
	private val bothSyncFields: MutableSet<FieldSyncData> = ObjectOpenHashSet()
	private val serverUpdateFields: MutableSet<FieldSyncData> = ObjectOpenHashSet()
	private var orderedClientSyncFields: Array<FieldSyncData>
	private var orderedServerUpdateFields: Array<FieldSyncData>

	init {
		val isManaged = ISyncManaged::class.java.isAssignableFrom(clazz)
		val isAnnotated = ISyncAnnotated::class.java.isAssignableFrom(clazz)

		if (!isManaged && !isAnnotated) {
			throw IllegalArgumentException("Cannot create class sync data for non-sync class")
		}
		if (isManaged && isAnnotated) {
			throw IllegalArgumentException("Class ${clazz.name} cannot inherit both ISyncAnnotated and ISyncManaged")
		}

		val privateLookup = try {
			MethodHandles.privateLookupIn(clazz, LOOKUP)
		} catch (e: IllegalAccessException) {
			GTCEu.LOGGER.error("Sync: Failed to create method handle lookup for class {}", clazz)
			throw e
		}

		val changeListeners = HashMap<String, MutableList<MethodHandle>>()
		val clientListenerTargets = HashSet<String>()

		for (method in clazz.declaredMethods) {
			val listener = method.getAnnotation(ClientFieldChangeListener::class.java) ?: continue

			if (Modifier.isStatic(method.modifiers)) {
				throw IllegalArgumentException("Cannot apply syncdata annotation to static method: ${clazz.name}.${method.name}")
			}

			val handle = try {
				privateLookup.unreflect(method)
			} catch (e: IllegalAccessException) {
				GTCEu.LOGGER.error("Sync: Failed to acquire method handle for method {} {}", method.name, clazz.name)
				GTCEu.LOGGER.error(e)
				continue
			}

			if (listener.fieldName.isBlank()) {
				throw IllegalArgumentException("@ClientFieldChangeListener requires a non-blank fieldName: ${clazz.name}.${method.name}")
			}

			changeListeners.computeIfAbsent(listener.fieldName) { ArrayList() }.add(handle)
			clientListenerTargets.add(listener.fieldName)
		}

		val localFieldsByName = HashMap<String, FieldSyncData>()
		val localSaveKeys = HashSet<String>()
		val localItemKeys = HashSet<String>()
		val localClientSyncKeys = HashSet<String>()
		val localServerSyncKeys = HashSet<String>()

		for (field in clazz.declaredFields) {
			val hasSaveField = field.isAnnotationPresent(SaveField::class.java)
			val hasItemSave = field.isAnnotationPresent(ItemSave::class.java)
			val hasClientSync = field.isAnnotationPresent(SyncToClient::class.java)
			val hasServerSync = field.isAnnotationPresent(SyncToServer::class.java)
			val hasSyncBoth = field.isAnnotationPresent(SyncBoth::class.java)
			if (!hasSaveField && !hasItemSave && !hasClientSync && !hasServerSync && !hasSyncBoth) continue

			if (Modifier.isStatic(field.modifiers)) {
				throw IllegalArgumentException("Cannot apply syncdata annotations to static field: ${field.declaringClass.name}.${field.name}")
			}

			val handle = try {
				privateLookup.unreflectVarHandle(field)
			} catch (e: IllegalAccessException) {
				GTCEu.LOGGER.error("Sync: Failed to acquire variable handle for field {} {}", field.name, clazz.name)
				throw e
			}

			val syncData = FieldSyncData(field, handle, changeListeners.getOrDefault(field.name, listOf()))
			if (localFieldsByName.put(syncData.fieldName, syncData) != null) {
				throw IllegalArgumentException("Duplicate managed field name in ${clazz.name}: ${syncData.fieldName}")
			}
			managedFields.add(syncData)
			if (hasClientSync) {
				checkDuplicateKey(localClientSyncKeys, syncData.nbtSaveKey, clazz, "client sync")
				clientSyncFields.add(syncData)
			}
			if (hasSaveField) {
				checkDuplicateKey(localSaveKeys, syncData.nbtSaveKey, clazz, "save")
				serverSaveFields.add(syncData)
			}
			if (hasItemSave) {
				checkDuplicateKey(localItemKeys, syncData.itemDataName, clazz, "item")
				itemSaveFields.add(syncData)
			}
			if (hasServerSync) {
				checkDuplicateKey(localServerSyncKeys, syncData.fieldName, clazz, "server sync")
				serverSyncFields.add(syncData)
				serverUpdateFields.add(syncData)
			}
			if (hasSyncBoth) {
				checkDuplicateKey(localClientSyncKeys, syncData.nbtSaveKey, clazz, "client sync")
				checkDuplicateKey(localServerSyncKeys, syncData.fieldName, clazz, "server sync")
				bothSyncFields.add(syncData)
				clientSyncFields.add(syncData)
				serverSyncFields.add(syncData)
				serverUpdateFields.add(syncData)
			}
		}

		val parent = clazz.superclass
		if (parent != null && (ISyncManaged::class.java.isAssignableFrom(parent) || ISyncAnnotated::class.java.isAssignableFrom(parent))) {
			val parentHandles = CACHE.get(parent)
			managedFields.addAll(parentHandles.managedFields)
			clientSyncFields.addAll(parentHandles.clientSyncFields)
			serverSaveFields.addAll(parentHandles.serverSaveFields)
			itemSaveFields.addAll(parentHandles.itemSaveFields)
			serverSyncFields.addAll(parentHandles.serverSyncFields)
			bothSyncFields.addAll(parentHandles.bothSyncFields)
			serverUpdateFields.addAll(parentHandles.serverUpdateFields)
		}

		for (fieldName in clientListenerTargets) {
			val localField = localFieldsByName[fieldName]
			if (localField != null && !localField.hasSyncToClient && !localField.hasSyncBoth) {
				throw IllegalArgumentException("@ClientFieldChangeListener targets a field that never syncs to client: ${clazz.name}.$fieldName")
			}
			if (localField == null && clientSyncFields.stream().noneMatch { field -> field.fieldName == fieldName }) {
				throw IllegalArgumentException("@ClientFieldChangeListener targets unknown field: ${clazz.name}.$fieldName")
			}
		}

		orderedClientSyncFields = clientSyncFields.stream()
			.sorted(Comparator.comparing { field: FieldSyncData -> field.nbtSaveKey })
			.toArray { size -> arrayOfNulls<FieldSyncData>(size) }
		orderedServerUpdateFields = serverUpdateFields.stream()
			.sorted(Comparator.comparing { field: FieldSyncData -> field.fieldName })
			.toArray { size -> arrayOfNulls<FieldSyncData>(size) }
	}

	fun getManagedFields(): List<FieldSyncData> = managedFields

	fun getClientSyncFields(): Set<FieldSyncData> = clientSyncFields

	fun getServerSaveFields(): Set<FieldSyncData> = serverSaveFields

	fun getItemSaveFields(): Set<FieldSyncData> = itemSaveFields

	fun getServerSyncFields(): Set<FieldSyncData> = serverSyncFields

	fun getBothSyncFields(): Set<FieldSyncData> = bothSyncFields

	fun getServerUpdateFields(): Set<FieldSyncData> = serverUpdateFields

	fun getOrderedClientSyncFields(): Array<FieldSyncData> = orderedClientSyncFields

	fun getOrderedServerUpdateFields(): Array<FieldSyncData> = orderedServerUpdateFields

	fun getWorldSaveFields(): Set<FieldSyncData> = serverSaveFields

	/**
	 * Allows for a custom codec to be used for a specific field, ignoring the default codec lookup.
	 *
	 * @param fieldName The field name
	 * @param codec The custom codec
	 */
	fun setCustomCodecForField(fieldName: String, codec: Codec<*>) {
		managedFields.stream()
			.filter { f -> f.fieldName == fieldName }
			.findFirst()
			.ifPresent { fieldData -> fieldData.setCodec(codec) }
	}

	/**
	 * Allows for a field codec that needs the owning object/current value context.
	 *
	 * @param fieldName The field name
	 * @param codec The custom contextual codec
	 */
	fun setCustomContextualCodecForField(fieldName: String, codec: ContextualFieldCodec<*>) {
		managedFields.stream()
			.filter { f -> f.fieldName == fieldName }
			.findFirst()
			.ifPresent { fieldData -> fieldData.setContextualCodec(codec) }
	}

	companion object {
		private val LOOKUP: MethodHandles.Lookup = MethodHandles.lookup()
		private val CACHE: ClassValue<ClassSyncData> = object : ClassValue<ClassSyncData>() {
			override fun computeValue(type: Class<*>): ClassSyncData = ClassSyncData(type)
		}

		/**
		 * Gets the [ClassSyncData] object for a specific class
		 */
		@JvmStatic
		fun getClassData(cls: Class<*>): ClassSyncData = CACHE.get(cls)

		private fun checkDuplicateKey(keys: MutableSet<String>, key: String, owner: Class<*>, kind: String) {
			if (!keys.add(key)) {
				throw IllegalArgumentException("Duplicate $kind key in ${owner.name}: $key")
			}
		}
	}
}

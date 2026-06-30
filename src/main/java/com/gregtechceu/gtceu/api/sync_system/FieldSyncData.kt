package com.gregtechceu.gtceu.api.sync_system

import com.gregtechceu.gtceu.api.sync_system.annotations.ItemSave
import com.gregtechceu.gtceu.api.sync_system.annotations.RerenderOnChanged
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToServer

import net.minecraft.resources.ResourceLocation

import com.mojang.serialization.Codec
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nullable

import java.lang.invoke.MethodHandle
import java.lang.invoke.VarHandle
import java.lang.reflect.Field

/**
 * Information about the sync behaviour of fields with sync annotations in ISyncManaged classes
 */
@ApiStatus.Internal
class FieldSyncData(field: Field, @JvmField val handle: VarHandle, @JvmField val changeListenerHandles: List<MethodHandle>) {
	@JvmField
	val fieldName: String = field.name

	@JvmField
	val nbtSaveKey: String

	@JvmField
	val itemNbtKey: String

	@JvmField
	@field:Nullable
	val itemComponentKey: ResourceLocation?

	@JvmField
	@field:Nullable
	val itemDataKey: ResourceLocation?

	@JvmField
	val componentKey: ResourceLocation

	@JvmField
	val triggerClientRerender: Boolean

	@JvmField
	val hasSaveField: Boolean

	@JvmField
	val hasItemSave: Boolean

	@JvmField
	val hasSyncToClient: Boolean

	@JvmField
	val hasSyncToServer: Boolean

	@JvmField
	val hasSyncBoth: Boolean

	@JvmField
	@field:Nullable
	var codec: Codec<*>?

	@JvmField
	@field:Nullable
	var contextualCodec: ContextualFieldCodec<*>?

	@JvmField
	val type: TypeDeclaration

	init {
		val saveField = field.getAnnotation(SaveField::class.java)
		val itemSave = field.getAnnotation(ItemSave::class.java)
		hasSaveField = saveField != null
		hasItemSave = itemSave != null
		hasSyncToClient = field.isAnnotationPresent(SyncToClient::class.java)
		hasSyncToServer = field.isAnnotationPresent(SyncToServer::class.java)
		hasSyncBoth = field.isAnnotationPresent(SyncBoth::class.java)
		nbtSaveKey = if (saveField != null && saveField.nbtKey.isNotBlank()) saveField.nbtKey else fieldName
		itemNbtKey = if (itemSave != null && itemSave.nbtKey.isNotBlank()) itemSave.nbtKey else fieldName
		itemComponentKey = itemSave?.let {
			val componentKey = if (it.component.isNotBlank()) it.component else itemNbtKey
			SyncFieldData.key(componentKey)
		}
		itemDataKey = itemComponentKey
		componentKey = SyncFieldData.key(nbtSaveKey)
		triggerClientRerender = field.isAnnotationPresent(RerenderOnChanged::class.java)
		codec = FieldCodecs.get(field.genericType)
		contextualCodec = FieldCodecs.getContextual(field.genericType)
		type = TypeDeclaration(field.genericType)
	}

	fun setCodec(codec: Codec<*>?) {
		this.codec = codec
	}

	fun setContextualCodec(contextualCodec: ContextualFieldCodec<*>?) {
		this.contextualCodec = contextualCodec
	}
}

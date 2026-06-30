package com.gregtechceu.gtceu.api.sync_system

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.Tag

import com.google.gson.JsonElement
import org.jetbrains.annotations.Nullable

enum class SyncSerializationTarget {
	NBT,
	DATA_COMPONENTS,
}

interface ContextualFieldCodec<T> {
	fun serializeNBT(value: T, context: Context<T>): Tag

	@Nullable
	fun deserializeNBT(tag: Tag, context: Context<T>): T?

	fun serializeField(value: T, context: Context<T>): JsonElement =
		throw UnsupportedOperationException("Sync: field ${context.fieldName} uses ${javaClass.name}, which does not support non-NBT serialization")

	@Nullable
	fun deserializeField(value: JsonElement, context: Context<T>): T? =
		throw UnsupportedOperationException("Sync: field ${context.fieldName} uses ${javaClass.name}, which does not support non-NBT deserialization")

	fun shouldSyncField(value: T, context: Context<T>, fullSync: Boolean, manuallyDirty: Boolean): Boolean = fullSync || manuallyDirty

	@JvmRecord
	data class Context<T>(
		val holder: Any,
		val type: TypeDeclaration,
		@field:Nullable val currentValue: T?,
		val fieldName: String,
		val isClientSync: Boolean,
		val isClientFullSyncUpdate: Boolean,
		val lookup: HolderLookup.Provider,
		val serializationTarget: SyncSerializationTarget = SyncSerializationTarget.DATA_COMPONENTS,
	)
}

package com.gregtechceu.gtceu.api.sync_system

import net.minecraft.core.HolderLookup

import com.google.gson.JsonElement
import org.jetbrains.annotations.Nullable

enum class SyncSerializationTarget {
	NBT,
	DATA_COMPONENTS,
}

interface ContextualFieldCodec<T> {
	fun serializeField(value: T, context: Context<T>): JsonElement =
		throw UnsupportedOperationException("Sync: field ${context.fieldName} uses ${javaClass.name}, which does not support DataComponentMap serialization")

	@Nullable
	fun deserializeField(value: JsonElement, context: Context<T>): T? =
		throw UnsupportedOperationException("Sync: field ${context.fieldName} uses ${javaClass.name}, which does not support DataComponentMap deserialization")

	fun shouldSyncField(value: T, context: Context<T>, fullSync: Boolean, manuallyDirty: Boolean): Boolean = fullSync || manuallyDirty

	@JvmRecord
	data class Context<T> @JvmOverloads constructor(
		val holder: Any,
		val type: TypeDeclaration,
		@field:Nullable val currentValue: T?,
		val fieldName: String,
		val isClientSync: Boolean,
		val isClientFullSyncUpdate: Boolean,
		val lookup: HolderLookup.Provider,
		val serializationTarget: SyncSerializationTarget = SyncSerializationTarget.DATA_COMPONENTS,
		val parseExplicitNull: Boolean = false,
	)
}

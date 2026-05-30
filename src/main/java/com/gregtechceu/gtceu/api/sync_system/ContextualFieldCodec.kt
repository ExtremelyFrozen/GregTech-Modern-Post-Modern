package com.gregtechceu.gtceu.api.sync_system

import net.minecraft.core.HolderLookup
import net.minecraft.nbt.Tag

import org.jetbrains.annotations.Nullable

interface ContextualFieldCodec<T> {
	fun serializeNBT(value: T, context: Context<T>): Tag

	@Nullable
	fun deserializeNBT(tag: Tag, context: Context<T>): T?

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
	)
}

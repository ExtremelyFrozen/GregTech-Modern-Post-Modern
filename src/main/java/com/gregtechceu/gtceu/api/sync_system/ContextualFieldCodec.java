package com.gregtechceu.gtceu.api.sync_system;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;

import org.jetbrains.annotations.Nullable;

public interface ContextualFieldCodec<T> {

    Tag serializeNBT(T value, Context<T> context);

    @Nullable
    T deserializeNBT(Tag tag, Context<T> context);

    default boolean shouldSyncField(T value, Context<T> context, boolean fullSync, boolean manuallyDirty) {
        return fullSync || manuallyDirty;
    }

    record Context<T>(Object holder, TypeDeclaration type, @Nullable T currentValue, String fieldName,
                      boolean isClientSync, boolean isClientFullSyncUpdate, HolderLookup.Provider lookup) {}
}

package com.gregtechceu.gtceu.api.sync_system;

import com.gregtechceu.gtceu.api.sync_system.annotations.RerenderOnChanged;
import com.gregtechceu.gtceu.api.sync_system.annotations.ItemSave;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToServer;

import com.mojang.serialization.Codec;
import lombok.Setter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Information about the sync behaviour of fields with sync annotations in ISyncManaged classes
 */
@ApiStatus.Internal
public final class FieldSyncData {

    public final String fieldName, nbtSaveKey, itemNbtKey;
    public final VarHandle handle;
    public final boolean triggerClientRerender;
    public final boolean hasSaveField, hasItemSave, hasSyncToClient, hasSyncToServer, hasSyncBoth;
    @Setter
    public @Nullable Codec<?> codec;
    @Setter
    public @Nullable ContextualFieldCodec<?> contextualCodec;
    public final List<MethodHandle> changeListenerHandles;
    public final TypeDeclaration type;

    public FieldSyncData(Field field, VarHandle handle, List<MethodHandle> changeListenerHandles) {
        fieldName = field.getName();
        SaveField saveField = field.getAnnotation(SaveField.class);
        ItemSave itemSave = field.getAnnotation(ItemSave.class);
        this.hasSaveField = saveField != null;
        this.hasItemSave = itemSave != null;
        this.hasSyncToClient = field.isAnnotationPresent(SyncToClient.class);
        this.hasSyncToServer = field.isAnnotationPresent(SyncToServer.class);
        this.hasSyncBoth = field.isAnnotationPresent(SyncBoth.class);
        this.nbtSaveKey = (saveField != null && !saveField.nbtKey().isBlank()) ? saveField.nbtKey() : fieldName;
        this.itemNbtKey = (itemSave != null && !itemSave.nbtKey().isBlank()) ? itemSave.nbtKey() : fieldName;
        this.handle = handle;
        this.triggerClientRerender = field.isAnnotationPresent(RerenderOnChanged.class);
        this.changeListenerHandles = changeListenerHandles;
        this.codec = FieldCodecs.get(field.getGenericType());
        this.contextualCodec = FieldCodecs.getContextual(field.getGenericType());
        this.type = new TypeDeclaration(field.getGenericType());
    }
}

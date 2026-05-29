package com.gregtechceu.gtceu.api.sync_system;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.data_transformers.ValueTransformer;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;

import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.WrongMethodTypeException;
import java.util.*;

/**
 * Class that holds all sync info for an {@link ISyncManaged} object.
 */
public class SyncDataHolder {

    public static final ContextualFieldCodec<ISyncManaged> SYNC_MANAGED_CODEC = new ContextualFieldCodec<>() {

        @Override
        public Tag serializeNBT(ISyncManaged value, Context<ISyncManaged> context) {
            return value.getSyncDataHolder().serializeNBT(context.lookup(), context.isClientSync(),
                    context.isClientFullSyncUpdate());
        }

        @Override
        public @Nullable ISyncManaged deserializeNBT(Tag tag, Context<ISyncManaged> context) {
            ISyncManaged syncManaged = context.currentValue();
            if (syncManaged == null) {
                GTCEu.LOGGER.error("Sync: ISyncManaged field was null, cannot instantiate {}",
                        context.fieldName());
                return null;
            }
            syncManaged.getSyncDataHolder().deserializeNBT(context.lookup(), (CompoundTag) tag, context.isClientSync());
            return syncManaged;
        }
    };

    private final ClassSyncData syncData;
    private final ISyncManaged holder;

    private final Map<FieldSyncData, Object> cachedClientValues = new Reference2ReferenceOpenHashMap<>();
    private final Map<FieldSyncData, Object> cachedServerValues = new Reference2ReferenceOpenHashMap<>();
    private final ObjectSet<String> dirtySyncFields = new ObjectOpenHashSet<>();
    private @Nullable CompoundTag pendingClientChanges;
    private boolean resyncAll = true;

    public SyncDataHolder(ISyncManaged o) {
        holder = o;
        syncData = ClassSyncData.getClassData(o.getClass());
        for (FieldSyncData field : syncData.getClientSyncFields()) {
            cachedClientValues.put(field, field.handle.get(holder));
        }
        for (FieldSyncData field : syncData.getServerUpdateFields()) {
            cachedServerValues.put(field, field.handle.get(holder));
        }
    }

    /**
     * Instructs the sync system that this field has been updated and must be synced with clients.
     *
     * @param fieldName The field that has changed.
     */
    public void markClientSyncFieldDirty(String fieldName) {
        dirtySyncFields.add(fieldName);
        holder.markAsChanged();
    }

    public void resyncAllFields() {
        resyncAll = true;
        holder.markAsChanged();
    }

    public CompoundTag serializeNBT(HolderLookup.Provider registries, boolean writeClientFields) {
        return writeClientFields ? getOrCreateClientSyncNBT(registries, resyncAll) : serializeToSaveNBT(registries);
    }

    public CompoundTag serializeNBT(HolderLookup.Provider registries, boolean writeClientFields, boolean fullSync) {
        return writeClientFields ? getOrCreateClientSyncNBT(registries, fullSync) : serializeToSaveNBT(registries);
    }

    public CompoundTag serializeToSaveNBT(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        for (var field : syncData.getServerSaveFields()) {
            Tag nbtValue = FieldSyncHandler.serializeField(registries, holder, field, false, false);
            tag.put(field.nbtSaveKey, nbtValue);
        }
        return tag;
    }

    public CompoundTag serializeToItemNBT(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        for (var field : syncData.getItemSaveFields()) {
            Tag nbtValue = FieldSyncHandler.serializeField(registries, holder, field, false, false);
            tag.put(field.itemNbtKey, nbtValue);
        }
        return tag;
    }

    public CompoundTag serializeFullClientSyncNBT(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        for (var field : syncData.getClientSyncFields()) {
            Tag nbtValue = FieldSyncHandler.serializeField(registries, holder, field, true, true);
            tag.put(field.nbtSaveKey, nbtValue);
            cachedClientValues.put(field, field.handle.get(holder));
        }
        resyncAll = false;
        dirtySyncFields.clear();
        pendingClientChanges = null;
        return tag;
    }

    public boolean scanAndMarkChanges(HolderLookup.Provider registries) {
        CompoundTag changes = new CompoundTag();
        boolean fullSync = resyncAll;

        for (var field : syncData.getClientSyncFields()) {
            Object currentValue = field.handle.get(holder);
            Object previousValue = cachedClientValues.get(field);
            boolean changed = fullSync || dirtySyncFields.contains(field.fieldName) ||
                    !Objects.equals(currentValue, previousValue);
            if (changed) {
                Tag nbtValue = FieldSyncHandler.serializeField(registries, holder, field, true, fullSync);
                changes.put(field.nbtSaveKey, nbtValue);
                cachedClientValues.put(field, currentValue);
            }
        }

        resyncAll = false;
        dirtySyncFields.clear();
        if (!changes.isEmpty()) {
            pendingClientChanges = changes;
            return true;
        }
        return false;
    }

    public CompoundTag getPendingChanges() {
        CompoundTag changes = pendingClientChanges;
        pendingClientChanges = null;
        return changes != null ? changes : new CompoundTag();
    }

    public byte[] collectClientNetworkChanges(RegistryAccess registries, boolean force) {
        if (force) {
            pendingClientChanges = serializeFullClientSyncNBT(registries);
        }

        CompoundTag pendingChanges = getPendingChanges();
        if (pendingChanges.isEmpty()) {
            return new byte[0];
        }

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.OTHER);
        try {
            for (int i = 0; i < syncData.getOrderedClientSyncFields().length; i++) {
                FieldSyncData field = syncData.getOrderedClientSyncFields()[i];
                Tag value = pendingChanges.get(field.nbtSaveKey);
                if (value == null) {
                    continue;
                }
                buf.writeVarInt(i);
                buf.writeNbt(value);
            }
            byte[] data = new byte[buf.readableBytes()];
            buf.getBytes(0, data);
            return data;
        } finally {
            buf.release();
        }
    }

    public CompoundTag collectServerChanges(HolderLookup.Provider registries) {
        CompoundTag changes = new CompoundTag();
        for (var field : syncData.getServerUpdateFields()) {
            Object currentValue = field.handle.get(holder);
            Object previousValue = cachedServerValues.get(field);
            if (!Objects.equals(currentValue, previousValue)) {
                Tag nbtValue = FieldSyncHandler.serializeField(registries, holder, field, false, false);
                changes.put(field.fieldName, nbtValue);
                cachedServerValues.put(field, currentValue);
            }
        }
        return changes;
    }

    public byte[] collectServerNetworkChanges(RegistryAccess registries) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries, ConnectionType.OTHER);
        try {
            boolean wroteAny = false;
            for (int i = 0; i < syncData.getOrderedServerUpdateFields().length; i++) {
                FieldSyncData field = syncData.getOrderedServerUpdateFields()[i];
                Object currentValue = field.handle.get(holder);
                Object previousValue = cachedServerValues.get(field);
                if (Objects.equals(currentValue, previousValue)) {
                    continue;
                }
                buf.writeVarInt(i);
                buf.writeNbt(FieldSyncHandler.serializeField(registries, holder, field, false, false));
                cachedServerValues.put(field, currentValue);
                wroteAny = true;
            }

            if (!wroteAny) {
                return new byte[0];
            }

            byte[] data = new byte[buf.readableBytes()];
            buf.getBytes(0, data);
            return data;
        } finally {
            buf.release();
        }
    }

    private CompoundTag getOrCreateClientSyncNBT(HolderLookup.Provider registries, boolean fullSync) {
        if (fullSync) {
            return serializeFullClientSyncNBT(registries);
        }
        if (pendingClientChanges == null) {
            scanAndMarkChanges(registries);
        }
        return getPendingChanges();
    }

    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag, boolean readingClientFields) {
        Set<FieldSyncData> fieldsToCheck = readingClientFields ? syncData.getClientSyncFields() :
                syncData.getServerSaveFields();

        for (var field : fieldsToCheck) {

            Tag savedValue = tag.get(field.nbtSaveKey);
            FieldSyncHandler.deserializeField(registries, holder, field, savedValue, readingClientFields);

            if (readingClientFields) {
                cachedClientValues.put(field, field.handle.get(holder));
                try {
                    for (MethodHandle changeListenerHandle : field.changeListenerHandles) {
                        changeListenerHandle.invoke(holder);
                    }
                } catch (Throwable e) {
                    if (e instanceof WrongMethodTypeException) {
                        throw new IllegalArgumentException(
                                "Invalid method signature for change listener for field %s %s"
                                        .formatted(field.fieldName, holder.getClass().getName()));
                    }
                    GTCEu.LOGGER.error("Sync: Error while invoking change listener for field {}", field.fieldName, e);
                }

                if (field.triggerClientRerender) holder.scheduleRenderUpdate();
            }
        }
    }

    public void deserializeItemNBT(HolderLookup.Provider registries, CompoundTag tag) {
        for (var field : syncData.getItemSaveFields()) {
            Tag savedValue = tag.get(field.itemNbtKey);
            FieldSyncHandler.deserializeField(registries, holder, field, savedValue, false);
        }
    }

    public void applyServerUpdate(HolderLookup.Provider registries, CompoundTag tag) {
        for (var field : syncData.getServerUpdateFields()) {
            Tag savedValue = tag.get(field.fieldName);
            FieldSyncHandler.deserializeField(registries, holder, field, savedValue, false);
        }
    }

    public void applyServerNetworkUpdate(RegistryAccess registries, byte[] data) {
        if (data.length == 0) {
            return;
        }

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), registries,
                ConnectionType.OTHER);
        try {
            FieldSyncData[] fields = syncData.getOrderedServerUpdateFields();
            while (buf.isReadable()) {
                int index = buf.readVarInt();
                if (index < 0 || index >= fields.length) {
                    throw new IllegalArgumentException("Invalid server sync field index: " + index);
                }
                Tag value = buf.readNbt(NbtAccounter.unlimitedHeap());
                if (value != null) {
                    FieldSyncHandler.deserializeField(registries, holder, fields[index], value, false);
                }
            }
        } finally {
            buf.release();
        }
    }

    public void applyClientNetworkUpdate(RegistryAccess registries, byte[] data) {
        if (data.length == 0) {
            return;
        }

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(data), registries,
                ConnectionType.OTHER);
        try {
            FieldSyncData[] fields = syncData.getOrderedClientSyncFields();
            while (buf.isReadable()) {
                int index = buf.readVarInt();
                if (index < 0 || index >= fields.length) {
                    throw new IllegalArgumentException("Invalid client sync field index: " + index);
                }

                FieldSyncData field = fields[index];
                Tag value = buf.readNbt(NbtAccounter.unlimitedHeap());
                if (value == null) {
                    continue;
                }

                FieldSyncHandler.deserializeField(registries, holder, field, value, true);
                cachedClientValues.put(field, field.handle.get(holder));
                try {
                    for (MethodHandle changeListenerHandle : field.changeListenerHandles) {
                        changeListenerHandle.invoke(holder);
                    }
                } catch (Throwable e) {
                    if (e instanceof WrongMethodTypeException) {
                        throw new IllegalArgumentException(
                                "Invalid method signature for change listener for field %s %s"
                                        .formatted(field.fieldName, holder.getClass().getName()));
                    }
                    GTCEu.LOGGER.error("Sync: Error while invoking change listener for field {}", field.fieldName, e);
                }
                if (field.triggerClientRerender) holder.scheduleRenderUpdate();
            }
        } finally {
            buf.release();
        }
    }

    public static class SyncManagedTransformer implements ValueTransformer<ISyncManaged> {

        @Override
        public Tag serializeNBT(ISyncManaged value, TransformerContext<ISyncManaged> context) {
            return value.getSyncDataHolder().serializeNBT(context.lookup(), context.isClientSync(),
                    context.isClientFullSyncUpdate());
        }

        @Override
        public @Nullable ISyncManaged deserializeNBT(Tag tag, TransformerContext<ISyncManaged> context) {
            ISyncManaged syncManaged = context.currentValue();

            if (syncManaged == null) {
                GTCEu.LOGGER.error("Sync: ISyncManaged field was null, cannot instantiate {}",
                        context.fieldName());
                return null;
            }

            syncManaged.getSyncDataHolder().deserializeNBT(context.lookup(), (CompoundTag) tag, context.isClientSync());
            return syncManaged;
        }
    }
}

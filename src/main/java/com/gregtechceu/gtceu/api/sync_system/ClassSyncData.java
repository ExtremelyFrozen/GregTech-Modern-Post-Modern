package com.gregtechceu.gtceu.api.sync_system;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.annotations.ClientFieldChangeListener;
import com.gregtechceu.gtceu.api.sync_system.annotations.ItemSave;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToServer;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;
import lombok.SneakyThrows;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.*;
import java.util.*;

/**
 * Static data for {@link ISyncManaged} classes.
 */
public final class ClassSyncData {

    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final ClassValue<ClassSyncData> CACHE = new ClassValue<>() {

        @Override
        protected ClassSyncData computeValue(Class<?> type) {
            return new ClassSyncData(type);
        }
    };

    /**
     * Gets the {@link ClassSyncData} object for a specific class
     */
    public static ClassSyncData getClassData(Class<?> cls) {
        return CACHE.get(cls);
    }

    @Getter
    private final List<FieldSyncData> managedFields = new ObjectArrayList<>();
    @Getter
    private final Set<FieldSyncData> clientSyncFields = new ObjectOpenHashSet<>();
    @Getter
    private final Set<FieldSyncData> serverSaveFields = new ObjectOpenHashSet<>();
    @Getter
    private final Set<FieldSyncData> itemSaveFields = new ObjectOpenHashSet<>();
    @Getter
    private final Set<FieldSyncData> serverSyncFields = new ObjectOpenHashSet<>();
    @Getter
    private final Set<FieldSyncData> bothSyncFields = new ObjectOpenHashSet<>();
    @Getter
    private final Set<FieldSyncData> serverUpdateFields = new ObjectOpenHashSet<>();
    @Getter
    private FieldSyncData[] orderedClientSyncFields = new FieldSyncData[0];
    @Getter
    private FieldSyncData[] orderedServerUpdateFields = new FieldSyncData[0];

    @SneakyThrows
    private ClassSyncData(Class<?> clazz) {
        var isManaged = ISyncManaged.class.isAssignableFrom(clazz);
        var isAnnotated = ISyncAnnotated.class.isAssignableFrom(clazz);

        if (!isManaged && !isAnnotated)
            throw new IllegalArgumentException("Cannot create class sync data for non-sync class");
        if (isManaged && isAnnotated) throw new IllegalArgumentException(
                "Class %s cannot inherit both ISyncAnnotated and ISyncManaged".formatted(clazz.getName()));

        MethodHandles.Lookup privateLookup;
        try {
            privateLookup = MethodHandles.privateLookupIn(clazz, LOOKUP);
        } catch (IllegalAccessException e) {
            GTCEu.LOGGER.error("Sync: Failed to create method handle lookup for class {}", clazz);
            throw e;
        }

        Map<String, List<MethodHandle>> changeListeners = new HashMap<>();
        Set<String> clientListenerTargets = new HashSet<>();

        for (Method method : clazz.getDeclaredMethods()) {
            ClientFieldChangeListener listener = method.getAnnotation(ClientFieldChangeListener.class);
            if (listener == null) continue;

            if (Modifier.isStatic(method.getModifiers()))
                throw new IllegalArgumentException("Cannot apply syncdata annotation to static method: %s.%s"
                        .formatted(clazz.getName(), method.getName()));

            MethodHandle handle;
            try {
                handle = privateLookup.unreflect(method);
            } catch (IllegalAccessException e) {
                GTCEu.LOGGER.error("Sync: Failed to acquire method handle for method {} {}", method.getName(),
                        clazz.getName());
                GTCEu.LOGGER.error(e.getMessage());
                continue;
            }

            if (listener.fieldName().isBlank()) {
                throw new IllegalArgumentException("@ClientFieldChangeListener requires a non-blank fieldName: %s.%s"
                        .formatted(clazz.getName(), method.getName()));
            }

            changeListeners.computeIfAbsent(listener.fieldName(), $ -> new ArrayList<>()).add(handle);
            clientListenerTargets.add(listener.fieldName());
        }

        Map<String, FieldSyncData> localFieldsByName = new HashMap<>();
        Set<String> localSaveKeys = new HashSet<>();
        Set<String> localItemKeys = new HashSet<>();
        Set<String> localClientSyncKeys = new HashSet<>();
        Set<String> localServerSyncKeys = new HashSet<>();

        for (Field field : clazz.getDeclaredFields()) {

            boolean hasSaveField = field.isAnnotationPresent(SaveField.class);
            boolean hasItemSave = field.isAnnotationPresent(ItemSave.class);
            boolean hasClientSync = field.isAnnotationPresent(SyncToClient.class);
            boolean hasServerSync = field.isAnnotationPresent(SyncToServer.class);
            boolean hasSyncBoth = field.isAnnotationPresent(SyncBoth.class);
            if (!hasSaveField && !hasItemSave && !hasClientSync && !hasServerSync && !hasSyncBoth) continue;

            if (Modifier.isStatic(field.getModifiers()))
                throw new IllegalArgumentException("Cannot apply syncdata annotations to static field: %s.%s"
                        .formatted(field.getDeclaringClass().getName(), field.getName()));

            VarHandle handle;
            try {
                handle = privateLookup.unreflectVarHandle(field);
            } catch (IllegalAccessException e) {
                GTCEu.LOGGER.error("Sync: Failed to acquire variable handle for field {} {}", field.getName(),
                        clazz.getName());
                throw e;
            }

            FieldSyncData syncData = new FieldSyncData(field, handle, changeListeners.getOrDefault(field.getName(),
                    List.of()));
            if (localFieldsByName.put(syncData.fieldName, syncData) != null) {
                throw new IllegalArgumentException("Duplicate managed field name in %s: %s"
                        .formatted(clazz.getName(), syncData.fieldName));
            }
            managedFields.add(syncData);
            if (hasClientSync) {
                checkDuplicateKey(localClientSyncKeys, syncData.nbtSaveKey, clazz, "client sync");
                clientSyncFields.add(syncData);
            }
            if (hasSaveField) {
                checkDuplicateKey(localSaveKeys, syncData.nbtSaveKey, clazz, "save");
                serverSaveFields.add(syncData);
            }
            if (hasItemSave) {
                checkDuplicateKey(localItemKeys, syncData.itemNbtKey, clazz, "item");
                itemSaveFields.add(syncData);
            }
            if (hasServerSync) {
                checkDuplicateKey(localServerSyncKeys, syncData.fieldName, clazz, "server sync");
                serverSyncFields.add(syncData);
                serverUpdateFields.add(syncData);
            }
            if (hasSyncBoth) {
                checkDuplicateKey(localClientSyncKeys, syncData.nbtSaveKey, clazz, "client sync");
                checkDuplicateKey(localServerSyncKeys, syncData.fieldName, clazz, "server sync");
                bothSyncFields.add(syncData);
                clientSyncFields.add(syncData);
                serverSyncFields.add(syncData);
                serverUpdateFields.add(syncData);
            }
        }

        Class<?> parent = clazz.getSuperclass();
        if (ISyncManaged.class.isAssignableFrom(parent) || ISyncAnnotated.class.isAssignableFrom(parent)) {
            ClassSyncData parentHandles = CACHE.get(parent);
            managedFields.addAll(parentHandles.managedFields);
            clientSyncFields.addAll(parentHandles.clientSyncFields);
            serverSaveFields.addAll(parentHandles.serverSaveFields);
            itemSaveFields.addAll(parentHandles.itemSaveFields);
            serverSyncFields.addAll(parentHandles.serverSyncFields);
            bothSyncFields.addAll(parentHandles.bothSyncFields);
            serverUpdateFields.addAll(parentHandles.serverUpdateFields);
        }

        for (String fieldName : clientListenerTargets) {
            FieldSyncData localField = localFieldsByName.get(fieldName);
            if (localField != null && !localField.hasSyncToClient && !localField.hasSyncBoth) {
                throw new IllegalArgumentException(
                        "@ClientFieldChangeListener targets a field that never syncs to client: %s.%s"
                                .formatted(clazz.getName(), fieldName));
            }
            if (localField == null && clientSyncFields.stream().noneMatch(field -> field.fieldName.equals(fieldName))) {
                throw new IllegalArgumentException("@ClientFieldChangeListener targets unknown field: %s.%s"
                        .formatted(clazz.getName(), fieldName));
            }
        }

        orderedClientSyncFields = clientSyncFields.stream()
                .sorted(Comparator.comparing(field -> field.nbtSaveKey))
                .toArray(FieldSyncData[]::new);
        orderedServerUpdateFields = serverUpdateFields.stream()
                .sorted(Comparator.comparing(field -> field.fieldName))
                .toArray(FieldSyncData[]::new);
    }

    public Set<FieldSyncData> getWorldSaveFields() {
        return serverSaveFields;
    }

    private static void checkDuplicateKey(Set<String> keys, String key, Class<?> owner, String kind) {
        if (!keys.add(key)) {
            throw new IllegalArgumentException("Duplicate %s key in %s: %s"
                    .formatted(kind, owner.getName(), key));
        }
    }

    /**
     * Allows for a custom codec to be used for a specific field, ignoring the default codec lookup.
     *
     * @param fieldName The field name
     * @param codec     The custom codec
     */
    public void setCustomCodecForField(String fieldName, Codec<?> codec) {
        managedFields.stream().filter(f -> Objects.equals(f.fieldName, fieldName))
                .findFirst()
                .ifPresent(fieldData -> fieldData.setCodec(codec));
    }

    /**
     * Allows for a field codec that needs the owning object/current value context.
     *
     * @param fieldName The field name
     * @param codec     The custom contextual codec
     */
    public void setCustomContextualCodecForField(String fieldName, ContextualFieldCodec<?> codec) {
        managedFields.stream().filter(f -> Objects.equals(f.fieldName, fieldName))
                .findFirst()
                .ifPresent(fieldData -> fieldData.setContextualCodec(codec));
    }
}

package com.gregtechceu.gtceu.api.misc.virtualregistry;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class VirtualRegistryMap {

    private final Map<EntryTypes<?>, Map<String, VirtualEntry>> registryMap = new ConcurrentHashMap<>();

    public VirtualRegistryMap() {}

    public VirtualRegistryMap(HolderLookup.@NotNull Provider registries, DataComponentMap components) {
        importComponents(registries, components);
    }

    @SuppressWarnings("unchecked")
    public @Nullable <T extends VirtualEntry> T getEntry(EntryTypes<T> type, String name) {
        return (T) registryMap.getOrDefault(type, Collections.emptyMap()).get(name);
    }

    public void addEntry(String name, VirtualEntry entry) {
        registryMap.computeIfAbsent(entry.getType(), k -> new ConcurrentHashMap<>()).put(name, entry);
    }

    public boolean contains(EntryTypes<?> type, String name) {
        return registryMap.containsKey(type) && registryMap.get(type).containsKey(name);
    }

    public void deleteEntry(EntryTypes<?> type, String name) {
        Map<String, VirtualEntry> entries = registryMap.get(type);
        if (entries != null) {
            entries.remove(name);
            if (entries.isEmpty()) {
                registryMap.remove(type);
            }
        }
    }

    public void clear() {
        registryMap.clear();
    }

    public Set<String> getEntryNames(EntryTypes<?> type) {
        return new HashSet<>(registryMap.getOrDefault(type, Collections.emptyMap()).keySet());
    }

    public DataComponentMap exportComponents(HolderLookup.@NotNull Provider registries) {
        DataComponentMap.Builder builder = DataComponentMap.builder();
        for (Map.Entry<EntryTypes<?>, Map<String, VirtualEntry>> entry : registryMap.entrySet()) {
            builder.set(dataType(entry.getKey()), exportEntries(registries, entry.getValue()));
        }
        return builder.build();
    }

    public void importComponents(HolderLookup.@NotNull Provider registries, DataComponentMap components) {
        registryMap.clear();
        for (EntryTypes<?> type : EntryTypes.values()) {
            Map<String, DataComponentMap> entries = components.get(dataType(type));
            if (entries == null) {
                continue;
            }
            importEntries(registries, type, entries);
        }
    }

    private static Map<String, DataComponentMap> exportEntries(HolderLookup.Provider registries,
                                                               Map<String, VirtualEntry> entries) {
        Map<String, DataComponentMap> data = new ConcurrentHashMap<>();
        for (Map.Entry<String, VirtualEntry> entry : entries.entrySet()) {
            data.put(entry.getKey(), entry.getValue().exportComponents(registries));
        }
        return data;
    }

    private void importEntries(HolderLookup.Provider registries, EntryTypes<?> type,
                               Map<String, DataComponentMap> entries) {
        for (Map.Entry<String, DataComponentMap> entry : entries.entrySet()) {
            addEntry(entry.getKey(), type.createInstance(registries, entry.getValue()));
        }
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static DataComponentType<Map<String, DataComponentMap>> dataType(EntryTypes<?> type) {
        return (DataComponentType) type.getDataComponentType();
    }
}

package com.gregtechceu.gtceu.api.misc.virtualregistry;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.datacomponents.VirtualEntryData;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.saveddata.SavedData;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

public class VirtualEnderRegistry extends SavedData {

    private static final String DATA_ID = GTCEu.MOD_ID + ".virtual_entry_data";
    private static volatile VirtualEnderRegistry data;
    private final Map<UUID, VirtualRegistryMap> VIRTUAL_REGISTRIES = new HashMap<>();

    public VirtualEnderRegistry() {}

    public VirtualEnderRegistry(CompoundTag name, HolderLookup.@NotNull Provider registries) {
        importComponents(registries, readComponents(registries, name));
    }

    public static VirtualEnderRegistry getInstance() {
        if (data == null) {
            var server = GTCEu.getMinecraftServer();
            if (server != null) {
                data = server.overworld().getDataStorage().computeIfAbsent(
                        new SavedData.Factory<>(VirtualEnderRegistry::new, VirtualEnderRegistry::new), DATA_ID);
            }
        }

        return data;
    }

    /**
     * To be called on server stopped event
     */
    public static void release() {
        if (data != null) {
            data = null;
            GTCEu.LOGGER.debug("VirtualEnderRegistry has been unloaded");
        }
    }

    public <T extends VirtualEntry> T getEntry(@Nullable UUID owner, EntryTypes<T> type, String name) {
        return getRegistry(owner).getEntry(type, name);
    }

    public void addEntry(@Nullable UUID owner, String name, VirtualEntry entry) {
        getRegistry(owner).addEntry(name, entry);
    }

    public boolean hasEntry(@Nullable UUID owner, EntryTypes<?> type, String name) {
        return getRegistry(owner).contains(type, name);
    }

    public @NotNull <T extends VirtualEntry> T getOrCreateEntry(@Nullable UUID owner, EntryTypes<T> type, String name) {
        if (!hasEntry(owner, type, name)) addEntry(owner, name, type.createInstance());
        return getEntry(owner, type, name);
    }

    /**
     * Removes an entry from the registry. Use with caution!
     *
     * @param owner The uuid of the player the entry is private to, or null if the entry is public
     * @param type  Type of the registry to remove from
     * @param name  The name of the entry
     */
    public void deleteEntry(@Nullable UUID owner, EntryTypes<?> type, String name) {
        var registry = getRegistry(owner);
        if (registry.contains(type, name)) {
            registry.deleteEntry(type, name);
            return;
        }
        GTCEu.LOGGER.warn("Attempted to delete {} entry {} of type {}, which does not exist",
                owner == null ? "public" : String.format("private [%s]", owner), name, type);
    }

    public <T extends VirtualEntry> void deleteEntryIf(@Nullable UUID owner, EntryTypes<T> type, String name,
                                                       Predicate<T> shouldDelete) {
        T entry = getEntry(owner, type, name);
        if (entry != null && shouldDelete.test(entry)) deleteEntry(owner, type, name);
    }

    public Set<String> getEntryNames(UUID owner, EntryTypes<?> type) {
        return getRegistry(owner).getEntryNames(type);
    }

    private VirtualRegistryMap getRegistry(UUID owner) {
        return VIRTUAL_REGISTRIES.computeIfAbsent(owner, key -> new VirtualRegistryMap());
    }

    public DataComponentMap exportComponents(HolderLookup.@NotNull Provider registries) {
        DataComponentMap publicEntries = DataComponentMap.EMPTY;
        Map<String, DataComponentMap> privateEntries = new HashMap<>();
        for (Map.Entry<UUID, VirtualRegistryMap> entry : VIRTUAL_REGISTRIES.entrySet()) {
            DataComponentMap registryComponents = entry.getValue().exportComponents(registries);
            if (entry.getKey() == null) {
                publicEntries = registryComponents;
            } else {
                privateEntries.put(entry.getKey().toString(), registryComponents);
            }
        }
        VirtualEntryData.RegistryRoot root = new VirtualEntryData.RegistryRoot(publicEntries, privateEntries);
        if (root.isEmpty()) {
            return DataComponentMap.EMPTY;
        }
        return DataComponentMap.builder()
                .set(GTDataComponents.VIRTUAL_REGISTRY_ROOT, root)
                .build();
    }

    public void importComponents(HolderLookup.@NotNull Provider registries, DataComponentMap components) {
        VIRTUAL_REGISTRIES.clear();
        if (components.isEmpty()) {
            return;
        }
        VirtualEntryData.RegistryRoot root = components.get(GTDataComponents.VIRTUAL_REGISTRY_ROOT.get());
        if (root == null) {
            throw new IllegalArgumentException("Virtual ender registry data is missing root component");
        }
        if (!root.publicEntries().isEmpty()) {
            VIRTUAL_REGISTRIES.put(null, new VirtualRegistryMap(registries, root.publicEntries()));
        }
        for (Map.Entry<String, DataComponentMap> entry : root.privateEntries().entrySet()) {
            UUID owner = UUID.fromString(entry.getKey());
            VIRTUAL_REGISTRIES.put(owner, new VirtualRegistryMap(registries, entry.getValue()));
        }
    }

    @NotNull
    @Override
    public final CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        return writeComponents(registries, exportComponents(registries));
    }

    private static DataComponentMap readComponents(HolderLookup.Provider registries, CompoundTag tag) {
        return DataComponentMap.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .getOrThrow();
    }

    private static CompoundTag writeComponents(HolderLookup.Provider registries, DataComponentMap components) {
        return (CompoundTag) DataComponentMap.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), components)
                .getOrThrow();
    }

    @Override
    public boolean isDirty() {
        // can't think of a good way to mark dirty other than always return true;
        return true;
    }
}

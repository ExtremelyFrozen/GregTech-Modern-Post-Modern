package com.gregtechceu.gtceu.integration.map.cache.server;

import com.gregtechceu.gtceu.integration.map.cache.DimensionCache;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import org.jetbrains.annotations.NotNull;

public class ServerCacheSavedData extends SavedData {

    public static final String DATA_NAME = "gtceu_ore_vein_cache";

    private DimensionCache backingCache;
    private DataComponentMap toRead;
    private HolderLookup.Provider toReadProvider;

    public static ServerCacheSavedData init(ServerLevel world, final DimensionCache backingCache) {
        ServerCacheSavedData instance = world.getDataStorage()
                .computeIfAbsent(new Factory<>(() -> new ServerCacheSavedData(backingCache),
                        (tag, registries) -> new ServerCacheSavedData(backingCache, tag, registries)),
                        DATA_NAME);

        instance.backingCache = backingCache;
        if (backingCache.dirty) {
            instance.setDirty();
        }
        if (instance.toRead != null) {
            backingCache.readComponents(instance.toRead, instance.toReadProvider);
            instance.toRead = null;
            instance.toReadProvider = null;
        }

        return instance;
    }

    public ServerCacheSavedData(DimensionCache backingCache) {
        this.backingCache = backingCache;
    }

    public ServerCacheSavedData(DimensionCache backingCache,
                                CompoundTag compoundTag, HolderLookup.Provider registries) {
        this.backingCache = backingCache;
        DataComponentMap components = readComponents(compoundTag, registries);
        if (backingCache != null) {
            backingCache.readComponents(components, registries);
        } else {
            toRead = components;
            toReadProvider = registries;
        }
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        return writeComponents(backingCache.saveComponents(registries), registries);
    }

    private static DataComponentMap readComponents(CompoundTag tag, HolderLookup.Provider registries) {
        return DataComponentMap.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag)
                .getOrThrow();
    }

    private static CompoundTag writeComponents(DataComponentMap components, HolderLookup.Provider registries) {
        return (CompoundTag) DataComponentMap.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), components)
                .getOrThrow();
    }
}

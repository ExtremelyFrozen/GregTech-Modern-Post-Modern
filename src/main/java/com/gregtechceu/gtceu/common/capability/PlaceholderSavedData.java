package com.gregtechceu.gtceu.common.capability;

import com.gregtechceu.gtceu.api.placeholder.Placeholder;
import com.gregtechceu.gtceu.api.placeholder.PlaceholderData;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class PlaceholderSavedData extends SavedData {

    private final Map<String, Map<UUID, DataComponentMap>> data;

    public static PlaceholderSavedData getOrCreate(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(
                        new SavedData.Factory<PlaceholderSavedData>(() -> new PlaceholderSavedData(level),
                                (tag, provider) -> new PlaceholderSavedData(level, tag, provider)),
                        "gtceu_placeholder_data");
    }

    public PlaceholderSavedData(ServerLevel level) {
        this(level, new CompoundTag(), (HolderLookup.Provider) null);
    }

    public PlaceholderSavedData(ServerLevel level, CompoundTag tag, @Nullable HolderLookup.Provider provider) {
        if (provider == null) {
            if (!tag.isEmpty()) {
                throw new IllegalArgumentException("Cannot load placeholder data without registry provider");
            }
            this.data = new HashMap<>();
        } else {
            this.data = loadData(tag.getCompound("data"), provider);
        }
    }

    public PlaceholderData getPlaceholderData(Placeholder placeholder, UUID uuid) {
        String placeholderName = placeholder.getName();
        Map<UUID, DataComponentMap> entries = data.computeIfAbsent(placeholderName, ignored -> new HashMap<>());
        DataComponentMap components = entries.getOrDefault(uuid, DataComponentMap.EMPTY);
        return new PlaceholderData(components, updated -> {
            if (updated.isEmpty()) {
                entries.remove(uuid);
                if (entries.isEmpty()) {
                    data.remove(placeholderName);
                }
            } else {
                entries.put(uuid, updated);
            }
            setDirty();
        });
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag savedData = new CompoundTag();
        for (Map.Entry<String, Map<UUID, DataComponentMap>> placeholderEntry : data.entrySet()) {
            CompoundTag placeholderTag = new CompoundTag();
            for (Map.Entry<UUID, DataComponentMap> valueEntry : placeholderEntry.getValue().entrySet()) {
                placeholderTag.put(valueEntry.getKey().toString(), saveComponents(valueEntry.getValue(), provider));
            }
            savedData.put(placeholderEntry.getKey(), placeholderTag);
        }
        tag.put("data", savedData);
        return tag;
    }

    private static Map<String, Map<UUID, DataComponentMap>> loadData(CompoundTag tag, HolderLookup.Provider provider) {
        Map<String, Map<UUID, DataComponentMap>> loadedData = new HashMap<>();
        for (String placeholderName : tag.getAllKeys()) {
            CompoundTag placeholderTag = tag.getCompound(placeholderName);
            Map<UUID, DataComponentMap> entries = new HashMap<>();
            for (String uuidKey : placeholderTag.getAllKeys()) {
                Tag componentsTag = Objects.requireNonNull(placeholderTag.get(uuidKey),
                        () -> "Missing placeholder component data for " + placeholderName + "/" + uuidKey);
                entries.put(UUID.fromString(uuidKey), loadComponents(componentsTag, provider));
            }
            if (!entries.isEmpty()) {
                loadedData.put(placeholderName, entries);
            }
        }
        return loadedData;
    }

    private static Tag saveComponents(DataComponentMap components, HolderLookup.Provider provider) {
        return DataComponentMap.CODEC
                .encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), components)
                .getOrThrow();
    }

    private static DataComponentMap loadComponents(Tag tag, HolderLookup.Provider provider) {
        return DataComponentMap.CODEC
                .parse(provider.createSerializationContext(NbtOps.INSTANCE), tag)
                .getOrThrow();
    }
}

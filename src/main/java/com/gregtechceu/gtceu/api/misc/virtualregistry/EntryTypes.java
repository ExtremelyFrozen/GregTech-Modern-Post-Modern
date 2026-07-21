package com.gregtechceu.gtceu.api.misc.virtualregistry;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.misc.virtualregistry.entries.VirtualItemStorage;
import com.gregtechceu.gtceu.api.misc.virtualregistry.entries.VirtualRedstone;
import com.gregtechceu.gtceu.api.misc.virtualregistry.entries.VirtualTank;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredHolder;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Supplier;

public final class EntryTypes<T extends VirtualEntry> {

    private static final Map<ResourceLocation, EntryTypes<?>> TYPES = new Object2ObjectOpenHashMap<>();

    public static final EntryTypes<VirtualTank> ENDER_FLUID = addEntryType(GTCEu.id("ender_fluid"), VirtualTank::new,
            GTDataComponents.VIRTUAL_FLUID_ENTRIES);
    public static final EntryTypes<VirtualItemStorage> ENDER_ITEM = addEntryType(GTCEu.id("ender_item"),
            VirtualItemStorage::new, GTDataComponents.VIRTUAL_ITEM_ENTRIES);
    public static final EntryTypes<VirtualRedstone> ENDER_REDSTONE = addEntryType(GTCEu.id("ender_redstone"),
            VirtualRedstone::new, GTDataComponents.VIRTUAL_REDSTONE_ENTRIES);
    // ENDER_ENERGY("ender_energy", null),
    // ENDER_REDSTONE("ender_redstone", null);

    private final ResourceLocation id;
    private final Supplier<T> factory;
    private final DeferredHolder<DataComponentType<?>, DataComponentType<Map<String, DataComponentMap>>> dataComponentType;

    private EntryTypes(ResourceLocation id, Supplier<T> supplier,
                       DeferredHolder<DataComponentType<?>, DataComponentType<Map<String, DataComponentMap>>> dataComponentType) {
        this.id = id;
        this.factory = supplier;
        this.dataComponentType = dataComponentType;
    }

    @Nullable
    public static EntryTypes<? extends VirtualEntry> fromString(String name) {
        return TYPES.get(GTCEu.id(name));
    }

    public static <E extends VirtualEntry> EntryTypes<E> addEntryType(ResourceLocation location, Supplier<E> supplier,
                                                                      DeferredHolder<DataComponentType<?>, DataComponentType<Map<String, DataComponentMap>>> dataComponentType) {
        var type = new EntryTypes<>(location, supplier, dataComponentType);
        if (!TYPES.containsKey(location)) {
            TYPES.put(location, type);
        } else {
            GTCEu.LOGGER.warn("Entry \"{}\" is already registered!", location);
        }
        return type;
    }

    public T createInstance(HolderLookup.@NotNull Provider registries, DataComponentMap components) {
        var entry = createInstance();
        entry.importComponents(registries, components);
        return entry;
    }

    public T createInstance() {
        return factory.get();
    }

    public ResourceLocation getId() {
        return id;
    }

    public static Iterable<EntryTypes<?>> values() {
        return TYPES.values();
    }

    public DataComponentType<Map<String, DataComponentMap>> getDataComponentType() {
        return dataComponentType.get();
    }

    @Override
    public String toString() {
        return this.id.toString();
    }
}

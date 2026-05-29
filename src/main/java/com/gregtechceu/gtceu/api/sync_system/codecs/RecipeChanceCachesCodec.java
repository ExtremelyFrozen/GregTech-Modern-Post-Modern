package com.gregtechceu.gtceu.api.sync_system.codecs;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;

public final class RecipeChanceCachesCodec
        implements ContextualFieldCodec<IdentityHashMap<RecipeCapability<?>, Object2IntMap<?>>> {

    public static final RecipeChanceCachesCodec INSTANCE = new RecipeChanceCachesCodec();

    private RecipeChanceCachesCodec() {}

    @Override
    public Tag serializeNBT(IdentityHashMap<RecipeCapability<?>, Object2IntMap<?>> value,
                            Context<IdentityHashMap<RecipeCapability<?>, Object2IntMap<?>>> context) {
        CompoundTag chanceCache = new CompoundTag();
        if (context.currentValue() == null) return chanceCache;

        context.currentValue().forEach((cap, cache) -> {
            ListTag cacheTag = new ListTag();
            for (var entry : cache.object2IntEntrySet()) {
                CompoundTag compoundTag = new CompoundTag();
                var obj = cap.toNbt(entry.getKey(), context.lookup());
                compoundTag.put("entry", obj);
                compoundTag.putInt("cached_chance", entry.getIntValue());
                cacheTag.add(compoundTag);
            }
            chanceCache.put(cap.name, cacheTag);
        });

        return chanceCache;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public @Nullable IdentityHashMap<RecipeCapability<?>, Object2IntMap<?>> deserializeNBT(
                                                                                           Tag tag,
                                                                                           Context<IdentityHashMap<RecipeCapability<?>, Object2IntMap<?>>> context) {
        if (!(tag instanceof CompoundTag chanceCache)) return context.currentValue();
        if (context.currentValue() == null) return null;

        for (String key : chanceCache.getAllKeys()) {
            RecipeCapability<?> cap = GTRegistries.RECIPE_CAPABILITIES.get(ResourceLocation.parse(key));
            if (cap == null) continue;
            Object2IntMap map = context.currentValue().computeIfAbsent(cap, RecipeCapability::makeChanceCache);

            ListTag chanceTag = chanceCache.getList(key, Tag.TAG_COMPOUND);
            for (int i = 0; i < chanceTag.size(); ++i) {
                CompoundTag chanceKey = chanceTag.getCompound(i);
                var entry = cap.fromNbt(chanceKey.get("entry"), context.lookup());
                int value = chanceKey.getInt("cached_chance");
                map.put(entry, value);
            }
        }
        return context.currentValue();
    }
}

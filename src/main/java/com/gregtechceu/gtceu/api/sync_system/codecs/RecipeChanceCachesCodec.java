package com.gregtechceu.gtceu.api.sync_system.codecs;

import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;

public final class RecipeChanceCachesCodec
                                           implements
                                           ContextualFieldCodec<IdentityHashMap<RecipeCapability<?>, Object2IntMap<?>>> {

    public static final RecipeChanceCachesCodec INSTANCE = new RecipeChanceCachesCodec();

    private RecipeChanceCachesCodec() {}

    @Override
    public Tag serializeNBT(IdentityHashMap<RecipeCapability<?>, Object2IntMap<?>> value,
                            Context<IdentityHashMap<RecipeCapability<?>, Object2IntMap<?>>> context) {
        CompoundTag chanceCache = new CompoundTag();
        IdentityHashMap<RecipeCapability<?>, Object2IntMap<?>> currentValue = context.currentValue();
        if (currentValue == null) {
            return chanceCache;
        }

        for (var entry : currentValue.entrySet()) {
            RecipeCapability<?> capability = entry.getKey();
            ListTag cacheTag = new ListTag();
            for (Object2IntMap.Entry<?> cacheEntry : entry.getValue().object2IntEntrySet()) {
                CompoundTag cacheEntryTag = new CompoundTag();
                cacheEntryTag.put("entry", capability.toNbt(cacheEntry.getKey(), context.lookup()));
                cacheEntryTag.putInt("cached_chance", cacheEntry.getIntValue());
                cacheTag.add(cacheEntryTag);
            }
            chanceCache.put(capability.name, cacheTag);
        }

        return chanceCache;
    }

    @Override
    public JsonElement serializeField(IdentityHashMap<RecipeCapability<?>, Object2IntMap<?>> value,
                                      Context<IdentityHashMap<RecipeCapability<?>, Object2IntMap<?>>> context) {
        JsonObject chanceCache = new JsonObject();
        IdentityHashMap<RecipeCapability<?>, Object2IntMap<?>> currentValue = context.currentValue();
        if (currentValue == null) {
            return chanceCache;
        }

        for (var entry : currentValue.entrySet()) {
            RecipeCapability<?> capability = entry.getKey();
            JsonArray cacheJson = new JsonArray();
            for (Object2IntMap.Entry<?> cacheEntry : entry.getValue().object2IntEntrySet()) {
                JsonObject entryJson = new JsonObject();
                writeEntryJson(entryJson, capability, cacheEntry.getKey(), cacheEntry.getIntValue(), context);
                cacheJson.add(entryJson);
            }
            chanceCache.add(GTRegistries.RECIPE_CAPABILITIES.getKey(capability).toString(), cacheJson);
        }

        return chanceCache;
    }

    @Override
    public @Nullable IdentityHashMap<RecipeCapability<?>, Object2IntMap<?>> deserializeNBT(
                                                                                           Tag tag,
                                                                                           Context<IdentityHashMap<RecipeCapability<?>, Object2IntMap<?>>> context) {
        if (!(tag instanceof CompoundTag compoundTag)) {
            return context.currentValue();
        }
        IdentityHashMap<RecipeCapability<?>, Object2IntMap<?>> currentValue = context.currentValue();
        if (currentValue == null) {
            return null;
        }

        for (String key : compoundTag.getAllKeys()) {
            RecipeCapability<?> capability = GTRegistries.RECIPE_CAPABILITIES.get(ResourceLocation.parse(key));
            if (capability == null) {
                continue;
            }
            Object2IntMap<Object> map = getOrCreateCache(currentValue, capability);

            ListTag chanceTag = compoundTag.getList(key, Tag.TAG_COMPOUND);
            for (int i = 0; i < chanceTag.size(); i++) {
                CompoundTag chanceKey = chanceTag.getCompound(i);
                Object entry = readEntryNbt(capability, chanceKey.get("entry"), context);
                map.put(entry, chanceKey.getInt("cached_chance"));
            }
        }
        return currentValue;
    }

    @Override
    public @Nullable IdentityHashMap<RecipeCapability<?>, Object2IntMap<?>> deserializeField(
                                                                                             JsonElement value,
                                                                                             Context<IdentityHashMap<RecipeCapability<?>, Object2IntMap<?>>> context) {
        if (!value.isJsonObject()) {
            return context.currentValue();
        }
        IdentityHashMap<RecipeCapability<?>, Object2IntMap<?>> currentValue = context.currentValue();
        if (currentValue == null) {
            return null;
        }

        JsonObject json = value.getAsJsonObject();
        for (var entry : json.entrySet()) {
            RecipeCapability<?> capability = GTRegistries.RECIPE_CAPABILITIES
                    .get(ResourceLocation.parse(entry.getKey()));
            if (capability == null || !entry.getValue().isJsonArray()) {
                continue;
            }
            Object2IntMap<Object> map = getOrCreateCache(currentValue, capability);
            for (JsonElement entryJson : entry.getValue().getAsJsonArray()) {
                if (!entryJson.isJsonObject()) {
                    continue;
                }
                JsonObject entryObject = entryJson.getAsJsonObject();
                Object cacheKey = readEntryJson(capability, entryObject, context);
                map.put(cacheKey, entryObject.get("cached_chance").getAsInt());
            }
        }
        return currentValue;
    }

    @SuppressWarnings("unchecked")
    private static Object2IntMap<Object> getOrCreateCache(
                                                          IdentityHashMap<RecipeCapability<?>, Object2IntMap<?>> currentValue,
                                                          RecipeCapability<?> capability) {
        return (Object2IntMap<Object>) currentValue.computeIfAbsent(capability,
                key -> ((RecipeCapability<Object>) key).makeChanceCache());
    }

    @SuppressWarnings("unchecked")
    private static void writeEntryJson(JsonObject json,
                                       RecipeCapability<?> capability,
                                       @Nullable Object content,
                                       int chance,
                                       Context<?> context) {
        RecipeCapability<Object> typedCapability = (RecipeCapability<Object>) capability;
        json.add("entry", typedCapability.serializer.toJson(typedCapability.of(content), context.lookup()));
        json.addProperty("cached_chance", chance);
    }

    @SuppressWarnings("unchecked")
    private static Object readEntryJson(RecipeCapability<?> capability, JsonObject json, Context<?> context) {
        return ((RecipeCapability<Object>) capability).serializer.fromJson(json.get("entry"), context.lookup());
    }

    @SuppressWarnings("unchecked")
    private static Object readEntryNbt(RecipeCapability<?> capability, @Nullable Tag tag, Context<?> context) {
        return ((RecipeCapability<Object>) capability).fromNbt(tag, context.lookup());
    }
}

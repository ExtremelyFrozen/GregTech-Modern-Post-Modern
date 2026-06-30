package com.gregtechceu.gtceu.api.blockentity;

import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class ConfigCopyHelper {

    private ConfigCopyHelper() {}

    public static DataComponentMap withFields(DataComponentMap components, Consumer<SyncFieldData.Builder> consumer) {
        SyncFieldData.Builder builder = SyncFieldData.builder();
        consumer.accept(builder);
        return withFields(components, builder.build());
    }

    public static DataComponentMap withFields(DataComponentMap components, SyncFieldData fieldData) {
        if (fieldData.isEmpty()) {
            return components;
        }

        SyncFieldData current = fields(components);
        SyncFieldData merged = merge(current, fieldData);
        return withComponent(components, GTDataComponents.SYNC_FIELD_DATA.get(), merged);
    }

    public static DataComponentMap mergeComponents(DataComponentMap first, DataComponentMap second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }

        SyncFieldData mergedFields = merge(fields(first), fields(second));
        DataComponentMap.Builder builder = DataComponentMap.builder()
                .addAll(first)
                .addAll(second);
        if (!mergedFields.isEmpty()) {
            builder.set(GTDataComponents.SYNC_FIELD_DATA.get(), mergedFields);
        }
        return builder.build();
    }

    public static <T> DataComponentMap withComponent(DataComponentMap components, DataComponentType<T> type, T value) {
        return DataComponentMap.builder()
                .addAll(components)
                .set(type, value)
                .build();
    }

    public static SyncFieldData fields(DataComponentMap components) {
        return components.getOrDefault(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.EMPTY);
    }

    public static boolean contains(DataComponentMap components, String key) {
        return fields(components).contains(SyncFieldData.key(key));
    }

    public static @Nullable JsonElement getField(DataComponentMap components, String key) {
        return fields(components).get(SyncFieldData.key(key));
    }

    public static int getInt(DataComponentMap components, String key) {
        JsonElement field = getField(components, key);
        return field instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsInt() : 0;
    }

    public static long getLong(DataComponentMap components, String key) {
        JsonElement field = getField(components, key);
        return field instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsLong() : 0L;
    }

    public static boolean getBoolean(DataComponentMap components, String key) {
        JsonElement field = getField(components, key);
        if (!(field instanceof JsonPrimitive primitive)) {
            return false;
        }
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        return primitive.isNumber() && primitive.getAsByte() != 0;
    }

    public static String getString(DataComponentMap components, String key) {
        JsonElement field = getField(components, key);
        return field instanceof JsonPrimitive primitive ? primitive.getAsString() : "";
    }

    public static JsonElement encodeItem(HolderLookup.Provider registries, ItemStack stack) {
        return ItemStack.OPTIONAL_CODEC
                .encodeStart(registries.createSerializationContext(JsonOps.INSTANCE), stack)
                .getOrThrow();
    }

    public static ItemStack decodeItem(HolderLookup.Provider registries, @Nullable JsonElement json) {
        if (json == null) {
            return ItemStack.EMPTY;
        }
        return ItemStack.OPTIONAL_CODEC
                .parse(registries.createSerializationContext(JsonOps.INSTANCE), json)
                .getOrThrow();
    }

    public static JsonPrimitive intValue(int value) {
        return new JsonPrimitive(value);
    }

    public static JsonPrimitive longValue(long value) {
        return new JsonPrimitive(value);
    }

    public static JsonPrimitive booleanValue(boolean value) {
        return new JsonPrimitive(value);
    }

    public static JsonPrimitive stringValue(String value) {
        return new JsonPrimitive(value);
    }

    private static SyncFieldData merge(SyncFieldData first, SyncFieldData second) {
        if (first.isEmpty()) {
            return second;
        }
        if (second.isEmpty()) {
            return first;
        }

        Map<ResourceLocation, JsonElement> fields = new LinkedHashMap<>(first.fields());
        fields.putAll(second.fields());
        return new SyncFieldData(fields);
    }
}

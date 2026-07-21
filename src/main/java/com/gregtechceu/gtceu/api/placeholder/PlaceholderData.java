package com.gregtechceu.gtceu.api.placeholder;

import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Mutable placeholder runtime state backed by typed data components.
 *
 * <p>
 * Placeholder scripts need a small persistent key-value state across ticks. This class keeps that state away from
 * NBT and stores it in a {@link DataComponentMap}; persistence boundaries are responsible for encoding the component
 * map to the storage format required by Minecraft.
 * </p>
 */
public final class PlaceholderData {

    private DataComponentMap components;
    private final Consumer<DataComponentMap> changeConsumer;

    public PlaceholderData(DataComponentMap components, Consumer<DataComponentMap> changeConsumer) {
        this.components = components;
        this.changeConsumer = changeConsumer;
    }

    public DataComponentMap components() {
        return components;
    }

    public boolean contains(String key) {
        return fields().contains(SyncFieldData.key(key));
    }

    public boolean getBoolean(String key) {
        JsonElement value = get(key);
        if (!(value instanceof JsonPrimitive primitive)) {
            return false;
        }
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        return primitive.isNumber() && primitive.getAsByte() != 0;
    }

    public int getInt(String key) {
        JsonElement value = get(key);
        return value instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsInt() : 0;
    }

    public long getLong(String key) {
        JsonElement value = get(key);
        return value instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsLong() : 0L;
    }

    public String getString(String key) {
        JsonElement value = get(key);
        return value instanceof JsonPrimitive primitive ? primitive.getAsString() : "";
    }

    public void putBoolean(String key, boolean value) {
        put(key, new JsonPrimitive(value));
    }

    public void putInt(String key, int value) {
        put(key, new JsonPrimitive(value));
    }

    public void putLong(String key, long value) {
        put(key, new JsonPrimitive(value));
    }

    public void putString(String key, String value) {
        put(key, new JsonPrimitive(value));
    }

    public void remove(String key) {
        ResourceLocation removedKey = SyncFieldData.key(key);
        SyncFieldData.Builder builder = SyncFieldData.builder();
        for (Map.Entry<ResourceLocation, JsonElement> entry : fields().fields().entrySet()) {
            if (!entry.getKey().equals(removedKey)) {
                builder.put(entry.getKey(), entry.getValue());
            }
        }
        setFields(builder.build());
    }

    private @Nullable JsonElement get(String key) {
        return fields().get(SyncFieldData.key(key));
    }

    private void put(String key, JsonElement value) {
        SyncFieldData.Builder builder = SyncFieldData.builder();
        for (Map.Entry<ResourceLocation, JsonElement> entry : fields().fields().entrySet()) {
            builder.put(entry.getKey(), entry.getValue());
        }
        builder.put(SyncFieldData.key(key), value);
        setFields(builder.build());
    }

    private SyncFieldData fields() {
        return components.getOrDefault(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.EMPTY);
    }

    private void setFields(SyncFieldData fields) {
        DataComponentMap.Builder builder = DataComponentMap.builder().addAll(components);
        if (fields.isEmpty()) {
            components = DataComponentMap.EMPTY;
        } else {
            components = builder.set(GTDataComponents.SYNC_FIELD_DATA.get(), fields).build();
        }
        changeConsumer.accept(components);
    }
}

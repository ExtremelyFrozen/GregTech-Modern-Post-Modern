package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.common.data.GTRecipeDataComponents;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Recipe data stored as a typed data component instead of runtime NBT.
 *
 * <p>
 * The open keyed map preserves the existing recipe JSON and KubeJS data surface while keeping recipe runtime and
 * network payloads on {@link DataComponentMap}.
 * </p>
 */
public record RecipeData(Map<String, JsonElement> values) {

    private static final Gson GSON = new Gson();
    private static final int MAX_DATA_JSON_LENGTH = 1_048_576;

    public static final RecipeData EMPTY = new RecipeData(Map.of());
    public static final Codec<RecipeData> CODEC = Codec.unboundedMap(Codec.STRING, ExtraCodecs.JSON)
            .xmap(RecipeData::new, RecipeData::values);
    public static final StreamCodec<RegistryFriendlyByteBuf, RecipeData> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public @NotNull RecipeData decode(RegistryFriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            Map<String, JsonElement> values = new LinkedHashMap<>(size);
            for (int i = 0; i < size; i++) {
                values.put(buffer.readUtf(), JsonParser.parseString(buffer.readUtf(MAX_DATA_JSON_LENGTH)));
            }
            return new RecipeData(values);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, RecipeData value) {
            buffer.writeVarInt(value.values.size());
            for (Map.Entry<String, JsonElement> entry : value.values.entrySet()) {
                buffer.writeUtf(entry.getKey());
                buffer.writeUtf(GSON.toJson(entry.getValue()), MAX_DATA_JSON_LENGTH);
            }
        }
    };

    public RecipeData {
        values = Map.copyOf(values);
    }

    public static DataComponentMap emptyMap() {
        return DataComponentMap.EMPTY;
    }

    public static DataComponentMap copy(DataComponentMap data) {
        RecipeData recipeData = get(data);
        if (data.isEmpty()) {
            return DataComponentMap.EMPTY;
        }
        DataComponentMap.Builder builder = DataComponentMap.builder()
                .addAll(data);
        if (!recipeData.isEmpty()) {
            builder.set(GTRecipeDataComponents.RECIPE_DATA.get(), recipeData.copy());
        }
        return builder.build();
    }

    public static DataComponentMap of(RecipeData data) {
        if (data.isEmpty()) {
            return DataComponentMap.EMPTY;
        }
        return DataComponentMap.builder()
                .set(GTRecipeDataComponents.RECIPE_DATA.get(), data)
                .build();
    }

    public static RecipeData get(DataComponentMap components) {
        return components.getOrDefault(GTRecipeDataComponents.RECIPE_DATA.get(), EMPTY);
    }

    public static boolean isEmpty(DataComponentMap components) {
        return get(components).isEmpty();
    }

    public static boolean contains(DataComponentMap components, String key) {
        return get(components).contains(key);
    }

    public static int getInt(DataComponentMap components, String key) {
        return get(components).getInt(key);
    }

    public static long getLong(DataComponentMap components, String key) {
        return get(components).getLong(key);
    }

    public static String getString(DataComponentMap components, String key) {
        return get(components).getString(key);
    }

    public static boolean getBoolean(DataComponentMap components, String key) {
        return get(components).getBoolean(key);
    }

    public static DataComponentMap putInt(DataComponentMap components, String key, int value) {
        return update(components, builder -> builder.put(key, new JsonPrimitive(value)));
    }

    public static DataComponentMap putLong(DataComponentMap components, String key, long value) {
        return update(components, builder -> builder.put(key, new JsonPrimitive(value)));
    }

    public static DataComponentMap putString(DataComponentMap components, String key, String value) {
        return update(components, builder -> builder.put(key, new JsonPrimitive(value)));
    }

    public static DataComponentMap putFloat(DataComponentMap components, String key, float value) {
        return update(components, builder -> builder.put(key, new JsonPrimitive(value)));
    }

    public static DataComponentMap putDouble(DataComponentMap components, String key, double value) {
        return update(components, builder -> builder.put(key, new JsonPrimitive(value)));
    }

    public static DataComponentMap putBoolean(DataComponentMap components, String key, boolean value) {
        return update(components, builder -> builder.put(key, new JsonPrimitive(value)));
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public boolean contains(String key) {
        return values.containsKey(key);
    }

    public int getInt(String key) {
        JsonElement value = values.get(key);
        return value instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsInt() : 0;
    }

    public long getLong(String key) {
        JsonElement value = values.get(key);
        return value instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsLong() : 0L;
    }

    public String getString(String key) {
        JsonElement value = values.get(key);
        return value instanceof JsonPrimitive primitive ? primitive.getAsString() : "";
    }

    public boolean getBoolean(String key) {
        JsonElement value = values.get(key);
        if (!(value instanceof JsonPrimitive primitive)) {
            return false;
        }
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        return primitive.isNumber() && primitive.getAsByte() != 0;
    }

    public DataComponentMap toComponentMap() {
        return of(this);
    }

    public RecipeData copy() {
        if (values.isEmpty()) {
            return EMPTY;
        }
        Builder builder = builder();
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            builder.put(entry.getKey(), entry.getValue().deepCopy());
        }
        return builder.build();
    }

    public Builder toBuilder() {
        return new Builder(values);
    }

    public static Builder builder() {
        return new Builder();
    }

    private static DataComponentMap update(DataComponentMap components, Consumer<Builder> updater) {
        Builder builder = get(components).toBuilder();
        updater.accept(builder);
        return builder.toComponentMap();
    }

    public static final class Builder {

        private final Map<String, JsonElement> values;

        private Builder() {
            this.values = new LinkedHashMap<>();
        }

        private Builder(Map<String, JsonElement> values) {
            this.values = new LinkedHashMap<>(values);
        }

        public Builder put(String key, JsonElement value) {
            values.put(key, value == null ? JsonNull.INSTANCE : value);
            return this;
        }

        public RecipeData build() {
            if (values.isEmpty()) {
                return EMPTY;
            }
            return new RecipeData(values);
        }

        public DataComponentMap toComponentMap() {
            return build().toComponentMap();
        }
    }
}

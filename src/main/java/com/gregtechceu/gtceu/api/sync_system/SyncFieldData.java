package com.gregtechceu.gtceu.api.sync_system;

import com.gregtechceu.gtceu.GTCEu;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Field payload stored as a typed data component outside of block-entity NBT boundaries.
 *
 * <p>
 * Missing keys mean "unchanged"; {@link JsonNull} means the field value was explicitly set to {@code null}.
 * </p>
 */
public record SyncFieldData(Map<ResourceLocation, JsonElement> fields) {

    private static final Gson GSON = new Gson();
    private static final int MAX_FIELD_JSON_LENGTH = 1_048_576;

    public static final SyncFieldData EMPTY = new SyncFieldData(Map.of());
    public static final Codec<SyncFieldData> CODEC = Codec
            .unboundedMap(ResourceLocation.CODEC, ExtraCodecs.JSON)
            .xmap(SyncFieldData::new, SyncFieldData::fields);
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncFieldData> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public SyncFieldData decode(RegistryFriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            Map<ResourceLocation, JsonElement> fields = new LinkedHashMap<>(size);
            for (int i = 0; i < size; i++) {
                ResourceLocation key = ResourceLocation.STREAM_CODEC.decode(buffer);
                fields.put(key, JsonParser.parseString(buffer.readUtf(MAX_FIELD_JSON_LENGTH)));
            }
            return new SyncFieldData(fields);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, SyncFieldData value) {
            buffer.writeVarInt(value.fields.size());
            for (Map.Entry<ResourceLocation, JsonElement> entry : value.fields.entrySet()) {
                ResourceLocation.STREAM_CODEC.encode(buffer, entry.getKey());
                buffer.writeUtf(GSON.toJson(entry.getValue()), MAX_FIELD_JSON_LENGTH);
            }
        }
    };
    public static final StreamCodec<RegistryFriendlyByteBuf, DataComponentMap> DATA_COMPONENT_MAP_STREAM_CODEC = new StreamCodec<>() {

        @Override
        public DataComponentMap decode(RegistryFriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            DataComponentMap.Builder builder = DataComponentMap.builder();
            for (int i = 0; i < size; i++) {
                setDecodedComponent(builder, TypedDataComponent.STREAM_CODEC.decode(buffer));
            }
            return builder.build();
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, DataComponentMap value) {
            buffer.writeVarInt(value.size());
            for (TypedDataComponent<?> component : value) {
                TypedDataComponent.STREAM_CODEC.encode(buffer, component);
            }
        }
    };

    public SyncFieldData {
        fields = Map.copyOf(fields);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ResourceLocation key(String key) {
        if (key.indexOf(':') >= 0) {
            return ResourceLocation.parse(key);
        }
        return GTCEu.id(escapePath(key));
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    public boolean contains(ResourceLocation key) {
        return fields.containsKey(key);
    }

    public @Nullable JsonElement get(ResourceLocation key) {
        return fields.get(key);
    }

    public JsonElement toJson() {
        return CODEC.encodeStart(JsonOps.INSTANCE, this).getOrThrow();
    }

    public DataComponentMap toComponentMap(DataComponentType<SyncFieldData> componentType) {
        if (isEmpty()) {
            return DataComponentMap.EMPTY;
        }
        return DataComponentMap.builder()
                .set(componentType, this)
                .build();
    }

    public static SyncFieldData fromJson(JsonElement json) {
        return CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static <T> void setDecodedComponent(DataComponentMap.Builder builder, TypedDataComponent<T> component) {
        DataComponentType<T> type = component.type();
        builder.set(type, component.value());
    }

    private static String escapePath(String key) {
        StringBuilder path = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '/') {
                path.append(c);
            } else {
                path.append("_u");
                String hex = Integer.toHexString(c);
                path.repeat("0", 4 - hex.length());
                path.append(hex);
            }
        }
        return path.toString();
    }

    public static final class Builder {

        private final Map<ResourceLocation, JsonElement> fields = new LinkedHashMap<>();

        public Builder put(ResourceLocation key, @Nullable JsonElement value) {
            fields.put(key, value == null ? JsonNull.INSTANCE : value);
            return this;
        }

        public Builder put(ResourceLocation key, SyncFieldData value) {
            fields.put(key, value.toJson());
            return this;
        }

        public SyncFieldData build() {
            if (fields.isEmpty()) {
                return EMPTY;
            }
            return new SyncFieldData(fields);
        }
    }
}

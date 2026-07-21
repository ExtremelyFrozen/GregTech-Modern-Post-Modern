package com.gregtechceu.gtceu.common.data.datacomponents;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;

/**
 * Typed component payload for AE2 managed grid node state.
 *
 * <p>
 * AE2 exposes its managed node state through NBT-only APIs. GT sync converts that external boundary payload into JSON
 * before storing it in this typed component, so data component network buffers do not carry NBT tags.
 * </p>
 */
public record AE2GridNodeData(JsonElement payload) {

    private static final Gson GSON = new Gson();
    private static final int MAX_PAYLOAD_JSON_LENGTH = 1_048_576;

    public static final Codec<AE2GridNodeData> CODEC = ExtraCodecs.JSON.xmap(AE2GridNodeData::new,
            AE2GridNodeData::payload);
    public static final StreamCodec<RegistryFriendlyByteBuf, AE2GridNodeData> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public AE2GridNodeData decode(RegistryFriendlyByteBuf buffer) {
            return new AE2GridNodeData(JsonParser.parseString(buffer.readUtf(MAX_PAYLOAD_JSON_LENGTH)));
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, AE2GridNodeData value) {
            buffer.writeUtf(GSON.toJson(value.payload), MAX_PAYLOAD_JSON_LENGTH);
        }
    };

    public AE2GridNodeData {
        if (payload == null || payload.isJsonNull()) {
            throw new IllegalArgumentException("AE2 grid node payload cannot be blank");
        }
    }
}

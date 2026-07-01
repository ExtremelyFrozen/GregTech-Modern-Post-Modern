package com.gregtechceu.gtceu.common.data.datacomponents;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;

/**
 * Typed component payload for AE2 managed grid node state.
 *
 * <p>
 * AE2 exposes its managed node state through NBT-only APIs. GT sync stores the external AE2 payload as JSON text inside
 * this typed component so data component network buffers do not carry NBT tags.
 * </p>
 */
public record AE2GridNodeData(String payload) {

    public static final Codec<AE2GridNodeData> CODEC = Codec.STRING.xmap(AE2GridNodeData::new,
            AE2GridNodeData::payload);
    public static final StreamCodec<ByteBuf, AE2GridNodeData> STREAM_CODEC = ByteBufCodecs.STRING_UTF8
            .map(AE2GridNodeData::new, AE2GridNodeData::payload);

    public AE2GridNodeData {
        if (payload.isBlank()) {
            throw new IllegalArgumentException("AE2 grid node payload cannot be blank");
        }
    }
}

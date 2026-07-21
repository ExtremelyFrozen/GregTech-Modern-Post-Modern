package com.gregtechceu.gtceu.common.data.datacomponents;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CapeData {

    private CapeData() {}

    public record Registry(Map<UUID, List<ResourceLocation>> unlockedCapes,
                           Map<UUID, ResourceLocation> currentCapes) {

        private static final Codec<Map<UUID, List<ResourceLocation>>> UNLOCKED_CAPES_CODEC = Codec.unboundedMap(
                UUIDUtil.STRING_CODEC, ResourceLocation.CODEC.listOf());
        private static final Codec<Map<UUID, ResourceLocation>> CURRENT_CAPES_CODEC = Codec.unboundedMap(
                UUIDUtil.STRING_CODEC, ResourceLocation.CODEC);
        private static final StreamCodec<ByteBuf, List<ResourceLocation>> CAPE_LIST_STREAM_CODEC = ResourceLocation.STREAM_CODEC
                .apply(ByteBufCodecs.list());
        private static final StreamCodec<ByteBuf, Map<UUID, List<ResourceLocation>>> UNLOCKED_CAPES_STREAM_CODEC = ByteBufCodecs
                .map(Object2ObjectOpenHashMap::new, UUIDUtil.STREAM_CODEC, CAPE_LIST_STREAM_CODEC);
        private static final StreamCodec<ByteBuf, Map<UUID, ResourceLocation>> CURRENT_CAPES_STREAM_CODEC = ByteBufCodecs
                .map(Object2ObjectOpenHashMap::new, UUIDUtil.STREAM_CODEC, ResourceLocation.STREAM_CODEC);
        public static final Codec<Registry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                UNLOCKED_CAPES_CODEC.optionalFieldOf("unlocked_capes", Map.of())
                        .forGetter(Registry::unlockedCapes),
                CURRENT_CAPES_CODEC.optionalFieldOf("current_capes", Map.of())
                        .forGetter(Registry::currentCapes))
                .apply(instance, Registry::new));
        public static final StreamCodec<ByteBuf, Registry> STREAM_CODEC = StreamCodec.composite(
                UNLOCKED_CAPES_STREAM_CODEC, Registry::unlockedCapes,
                CURRENT_CAPES_STREAM_CODEC, Registry::currentCapes,
                Registry::new);

        public Registry {
            unlockedCapes = copyUnlockedCapes(unlockedCapes);
            currentCapes = Map.copyOf(currentCapes);
        }

        public boolean isEmpty() {
            return unlockedCapes.isEmpty() && currentCapes.isEmpty();
        }

        private static Map<UUID, List<ResourceLocation>> copyUnlockedCapes(
                                                                           Map<UUID, List<ResourceLocation>> unlockedCapes) {
            Map<UUID, List<ResourceLocation>> copy = new Object2ObjectOpenHashMap<>();
            for (Map.Entry<UUID, List<ResourceLocation>> entry : unlockedCapes.entrySet()) {
                copy.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            return Map.copyOf(copy);
        }
    }
}

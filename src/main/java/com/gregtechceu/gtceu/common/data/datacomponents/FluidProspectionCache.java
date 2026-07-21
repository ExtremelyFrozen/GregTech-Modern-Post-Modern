package com.gregtechceu.gtceu.common.data.datacomponents;

import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;
import com.gregtechceu.gtceu.api.gui.misc.ProspectorMode;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record FluidProspectionCache(List<Entry> entries) {

    public static final FluidProspectionCache EMPTY = new FluidProspectionCache(List.of());
    public static final Codec<FluidProspectionCache> CODEC = Entry.CODEC.listOf()
            .xmap(FluidProspectionCache::new, FluidProspectionCache::entries);
    public static final StreamCodec<RegistryFriendlyByteBuf, FluidProspectionCache> STREAM_CODEC = Entry.STREAM_CODEC
            .apply(ByteBufCodecs.list())
            .map(FluidProspectionCache::new, FluidProspectionCache::entries);

    public FluidProspectionCache {
        entries = List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public record Entry(ResourceKey<Level> dimension, ChunkPos pos, ProspectorMode.FluidInfo fluid) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(Entry::dimension),
                GeneratedVeinMetadata.CHUNK_POS_CODEC.fieldOf("pos").forGetter(Entry::pos),
                ProspectorMode.FluidInfo.CODEC.fieldOf("fluid").forGetter(Entry::fluid))
                .apply(instance, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = new StreamCodec<>() {

            @Override
            public Entry decode(RegistryFriendlyByteBuf buffer) {
                ResourceKey<Level> dimension = buffer.readResourceKey(Registries.DIMENSION);
                ChunkPos pos = buffer.readChunkPos();
                ProspectorMode.FluidInfo fluid = ProspectorMode.FluidInfo.STREAM_CODEC.decode(buffer);
                return new Entry(dimension, pos, fluid);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Entry value) {
                buffer.writeResourceKey(value.dimension);
                buffer.writeChunkPos(value.pos);
                ProspectorMode.FluidInfo.STREAM_CODEC.encode(buffer, value.fluid);
            }
        };
    }
}

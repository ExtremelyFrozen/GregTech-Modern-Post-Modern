package com.gregtechceu.gtceu.common.data.datacomponents;

import com.gregtechceu.gtceu.api.data.worldgen.ores.GeneratedVeinMetadata;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.List;

public record OreProspectionCache(List<Entry> entries) {

    public static final OreProspectionCache EMPTY = new OreProspectionCache(List.of());
    public static final Codec<OreProspectionCache> CODEC = Entry.CODEC.listOf()
            .xmap(OreProspectionCache::new, OreProspectionCache::entries);
    public static final StreamCodec<RegistryFriendlyByteBuf, OreProspectionCache> STREAM_CODEC = Entry.STREAM_CODEC
            .apply(ByteBufCodecs.list())
            .map(OreProspectionCache::new, OreProspectionCache::entries);

    public OreProspectionCache {
        entries = List.copyOf(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public record Entry(int gridX, int gridZ, List<GeneratedVeinMetadata> veins) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("grid_x").forGetter(Entry::gridX),
                Codec.INT.fieldOf("grid_z").forGetter(Entry::gridZ),
                GeneratedVeinMetadata.CODEC.listOf().fieldOf("veins").forGetter(Entry::veins))
                .apply(instance, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = new StreamCodec<>() {

            @Override
            public Entry decode(RegistryFriendlyByteBuf buffer) {
                int gridX = buffer.readVarInt();
                int gridZ = buffer.readVarInt();
                int veinCount = buffer.readVarInt();
                List<GeneratedVeinMetadata> veins = new ArrayList<>(veinCount);
                for (int i = 0; i < veinCount; i++) {
                    veins.add(GeneratedVeinMetadata.readFromPacket(buffer));
                }
                return new Entry(gridX, gridZ, veins);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Entry value) {
                buffer.writeVarInt(value.gridX);
                buffer.writeVarInt(value.gridZ);
                buffer.writeVarInt(value.veins.size());
                for (GeneratedVeinMetadata vein : value.veins) {
                    vein.writeToPacket(buffer);
                }
            }
        };

        public Entry {
            veins = List.copyOf(veins);
        }
    }
}

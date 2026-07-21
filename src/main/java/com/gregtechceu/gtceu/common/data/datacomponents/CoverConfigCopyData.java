package com.gregtechceu.gtceu.common.data.datacomponents;

import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.EnumMap;
import java.util.Map;

public record CoverConfigCopyData(Map<Direction, Entry> covers) {

    public static final Codec<CoverConfigCopyData> CODEC = Codec.unboundedMap(Direction.CODEC, Entry.CODEC)
            .xmap(CoverConfigCopyData::new, CoverConfigCopyData::covers);
    public static final StreamCodec<RegistryFriendlyByteBuf, CoverConfigCopyData> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public CoverConfigCopyData decode(RegistryFriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            Map<Direction, Entry> covers = new EnumMap<>(Direction.class);
            Direction[] directions = Direction.values();
            for (int i = 0; i < size; i++) {
                Direction direction = directions[buffer.readVarInt()];
                covers.put(direction, Entry.STREAM_CODEC.decode(buffer));
            }
            return new CoverConfigCopyData(covers);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, CoverConfigCopyData value) {
            buffer.writeVarInt(value.covers.size());
            for (Map.Entry<Direction, Entry> entry : value.covers.entrySet()) {
                buffer.writeVarInt(entry.getKey().ordinal());
                Entry.STREAM_CODEC.encode(buffer, entry.getValue());
            }
        }
    };

    public CoverConfigCopyData {
        covers = Map.copyOf(covers);
    }

    public boolean isEmpty() {
        return covers.isEmpty();
    }

    public record Entry(ResourceLocation id, ItemStack attachItem, DataComponentMap config) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(Entry::id),
                ItemStack.OPTIONAL_CODEC.fieldOf("attach_item").forGetter(Entry::attachItem),
                DataComponentMap.CODEC.fieldOf("config").forGetter(Entry::config))
                .apply(instance, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Entry::id,
                ItemStack.OPTIONAL_STREAM_CODEC, Entry::attachItem,
                SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC, Entry::config,
                Entry::new);

        public Entry {
            attachItem = attachItem.copy();
        }
    }
}

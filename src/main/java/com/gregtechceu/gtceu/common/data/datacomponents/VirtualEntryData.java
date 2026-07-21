package com.gregtechceu.gtceu.common.data.datacomponents;

import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;

import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class VirtualEntryData {

    private VirtualEntryData() {}

    public record Base(String color, String description) {

        public static final Base EMPTY = new Base("FFFFFFFF", "");
        public static final Codec<Base> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("color").forGetter(Base::color),
                Codec.STRING.optionalFieldOf("description", "").forGetter(Base::description))
                .apply(instance, Base::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Base> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Base::color,
                ByteBufCodecs.STRING_UTF8, Base::description,
                Base::new);

        public boolean isEmpty() {
            return EMPTY.equals(this);
        }
    }

    public record Tank(int capacity, FluidStack fluid) {

        public static final Codec<Tank> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("capacity").forGetter(Tank::capacity),
                FluidStack.OPTIONAL_CODEC.fieldOf("fluid").forGetter(Tank::fluid))
                .apply(instance, Tank::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Tank> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Tank::capacity,
                FluidStack.OPTIONAL_STREAM_CODEC, Tank::fluid,
                Tank::new);

        public Tank {
            fluid = fluid.copy();
        }
    }

    public record Items(List<ItemStack> stacks) {

        public static final Codec<Items> CODEC = ItemStack.OPTIONAL_CODEC.listOf()
                .xmap(Items::new, Items::stacks);
        public static final StreamCodec<RegistryFriendlyByteBuf, Items> STREAM_CODEC = ItemStack.OPTIONAL_STREAM_CODEC
                .apply(ByteBufCodecs.list())
                .map(Items::new, Items::stacks);

        public Items {
            stacks = stacks.stream().map(ItemStack::copy).toList();
        }
    }

    public record Redstone(List<Member> members) {

        public static final Redstone EMPTY = new Redstone(List.of());
        public static final Codec<Redstone> CODEC = Member.CODEC.listOf()
                .xmap(Redstone::new, Redstone::members);
        public static final StreamCodec<RegistryFriendlyByteBuf, Redstone> STREAM_CODEC = Member.STREAM_CODEC
                .apply(ByteBufCodecs.list())
                .map(Redstone::new, Redstone::members);

        public Redstone {
            members = List.copyOf(members);
        }

        public boolean isEmpty() {
            return members.isEmpty();
        }
    }

    public record Member(UUID id, short signal) {

        public static final Codec<Member> CODEC = Codec.mapPair(
                UUIDUtil.STRING_CODEC.fieldOf("id"),
                Codec.SHORT.fieldOf("signal"))
                .codec()
                .xmap(pair -> new Member(pair.getFirst(), pair.getSecond()),
                        member -> Pair.of(member.id, member.signal));
        public static final StreamCodec<RegistryFriendlyByteBuf, Member> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Member::id,
                ByteBufCodecs.SHORT, Member::signal,
                Member::new);
    }

    public record RegistryRoot(DataComponentMap publicEntries, Map<String, DataComponentMap> privateEntries) {

        private static final Codec<Map<String, DataComponentMap>> PRIVATE_ENTRIES_CODEC = Codec.unboundedMap(
                Codec.STRING, DataComponentMap.CODEC);
        private static final StreamCodec<RegistryFriendlyByteBuf, Map<String, DataComponentMap>> PRIVATE_ENTRIES_STREAM_CODEC = ByteBufCodecs
                .map(HashMap::new, ByteBufCodecs.STRING_UTF8, SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC);
        public static final Codec<RegistryRoot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                DataComponentMap.CODEC.optionalFieldOf("public_entries", DataComponentMap.EMPTY)
                        .forGetter(RegistryRoot::publicEntries),
                PRIVATE_ENTRIES_CODEC.optionalFieldOf("private_entries", Map.of())
                        .forGetter(RegistryRoot::privateEntries))
                .apply(instance, RegistryRoot::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, RegistryRoot> STREAM_CODEC = StreamCodec.composite(
                SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC, RegistryRoot::publicEntries,
                PRIVATE_ENTRIES_STREAM_CODEC, RegistryRoot::privateEntries,
                RegistryRoot::new);

        public RegistryRoot {
            privateEntries = Map.copyOf(privateEntries);
        }

        public boolean isEmpty() {
            return publicEntries.isEmpty() && privateEntries.isEmpty();
        }
    }
}

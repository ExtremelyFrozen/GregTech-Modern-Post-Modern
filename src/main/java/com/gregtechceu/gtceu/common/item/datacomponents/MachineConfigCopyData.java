package com.gregtechceu.gtceu.common.item.datacomponents;

import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record MachineConfigCopyData(String source, List<ItemStack> itemsToPaste, DataComponentMap config) {

    // spotless:off
    public static final Codec<MachineConfigCopyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("source").forGetter(MachineConfigCopyData::source),
            ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items_to_paste").forGetter(MachineConfigCopyData::itemsToPaste),
            DataComponentMap.CODEC.fieldOf("config").forGetter(MachineConfigCopyData::config)
    ).apply(instance, MachineConfigCopyData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, MachineConfigCopyData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, MachineConfigCopyData::source,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()), MachineConfigCopyData::itemsToPaste,
            SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC, MachineConfigCopyData::config,
            MachineConfigCopyData::new
    );
    // spotless:on

    public MachineConfigCopyData {
        itemsToPaste = List.copyOf(itemsToPaste);
    }
}

package com.gregtechceu.gtceu.common.data.datacomponents;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import appeng.api.stacks.GenericStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public record AEInputConfigCopyData(List<@Nullable GenericStack> stacks, byte ghostCircuit, boolean distinctBuses,
                                    boolean autoPull) {

    private static final StreamCodec<RegistryFriendlyByteBuf, GenericStack> NULLABLE_STACK_STREAM_CODEC = ByteBufCodecs
            .optional(GenericStack.STREAM_CODEC)
            .map(optional -> optional.orElse(null), Optional::ofNullable);

    public static final Codec<AEInputConfigCopyData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GenericStack.FAULT_TOLERANT_NULLABLE_LIST_CODEC.fieldOf("stacks").forGetter(AEInputConfigCopyData::stacks),
            Codec.BYTE.fieldOf("ghost_circuit").forGetter(AEInputConfigCopyData::ghostCircuit),
            Codec.BOOL.fieldOf("distinct_buses").forGetter(AEInputConfigCopyData::distinctBuses),
            Codec.BOOL.fieldOf("auto_pull").forGetter(AEInputConfigCopyData::autoPull))
            .apply(instance, AEInputConfigCopyData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, AEInputConfigCopyData> STREAM_CODEC = StreamCodec
            .composite(
                    NULLABLE_STACK_STREAM_CODEC.apply(ByteBufCodecs.list()), AEInputConfigCopyData::stacks,
                    ByteBufCodecs.BYTE, AEInputConfigCopyData::ghostCircuit,
                    ByteBufCodecs.BOOL, AEInputConfigCopyData::distinctBuses,
                    ByteBufCodecs.BOOL, AEInputConfigCopyData::autoPull,
                    AEInputConfigCopyData::new);

    public AEInputConfigCopyData {
        stacks = Collections.unmodifiableList(new ArrayList<>(stacks));
    }
}

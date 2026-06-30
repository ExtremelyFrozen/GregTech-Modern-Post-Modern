package com.gregtechceu.gtceu.common.data.datacomponents;

import com.gregtechceu.gtceu.api.data.medicalcondition.MedicalCondition;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record MedicalConditionTrackerData(List<Entry> medicalConditions, List<MedicalCondition> permanentConditions) {

    public static final MedicalConditionTrackerData EMPTY = new MedicalConditionTrackerData(List.of(), List.of());
    public static final Codec<MedicalConditionTrackerData> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Entry.CODEC.listOf().fieldOf("medical_conditions")
                            .forGetter(MedicalConditionTrackerData::medicalConditions),
                    MedicalCondition.CODEC.listOf().fieldOf("permanent_conditions")
                            .forGetter(MedicalConditionTrackerData::permanentConditions))
            .apply(instance, MedicalConditionTrackerData::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, MedicalConditionTrackerData> STREAM_CODEC = StreamCodec
            .composite(
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), MedicalConditionTrackerData::medicalConditions,
                    MedicalConditionStreamCodec.INSTANCE.apply(ByteBufCodecs.list()),
                    MedicalConditionTrackerData::permanentConditions,
                    MedicalConditionTrackerData::new);

    public MedicalConditionTrackerData {
        medicalConditions = List.copyOf(medicalConditions);
        permanentConditions = List.copyOf(permanentConditions);
    }

    public boolean isEmpty() {
        return medicalConditions.isEmpty() && permanentConditions.isEmpty();
    }

    public record Entry(MedicalCondition condition, float progression) {

        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                MedicalCondition.CODEC.fieldOf("condition").forGetter(Entry::condition),
                Codec.FLOAT.fieldOf("progression").forGetter(Entry::progression))
                .apply(instance, Entry::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                MedicalConditionStreamCodec.INSTANCE, Entry::condition,
                ByteBufCodecs.FLOAT, Entry::progression,
                Entry::new);
    }

    private enum MedicalConditionStreamCodec implements StreamCodec<RegistryFriendlyByteBuf, MedicalCondition> {

        INSTANCE;

        @Override
        public MedicalCondition decode(RegistryFriendlyByteBuf buffer) {
            MedicalCondition condition = MedicalCondition.CONDITIONS.get(buffer.readUtf());
            if (condition == null) {
                throw new IllegalArgumentException("Unknown medical condition in network payload");
            }
            return condition;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, MedicalCondition value) {
            buffer.writeUtf(value.getName());
        }
    }
}

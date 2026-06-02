package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.multiblock.MultiblockState;
import com.gregtechceu.gtceu.api.multiblock.predicates.SimplePredicate;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.level.block.Block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.*;

public record RestrictedPredicate(StructurePredicate predicate, Optional<Integer> minCount,
                                  Optional<Integer> maxCount, Optional<Integer> minCountByLayer,
                                  Optional<Integer> maxCountByLayer, Optional<Integer> previewCount,
                                  Optional<List<Component>> tooltips)
        implements StructurePredicate {

    public static final MapCodec<RestrictedPredicate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(
                    StructurePredicate.CODEC.fieldOf("predicate").forGetter(RestrictedPredicate::predicate),
                    Codec.INT.optionalFieldOf("minCount").forGetter(RestrictedPredicate::minCount),
                    Codec.INT.optionalFieldOf("maxCount").forGetter(RestrictedPredicate::maxCount),
                    Codec.INT.optionalFieldOf("minCountByLayer").forGetter(RestrictedPredicate::minCountByLayer),
                    Codec.INT.optionalFieldOf("maxCountByLayer").forGetter(RestrictedPredicate::maxCountByLayer),
                    Codec.INT.optionalFieldOf("previewCount").forGetter(RestrictedPredicate::previewCount),
                    ComponentSerialization.CODEC.listOf().optionalFieldOf("tooltips")
                            .forGetter(RestrictedPredicate::tooltips))
            .apply(instance, RestrictedPredicate::new));

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.RESTRICTED;
    }

    @SuppressWarnings("deprecation")
    @Override
    @Deprecated
    public SimplePredicate asLegacy() {
        SimplePredicate legacy = predicate.asLegacy();
        this.minCount.ifPresent(value -> legacy.minCount = value);
        this.maxCount.ifPresent(value -> legacy.maxCount = value);
        this.minCountByLayer.ifPresent(value -> legacy.minLayerCount = value);
        this.maxCountByLayer.ifPresent(value -> legacy.maxLayerCount = value);
        this.previewCount.ifPresent(value -> legacy.previewCount = value);
        return legacy;
    }

    @Override
    public List<BlockInfo> candidates() {
        return predicate.candidates();
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        return predicate.blockCandidates();
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        return predicate.test(multiblockState, mutateCount) &&
                testGlobal(multiblockState, mutateCount) &&
                testLayer(multiblockState, mutateCount);
    }

    private boolean testGlobal(MultiblockState multiblockState, boolean mutateCount) {
        // if (minCount.isEmpty() && maxCount.isEmpty()) return true;
        // boolean base = predicate.test(multiblockState, mutateCount);
        // Object2IntOpenHashMap<SimplePredicate> globalCount = multiblockState.getGlobalCount();
        // int count = mutateCount ? globalCount.mergeInt(this, base ? 1 : 0, Integer::sum) : globalCount.getInt(this);
        // if (maxCount.isEmpty() || count <= maxCount.get()) return base;
        // multiblockState.setError(new SinglePredicateError(this, 0));
        // return false;
        return true; // FIXME
    }

    private boolean testLayer(MultiblockState multiblockState, boolean mutateCount) {
        // if (minCountByLayer.isEmpty() && maxCountByLayer.isEmpty()) return true;
        // boolean base = predicate.test(multiblockState, mutateCount);
        // Object2IntOpenHashMap<SimplePredicate> layerCount = multiblockState.getLayerCount();
        // int count = mutateCount ? layerCount.mergeInt(this, base ? 1 : 0, Integer::sum) : layerCount.getInt(this);
        // if (maxCountByLayer.isEmpty() || count <= maxCountByLayer.get()) return base;
        // multiblockState.setError(new SinglePredicateError(this, 2));
        // return false;
        return true; // FIXME
    }

    @Override
    public boolean hasAir() {
        return predicate.hasAir();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Getter
    @Setter
    @Accessors(fluent = true)
    @ToString
    public static class Builder {

        private StructurePredicate base;
        private @Nullable Integer minCount = null, maxCount = null, minCountByLayer = null, maxCountByLayer = null,
                previewCount = null;
        private @Nullable List<Component> tooltips = null;

        public Builder exactCount(int count) {
            return minCount(count).maxCount(count);
        }

        public Builder exactCountByLayer(int count) {
            return minCountByLayer(count).maxCountByLayer(count);
        }

        public RestrictedPredicate build() {
            return new RestrictedPredicate(Objects.requireNonNull(base), Optional.ofNullable(minCount),
                    Optional.ofNullable(maxCount), Optional.ofNullable(minCountByLayer),
                    Optional.ofNullable(maxCountByLayer), Optional.ofNullable(previewCount),
                    Optional.ofNullable(tooltips));
        }
    }
}

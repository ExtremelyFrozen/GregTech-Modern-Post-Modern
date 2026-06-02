package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.multiblock.MultiblockState;
import com.gregtechceu.gtceu.api.multiblock.predicates.SimplePredicate;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public record ConcatenatedPredicate(List<StructurePredicate> predicates) implements StructurePredicate {

    /* using listOf() instead of oneOrMore(), because it makes no sense to allow concat predicate with single element */
    public static final MapCodec<ConcatenatedPredicate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(StructurePredicate.CODEC.listOf().fieldOf("predicates").forGetter(ConcatenatedPredicate::predicates))
            .apply(instance, ConcatenatedPredicate::new));

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.CONCATENATED;
    }

    @SuppressWarnings("deprecation")
    @Override
    public SimplePredicate asLegacy() {
        return new SimplePredicate(this::testLegacy, () -> candidates().toArray(BlockInfo[]::new));
    }

    private boolean testLegacy(MultiblockState multiblockState) {
        return test(multiblockState, true);
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        return predicates.stream().anyMatch(p -> p.test(multiblockState, mutateCount));
    }

    @Override
    public @Unmodifiable List<BlockInfo> candidates() {
        return predicates.stream().flatMap(p -> p.candidates().stream()).toList();
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        return predicates.stream().flatMap(p -> p.blockCandidates().stream()).toList();
    }

    @Override
    public boolean hasAir() {
        return predicates.stream().anyMatch(StructurePredicate::hasAir);
    }

    static ConcatenatedPredicate concat(StructurePredicate a, StructurePredicate b) {
        ImmutableList.Builder<StructurePredicate> builder = ImmutableList.builder();
        if (a instanceof ConcatenatedPredicate(List<StructurePredicate> predicates)) {
            builder.addAll(predicates);
        } else {
            builder.add(a);
        }
        if (b instanceof ConcatenatedPredicate(List<StructurePredicate> predicates)) {
            builder.addAll(predicates);
        } else {
            builder.add(b);
        }
        return new ConcatenatedPredicate(builder.build());
    }
}

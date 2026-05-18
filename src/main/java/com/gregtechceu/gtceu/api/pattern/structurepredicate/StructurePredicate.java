package com.gregtechceu.gtceu.api.pattern.structurepredicate;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.predicates.SimplePredicate;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import com.mojang.serialization.Codec;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public interface StructurePredicate {

    Codec<StructurePredicate> CODEC = GTRegistries.STRUCTURE_PREDICATE_TYPES.byNameCodec()
            .dispatch("type", StructurePredicate::type, StructurePredicateType::codec);

    StructurePredicateType<?> type();

    @Contract(" -> new")
    @Deprecated // TODO: remove when this new design fully replaces the old one
    default SimplePredicate asLegacy() {
        throw new UnsupportedOperationException();
    }

    @Unmodifiable
    default List<BlockInfo> candidates() {
        return List.of();
    }

    boolean test(MultiblockState multiblockState, boolean mutateCount);

    default ConcatenatedPredicate or(StructurePredicate other) {
        return ConcatenatedPredicate.concat(this, other);
    }

    default boolean isAny() {
        return this instanceof AnyPredicate;
    }

    default boolean isAir() {
        return this instanceof AirPredicate;
    }

    default boolean hasAir() {
        return false;
    }
}

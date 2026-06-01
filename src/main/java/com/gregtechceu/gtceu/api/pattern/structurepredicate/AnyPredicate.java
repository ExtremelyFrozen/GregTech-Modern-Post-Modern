package com.gregtechceu.gtceu.api.pattern.structurepredicate;

import com.gregtechceu.gtceu.api.multiblock.MultiblockState;
import com.gregtechceu.gtceu.api.multiblock.predicates.SimplePredicate;

import com.mojang.serialization.MapCodec;

public enum AnyPredicate implements StructurePredicate {

    INSTANCE;

    public static final MapCodec<AnyPredicate> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.ANY;
    }

    @Override
    public SimplePredicate asLegacy() {
        return SimplePredicate.ANY;
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        return true;
    }
}

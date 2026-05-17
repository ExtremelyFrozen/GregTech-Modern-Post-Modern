package com.gregtechceu.gtceu.api.pattern.structurepredicate;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.mojang.serialization.MapCodec;

public enum AnyPredicate implements StructurePredicate {

    INSTANCE;

    public static final MapCodec<AnyPredicate> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.ANY;
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        return true;
    }
}

package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.multiblock.MultiblockState;
import com.gregtechceu.gtceu.api.multiblock.predicates.SimplePredicate;

import com.mojang.serialization.MapCodec;

public enum AirPredicate implements StructurePredicate {

    INSTANCE;

    public static final MapCodec<AirPredicate> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.AIR;
    }

    @Override
    public SimplePredicate asLegacy() {
        return SimplePredicate.AIR;
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        return multiblockState.getBlockState().isAir();
    }

    @Override
    public boolean hasAir() {
        return true;
    }
}

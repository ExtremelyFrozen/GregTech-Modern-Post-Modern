package com.gregtechceu.gtceu.api.pattern.structurepredicate;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import com.mojang.serialization.MapCodec;

public record StructurePredicateType<T extends StructurePredicate>(MapCodec<T> codec) {

    public static final StructurePredicateType<BlockPredicate> BLOCKS = register("blocks",
            new StructurePredicateType<>(BlockPredicate.CODEC));
    public static final StructurePredicateType<BlockStatePredicate> BLOCK_STATES = register("block_states",
            new StructurePredicateType<>(BlockStatePredicate.CODEC));
    public static final StructurePredicateType<BlockTagPredicate> BLOCK_TAGS = register("block_tags",
            new StructurePredicateType<>(BlockTagPredicate.CODEC));
    public static final StructurePredicateType<FluidPredicate> FLUIDS = register("fluids",
            new StructurePredicateType<>(FluidPredicate.CODEC));
    public static final StructurePredicateType<FluidTagPredicate> FLUID_TAGS = register("fluid_tags",
            new StructurePredicateType<>(FluidTagPredicate.CODEC));

    public static final StructurePredicateType<RestrictedPredicate> RESTRICTED = register("restricted",
            new StructurePredicateType<>(RestrictedPredicate.CODEC));
    public static final StructurePredicateType<ConcatenatedPredicate> CONCATENATED = register("concatenated",
            new StructurePredicateType<>(ConcatenatedPredicate.CODEC));

    public static final StructurePredicateType<AnyPredicate> ANY = register("any",
            new StructurePredicateType<>(AnyPredicate.CODEC));
    public static final StructurePredicateType<AirPredicate> AIR = register("air",
            new StructurePredicateType<>(AirPredicate.CODEC));

    private static <T extends StructurePredicate> StructurePredicateType<T> register(String id,
                                                                                     StructurePredicateType<T> type) {
        return GTRegistries.register(GTRegistries.STRUCTURE_PREDICATE_TYPES, GTCEu.id(id), type);
    }

    public static void init() {}
}

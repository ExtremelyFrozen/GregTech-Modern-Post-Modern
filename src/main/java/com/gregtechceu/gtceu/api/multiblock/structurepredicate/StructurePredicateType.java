package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.resources.ResourceLocation;

import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

public record StructurePredicateType<T extends StructurePredicate>(MapCodec<T> codec) {

    private static final Map<ResourceLocation, StructurePredicateType<?>> EARLY_TYPES = new LinkedHashMap<>();

    public static final StructurePredicateType<BlockPredicate> BLOCKS = registerBuiltin("blocks")
            .codec(BlockPredicate.CODEC);
    public static final StructurePredicateType<BlockStatePredicate> BLOCK_STATES = registerBuiltin("block_states")
            .codec(BlockStatePredicate.CODEC);
    public static final StructurePredicateType<BlockTagPredicate> BLOCK_TAGS = registerBuiltin("block_tags")
            .codec(BlockTagPredicate.CODEC);
    public static final StructurePredicateType<FluidPredicate> FLUIDS = registerBuiltin("fluids")
            .codec(FluidPredicate.CODEC);
    public static final StructurePredicateType<FluidTagPredicate> FLUID_TAGS = registerBuiltin("fluid_tags")
            .codec(FluidTagPredicate.CODEC);
    public static final StructurePredicateType<AbilityPredicate> ABILITIES = registerBuiltin("abilities")
            .codec(AbilityPredicate.CODEC);
    public static final StructurePredicateType<HeatingCoilPredicate> HEATING_COILS = registerBuiltin("heating_coils")
            .codec(HeatingCoilPredicate.CODEC);
    public static final StructurePredicateType<CleanroomFilterPredicate> CLEANROOM_FILTERS = registerBuiltin(
            "cleanroom_filters")
            .codec(CleanroomFilterPredicate.CODEC);

    public static final StructurePredicateType<RestrictedPredicate> RESTRICTED = registerBuiltin("restricted")
            .codec(RestrictedPredicate.CODEC);
    public static final StructurePredicateType<ConcatenatedPredicate> CONCATENATED = registerBuiltin("concatenated")
            .codec(ConcatenatedPredicate.CODEC);

    public static final StructurePredicateType<AnyPredicate> ANY = registerBuiltin("any").codec(AnyPredicate.CODEC);
    public static final StructurePredicateType<AirPredicate> AIR = registerBuiltin("air").codec(AirPredicate.CODEC);

    private static Builder registerBuiltin(String id) {
        return new Builder(GTCEu.id(id));
    }

    public static <T extends StructurePredicate> StructurePredicateType<T> register(ResourceLocation id,
                                                                                    MapCodec<T> codec) {
        StructurePredicateType<T> type = new StructurePredicateType<>(codec);
        StructurePredicateType<?> previous = EARLY_TYPES.putIfAbsent(id, type);
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate structure predicate type: " + id);
        }
        return GTRegistries.register(GTRegistries.STRUCTURE_PREDICATE_TYPES, id, type);
    }

    private record Builder(ResourceLocation id) {

        private <T extends StructurePredicate> StructurePredicateType<T> codec(MapCodec<T> codec) {
            return register(id, codec);
        }
    }

    public static StructurePredicateType<?> byId(ResourceLocation id) {
        return EARLY_TYPES.get(id);
    }

    public static @Nullable ResourceLocation id(StructurePredicateType<?> type) {
        for (Map.Entry<ResourceLocation, StructurePredicateType<?>> entry : EARLY_TYPES.entrySet()) {
            if (entry.getValue() == type) {
                return entry.getKey();
            }
        }
        return null;
    }

    public static MapCodec<? extends StructurePredicate> dispatchCodec(StructurePredicateType<?> type) {
        return type.codec();
    }

    public static void init() {}
}

package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.multiblock.MultiblockState;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

public interface StructurePredicate {

    Codec<StructurePredicate> CODEC = typeCodec()
            .dispatch("type", StructurePredicate::type, StructurePredicate::dispatchCodec);

    StructurePredicateType<?> type();

    private static Codec<StructurePredicateType<?>> typeCodec() {
        return ResourceLocation.CODEC.comapFlatMap(
                id -> {
                    StructurePredicateType<?> type = StructurePredicateType.byId(id);
                    return type == null ?
                            DataResult.error(() -> "Unknown structure predicate type: " + id) :
                            DataResult.success(type);
                },
                type -> Objects.requireNonNull(StructurePredicateType.id(type),
                        "Unregistered structure predicate type"));
    }

    private static MapCodec<? extends StructurePredicate> dispatchCodec(StructurePredicateType<?> type) {
        return type.codec();
    }

    @Unmodifiable
    default List<BlockInfo> candidates() {
        return List.of();
    }

    @Unmodifiable
    default List<Block> blockCandidates() {
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

    default boolean addCache() {
        return !isAny();
    }
}

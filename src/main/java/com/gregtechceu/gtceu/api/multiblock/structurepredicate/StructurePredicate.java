package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.multiblock.MultiblockBlockInfo;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;

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
                type -> {
                    ResourceLocation id = StructurePredicateType.id(type);
                    if (id == null) {
                        throw new IllegalStateException("Unregistered structure predicate type");
                    }
                    return id;
                });
    }

    private static MapCodec<? extends StructurePredicate> dispatchCodec(StructurePredicateType<?> type) {
        return type.codec();
    }

    @Unmodifiable
    default List<MultiblockBlockInfo> candidates() {
        return List.of();
    }

    @Unmodifiable
    default List<Block> blockCandidates() {
        return List.of();
    }

    /**
     * Expands this predicate into definition-aware choices used to build a representative preview structure.
     * Composite predicates override this method so nested count restrictions remain attached to their candidates.
     *
     * @param definition machine definition owning the pattern being previewed
     * @return ordered preview choices, or an empty list when this predicate cannot provide a representative block
     */
    @Unmodifiable
    default List<StructurePreviewChoice> previewChoices(MultiblockMachineDefinition definition) {
        List<MultiblockBlockInfo> previewCandidates = candidates();
        if (!previewCandidates.isEmpty()) {
            return List.of(StructurePreviewChoice.unrestricted(previewCandidates));
        }
        if (isAny() || isAir()) {
            return List.of(StructurePreviewChoice.unrestricted(List.of(MultiblockBlockInfo.EMPTY)));
        }
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

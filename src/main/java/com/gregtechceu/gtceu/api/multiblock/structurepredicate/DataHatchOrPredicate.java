package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.Lazy;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Serialized research data hatch predicate for JSON multiblock patterns.
 *
 * <p>
 * The {@code alternative} field stores the non-data-hatch predicate. When research is enabled, this predicate also
 * accepts exactly one data access or optical data reception hatch across all matching positions.
 */
public final class DataHatchOrPredicate implements StructurePredicate {

    /**
     * JSON codec for {@code gtpm:data_hatch_or}.
     *
     * <p>
     * The {@code alternative} member is the predicate used when research is disabled and the non-data-hatch branch when
     * research is enabled.
     */
    public static final MapCodec<DataHatchOrPredicate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(StructurePredicate.CODEC.fieldOf("alternative").forGetter(DataHatchOrPredicate::alternative))
            .apply(instance, DataHatchOrPredicate::new));

    /**
     * Predicate accepted in addition to data hatches, and used alone when research is disabled.
     */
    private final StructurePredicate alternative;

    /**
     * Exact-count data hatch predicate used when research is enabled.
     *
     * <p>
     * The instance is kept stable so {@link MultiblockState#getStructureGlobalCount()} can enforce the same exact
     * count across all cells that use this predicate.
     */
    private final Lazy<RestrictedPredicate> dataHatches;

    public DataHatchOrPredicate(StructurePredicate alternative) {
        this.alternative = Objects.requireNonNull(alternative, "alternative");
        this.dataHatches = Lazy.of(DataHatchOrPredicate::createDataHatches);
    }

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.DATA_HATCH_OR;
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        if (!ConfigHolder.INSTANCE.machines.enableResearch) {
            return alternative.test(multiblockState, mutateCount);
        }

        RestrictedPredicate dataHatchPredicate = dataHatches.get();
        if (dataHatchPredicate.test(multiblockState, mutateCount)) {
            return true;
        }

        boolean alternativeMatched = alternative.test(multiblockState, mutateCount);
        if (alternativeMatched && mutateCount &&
                !multiblockState.getStructureGlobalCount().containsKey(dataHatchPredicate)) {
            multiblockState.getStructureGlobalCount().put(dataHatchPredicate, 0);
        }
        return alternativeMatched;
    }

    @Override
    public @Unmodifiable List<BlockInfo> candidates() {
        if (!ConfigHolder.INSTANCE.machines.enableResearch) {
            return alternative.candidates();
        }
        return Stream.concat(dataHatches.get().candidates().stream(), alternative.candidates().stream())
                .toList();
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        if (!ConfigHolder.INSTANCE.machines.enableResearch) {
            return alternative.blockCandidates();
        }
        return Stream.concat(dataHatches.get().blockCandidates().stream(), alternative.blockCandidates().stream())
                .toList();
    }

    @Override
    public boolean hasAir() {
        return alternative.hasAir();
    }

    public StructurePredicate alternative() {
        return alternative;
    }

    private static RestrictedPredicate createDataHatches() {
        AbilityPredicate dataAccess = new AbilityPredicate(List.of(
                PartAbility.DATA_ACCESS,
                PartAbility.OPTICAL_DATA_RECEPTION));
        return RestrictedPredicate.builder()
                .base(dataAccess)
                .exactCount(1)
                .build();
    }
}

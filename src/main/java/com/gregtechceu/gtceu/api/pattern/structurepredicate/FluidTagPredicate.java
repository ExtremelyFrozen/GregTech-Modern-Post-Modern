package com.gregtechceu.gtceu.api.pattern.structurepredicate;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.predicates.PredicateFluidTag;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.common.util.Lazy;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.*;

import static com.gregtechceu.gtceu.api.pattern.structurepredicate.Util.oneOrMore;

public final class FluidTagPredicate implements StructurePredicate {

    public static final MapCodec<FluidTagPredicate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(oneOrMore(TagKey.codec(Registries.FLUID)).fieldOf("fluidTagKeys")
                    .forGetter(FluidTagPredicate::fluidTagKeys))
            .apply(instance, FluidTagPredicate::new));
    private final List<TagKey<Fluid>> fluidTagKeys;
    private final Lazy<List<Fluid>> candidates;

    public FluidTagPredicate(List<TagKey<Fluid>> fluidTagKeys) {
        this.fluidTagKeys = fluidTagKeys;
        this.candidates = Lazy.of(() -> BlockTagPredicate.unwrapTags(BuiltInRegistries.FLUID, fluidTagKeys).toList());
    }

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.FLUID_TAGS;
    }

    @Override
    public PredicateFluidTag asLegacy() {
        if (fluidTagKeys.size() != 1)
            throw new IllegalStateException("Too many tags for PredicateFluidTag, expected exactly 1.");
        return new PredicateFluidTag(fluidTagKeys.getFirst());
    }

    @Override
    public List<BlockInfo> candidates() {
        return candidates.get().stream().map(FluidPredicate::blockInfoFromFluid).toList();
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        return candidates.get().stream()
                .map(FluidPredicate::blockStateFromFluid)
                .map(BlockState::getBlock)
                .distinct()
                .toList();
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        return candidates.get().contains(multiblockState.getBlockState().getFluidState().getType());
    }

    public List<TagKey<Fluid>> fluidTagKeys() {
        return fluidTagKeys;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (FluidTagPredicate) obj;
        return Objects.equals(this.fluidTagKeys, that.fluidTagKeys);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fluidTagKeys);
    }

    @Override
    public String toString() {
        return "FluidTagPredicate[" +
                "fluidTagKeys=" + fluidTagKeys + ']';
    }
}

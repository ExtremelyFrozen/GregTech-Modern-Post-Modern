package com.gregtechceu.gtceu.api.pattern.structurepredicate;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.predicates.PredicateStates;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.lowdragmc.lowdraglib.utils.BlockInfo;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;

import static com.gregtechceu.gtceu.api.pattern.structurepredicate.Util.oneOrMore;

public record BlockStatePredicate(List<BlockState> blockStates) implements StructurePredicate {

    public static final MapCodec<BlockStatePredicate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(oneOrMore(BlockState.CODEC).fieldOf("blockStates").forGetter(BlockStatePredicate::blockStates))
            .apply(instance, BlockStatePredicate::new));

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.BLOCK_STATES;
    }

    @Override
    public PredicateStates asLegacy() {
        return new PredicateStates(blockStates.toArray(BlockState[]::new));
    }

    @Override
    public List<BlockInfo> candidates() {
        return blockStates.stream().map(BlockInfo::new).toList();
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        return blockStates.contains(multiblockState.getBlockState());
    }

    @Override
    public boolean hasAir() {
        return blockStates.contains(Blocks.AIR.defaultBlockState());
    }
}

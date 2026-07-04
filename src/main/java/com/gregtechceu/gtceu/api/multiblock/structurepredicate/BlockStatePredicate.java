package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.multiblock.MultiblockBlockInfo;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;

import static com.gregtechceu.gtceu.api.multiblock.structurepredicate.Util.oneOrMore;

public record BlockStatePredicate(List<BlockState> blockStates) implements StructurePredicate {

    public static final MapCodec<BlockStatePredicate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(oneOrMore(BlockState.CODEC).fieldOf("blockStates").forGetter(BlockStatePredicate::blockStates))
            .apply(instance, BlockStatePredicate::new));

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.BLOCK_STATES;
    }

    @Override
    public List<MultiblockBlockInfo> candidates() {
        return blockStates.stream().map(MultiblockBlockInfo::new).toList();
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        return blockStates.stream().map(BlockState::getBlock).distinct().toList();
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

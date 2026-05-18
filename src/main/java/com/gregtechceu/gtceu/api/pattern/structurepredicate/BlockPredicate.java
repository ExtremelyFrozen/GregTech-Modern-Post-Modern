package com.gregtechceu.gtceu.api.pattern.structurepredicate;

import com.gregtechceu.gtceu.api.pattern.MultiblockState;
import com.gregtechceu.gtceu.api.pattern.predicates.PredicateBlocks;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.*;

import static com.gregtechceu.gtceu.api.pattern.structurepredicate.Util.oneOrMore;

public record BlockPredicate(List<Block> blocks) implements StructurePredicate {

    public static final MapCodec<BlockPredicate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(oneOrMore(BuiltInRegistries.BLOCK.byNameCodec()).fieldOf("blocks").forGetter(BlockPredicate::blocks))
            .apply(instance, BlockPredicate::new));

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.BLOCKS;
    }

    @Override
    public PredicateBlocks asLegacy() {
        return new PredicateBlocks(blocks.toArray(Block[]::new));
    }

    @Override
    public List<BlockInfo> candidates() {
        return blocks.stream().map(BlockInfo::new).toList();
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        return blocks.contains(multiblockState.getBlockState().getBlock());
    }

    @Override
    public boolean hasAir() {
        return blocks.contains(Blocks.AIR);
    }
}

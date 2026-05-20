package com.gregtechceu.gtceu.api.multiblock.predicates;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PredicateBlocks extends SimplePredicate {

    public Block[] blocks;

    public PredicateBlocks(Block... blocks) {
        this.blocks = blocks;
        buildPredicate();
    }

    @Override
    public SimplePredicate buildPredicate() {
        List<Block> filteredBlocks = new ArrayList<>(blocks.length);
        for (Block block : blocks) {
            if (block != null && block != Blocks.AIR) {
                filteredBlocks.add(block);
            }
        }
        if (filteredBlocks.isEmpty()) {
            throw new IllegalArgumentException("Empty predicate: " + Arrays.toString(blocks));
        }
        blocks = filteredBlocks.toArray(new Block[0]);
        var block = blocks[0];
        if (block instanceof MetaMachineBlock) {
            blockInfo = () -> BlockInfo.fromBlock(block);
        } else {
            var info = BlockInfo.fromBlock(block);
            blockInfo = () -> info;
        }
        predicate = state -> ArrayUtils.contains(blocks, state.getBlockState().getBlock());
        candidates = () -> blocks;
        return this;
    }
}

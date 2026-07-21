package com.gregtechceu.gtceu.api.multiblock.predicates;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class PredicateBlockTag extends SimplePredicate {

    public TagKey<Block> tag;

    public PredicateBlockTag(TagKey<Block> tag) {
        this.tag = tag;
        buildPredicate();
    }

    @Override
    public SimplePredicate buildPredicate() {
        if (tag == null) {
            predicate = o -> false;
            blockInfo = () -> BlockInfo.EMPTY;
            candidates = () -> new Block[] { Blocks.AIR };
            return this;
        }
        predicate = state -> state.getBlockState().is(tag);
        var blocks = BuiltInRegistries.BLOCK.getTag(tag)
                .stream()
                .flatMap(HolderSet.Named::stream)
                .map(Holder::value)
                .toArray(Block[]::new);
        if (blocks.length == 0) blocks = new Block[] { Blocks.BARRIER };
        Block[] finalBlocks = blocks;
        candidates = () -> finalBlocks;
        var info = BlockInfo.fromBlock(blocks[0]);
        blockInfo = () -> info;
        return this;
    }
}

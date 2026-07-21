package com.gregtechceu.gtceu.api.multiblock.predicates;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;

public class PredicateFluidTag extends SimplePredicate {

    public TagKey<Fluid> tag;

    public PredicateFluidTag(TagKey<Fluid> tag) {
        this.tag = tag;
        buildPredicate();
    }

    @Override
    public SimplePredicate buildPredicate() {
        if (tag == null) {
            predicate = state -> false;
            blockInfo = () -> BlockInfo.fromBlock(Blocks.BARRIER);
            candidates = () -> new Block[] { Blocks.BARRIER };
            return this;
        }
        predicate = state -> state.getBlockState().getFluidState().is(tag);
        Block[] blocks = BuiltInRegistries.FLUID.getTag(tag)
                .stream()
                .flatMap(HolderSet.Named::stream)
                .map(Holder::value)
                .map(fluid -> fluid.defaultFluidState().createLegacyBlock().getBlock())
                .toArray(Block[]::new);
        if (blocks.length == 0) blocks = new Block[] { Blocks.BARRIER };
        Block[] finalBlocks = blocks;
        candidates = () -> finalBlocks;
        var info = BlockInfo.fromBlock(blocks[0]);
        blockInfo = () -> info;
        return this;
    }
}

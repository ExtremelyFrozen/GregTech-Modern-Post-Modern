package com.gregtechceu.gtceu.api.multiblock.predicates;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.Objects;

public class PredicateStates extends SimplePredicate {

    public BlockState[] states = new BlockState[0];

    public PredicateStates(BlockState... states) {
        this.states = states;
        buildPredicate();
    }

    @Override
    public SimplePredicate buildPredicate() {
        states = Arrays.stream(states).filter(Objects::nonNull).toArray(BlockState[]::new);
        if (states.length == 0) states = new BlockState[] { Blocks.BARRIER.defaultBlockState() };
        predicate = state -> ArrayUtils.contains(states, state.getBlockState());
        Block[] blocks = Arrays.stream(states).map(BlockState::getBlock).toArray(Block[]::new);
        candidates = () -> blocks;
        var info = BlockInfo.fromBlockState(states[0]);
        blockInfo = () -> info;
        return this;
    }
}

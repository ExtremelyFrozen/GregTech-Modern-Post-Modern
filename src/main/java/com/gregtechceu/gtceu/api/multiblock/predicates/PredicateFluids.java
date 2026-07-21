package com.gregtechceu.gtceu.api.multiblock.predicates;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Arrays;
import java.util.Objects;

public class PredicateFluids extends SimplePredicate {

    protected Fluid[] fluids = new Fluid[0];

    public PredicateFluids(Fluid... fluids) {
        this.fluids = fluids;
        buildPredicate();
    }

    @Override
    public SimplePredicate buildPredicate() {
        fluids = Arrays.stream(fluids).filter(Objects::nonNull).toArray(Fluid[]::new);
        if (fluids.length == 0) fluids = new Fluid[] { Fluids.WATER };
        predicate = state -> ArrayUtils.contains(fluids, state.getBlockState().getFluidState().getType());
        Block[] blocks = Arrays.stream(fluids)
                .map(fluid -> fluid.defaultFluidState().createLegacyBlock().getBlock())
                .toArray(Block[]::new);
        candidates = () -> blocks;
        var info = BlockInfo.fromBlockState(fluids[0].defaultFluidState().createLegacyBlock());
        blockInfo = () -> info;
        return this;
    }
}

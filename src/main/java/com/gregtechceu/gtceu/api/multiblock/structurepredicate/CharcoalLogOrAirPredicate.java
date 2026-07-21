package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.multiblock.MultiblockBlockInfo;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Matches burnable logs or air and records log positions for the charcoal pile burn conversion.
 */
public enum CharcoalLogOrAirPredicate implements StructurePredicate {

    INSTANCE;

    public static final MapCodec<CharcoalLogOrAirPredicate> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.CHARCOAL_LOG_OR_AIR;
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        boolean log = multiblockState.getBlockState().is(BlockTags.LOGS_THAT_BURN);
        if (log || multiblockState.getBlockState().isAir()) {
            if (mutateCount) {
                multiblockState.getMatchContext().getOrCreate("logPos", Long2BooleanOpenHashMap::new)
                        .put(multiblockState.getPos().asLong(), log);
            }
            return true;
        }
        return false;
    }

    @Override
    public @Unmodifiable List<MultiblockBlockInfo> candidates() {
        return blockCandidates().stream().map(MultiblockBlockInfo::fromBlock).toList();
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        return BuiltInRegistries.BLOCK.getTag(BlockTags.LOGS_THAT_BURN)
                .stream()
                .flatMap(HolderSet.Named::stream)
                .map(Holder::value)
                .toList();
    }

    @Override
    public boolean hasAir() {
        return true;
    }
}

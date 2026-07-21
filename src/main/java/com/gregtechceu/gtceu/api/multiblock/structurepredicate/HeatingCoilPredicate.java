package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;
import com.gregtechceu.gtceu.api.multiblock.error.PatternStringError;
import com.gregtechceu.gtceu.common.block.CoilBlock;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;

import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public enum HeatingCoilPredicate implements StructurePredicate {

    INSTANCE;

    public static final MapCodec<HeatingCoilPredicate> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.HEATING_COILS;
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        var blockState = multiblockState.getBlockState();
        for (Map.Entry<ICoilType, Supplier<CoilBlock>> entry : GTCEuAPI.HEATING_COILS.entrySet()) {
            if (blockState.is(entry.getValue().get())) {
                ICoilType stats = entry.getKey();
                Object currentCoil = multiblockState.getMatchContext().getOrPut("CoilType", stats);
                if (!currentCoil.equals(stats)) {
                    multiblockState.setError(new PatternStringError("gtpm.multiblock.pattern.error.coils"));
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public @Unmodifiable List<BlockInfo> candidates() {
        return GTCEuAPI.HEATING_COILS.entrySet().stream()
                .sorted(Comparator.comparingInt(value -> value.getKey().getTier()))
                .map(coil -> BlockInfo.fromBlockState(coil.getValue().get().defaultBlockState()))
                .toList();
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        return GTCEuAPI.HEATING_COILS.entrySet().stream()
                .sorted(Comparator.comparingInt(value -> value.getKey().getTier()))
                .map(coil -> (Block) coil.getValue().get())
                .toList();
    }
}

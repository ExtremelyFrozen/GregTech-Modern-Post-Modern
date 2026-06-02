package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.block.IFilterType;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;
import com.gregtechceu.gtceu.api.multiblock.error.PatternStringError;
import com.gregtechceu.gtceu.api.multiblock.predicates.SimplePredicate;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public enum CleanroomFilterPredicate implements StructurePredicate {

    INSTANCE;

    public static final MapCodec<CleanroomFilterPredicate> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.CLEANROOM_FILTERS;
    }

    @Override
    public SimplePredicate asLegacy() {
        SimplePredicate predicate = new SimplePredicate(this::testLegacy, () -> candidates().toArray(BlockInfo[]::new));
        predicate.toolTips = List.of(Component.translatable("gtpm.multiblock.pattern.error.filters"));
        return predicate;
    }

    private boolean testLegacy(MultiblockState multiblockState) {
        return test(multiblockState, true);
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        var blockState = multiblockState.getBlockState();
        for (Map.Entry<IFilterType, Supplier<Block>> entry : GTCEuAPI.CLEANROOM_FILTERS.entrySet()) {
            if (blockState.is(entry.getValue().get())) {
                IFilterType stats = entry.getKey();
                Object currentFilter = multiblockState.getMatchContext().getOrPut("FilterType", stats);
                if (!currentFilter.equals(stats)) {
                    multiblockState.setError(new PatternStringError("gtpm.multiblock.pattern.error.filters"));
                    return false;
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public @Unmodifiable List<BlockInfo> candidates() {
        return GTCEuAPI.CLEANROOM_FILTERS.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getSerializedName()))
                .map(entry -> BlockInfo.fromBlockState(entry.getValue().get().defaultBlockState()))
                .toList();
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        return GTCEuAPI.CLEANROOM_FILTERS.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().getSerializedName()))
                .map(entry -> entry.getValue().get())
                .toList();
    }
}

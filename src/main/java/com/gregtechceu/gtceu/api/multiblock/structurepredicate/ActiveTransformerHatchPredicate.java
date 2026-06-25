package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;

import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Matches all hatch abilities accepted by the active transformer structure.
 */
public enum ActiveTransformerHatchPredicate implements StructurePredicate {

    INSTANCE;

    public static final MapCodec<ActiveTransformerHatchPredicate> CODEC = MapCodec.unit(INSTANCE);

    private static final List<PartAbility> ABILITIES = List.of(
            PartAbility.INPUT_ENERGY,
            PartAbility.OUTPUT_ENERGY,
            PartAbility.SUBSTATION_INPUT_ENERGY,
            PartAbility.SUBSTATION_OUTPUT_ENERGY,
            PartAbility.INPUT_LASER,
            PartAbility.OUTPUT_LASER);

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.ACTIVE_TRANSFORMER_HATCHES;
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        Block block = multiblockState.getBlockState().getBlock();
        return ABILITIES.stream().anyMatch(ability -> ability.isApplicable(block));
    }

    @Override
    public @Unmodifiable List<BlockInfo> candidates() {
        return blockCandidates().stream().map(BlockInfo::new).toList();
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        return ABILITIES.stream()
                .flatMap(ability -> ability.getAllBlocks().stream())
                .toList();
    }
}

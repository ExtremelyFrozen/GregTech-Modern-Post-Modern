package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.MultiblockBlockInfo;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;

import net.minecraft.world.level.block.Block;

import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.stream.Stream;

/**
 * Matches all hatch abilities accepted by the active transformer structure.
 */
public enum ActiveTransformerHatchPredicate implements StructurePredicate {

    INSTANCE;

    public static final MapCodec<ActiveTransformerHatchPredicate> CODEC = MapCodec.unit(INSTANCE);

    private static final List<PartAbility> INPUT_ABILITIES = List.of(
            PartAbility.INPUT_ENERGY,
            PartAbility.SUBSTATION_INPUT_ENERGY,
            PartAbility.INPUT_LASER);
    private static final List<PartAbility> OUTPUT_ABILITIES = List.of(
            PartAbility.OUTPUT_ENERGY,
            PartAbility.SUBSTATION_OUTPUT_ENERGY,
            PartAbility.OUTPUT_LASER);
    private static final List<PartAbility> ABILITIES = Stream
            .concat(INPUT_ABILITIES.stream(), OUTPUT_ABILITIES.stream())
            .toList();
    private static final List<StructurePredicate> PREVIEW_PREDICATES = List.of(
            previewPredicate(INPUT_ABILITIES),
            previewPredicate(OUTPUT_ABILITIES));

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
    public @Unmodifiable List<MultiblockBlockInfo> candidates() {
        return blockCandidates().stream().map(MultiblockBlockInfo::new).toList();
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        return ABILITIES.stream()
                .flatMap(ability -> ability.getAllBlocks().stream())
                .toList();
    }

    @Override
    public @Unmodifiable List<StructurePreviewChoice> previewChoices(MultiblockMachineDefinition definition) {
        return PREVIEW_PREDICATES.stream()
                .flatMap(predicate -> predicate.previewChoices(definition).stream())
                .toList();
    }

    private static RestrictedPredicate previewPredicate(List<PartAbility> abilities) {
        return RestrictedPredicate.builder()
                .base(new AbilityPredicate(abilities))
                .previewCount(1)
                .build();
    }
}

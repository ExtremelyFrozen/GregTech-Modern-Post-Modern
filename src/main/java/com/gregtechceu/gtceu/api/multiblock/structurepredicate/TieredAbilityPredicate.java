package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.List;

import static com.gregtechceu.gtceu.api.multiblock.structurepredicate.Util.oneOrMore;

/**
 * Matches blocks for one {@link PartAbility} constrained to explicit voltage tiers.
 *
 * <p>
 * JSON structure predicates need this when an old Java pattern used
 * {@code Predicates.ability(ability, tiers...)} instead of all registered ability blocks.
 */
public record TieredAbilityPredicate(PartAbility ability, List<Integer> tiers) implements StructurePredicate {

    private static final Codec<PartAbility> ABILITY_CODEC = Codec.STRING.comapFlatMap(
            name -> PartAbility.byName(name)
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown part ability: " + name)),
            PartAbility::getName);

    public static final MapCodec<TieredAbilityPredicate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(
                    ABILITY_CODEC.fieldOf("ability").forGetter(TieredAbilityPredicate::ability),
                    oneOrMore(Codec.INT).fieldOf("tiers").forGetter(TieredAbilityPredicate::tiers))
            .apply(instance, TieredAbilityPredicate::new));

    public TieredAbilityPredicate {
        tiers = List.copyOf(tiers);
        if (tiers.isEmpty()) {
            throw new IllegalArgumentException("Tiered ability predicate requires at least one tier");
        }
    }

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.TIERED_ABILITY;
    }

    @Override
    public @Unmodifiable List<BlockInfo> candidates() {
        return blockCandidates().stream().map(BlockInfo::new).toList();
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        int[] tierArray = tiers.stream().mapToInt(Integer::intValue).toArray();
        return List.copyOf(ability.getBlocks(tierArray));
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        return blockCandidates().contains(multiblockState.getBlockState().getBlock());
    }

    @Override
    public String toString() {
        return "TieredAbilityPredicate[ability=" + ability.getName() + ", tiers=" + Arrays.toString(
                tiers.stream().mapToInt(Integer::intValue).toArray()) + ']';
    }
}

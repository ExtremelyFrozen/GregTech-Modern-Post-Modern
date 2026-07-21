package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON predicate for the maintenance, muffler and parallel-hatch branch of legacy {@code autoAbilities(...)}.
 */
public record AutoMaintenancePredicate(boolean checkMaintenance, boolean checkMuffler, boolean checkParallel)
        implements StructurePredicate {

    private static final StructurePredicate MUFFLER = RestrictedPredicate.builder()
            .base(new AbilityPredicate(List.of(PartAbility.MUFFLER)))
            .exactCount(1)
            .build();
    private static final StructurePredicate PARALLEL = RestrictedPredicate.builder()
            .base(new AbilityPredicate(List.of(PartAbility.PARALLEL_HATCH)))
            .maxCount(1)
            .previewCount(1)
            .build();

    public static final MapCodec<AutoMaintenancePredicate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(
                    Codec.BOOL.optionalFieldOf("checkMaintenance", true)
                            .forGetter(AutoMaintenancePredicate::checkMaintenance),
                    Codec.BOOL.optionalFieldOf("checkMuffler", false).forGetter(AutoMaintenancePredicate::checkMuffler),
                    Codec.BOOL.optionalFieldOf("checkParallel", true)
                            .forGetter(AutoMaintenancePredicate::checkParallel))
            .apply(instance, AutoMaintenancePredicate::new));

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.AUTO_MAINTENANCE;
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        boolean matched = false;
        for (StructurePredicate predicate : predicates()) {
            matched |= predicate.test(multiblockState, mutateCount);
        }
        return matched;
    }

    @Override
    public @Unmodifiable List<BlockInfo> candidates() {
        return predicates().stream().flatMap(predicate -> predicate.candidates().stream()).toList();
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        return predicates().stream().flatMap(predicate -> predicate.blockCandidates().stream()).toList();
    }

    private List<StructurePredicate> predicates() {
        List<StructurePredicate> predicates = new ArrayList<>();
        if (checkMaintenance) {
            predicates.add(maintenancePredicate());
        }
        if (checkMuffler) {
            predicates.add(MUFFLER);
        }
        if (checkParallel) {
            predicates.add(PARALLEL);
        }
        return predicates;
    }

    private static StructurePredicate maintenancePredicate() {
        return ConfigHolder.INSTANCE.machines.enableMaintenance ? MaintenancePredicate.ENABLED :
                MaintenancePredicate.DISABLED;
    }

    private static final class MaintenancePredicate {

        private static final StructurePredicate ENABLED = RestrictedPredicate.builder()
                .base(new AbilityPredicate(List.of(PartAbility.MAINTENANCE)))
                .minCount(1)
                .maxCount(1)
                .build();
        private static final StructurePredicate DISABLED = RestrictedPredicate.builder()
                .base(new AbilityPredicate(List.of(PartAbility.MAINTENANCE)))
                .minCount(0)
                .maxCount(1)
                .build();
    }
}

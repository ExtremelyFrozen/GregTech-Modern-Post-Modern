package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.Lazy;

import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;

/**
 * Matches all blocks accepted by the central monitor body predicate.
 */
public enum CentralMonitorComponentPredicate implements StructurePredicate {

    INSTANCE;

    public static final MapCodec<CentralMonitorComponentPredicate> CODEC = MapCodec.unit(INSTANCE);
    private static final Lazy<List<StructurePredicate>> PREDICATES = Lazy
            .of(CentralMonitorComponentPredicate::createPredicates);

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.CENTRAL_MONITOR_COMPONENTS;
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

    private static List<StructurePredicate> predicates() {
        return PREDICATES.get();
    }

    private static List<StructurePredicate> createPredicates() {
        List<StructurePredicate> predicates = new ArrayList<>();
        predicates.add(RestrictedPredicate.builder()
                .base(new AbilityPredicate(List.of(PartAbility.INPUT_ENERGY)))
                .minCount(1)
                .maxCount(2)
                .previewCount(1)
                .build());
        predicates.add(RestrictedPredicate.builder()
                .base(new ConcatenatedPredicate(List.of(
                        new AbilityPredicate(List.of(PartAbility.DATA_ACCESS)),
                        new BlockPredicate(CentralMonitorComponentPredicate::batteryBuffer4Blocks),
                        new BlockPredicate(CentralMonitorComponentPredicate::batteryBuffer16Blocks))))
                .maxCount(4)
                .previewCount(1)
                .build());
        predicates.add(new BlockPredicate(CentralMonitorComponentPredicate::machineHullBlocks));
        predicates.add(new BlockPredicate(CentralMonitorComponentPredicate::monitorBlocks));
        return List.copyOf(predicates);
    }

    private static List<net.minecraft.resources.ResourceLocation> batteryBuffer4Blocks() {
        return machineBlocks(GTMachines.BATTERY_BUFFER_4);
    }

    private static List<net.minecraft.resources.ResourceLocation> batteryBuffer16Blocks() {
        return machineBlocks(GTMachines.BATTERY_BUFFER_16);
    }

    private static List<net.minecraft.resources.ResourceLocation> machineHullBlocks() {
        return machineBlocks(GTMachines.HULL);
    }

    private static List<net.minecraft.resources.ResourceLocation> monitorBlocks() {
        List<net.minecraft.resources.ResourceLocation> blocks = new ArrayList<>();
        blocks.add(GTMachines.MONITOR.getId());
        blocks.add(GTMachines.ADVANCED_MONITOR.getId());
        blocks.add(GTBlocks.CASING_ALUMINIUM_FROSTPROOF.getId());
        return List.copyOf(blocks);
    }

    private static List<net.minecraft.resources.ResourceLocation> machineBlocks(MachineDefinition[] definitions) {
        List<net.minecraft.resources.ResourceLocation> blocks = new ArrayList<>();
        for (var definition : definitions) {
            if (definition != null) {
                blocks.add(definition.getId());
            }
        }
        return List.copyOf(blocks);
    }
}

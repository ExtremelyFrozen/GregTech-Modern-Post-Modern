package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.MultiblockBlockInfo;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CleanroomMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.data.recipe.CustomTags;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.util.Lazy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;

/**
 * Serialized cleanroom boundary predicate.
 *
 * <p>
 * The old cleanroom structure built this branch in Java because pass-through hatch limits depend on the scanned
 * floor area. JSON patterns use this predicate to keep that rule in data-defined structures without reintroducing
 * Java-side symbol predicates.
 */
public record CleanroomBasePredicate(boolean allowFloorBlocks, boolean allowDoors) implements StructurePredicate {

    public static final MapCodec<CleanroomBasePredicate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(
                    Codec.BOOL.optionalFieldOf("allowFloorBlocks", false)
                            .forGetter(CleanroomBasePredicate::allowFloorBlocks),
                    Codec.BOOL.optionalFieldOf("allowDoors", false)
                            .forGetter(CleanroomBasePredicate::allowDoors))
            .apply(instance, CleanroomBasePredicate::new));

    private static final Lazy<StructurePredicate> WALL_BLOCKS = Lazy.of(
            () -> new BlockPredicate(CleanroomBasePredicate::wallBlocks));
    private static final Lazy<StructurePredicate> INPUT_ENERGY = Lazy.of(() -> RestrictedPredicate.builder()
            .base(new AbilityPredicate(List.of(PartAbility.INPUT_ENERGY)))
            .minCount(1)
            .maxCount(2)
            .build());
    private static final Lazy<StructurePredicate> MAINTENANCE_ENABLED = Lazy.of(() -> RestrictedPredicate.builder()
            .base(new BlockPredicate(CleanroomBasePredicate::maintenanceBlocks))
            .minCount(1)
            .maxCount(1)
            .build());
    private static final Lazy<StructurePredicate> MAINTENANCE_DISABLED = Lazy.of(() -> RestrictedPredicate.builder()
            .base(new BlockPredicate(CleanroomBasePredicate::maintenanceBlocks))
            .minCount(0)
            .maxCount(1)
            .build());
    private static final Lazy<StructurePredicate> FLOOR_BLOCKS = Lazy.of(
            () -> new BlockTagPredicate(List.of(CustomTags.CLEANROOM_FLOORS)));
    private static final Lazy<StructurePredicate> DOORS = Lazy.of(() -> RestrictedPredicate.builder()
            .base(CleanroomDoorPredicate.INSTANCE)
            .maxCount(8)
            .build());

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.CLEANROOM_BASE;
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        boolean matched = false;
        for (StructurePredicate predicate : predicates(multiblockState)) {
            matched |= predicate.test(multiblockState, mutateCount);
        }
        return matched;
    }

    @Override
    public @Unmodifiable List<MultiblockBlockInfo> candidates() {
        return predicates(null).stream().flatMap(predicate -> predicate.candidates().stream()).toList();
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        return predicates(null).stream().flatMap(predicate -> predicate.blockCandidates().stream()).toList();
    }

    @Override
    public @Unmodifiable List<StructurePreviewChoice> previewChoices(MultiblockMachineDefinition definition) {
        return predicates(null).stream().flatMap(predicate -> predicate.previewChoices(definition).stream()).toList();
    }

    private List<StructurePredicate> predicates(MultiblockState multiblockState) {
        List<StructurePredicate> predicates = new ArrayList<>();
        StructurePredicate wallBlocks = WALL_BLOCKS.get();
        predicates.add(allowFloorBlocks ? wallBlocks.or(FLOOR_BLOCKS.get()) : wallBlocks);
        predicates.add(INPUT_ENERGY.get());
        predicates.add(maintenancePredicate());
        if (multiblockState != null) {
            predicates.add(passthroughPredicate(multiblockState));
        }
        if (allowDoors) {
            predicates.add(DOORS.get());
        }
        return predicates;
    }

    private static StructurePredicate maintenancePredicate() {
        return ConfigHolder.INSTANCE.machines.enableMaintenance ? MAINTENANCE_ENABLED.get() :
                MAINTENANCE_DISABLED.get();
    }

    private static StructurePredicate passthroughPredicate(MultiblockState multiblockState) {
        var controller = multiblockState.getController();
        if (!(controller instanceof CleanroomMachine cleanroom)) {
            throw new IllegalStateException("Cleanroom base predicate can only be used by cleanroom structures");
        }
        return RestrictedPredicate.builder()
                .base(new AbilityPredicate(List.of(PartAbility.PASSTHROUGH_HATCH)))
                .maxCount(cleanroom.getDynamicPatternFloorArea() / 4)
                .build();
    }

    private static List<ResourceLocation> wallBlocks() {
        return List.of(
                GTBlocks.PLASTCRETE.getId(),
                GTBlocks.CLEANROOM_GLASS.getId());
    }

    private static List<ResourceLocation> maintenanceBlocks() {
        return List.of(
                GTMachines.MAINTENANCE_HATCH.getId(),
                GTMachines.AUTO_MAINTENANCE_HATCH.getId());
    }
}

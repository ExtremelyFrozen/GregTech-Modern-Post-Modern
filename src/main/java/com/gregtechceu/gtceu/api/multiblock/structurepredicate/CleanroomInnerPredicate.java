package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.SimpleGeneratorMachine;
import com.gregtechceu.gtceu.api.multiblock.MultiblockBlockInfo;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.BedrockOreMinerMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FluidDrillMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.LargeMinerMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.generator.LargeCombustionEngineMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.generator.LargeTurbineMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MufflerPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.primitive.CokeOvenMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.primitive.PrimitiveBlastFurnaceMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.primitive.PrimitivePumpMachine;
import com.gregtechceu.gtceu.common.machine.trait.CleanroomProviderTrait;
import com.gregtechceu.gtceu.common.machine.trait.CleanroomReceiverTrait;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.google.common.collect.Sets;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Set;

/**
 * Matches any cleanroom interior block while rejecting machine types that contaminate the room.
 */
public enum CleanroomInnerPredicate implements StructurePredicate {

    INSTANCE;

    public static final MapCodec<CleanroomInnerPredicate> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.CLEANROOM_INNER;
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        BlockEntity blockEntity = multiblockState.getBlockEntity();
        if (blockEntity instanceof MetaMachine machine) {
            if (isMachineBanned(machine)) {
                return false;
            }
            if (mutateCount) {
                Set<CleanroomReceiverTrait> receivers = multiblockState.getMatchContext()
                        .getOrCreate("cleanroomReceiver", Sets::newHashSet);
                machine.getTraitOptional(CleanroomReceiverTrait.TYPE).ifPresent(receivers::add);
            }
        }
        return true;
    }

    @Override
    public @Unmodifiable List<MultiblockBlockInfo> candidates() {
        return List.of();
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        return List.of();
    }

    @Override
    public boolean isAny() {
        return true;
    }

    @Override
    public boolean addCache() {
        return true;
    }

    private static boolean isMachineBanned(MetaMachine machine) {
        if (machine.getTrait(CleanroomProviderTrait.TYPE) != null) return true;
        if (machine instanceof MufflerPartMachine) return true;
        if (machine instanceof SimpleGeneratorMachine) return true;
        if (machine instanceof LargeCombustionEngineMachine) return true;
        if (machine instanceof LargeTurbineMachine) return true;
        if (machine instanceof LargeMinerMachine) return true;
        if (machine instanceof FluidDrillMachine) return true;
        if (machine instanceof BedrockOreMinerMachine) return true;
        if (machine instanceof CokeOvenMachine) return true;
        if (machine instanceof PrimitiveBlastFurnaceMachine) return true;
        return machine instanceof PrimitivePumpMachine;
    }
}

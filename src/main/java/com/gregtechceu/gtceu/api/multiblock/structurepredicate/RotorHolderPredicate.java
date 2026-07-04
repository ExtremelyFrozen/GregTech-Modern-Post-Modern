package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.MultiblockBlockInfo;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;
import com.gregtechceu.gtceu.common.machine.multiblock.part.RotorHolderPartMachine;

import net.minecraft.world.level.block.Block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Matches turbine rotor holders and checks the rotor front clearance.
 */
public record RotorHolderPredicate(int tier) implements StructurePredicate {

    public static final MapCodec<RotorHolderPredicate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Codec.INT.fieldOf("tier").forGetter(RotorHolderPredicate::tier))
            .apply(instance, RotorHolderPredicate::new));

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.ROTOR_HOLDER;
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        return MetaMachine.getMachine(multiblockState.getWorld(),
                multiblockState.getPos()) instanceof RotorHolderPartMachine rotorHolder &&
                multiblockState.getWorld()
                        .getBlockState(multiblockState.getPos().relative(rotorHolder.self().getFrontFacing()))
                        .isAir();
    }

    @Override
    public @Unmodifiable List<MultiblockBlockInfo> candidates() {
        return blockCandidates().stream().map(MultiblockBlockInfo::fromBlock).toList();
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        return List.copyOf(PartAbility.ROTOR_HOLDER.getAllBlocks());
    }
}

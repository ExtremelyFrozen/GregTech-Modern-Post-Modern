package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.multiblock.MultiblockBlockInfo;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;
import com.gregtechceu.gtceu.data.recipe.CustomTags;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * Matches cleanroom door blocks.
 */
public enum CleanroomDoorPredicate implements StructurePredicate {

    INSTANCE;

    public static final MapCodec<CleanroomDoorPredicate> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.CLEANROOM_DOORS;
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        return multiblockState.getBlockState().is(CustomTags.CLEANROOM_DOORS);
    }

    @Override
    public @Unmodifiable List<MultiblockBlockInfo> candidates() {
        return List.of(
                new MultiblockBlockInfo(Blocks.IRON_DOOR.defaultBlockState()),
                new MultiblockBlockInfo(Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER)));
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        return List.of(Blocks.IRON_DOOR);
    }
}

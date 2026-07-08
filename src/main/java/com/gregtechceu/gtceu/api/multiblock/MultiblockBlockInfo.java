package com.gregtechceu.gtceu.api.multiblock;

import com.gregtechceu.gtceu.GTCEu;

import com.lowdragmc.lowdraglib2.utils.data.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class MultiblockBlockInfo extends BlockInfo {

    public static final MultiblockBlockInfo EMPTY = new MultiblockBlockInfo(Blocks.AIR);

    @Nullable
    private BlockEntity lastEntity;

    public MultiblockBlockInfo() {}

    public MultiblockBlockInfo(Block block) {
        super(block);
    }

    public MultiblockBlockInfo(BlockState blockState) {
        super(blockState);
    }

    public MultiblockBlockInfo(BlockState blockState, boolean hasBlockEntity) {
        super(blockState, hasBlockEntity);
    }

    public MultiblockBlockInfo(BlockState blockState, Consumer<BlockEntity> postCreate) {
        super(blockState, postCreate);
    }

    public MultiblockBlockInfo(BlockState blockState, boolean hasBlockEntity, @Nullable ItemStack itemStack,
                               @Nullable Consumer<BlockEntity> postCreate) {
        super(blockState, hasBlockEntity, itemStack, postCreate);
    }

    public static MultiblockBlockInfo fromBlockState(BlockState state) {
        try {
            if (state.getBlock() instanceof EntityBlock entityBlock &&
                    entityBlock.newBlockEntity(BlockPos.ZERO, state) != null) {
                return new MultiblockBlockInfo(state, true);
            }
        } catch (RuntimeException exception) {
            GTCEu.LOGGER.warn("Failed to create preview block entity for {}", state, exception);
        }
        return new MultiblockBlockInfo(state);
    }

    public static MultiblockBlockInfo fromBlock(Block block) {
        return fromBlockState(block.defaultBlockState());
    }

    @Nullable
    public BlockEntity getBlockEntity(BlockPos pos, HolderLookup.Provider provider) {
        if (hasBlockEntity() && getBlockState().getBlock() instanceof EntityBlock entityBlock) {
            if (lastEntity != null && lastEntity.getBlockPos().equals(pos)) {
                return lastEntity;
            }
            lastEntity = entityBlock.newBlockEntity(pos, getBlockState());
            postEntity(lastEntity);
            return lastEntity;
        }
        return null;
    }

    @Nullable
    public BlockEntity getBlockEntity(HolderLookup.Provider provider, Level level, BlockPos pos) {
        BlockEntity entity = getBlockEntity(pos, provider);
        if (entity != null) {
            entity.setLevel(level);
        }
        return entity;
    }

    public void apply(HolderLookup.Provider provider, Level level, BlockPos pos) {
        level.setBlockAndUpdate(pos, getBlockState());
        BlockEntity blockEntity = getBlockEntity(pos, provider);
        if (blockEntity != null) {
            level.setBlockEntity(blockEntity);
        }
    }

    public void clearBlockEntityCache() {
        lastEntity = null;
    }
}

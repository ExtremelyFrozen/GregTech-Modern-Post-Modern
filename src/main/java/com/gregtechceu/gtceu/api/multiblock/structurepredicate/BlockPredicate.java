package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.api.multiblock.MultiblockState;

import com.lowdragmc.lowdraglib.utils.BlockInfo;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.Lazy;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

import static com.gregtechceu.gtceu.api.multiblock.structurepredicate.Util.oneOrMore;

public final class BlockPredicate implements StructurePredicate {

    public static final MapCodec<BlockPredicate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(oneOrMore(ResourceLocation.CODEC).fieldOf("blocks").forGetter(BlockPredicate::blockIds))
            .apply(instance, BlockPredicate::new));

    private final Lazy<List<ResourceLocation>> blockIds;
    private final Lazy<List<Block>> blocks;

    public BlockPredicate(List<ResourceLocation> blockIds) {
        this(constantBlockIds(blockIds));
    }

    public BlockPredicate(Supplier<List<ResourceLocation>> blockIds) {
        this.blockIds = Lazy.of(() -> List.copyOf(Objects.requireNonNull(blockIds.get(),
                "Structure block predicate id supplier returned null")));
        this.blocks = Lazy.of(() -> blockIds().stream()
                .map(BlockPredicate::resolveBlock)
                .toList());
    }

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.BLOCKS;
    }

    @Override
    public List<BlockInfo> candidates() {
        return blocks().stream().map(BlockInfo::new).toList();
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        return blocks();
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        return blocks().contains(multiblockState.getBlockState().getBlock());
    }

    private static Block resolveBlock(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == null || !id.equals(BuiltInRegistries.BLOCK.getKey(block))) {
            throw new IllegalStateException("Unknown block id in structure block predicate: " + id);
        }
        return block;
    }

    private static Supplier<List<ResourceLocation>> constantBlockIds(List<ResourceLocation> blockIds) {
        List<ResourceLocation> copy = List.copyOf(Objects.requireNonNull(blockIds,
                "Structure block predicate id list cannot be null"));
        return () -> copy;
    }

    @Override
    public boolean hasAir() {
        return blocks().contains(Blocks.AIR);
    }

    public List<ResourceLocation> blockIds() {
        return blockIds.get();
    }

    public List<Block> blocks() {
        return blocks.get();
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (BlockPredicate) obj;
        return Objects.equals(this.blockIds(), that.blockIds());
    }

    @Override
    public int hashCode() {
        return Objects.hash(blockIds());
    }

    @Override
    public String toString() {
        return "BlockPredicate[" +
                "blockIds=" + blockIds() + ']';
    }
}

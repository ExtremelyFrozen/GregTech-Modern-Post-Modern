package com.gregtechceu.gtceu.api.multiblock.structurepredicate;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.multiblock.MultiblockBlockInfo;
import com.gregtechceu.gtceu.api.multiblock.MultiblockState;
import com.gregtechceu.gtceu.api.pipenet.IPipeNode;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTMaterialBlocks;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.Lazy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

import static com.gregtechceu.gtceu.api.multiblock.structurepredicate.Util.oneOrMore;

/**
 * Serialized frame predicate for JSON multiblock patterns.
 *
 * <p>
 * This predicate exists to express {@code Predicates.frames(...)} outside Java pattern declarations. It accepts
 * normal {@link TagPrefix#frameGt} material blocks and framed {@link IPipeNode} blocks whose frame material is listed
 * in the {@code materials} field.
 */
public final class FramePredicate implements StructurePredicate {

    /**
     * Material id codec used by the {@code materials} JSON field.
     *
     * <p>
     * The decoder follows the project id rules so bare names such as {@code "steel"} resolve to the GTPM namespace.
     * The predicate stores ids instead of material instances because JSON patterns may be decoded before materials are
     * registered.
     */
    private static final Codec<ResourceLocation> MATERIAL_ID_CODEC = GTCEu.GTCEU_ID;

    /**
     * JSON codec for {@code gtpm:frames}.
     *
     * <p>
     * The {@code materials} member accepts either one material string or an array of material strings.
     */
    public static final MapCodec<FramePredicate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(oneOrMore(MATERIAL_ID_CODEC).fieldOf("materials").forGetter(FramePredicate::materialIds))
            .apply(instance, FramePredicate::new));

    /**
     * Material ids accepted by this predicate for both frame blocks and framed pipe nodes.
     */
    private final List<ResourceLocation> materialIds;

    /**
     * Lazily resolved materials accepted by this predicate.
     *
     * <p>
     * Resolution fails fast if any configured material id is unknown or resolves to the null material.
     */
    private final Lazy<List<Material>> materials;

    /**
     * Lazily resolved {@link TagPrefix#frameGt} block candidates for {@link #materials}.
     *
     * <p>
     * Resolution is delayed until the block table is ready, then fails fast if a configured material has no bound
     * frame block.
     */
    private final Lazy<List<Block>> frameBlocks;

    public FramePredicate(List<ResourceLocation> materialIds) {
        if (materialIds.isEmpty()) {
            throw new IllegalArgumentException("Frame predicate requires at least one material");
        }
        this.materialIds = List.copyOf(materialIds);
        this.materials = Lazy.of(this::resolveMaterials);
        this.frameBlocks = Lazy.of(this::resolveFrameBlocks);
    }

    @Override
    public StructurePredicateType<?> type() {
        return StructurePredicateType.FRAMES;
    }

    @Override
    public boolean test(MultiblockState multiblockState, boolean mutateCount) {
        Block block = multiblockState.getBlockState().getBlock();
        if (frameBlocks().contains(block)) {
            return true;
        }

        BlockEntity blockEntity = multiblockState.getBlockEntity();
        return blockEntity instanceof IPipeNode<?, ?> pipeNode &&
                materials().contains(pipeNode.getFrameMaterial());
    }

    @Override
    public @Unmodifiable List<MultiblockBlockInfo> candidates() {
        return frameBlocks().stream()
                .map(MultiblockBlockInfo::fromBlock)
                .toList();
    }

    @Override
    public @Unmodifiable List<Block> blockCandidates() {
        return frameBlocks();
    }

    public List<ResourceLocation> materialIds() {
        return materialIds;
    }

    public List<Material> materials() {
        return materials.get();
    }

    public List<Block> frameBlocks() {
        return frameBlocks.get();
    }

    private List<Material> resolveMaterials() {
        return materialIds.stream()
                .map(this::resolveMaterial)
                .toList();
    }

    private Material resolveMaterial(ResourceLocation materialId) {
        Material material = GTRegistries.MATERIALS.getMaterial(materialId);
        if (material.isNull()) {
            throw new IllegalStateException("Unknown frame material: " + materialId);
        }
        return material;
    }

    private List<Block> resolveFrameBlocks() {
        return materials().stream()
                .map(this::resolveFrameBlock)
                .toList();
    }

    private Block resolveFrameBlock(Material material) {
        var entry = GTMaterialBlocks.MATERIAL_BLOCKS.get(TagPrefix.frameGt, material);
        if (entry == null) {
            throw new IllegalStateException("No frameGt block registered for material " +
                    material.getResourceLocation());
        }
        if (!entry.isBound()) {
            throw new IllegalStateException("FrameGt block is not bound for material " +
                    material.getResourceLocation());
        }
        return entry.get();
    }
}

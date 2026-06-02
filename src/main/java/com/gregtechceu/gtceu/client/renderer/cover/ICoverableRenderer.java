package com.gregtechceu.gtceu.client.renderer.cover;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.client.model.GTModelProperties;
import com.gregtechceu.gtceu.client.model.quad.StaticFaceBakery;
import com.gregtechceu.gtceu.client.util.RenderUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public interface ICoverableRenderer {

    @OnlyIn(Dist.CLIENT)
    TextureAtlasSprite[] COVER_BACK_PLATE = new TextureAtlasSprite[1];
    double THIN_OFFSET = 0.002;
    double LESS_THIN_OFFSET = 0.005;

    @OnlyIn(Dist.CLIENT)
    static void initSprites(TextureAtlas atlas) {
        COVER_BACK_PLATE[0] = atlas.getSprite(GTCEu.id("block/material_sets/dull/wire_side"));
    }

    @OnlyIn(Dist.CLIENT)
    default void renderCovers(List<BakedQuad> quads, @NotNull ICoverable coverable,
                              BlockPos pos, BlockAndTintGetter level, @Nullable Direction side,
                              RandomSource rand, @NotNull ModelData modelData, @Nullable RenderType renderType) {
        Map<Direction, ModelData> coverModelData = modelData.get(GTModelProperties.COVER_MODEL_DATA);
        double thickness = coverable.getCoverPlateThickness();

        for (Direction face : GTUtil.DIRECTIONS) {
            var cover = coverable.getCoverAtSide(face);
            if (cover != null) {
                ICoverRenderer coverRenderer = cover.getCoverRenderer().get();

                if (thickness > 0 && cover.shouldRenderPlate()) {
                    double min = thickness + 0.01;
                    double max = 0.99 - thickness;
                    var normal = face.getNormal();
                    var cube = new AABB(
                            normal.getX() > 0 ? max : LESS_THIN_OFFSET,
                            normal.getY() > 0 ? max : LESS_THIN_OFFSET,
                            normal.getZ() > 0 ? max : LESS_THIN_OFFSET,
                            normal.getX() >= 0 ? 1.0 - LESS_THIN_OFFSET : min,
                            normal.getY() >= 0 ? 1.0 - LESS_THIN_OFFSET : min,
                            normal.getZ() >= 0 ? 1.0 - LESS_THIN_OFFSET : min);
                    if (coverRenderer.shouldRenderBackPlateForSide(cover, pos, level, side)) {
                        if (side == null) { // render back
                            quads.add(StaticFaceBakery.bakeFace(cube, face.getOpposite(), COVER_BACK_PLATE[0]));
                        } else if (side != face.getOpposite()) { // render sides
                            quads.add(StaticFaceBakery.bakeFace(cube, side, COVER_BACK_PLATE[0]));
                        }
                    }
                }
                coverRenderer.renderCover(quads, side, rand, cover, pos, level,
                        coverModelData != null ? coverModelData.getOrDefault(face, ModelData.EMPTY) : ModelData.EMPTY,
                        renderType);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    default void renderDynamicCovers(MetaMachine machine, float partialTick, PoseStack poseStack,
                                     MultiBufferSource buffer, int packedLight, int packedOverlay) {
        ICoverable coverable = machine.getCoverContainer();
        for (Direction face : GTUtil.DIRECTIONS) {
            CoverBehavior cover = coverable.getCoverAtSide(face);
            IDynamicCoverRenderer renderer = cover != null ? cover.getDynamicRenderer().get() : null;
            if (renderer != null) {
                poseStack.pushPose();
                RenderUtil.moveToFace(poseStack, .5f, .5f, .5f, face);
                RenderUtil.rotateToFace(poseStack, face, Direction.NORTH);
                poseStack.translate(-.5f, -.5f, .01f);
                renderer.render(machine, face, partialTick, poseStack, buffer, packedLight, packedOverlay);
                poseStack.popPose();
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    default ChunkRenderTypeSet getCoverRenderTypes(ICoverable coverable, BlockPos pos, BlockAndTintGetter level,
                                                   RandomSource rand, ModelData modelData) {
        Map<Direction, ModelData> coverModelData = modelData.get(GTModelProperties.COVER_MODEL_DATA);
        Set<ChunkRenderTypeSet> renderTypeSets = new HashSet<>();

        for (Direction side : GTUtil.DIRECTIONS) {
            CoverBehavior cover = coverable.getCoverAtSide(side);
            if (cover == null) continue;

            ChunkRenderTypeSet renderTypes = cover.getCoverRenderer().get()
                    .getRenderTypes(cover, pos, level, rand,
                            coverModelData != null ? coverModelData.getOrDefault(side, ModelData.EMPTY) :
                                    ModelData.EMPTY);
            renderTypeSets.add(renderTypes);
        }

        return ChunkRenderTypeSet.union(renderTypeSets);
    }
}

package com.gregtechceu.gtceu.client.model.quad;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.model.QuadTransformers;

import org.joml.Vector3f;

public class StaticFaceBakery {

    private static final FaceBakery FACE_BAKERY = new FaceBakery();
    private static final float BLOCK_MODEL_SCALE = 16.0f;
    private static final String TEXTURE_KEY = "";

    public static final AABB BLOCK = new AABB(0, 0, 0, 1, 1, 1);
    public static final AABB SLIGHTLY_OVER_BLOCK = new AABB(-0.001f, -0.001f, -0.001f,
            1.001f, 1.001f, 1.001f);
    public static final AABB OUTPUT_OVERLAY = new AABB(-.006f, -.006f, -.006f,
            1.006f, 1.006f, 1.006f);
    public static final AABB AUTO_OUTPUT_OVERLAY = new AABB(-.008f, -.008f, -.008f,
            1.008f, 1.008f, 1.008f);
    public static final AABB COVER_OVERLAY = new AABB(-.008f, -.008f, -.008f,
            1.008f, 1.008f, 1.008f);

    public static BakedQuad bakeFace(AABB cube, Direction face, TextureAtlasSprite sprite) {
        return bakeFace(cube, face, sprite, BlockModelRotation.X0_Y0, -1, 0, true, true);
    }

    public static BakedQuad bakeFace(AABB cube, Direction face, TextureAtlasSprite sprite, ModelState rotation,
                                     int tintIndex, int emissivity, boolean cull, boolean shade) {
        Vector3f from = new Vector3f(
                (float) cube.minX * BLOCK_MODEL_SCALE,
                (float) cube.minY * BLOCK_MODEL_SCALE,
                (float) cube.minZ * BLOCK_MODEL_SCALE);
        Vector3f to = new Vector3f(
                (float) cube.maxX * BLOCK_MODEL_SCALE,
                (float) cube.maxY * BLOCK_MODEL_SCALE,
                (float) cube.maxZ * BLOCK_MODEL_SCALE);
        BlockElementFace blockFace = new BlockElementFace(cull ? face : null, tintIndex, TEXTURE_KEY,
                new BlockFaceUV(new float[] { 0.0f, 0.0f, BLOCK_MODEL_SCALE, BLOCK_MODEL_SCALE }, 0));
        BakedQuad quad = FACE_BAKERY.bakeQuad(from, to, blockFace, sprite, face, rotation, null, shade);
        QuadTransformers.settingEmissivity(emissivity).processInPlace(quad);
        return quad;
    }
}

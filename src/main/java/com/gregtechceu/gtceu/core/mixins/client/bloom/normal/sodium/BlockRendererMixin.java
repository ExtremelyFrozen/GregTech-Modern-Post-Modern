package com.gregtechceu.gtceu.core.mixins.client.bloom.normal.sodium;

import com.gregtechceu.gtceu.client.bloom.BloomShaderManager;
import com.gregtechceu.gtceu.client.util.TextureMetadataHelper;
import com.gregtechceu.gtceu.integration.sodium.GTSodiumCompat;

import net.caffeinemc.mods.sodium.client.model.light.data.QuadLightData;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.builder.ChunkMeshBufferBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = BlockRenderer.class, remap = false)
public class BlockRendererMixin {

    @Shadow
    private ChunkBuildBuffers buffers;

    @Unique
    private TextureAtlasSprite gtceu$bloomSprite;

    @Unique
    private ModelQuadFacing gtceu$bloomFacing;

    @ModifyExpressionValue(method = "bufferQuad",
                           at = @At(value = "INVOKE",
                                    target = "Lnet/caffeinemc/mods/sodium/client/render/frapi/mesh/MutableQuadViewImpl;sprite(Lnet/fabricmc/fabric/api/renderer/v1/model/SpriteFinder;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
    private TextureAtlasSprite gtceu$captureBloomSprite(TextureAtlasSprite sprite) {
        this.gtceu$bloomSprite = sprite;
        return sprite;
    }

    @ModifyExpressionValue(method = "bufferQuad",
                           at = @At(value = "INVOKE",
                                    target = "Lnet/caffeinemc/mods/sodium/client/render/frapi/mesh/MutableQuadViewImpl;normalFace()Lnet/caffeinemc/mods/sodium/client/model/quad/properties/ModelQuadFacing;"))
    private ModelQuadFacing gtceu$captureBloomFacing(ModelQuadFacing facing) {
        this.gtceu$bloomFacing = facing;
        return facing;
    }

    @WrapOperation(method = "bufferQuad",
                   at = @At(value = "INVOKE",
                            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/builder/ChunkMeshBufferBuilder;push([Lnet/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexEncoder$Vertex;I)V"))
    private void gtceu$copyBloomQuad(ChunkMeshBufferBuilder instance, ChunkVertexEncoder.Vertex[] vertices, int bits,
                                     Operation<Void> original) {
        original.call(instance, vertices, bits);

        if (!BloomShaderManager.isBloomActive()) return;

        TextureAtlasSprite sprite = this.gtceu$bloomSprite;
        ModelQuadFacing facing = this.gtceu$bloomFacing;
        if (sprite == null) {
            return;
        }
        if (facing == null) return;

        QuadLightData lightData = ((AbstractBlockRenderContextAccessor) this).gtceu$getQuadLightData();
        if (!TextureMetadataHelper.hasBloom(sprite, gtceu$getPackedLights(vertices), lightData.lm)) {
            return;
        }

        ChunkModelBuilder bloomBuilder = this.buffers.get(GTSodiumCompat.BLOOM_RENDER_PASS);
        ChunkMeshBufferBuilder vertexBuffer = bloomBuilder.getVertexBuffer(facing);

        vertexBuffer.push(vertices, bits);
        bloomBuilder.addSprite(sprite);
    }

    private int[] gtceu$getPackedLights(ChunkVertexEncoder.Vertex[] vertices) {
        return new int[] {
                vertices[0].light,
                vertices[1].light,
                vertices[2].light,
                vertices[3].light
        };
    }
}

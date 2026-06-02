package com.gregtechceu.gtceu.core.mixins.iris;

import com.gregtechceu.gtceu.client.bloom.BloomShaderManager;
import com.gregtechceu.gtceu.integration.sodium.GTSodiumCompat;

import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.pipeline.programs.SodiumPrograms;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SodiumPrograms.class, remap = false)
public class SodiumProgramsMixin {

    @Unique
    private GlFramebuffer gtpm$bloomFramebuffer;

    @Unique
    private RenderTarget gtpm$bloomFramebufferTarget;

    @Unique
    private int gtpm$bloomColorAttachment;

    @Unique
    private int gtpm$bloomDepthAttachment;

    @Inject(method = "getProgram", at = @At("HEAD"), cancellable = true)
    private void gtpm$getBloomProgram(TerrainRenderPass pass,
                                      CallbackInfoReturnable<GlProgram<ChunkShaderInterface>> cir) {
        if (pass == GTSodiumCompat.BLOOM_RENDER_PASS) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "getFramebuffer", at = @At("HEAD"), cancellable = true)
    private void gtpm$getBloomFramebuffer(TerrainRenderPass pass, CallbackInfoReturnable<GlFramebuffer> cir) {
        if (pass == GTSodiumCompat.BLOOM_RENDER_PASS && BloomShaderManager.isBloomActive()) {
            cir.setReturnValue(gtpm$getOrCreateBloomFramebuffer(BloomShaderManager.BLOOM_TARGET));
        }
    }

    @Unique
    private GlFramebuffer gtpm$getOrCreateBloomFramebuffer(RenderTarget target) {
        int colorAttachment = target.getColorTextureId();
        int depthAttachment = target.getDepthTextureId();

        if (gtpm$bloomFramebuffer == null || gtpm$bloomFramebufferTarget != target ||
                gtpm$bloomColorAttachment != colorAttachment || gtpm$bloomDepthAttachment != depthAttachment) {
            if (gtpm$bloomFramebuffer != null) {
                gtpm$bloomFramebuffer.destroy();
            }

            gtpm$bloomFramebuffer = new GlFramebuffer();
            gtpm$bloomFramebuffer.addColorAttachment(0, colorAttachment);
            gtpm$bloomFramebuffer.addDepthAttachment(depthAttachment);
            gtpm$bloomFramebuffer.drawBuffers(new int[] { 0 });

            gtpm$bloomFramebufferTarget = target;
            gtpm$bloomColorAttachment = colorAttachment;
            gtpm$bloomDepthAttachment = depthAttachment;
        }

        return gtpm$bloomFramebuffer;
    }
}

package com.gregtechceu.gtceu.core.mixins.client.bloom;

import com.gregtechceu.gtceu.client.bloom.BloomRenderer;
import com.gregtechceu.gtceu.client.bloom.BloomShaderManager;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;

import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 500)
public abstract class LevelRendererMixin {

    @Inject(method = "resize", at = @At("TAIL"))
    private void gtceu$resizeBloomChain(int width, int height, CallbackInfo ci) {
        if (BloomShaderManager.BLOOM_CHAIN != null) {
            BloomShaderManager.BLOOM_CHAIN.resize(width, height);
        }
    }

    @Inject(method = "graphicsChanged", at = @At(value = "HEAD"))
    private void gtceu$reinitBloomEffect(CallbackInfo ci) {
        BloomShaderManager.initPostShaders();
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void gtpm$processBloomAfterLevel(DeltaTracker deltaTracker, boolean renderBlockOutline, Camera camera,
                                             GameRenderer gameRenderer, LightTexture lightTexture,
                                             Matrix4f modelViewMatrix, Matrix4f projectionMatrix, CallbackInfo ci) {
        BloomRenderer.processPostEffect(deltaTracker.getGameTimeDeltaPartialTick(false),
                Minecraft.getInstance().getProfiler());
    }
}

package com.gregtechceu.gtceu.core.mixins.client.bloom;

import com.gregtechceu.gtceu.client.bloom.BloomRenderer;
import com.gregtechceu.gtceu.client.bloom.BloomShaderManager;
import com.gregtechceu.gtceu.client.renderer.GTRenderTypes;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.RenderChunkRegion;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.SectionPos;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.function.Supplier;

@Mixin(SectionCompiler.class)
public abstract class RebuildTaskMixin {

    @Invoker("getOrBeginLayer")
    protected abstract BufferBuilder gtceu$getOrBeginLayer(Map<RenderType, BufferBuilder> bufferLayers,
                                                           SectionBufferBuilderPack sectionBufferBuilderPack,
                                                           RenderType renderType);

    @Inject(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
            at = @At(value = "INVOKE",
                     target = "Lnet/minecraft/util/RandomSource;create()Lnet/minecraft/util/RandomSource;",
                     shift = At.Shift.AFTER))
    private void gtceu$initBloomContextData(SectionPos sectionPos, RenderChunkRegion region,
                                            com.mojang.blaze3d.vertex.VertexSorting vertexSorting,
                                            SectionBufferBuilderPack sectionBufferBuilderPack,
                                            java.util.List<?> additionalRenderers,
                                            CallbackInfoReturnable<SectionCompiler.Results> cir,
                                            @Local Map<RenderType, BufferBuilder> bufferLayers) {
        if (!BloomShaderManager.isBloomActive()) return;

        Supplier<VertexConsumer> provider = () -> {
            if (!BloomRenderer.SafeMode.enabled()) {
                return gtceu$getOrBeginLayer(bufferLayers, sectionBufferBuilderPack, GTRenderTypes.bloom());
            }
            return BloomRenderer.SafeMode.getOrStartBloomBuffer(sectionPos);
        };
        BloomRenderer.bloomChunkContext().get().with(provider);
    }

    @Inject(method = "compile(Lnet/minecraft/core/SectionPos;Lnet/minecraft/client/renderer/chunk/RenderChunkRegion;Lcom/mojang/blaze3d/vertex/VertexSorting;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Ljava/util/List;)Lnet/minecraft/client/renderer/chunk/SectionCompiler$Results;",
            at = @At(value = "INVOKE",
                     target = "Ljava/util/Map;entrySet()Ljava/util/Set;",
                     remap = false))
    private void gtceu$clearBloomContextData(SectionPos sectionPos, RenderChunkRegion region,
                                             com.mojang.blaze3d.vertex.VertexSorting vertexSorting,
                                             SectionBufferBuilderPack sectionBufferBuilderPack,
                                             java.util.List<?> additionalRenderers,
                                             CallbackInfoReturnable<SectionCompiler.Results> cir) {
        if (!BloomShaderManager.isBloomActive()) return;

        if (BloomRenderer.SafeMode.enabled()) {
            BloomRenderer.SafeMode.bakeBloomChunkBuffers(sectionPos, 0.0f, 0.0f, 0.0f);
        }

        BloomRenderer.bloomChunkContext().get().close();
    }
}

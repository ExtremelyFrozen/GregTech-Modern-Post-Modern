package com.gregtechceu.gtceu.core.mixins.client.bloom;

import net.minecraft.client.renderer.chunk.SectionCompiler;

import org.spongepowered.asm.mixin.Mixin;

/**
 * Placeholder for the 1.20 chunk rebuild bloom hook.
 *
 * <p>The upstream hook targets ChunkRenderDispatcher internals that were replaced by
 * SectionRenderDispatcher/SectionCompiler in 1.21. Special bloom render tickets still work; emissive chunk bloom
 * needs a dedicated 1.21 SectionCompiler hook instead of the old RebuildTask injection.
 */
@Mixin(SectionCompiler.class)
public abstract class RebuildTaskMixin {}

package com.gregtechceu.gtceu.client.bloom;

import com.gregtechceu.gtceu.client.renderer.GTRenderTypes;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;

public interface IRenderSetup {

    @OnlyIn(Dist.CLIENT)
    default BufferBuilder createBuffer() {
        return new BufferBuilder(
                new ByteBufferBuilder(GTRenderTypes.bloom().bufferSize()),
                GTRenderTypes.bloom().mode(), GTRenderTypes.bloom().format());
    }

    /**
     * Run any pre render gl code here.
     *
     * @param buffer Buffer builder
     */
    @OnlyIn(Dist.CLIENT)
    void preDraw(BufferBuilder buffer);

    /**
     * Run any post render gl code here.
     *
     * @param buffer Buffer builder
     */
    @OnlyIn(Dist.CLIENT)
    void postDraw(BufferBuilder buffer);
}

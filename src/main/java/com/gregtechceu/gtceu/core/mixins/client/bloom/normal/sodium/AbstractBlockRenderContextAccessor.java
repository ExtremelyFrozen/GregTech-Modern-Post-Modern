package com.gregtechceu.gtceu.core.mixins.client.bloom.normal.sodium;

import net.caffeinemc.mods.sodium.client.model.light.data.QuadLightData;
import net.caffeinemc.mods.sodium.client.render.frapi.render.AbstractBlockRenderContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AbstractBlockRenderContext.class, remap = false)
public interface AbstractBlockRenderContextAccessor {

    @Accessor("quadLightData")
    QuadLightData gtceu$getQuadLightData();
}

package com.gregtechceu.gtceu.core.mixins.iris;

import com.gregtechceu.gtceu.client.renderer.GTRenderTypes;

import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.minecraft.client.renderer.RenderType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = WorldRenderingPhase.class, remap = false)
public class WorldRenderingPhaseMixin {

    @Inject(method = "fromTerrainRenderType", at = @At("HEAD"), cancellable = true)
    private static void gtpm$mapBloomTerrainPhase(RenderType renderType,
                                                  CallbackInfoReturnable<WorldRenderingPhase> cir) {
        if (renderType == GTRenderTypes.bloom()) {
            cir.setReturnValue(WorldRenderingPhase.TERRAIN_TRANSLUCENT);
        }
    }
}

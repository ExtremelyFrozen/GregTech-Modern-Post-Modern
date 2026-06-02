package com.gregtechceu.gtceu.core.mixins.client;

import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(ModelBakery.class)
public interface ModelBakeryAccessor {

    @Accessor("topLevelModels")
    Map<ModelResourceLocation, UnbakedModel> gtceu$getTopLevelModels();

    @Invoker("getModel")
    UnbakedModel gtceu$getModel(ResourceLocation modelLocation);
}

package com.gregtechceu.gtceu.client.renderer.block;

import com.gregtechceu.gtceu.client.util.ModelEventHelper;
import com.gregtechceu.gtceu.common.item.LampBlockItem;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
public final class LampItemRendererHelper {

    private LampItemRendererHelper() {}

    public static final IClientItemExtensions CLIENT_EXTENSIONS = new IClientItemExtensions() {

        @Override
        public @NotNull BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return LampItemRenderer.getInstance();
        }
    };

    public static void registerModelBakeListener(LampBlockItem item) {
        ModelEventHelper.registerBakeEventListener(false,
                (resourceLocation, bakedModel, rootModel, modelBakery) -> {
                    ResourceLocation model = BuiltInRegistries.ITEM.getKey(item).withPrefix("item/");
                    ModelResourceLocation modelLoc = ModelResourceLocation.inventory(model);
                    if (!resourceLocation.equals(model) && !resourceLocation.equals(modelLoc)) {
                        return bakedModel;
                    }
                    return new BakedModelWrapper<>(bakedModel) {

                        @Override
                        public boolean isCustomRenderer() {
                            return true;
                        }
                    };
                });
    }
}

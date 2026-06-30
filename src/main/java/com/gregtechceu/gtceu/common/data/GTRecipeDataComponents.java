package com.gregtechceu.gtceu.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.recipe.RecipeData;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GTRecipeDataComponents {

    public static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister
            .createDataComponents(Registries.DATA_COMPONENT_TYPE, GTCEu.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RecipeData>> RECIPE_DATA = DATA_COMPONENTS
            .registerComponentType("recipe_data", builder -> builder.persistent(RecipeData.CODEC)
                    .networkSynchronized(RecipeData.STREAM_CODEC));

    private GTRecipeDataComponents() {}
}

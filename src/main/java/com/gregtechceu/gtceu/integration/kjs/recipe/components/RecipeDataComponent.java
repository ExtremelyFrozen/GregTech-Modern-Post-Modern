package com.gregtechceu.gtceu.integration.kjs.recipe.components;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.recipe.RecipeData;

import net.minecraft.core.component.DataComponentMap;

import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponent;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentType;
import dev.latvian.mods.rhino.type.TypeInfo;

public class RecipeDataComponent implements RecipeComponent<DataComponentMap> {

    // spotless:off
    public static final RecipeComponentType<DataComponentMap> RECIPE_DATA = RecipeComponentType.unit(GTCEu.id("recipe_data"), new RecipeDataComponent());
    // spotless:on

    @Override
    public Codec<DataComponentMap> codec() {
        return RecipeData.CODEC.xmap(RecipeData::toComponentMap, RecipeData::get);
    }

    @Override
    public TypeInfo typeInfo() {
        return TypeInfo.RAW_MAP;
    }

    @Override
    public String toString() {
        return "recipe_data";
    }

    @Override
    public RecipeComponentType<DataComponentMap> type() {
        return RECIPE_DATA;
    }
}

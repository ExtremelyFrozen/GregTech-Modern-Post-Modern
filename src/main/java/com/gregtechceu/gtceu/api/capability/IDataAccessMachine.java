package com.gregtechceu.gtceu.api.capability;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.NotNull;

/**
 * Provides researched recipe availability to machines that are gated by research data.
 */
public interface IDataAccessMachine {

    /**
     * @param recipeType recipe type whose research data is being queried
     * @param recipeId   recipe id whose research data is being queried
     * @return true when this data source allows the recipe to run
     */
    boolean isRecipeAvailable(@NotNull GTRecipeType recipeType, @NotNull ResourceLocation recipeId);

    /**
     * Notifies listening machines that the available research data changed.
     */
    default void notifyListeners() {}
}

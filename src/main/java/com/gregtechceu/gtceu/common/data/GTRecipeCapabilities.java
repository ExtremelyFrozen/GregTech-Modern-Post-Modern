package com.gregtechceu.gtceu.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

public class GTRecipeCapabilities {

    public final static RecipeCapability<SizedIngredient> ITEM = ItemRecipeCapability.CAP;
    public final static RecipeCapability<SizedFluidIngredient> FLUID = FluidRecipeCapability.CAP;
    public final static RecipeCapability<Long> EU = EURecipeCapability.CAP;
    public final static RecipeCapability<Integer> CWU = CWURecipeCapability.CAP;

    public static void init() {
        GTRegistries.register(GTRegistries.RECIPE_CAPABILITIES, GTCEu.id(ITEM.name), ITEM);
        GTRegistries.register(GTRegistries.RECIPE_CAPABILITIES, GTCEu.id(FLUID.name), FLUID);
        GTRegistries.register(GTRegistries.RECIPE_CAPABILITIES, GTCEu.id(EU.name), EU);
        GTRegistries.register(GTRegistries.RECIPE_CAPABILITIES, GTCEu.id(CWU.name), CWU);
    }
}

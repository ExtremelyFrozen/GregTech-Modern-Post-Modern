package com.gregtechceu.gtceu.integration.jei.recipe;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import com.lowdragmc.lowdraglib.jei.ModularUIRecipeCategory;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class GTRecipeJEICategory extends ModularUIRecipeCategory<GTRecipeDefinition> {

    public static final Function<GTRecipeCategory, RecipeType<GTRecipeDefinition>> TYPES = Util
            .memoize(c -> new RecipeType<>(c.registryKey, GTRecipeDefinition.class));

    private final GTRecipeCategory category;
    private final int width;
    private final int height;
    private final IDrawable icon;

    public GTRecipeJEICategory(@NotNull GTRecipeCategory category) {
        super(GTRecipeWrapper::new);
        this.category = category;
        var recipeType = category.getRecipeType();
        var size = recipeType.getRecipeUI().getJEISize();
        this.width = size.width;
        this.height = size.height;
        this.icon = toDrawable(category.getIcon(), 16, 16);
    }

    public static void registerRecipes(IRecipeRegistration registration) {
        List<GTRecipeCategory> subCategories = new ArrayList<>();
        // run main categories first
        for (GTRecipeCategory category : GTRegistries.RECIPE_CATEGORIES) {
            if (!category.shouldRegisterDisplays()) continue;
            var type = category.getRecipeType();
            if (category == type.getCategory()) {
                type.buildRepresentativeRecipes();
            } else {
                subCategories.add(category);
                continue;
            }
            var wrapped = List.copyOf(type.getRecipesInCategory(category));
            registration.addRecipes(TYPES.apply(category), wrapped);
        }
        // run subcategories
        for (GTRecipeCategory subCategory : subCategories) {
            if (!subCategory.shouldRegisterDisplays()) continue;
            var type = subCategory.getRecipeType();
            var wrapped = List.copyOf(type.getRecipesInCategory(subCategory));
            registration.addRecipes(TYPES.apply(subCategory), wrapped);
        }
    }

    public static void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (MachineDefinition machine : GTRegistries.MACHINES) {
            for (GTRecipeType type : machine.getRecipeTypes()) {
                for (GTRecipeCategory category : type.getCategories()) {
                    if (!category.isXEIVisible() && !GTCEu.isDev()) continue;
                    registration.addRecipeCatalyst(machine.asStack(), machineType(category));
                }
            }
        }
    }

    public static RecipeType<?> machineType(GTRecipeCategory category) {
        if (category == GTRecipeTypes.FURNACE_RECIPES.getCategory()) return RecipeTypes.SMELTING;
        return TYPES.apply(category);
    }

    @Override
    @NotNull
    public RecipeType<GTRecipeDefinition> getRecipeType() {
        return TYPES.apply(category);
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    @NotNull
    public Component getTitle() {
        return Component.translatable(category.getLanguageKey());
    }

    @Override
    public @Nullable ResourceLocation getRegistryName(@NotNull GTRecipeDefinition recipe) {
        return recipe.id;
    }

    private static IDrawable toDrawable(IGuiTexture texture, int width, int height) {
        return new IDrawable() {

            @Override
            public int getWidth() {
                return width;
            }

            @Override
            public int getHeight() {
                return height;
            }

            @Override
            public void draw(GuiGraphics graphics, int xOffset, int yOffset) {
                texture.draw(graphics, 0, 0, xOffset, yOffset, width, height);
                RenderSystem.enableDepthTest();
                RenderSystem.depthMask(true);
            }
        };
    }
}

package com.gregtechceu.gtceu.integration.jei.recipe;

import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.integration.xei.GTLDLib2RecipeUI;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIRecipeCategory;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.recipe.RecipeType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * Parallel LDLib2 JEI recipe category kept unregistered until the recipe viewer runtime path is migrated.
 */
public class GTLDLib2RecipeJEICategory extends ModularUIRecipeCategory<GTRecipeDefinition> {

    public static final Function<GTRecipeCategory, RecipeType<GTRecipeDefinition>> TYPES = Util
            .memoize(c -> new RecipeType<>(c.registryKey, GTRecipeDefinition.class));

    private final GTRecipeCategory category;
    private final int width;
    private final int height;
    private final IDrawable icon;

    public GTLDLib2RecipeJEICategory(@NotNull GTRecipeCategory category) {
        super(GTLDLib2RecipeJEICategory::createModularUI);
        this.category = category;
        var size = category.getRecipeType().getRecipeUI().getLDLib2XEIRecipeUISize();
        this.width = size.width();
        this.height = size.height();
        this.icon = toDrawable(category.getIcon(), 16, 16);
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

    private static ModularUI createModularUI(GTRecipeDefinition recipe) {
        int tier = RecipeHelper.getRecipeEUtTier(recipe);
        return GTLDLib2RecipeUI.createModularUI(recipe, tier, tier);
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

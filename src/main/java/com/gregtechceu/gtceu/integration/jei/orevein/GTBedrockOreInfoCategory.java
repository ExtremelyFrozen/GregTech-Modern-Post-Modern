package com.gregtechceu.gtceu.integration.jei.orevein;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.BedrockOreDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.integration.xei.widgets.GTBedrockOreWidget;

import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIRecipeCategory;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Function;

public class GTBedrockOreInfoCategory extends ModularUIRecipeCategory<Holder<BedrockOreDefinition>> {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public final static RecipeType<Holder<BedrockOreDefinition>> RECIPE_TYPE = new RecipeType(
            GTCEu.id("bedrock_ore_diagram"), Holder.class);
    private final int width;
    private final int height;
    private final IDrawable icon;

    public GTBedrockOreInfoCategory(IJeiHelpers helpers) {
        super(ore -> GTBedrockOreWidget.createModularUI(ore, GTBedrockOreWidget.JEI_HEIGHT));
        this.width = GTBedrockOreWidget.WIDTH;
        this.height = GTBedrockOreWidget.JEI_HEIGHT;
        this.icon = helpers.getGuiHelper().createDrawableItemStack(Items.RAW_IRON.getDefaultInstance());
    }

    public static void registerRecipes(IRecipeRegistration registry) {
        var level = Objects.requireNonNull(Minecraft.getInstance().level, "Client level is missing");
        var bedrockOres = level.registryAccess()
                .registryOrThrow(GTRegistries.BEDROCK_ORE_REGISTRY);
        registry.addRecipes(RECIPE_TYPE, bedrockOres.holders()
                .filter(ore -> ore.value().canGenerate())
                .<Holder<BedrockOreDefinition>>map(Function.identity())
                .toList());
    }

    public static void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(GTItems.PROSPECTOR_HV.asStack(), RECIPE_TYPE);
        registration.addRecipeCatalyst(GTItems.PROSPECTOR_LuV.asStack(), RECIPE_TYPE);
    }

    @NotNull
    @Override
    public RecipeType<Holder<BedrockOreDefinition>> getRecipeType() {
        return RECIPE_TYPE;
    }

    @NotNull
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
    public @NotNull Component getTitle() {
        return Component.translatable("gtpm.jei.bedrock_ore_diagram");
    }
}

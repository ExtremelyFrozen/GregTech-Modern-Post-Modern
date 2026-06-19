package com.gregtechceu.gtceu.integration.jei.orevein;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.BedrockOreDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.integration.xei.widgets.GTOreVeinWidget;

import com.lowdragmc.lowdraglib.jei.ModularUIRecipeCategory;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class GTBedrockOreInfoCategory extends ModularUIRecipeCategory<Holder<BedrockOreDefinition>> {

    public final static RecipeType<Holder<BedrockOreDefinition>> RECIPE_TYPE = new RecipeType(
            GTCEu.id("bedrock_ore_diagram"), Holder.class);
    private final int width;
    private final int height;
    private final mezz.jei.api.gui.drawable.IDrawable icon;

    public GTBedrockOreInfoCategory(IJeiHelpers helpers) {
        super(GTBedrockOreInfoWrapper::new);
        IGuiHelper guiHelper = helpers.getGuiHelper();
        this.width = GTOreVeinWidget.width;
        this.height = 120;
        this.icon = helpers.getGuiHelper().createDrawableItemStack(Items.RAW_IRON.getDefaultInstance());
    }

    public static void registerRecipes(IRecipeRegistration registry) {
        var bedrockOres = Minecraft.getInstance().level.registryAccess()
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
    public mezz.jei.api.gui.drawable.IDrawable getIcon() {
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
    public Component getTitle() {
        return Component.translatable("gtpm.jei.bedrock_ore_diagram");
    }
}

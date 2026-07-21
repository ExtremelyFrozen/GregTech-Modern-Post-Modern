package com.gregtechceu.gtceu.integration.jei.orevein;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.data.worldgen.GTOreDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.integration.xei.widgets.GTOreVeinWidget;

import com.lowdragmc.lowdraglib2.integration.xei.jei.ModularUIRecipeCategory;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class GTOreVeinInfoCategory extends ModularUIRecipeCategory<Holder<GTOreDefinition>> {

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public final static RecipeType<Holder<GTOreDefinition>> RECIPE_TYPE = new RecipeType(GTCEu.id("ore_vein_diagram"),
            Holder.class);
    private final int width;
    private final int height;
    private final IDrawable icon;

    public GTOreVeinInfoCategory(IJeiHelpers helpers) {
        super(ore -> GTOreVeinWidget.createModularUI(ore, GTOreVeinWidget.JEI_HEIGHT));
        this.width = GTOreVeinWidget.WIDTH;
        this.height = GTOreVeinWidget.JEI_HEIGHT;
        this.icon = helpers.getGuiHelper()
                .createDrawableItemStack(ChemicalHelper.get(TagPrefix.rawOre, GTMaterials.Iron));
    }

    public static void registerRecipes(IRecipeRegistration registry) {
        var level = Minecraft.getInstance().level;
        var ores = level.registryAccess()
                .registryOrThrow(GTRegistries.ORE_VEIN_REGISTRY);
        registry.addRecipes(RECIPE_TYPE, ores.holders()
                .filter(ore -> ore.value().canGenerate())
                .<Holder<GTOreDefinition>>map(Function.identity())
                .toList());
    }

    @Override
    public void setRecipe(@NotNull IRecipeLayoutBuilder builder, @NotNull Holder<GTOreDefinition> definition,
                          @NotNull IFocusGroup focuses) {
        super.setRecipe(builder, definition, focuses);
        builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT)
                .addItemStacks(GTOreVeinWidget.getContainedOresAndBlocks(definition.value()));
    }

    public static void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(GTItems.PROSPECTOR_LV.asStack(), RECIPE_TYPE);
        registration.addRecipeCatalyst(GTItems.PROSPECTOR_HV.asStack(), RECIPE_TYPE);
        registration.addRecipeCatalyst(GTItems.PROSPECTOR_LuV.asStack(), RECIPE_TYPE);
    }

    @Override
    public @NotNull RecipeType<Holder<GTOreDefinition>> getRecipeType() {
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
        return Component.translatable("gtpm.jei.ore_vein_diagram");
    }
}

package com.gregtechceu.gtceu.api.recipe;

import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.category.GTRecipeCategory;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentListMap;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.api.recipe.lookup.ingredient.AbstractMapIngredient;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GTRecipeDefinition implements Recipe<RecipeInput> {

    @Getter
    @Setter
    @Nullable
    public ResourceLocation id;
    public final GTRecipeType recipeType;
    public final GTRecipeCategory recipeCategory;
    public final ContentListMap inputs;
    public final ContentListMap outputs;
    public final ContentListMap tickInputs;
    public final ContentListMap tickOutputs;
    public final Map<RecipeCapability<?>, ChanceLogic> inputChanceLogics;
    public final Map<RecipeCapability<?>, ChanceLogic> outputChanceLogics;
    public final Map<RecipeCapability<?>, ChanceLogic> tickInputChanceLogics;
    public final Map<RecipeCapability<?>, ChanceLogic> tickOutputChanceLogics;
    public final List<RecipeCondition<?>> conditions;
    public final List<?> ingredientActions;
    @NotNull
    public final DataComponentMap data;
    public final int duration;
    public final int groupColor;

    public GTRecipeDefinition(ResourceLocation id,
                              GTRecipeType recipeType,
                              ContentListMap inputs,
                              ContentListMap outputs,
                              ContentListMap tickInputs,
                              ContentListMap tickOutputs,
                              Map<RecipeCapability<?>, ChanceLogic> inputChanceLogics,
                              Map<RecipeCapability<?>, ChanceLogic> outputChanceLogics,
                              Map<RecipeCapability<?>, ChanceLogic> tickInputChanceLogics,
                              Map<RecipeCapability<?>, ChanceLogic> tickOutputChanceLogics,
                              List<RecipeCondition<?>> conditions,
                              List<?> ingredientActions,
                              @NotNull DataComponentMap data,
                              int duration,
                              @NotNull GTRecipeCategory recipeCategory,
                              int groupColor) {
        this.id = id;
        this.recipeType = recipeType;
        this.inputs = inputs;
        this.outputs = outputs;
        this.tickInputs = tickInputs;
        this.tickOutputs = tickOutputs;
        this.inputChanceLogics = inputChanceLogics;
        this.outputChanceLogics = outputChanceLogics;
        this.tickInputChanceLogics = tickInputChanceLogics;
        this.tickOutputChanceLogics = tickOutputChanceLogics;
        this.conditions = conditions;
        this.ingredientActions = ingredientActions;
        this.data = data;
        this.duration = duration;
        this.recipeCategory = recipeCategory != GTRecipeCategory.DEFAULT ? recipeCategory : recipeType.getCategory();
        this.groupColor = groupColor;
    }

    public static GTRecipeDefinition fromRuntime(GTRecipe recipe) {
        return new GTRecipeDefinition(recipe.id, recipe.recipeType,
                ContentListMap.copyOf(recipe.inputs), ContentListMap.copyOf(recipe.outputs),
                ContentListMap.copyOf(recipe.tickInputs), ContentListMap.copyOf(recipe.tickOutputs),
                new HashMap<>(recipe.inputChanceLogics), new HashMap<>(recipe.outputChanceLogics),
                new HashMap<>(recipe.tickInputChanceLogics), new HashMap<>(recipe.tickOutputChanceLogics),
                new ArrayList<>(recipe.conditions), new ArrayList<>(recipe.ingredientActions),
                RecipeData.copy(recipe.data), recipe.duration, recipe.recipeCategory, recipe.groupColor);
    }

    public GTRecipe toRuntime() {
        return new GTRecipe(recipeType, id,
                inputs.copy().asContentMap(), outputs.copy().asContentMap(),
                tickInputs.copy().asContentMap(), tickOutputs.copy().asContentMap(),
                new HashMap<>(inputChanceLogics), new HashMap<>(outputChanceLogics),
                new HashMap<>(tickInputChanceLogics), new HashMap<>(tickOutputChanceLogics),
                new ArrayList<>(conditions), new ArrayList<>(ingredientActions), RecipeData.copy(data), duration,
                recipeCategory, groupColor);
    }

    public GTRecipeDefinition withId(ResourceLocation id) {
        return new GTRecipeDefinition(id, recipeType,
                inputs.copy(), outputs.copy(), tickInputs.copy(), tickOutputs.copy(),
                new HashMap<>(inputChanceLogics), new HashMap<>(outputChanceLogics),
                new HashMap<>(tickInputChanceLogics), new HashMap<>(tickOutputChanceLogics),
                new ArrayList<>(conditions), new ArrayList<>(ingredientActions), RecipeData.copy(data), duration,
                recipeCategory, groupColor);
    }

    public List<Content> getInputContents(RecipeCapability<?> capability) {
        return inputs.getOrDefault(capability, List.of());
    }

    public List<Content> getOutputContents(RecipeCapability<?> capability) {
        return outputs.getOrDefault(capability, List.of());
    }

    public List<Content> getTickInputContents(RecipeCapability<?> capability) {
        return tickInputs.getOrDefault(capability, List.of());
    }

    public List<Content> getTickOutputContents(RecipeCapability<?> capability) {
        return tickOutputs.getOrDefault(capability, List.of());
    }

    public boolean hasTick() {
        return !tickInputs.isEmpty() || !tickOutputs.isEmpty();
    }

    public EnergyStack getInputEUt() {
        return calculateEUt(tickInputs);
    }

    public EnergyStack getOutputEUt() {
        return calculateEUt(tickOutputs);
    }

    public ChanceLogic getChanceLogicForCapability(RecipeCapability<?> cap, IO io, boolean isTick) {
        if (io == IO.OUT) {
            if (isTick) {
                return tickOutputChanceLogics.getOrDefault(cap, ChanceLogic.OR);
            } else {
                return outputChanceLogics.getOrDefault(cap, ChanceLogic.OR);
            }
        } else if (io == IO.IN) {
            if (isTick) {
                return tickInputChanceLogics.getOrDefault(cap, ChanceLogic.OR);
            } else {
                return inputChanceLogics.getOrDefault(cap, ChanceLogic.OR);
            }
        }
        return ChanceLogic.OR;
    }

    private EnergyStack calculateEUt(ContentListMap contents) {
        var outputs = contents.get(EURecipeCapability.CAP);
        if (outputs == null) return EnergyStack.EMPTY;
        long v = 0;
        long a = 0;
        for (var content : outputs) {
            EnergyStack stack = EURecipeCapability.CAP.of(content.content);
            v += stack.voltage();
            a += stack.amperage();
        }
        return new EnergyStack(v, a);
    }

    public List<List<AbstractMapIngredient>> getInputMapIngredients() {
        return buildMapIngredients(inputs);
    }

    public List<List<AbstractMapIngredient>> getTickInputMapIngredients() {
        return buildMapIngredients(tickInputs);
    }

    private static List<List<AbstractMapIngredient>> buildMapIngredients(ContentListMap contents) {
        List<List<AbstractMapIngredient>> ingredients = new ArrayList<>();
        contents.forEachEntry(new ContentListMap.EntryConsumer() {

            @Override
            public <T> void accept(RecipeCapability<T> capability, List<Content> contents) {
                if (!capability.isRecipeSearchFilter()) return;
                for (Content content : contents) {
                    List<AbstractMapIngredient> mapIngredients = capability.getDefaultMapIngredient(content.content);
                    if (mapIngredients != null && !mapIngredients.isEmpty()) {
                        ingredients.add(mapIngredients);
                    }
                }
            }
        });
        return ingredients;
    }

    @Override
    public boolean matches(@NotNull RecipeInput input, @NotNull Level level) {
        return false;
    }

    @Override
    public @NotNull ItemStack assemble(@NotNull RecipeInput input, HolderLookup.@NotNull Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public @NotNull ItemStack getResultItem(HolderLookup.@NotNull Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public @NotNull RecipeSerializer<?> getSerializer() {
        return recipeType.getSerializer();
    }

    @Override
    public @NotNull GTRecipeType getType() {
        return recipeType;
    }

    @Override
    public String toString() {
        return id == null ? "null id" : id.toString();
    }
}

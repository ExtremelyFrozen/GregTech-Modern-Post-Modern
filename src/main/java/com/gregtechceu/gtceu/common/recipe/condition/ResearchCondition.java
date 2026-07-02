package com.gregtechceu.gtceu.common.recipe.condition;

import com.gregtechceu.gtceu.api.capability.IDataAccessMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.ResearchData;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;
import com.gregtechceu.gtceu.common.data.GTRecipeConditions;

import net.minecraft.network.chat.Component;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@AllArgsConstructor
public class ResearchCondition extends RecipeCondition<ResearchCondition> {

    // spotless:off
    public static final MapCodec<ResearchCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> RecipeCondition.isReverse(instance).and(
            ResearchData.CODEC.fieldOf("research").forGetter(ResearchCondition::getData)
    ).apply(instance, ResearchCondition::new));
    // spotless:on

    @Getter
    public ResearchData data;

    public ResearchCondition() {
        this.data = new ResearchData();
    }

    public ResearchCondition(boolean isReverse, ResearchData data) {
        super(isReverse);
        this.data = data;
    }

    @Override
    public RecipeConditionType<ResearchCondition> getType() {
        return GTRecipeConditions.RESEARCH;
    }

    @Override
    public Component getTooltips() {
        return Component.translatable("gtpm.recipe.research");
    }

    @Override
    public boolean testCondition(@NotNull GTRecipe recipe, @NotNull RecipeLogic recipeLogic) {
        var recipeType = recipe.recipeType;
        var recipeId = recipe.getId();
        if (recipeLogic.getMachine() instanceof IDataAccessMachine dataAccessMachine &&
                dataAccessMachine.isRecipeAvailable(recipeType, recipeId)) {
            return true;
        }
        if (recipeLogic.getMachine() instanceof MultiblockControllerMachine controller) {
            for (var part : controller.getParts()) {
                if (part instanceof IDataAccessMachine dataAccessMachine &&
                        dataAccessMachine.isRecipeAvailable(recipeType, recipeId)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public ResearchCondition createTemplate() {
        return new ResearchCondition();
    }
}

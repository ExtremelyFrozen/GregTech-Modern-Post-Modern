package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.data.medicalcondition.MedicalCondition;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.common.machine.trait.hazard.EnvironmentalHazardCleanerTrait;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import net.minecraft.network.chat.Component;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import static com.gregtechceu.gtceu.api.GTValues.LV;
import static com.gregtechceu.gtceu.api.GTValues.VHA;

public class AirScrubberMachine extends SimpleTieredMachine {

    public static final float MIN_CLEANING_PER_OPERATION = 10;

    private MedicalCondition currentRecipeMedicalCondition;

    @Getter
    private float removedLastSecond;

    @Getter
    private final EnvironmentalHazardCleanerTrait cleanerTrait;

    public AirScrubberMachine(BlockEntityCreationInfo info, int tier) {
        super(info, tier, GTMachineUtils.largeTankSizeFunction);
        this.cleanerTrait = attachTrait(new EnvironmentalHazardCleanerTrait(tier / 2, this::validateCleaningOperation));
    }

    @Override
    public boolean regressWhenWaiting() {
        return false;
    }

    public boolean validateCleaningOperation(MedicalCondition condition, float amount) {
        if (getWorkLogic().isActive()) {
            return false;
        }

        currentRecipeMedicalCondition = condition;

        GTRecipeBuilder builder = GTRecipeTypes.AIR_SCRUBBER_RECIPES.recipeBuilder(condition.name + "_autogen")
                .duration(200).EUt(VHA[LV]);
        condition.recipeModifier.accept(builder);
        return this.recipeLogic.checkMatchedRecipeAvailable(builder.build());
    }

    @Override
    public boolean isWorkLogicAvailable() {
        // Don't run recipes if hazards are off
        return ConfigHolder.INSTANCE.gameplay.environmentalHazards;
    }

    @Override
    public @Nullable Component beforeWorking(@Nullable GTRecipe recipe) {
        Component failReason = super.beforeWorking(recipe);
        if (failReason != null) {
            return failReason;
        }
        // Sets the amount of hazard to clean based on the recipe tier, not the machine tier.
        if (recipe != null && cleanerTrait.beginCleaningOperation(currentRecipeMedicalCondition,
                MIN_CLEANING_PER_OPERATION * (recipe.ocLevel + 1))) {
            return null;
        }
        return ModifierFunction.DEFAULT_FAILURE;
    }

    @Override
    public boolean onWorking() {
        if (!super.onWorking() || !ConfigHolder.INSTANCE.gameplay.environmentalHazards) {
            return false;
        }
        cleanerTrait.cleanHazard();
        return true;
    }

    @Override
    public void afterWorking() {
        cleanerTrait.endCleaningOperation();
    }
}

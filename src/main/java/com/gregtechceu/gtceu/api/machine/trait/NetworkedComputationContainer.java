package com.gregtechceu.gtceu.api.machine.trait;

import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.computation.ComputationConsumer;
import com.gregtechceu.gtceu.api.computation.ComputationProducer;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeData;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.common.computation.ComputationNetworkManager;

import net.minecraft.server.level.ServerLevel;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class NetworkedComputationContainer extends NotifiableRecipeHandlerTrait<Integer>
                                           implements ComputationProducer, ComputationConsumer {

    public static final MachineTraitType<NetworkedComputationContainer> TYPE = new MachineTraitType<>(
            NetworkedComputationContainer.class);

    @Getter
    private final IO handlerIO;
    @Getter
    private int receivedCWUt;

    public NetworkedComputationContainer(IO handlerIO) {
        super();
        this.handlerIO = handlerIO;
    }

    @Override
    public MachineTraitType<?> getTraitType() {
        return TYPE;
    }

    @Override
    public List<Integer> handleRecipeInner(IO io, GTRecipe recipe, List<Integer> left, boolean simulate) {
        int sum = left.stream().mapToInt(Integer::intValue).sum();
        if (io == IO.OUT) {
            return List.of();
        }
        if (simulate && getLevel() instanceof ServerLevel serverLevel) {
            ComputationConsumer target = getMachine() instanceof ComputationConsumer consumer ? consumer : this;
            if (ComputationNetworkManager.get(serverLevel).reserveDemand(target, sum)) {
                return List.of();
            }
            return left;
        }

        if (receivedCWUt < sum) {
            return left;
        }

        if (!simulate && RecipeData.getBoolean(recipe.data, "duration_is_total_cwu") &&
                getMachine() instanceof IRecipeLogicMachine recipeLogicMachine) {
            recipeLogicMachine.getRecipeLogic().setProgress(
                    recipeLogicMachine.getRecipeLogic().getProgress() - 1 + receivedCWUt);
        }
        return List.of();
    }

    @Override
    public @NotNull List<Object> getContents() {
        return List.of(handlerIO.support(IO.OUT) ? getOfferedCWUt() : receivedCWUt);
    }

    @Override
    public double getTotalContentAmount() {
        return handlerIO.support(IO.OUT) ? getOfferedCWUt() : receivedCWUt;
    }

    @Override
    public RecipeCapability<Integer> getCapability() {
        return CWURecipeCapability.CAP;
    }

    @Override
    public int getOfferedCWUt() {
        if (!handlerIO.support(IO.OUT)) return 0;
        GTRecipe recipe = getLastRecipe();
        if (recipe == null) return 0;
        return sumCWUt(recipe.getTickOutputContents(CWURecipeCapability.CAP));
    }

    @Override
    public int getMinimumCWUt() {
        if (!handlerIO.support(IO.IN)) return 0;
        GTRecipe recipe = getLastRecipe();
        if (recipe == null) return 0;
        return sumCWUt(recipe.getTickInputContents(CWURecipeCapability.CAP));
    }

    @Override
    public int getRequestedCWUt() {
        GTRecipe recipe = getLastRecipe();
        if (recipe != null && RecipeData.getBoolean(recipe.data, "duration_is_total_cwu")) {
            return Integer.MAX_VALUE;
        }
        return recipe == null ? 0 : getMinimumCWUt();
    }

    @Override
    public void applyReceivedCWUt(int receivedCWUt) {
        this.receivedCWUt = Math.max(0, receivedCWUt);
    }

    @Override
    public void onComputationChanged() {
        notifyListeners();
    }

    private GTRecipe getLastRecipe() {
        return getMachine() instanceof IRecipeLogicMachine recipeLogicMachine ?
                recipeLogicMachine.getRecipeLogic().getLastRecipe() : null;
    }

    private static int sumCWUt(List<Content> contents) {
        return contents.stream().map(Content::getContent).mapToInt(CWURecipeCapability.CAP::of).sum();
    }
}

package com.gregtechceu.gtceu.api.multiblock.autobuild;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of one automatic multiblock build request.
 *
 * @param success         whether the request completed its selected mode without blocking problems
 * @param placed          number of blocks or fluids placed
 * @param removed         number of blocks or fluids removed
 * @param completedStages number of committed stages
 * @param problems        diagnostics collected during planning or execution
 */
public record AutoBuildResult(boolean success, int placed, int removed, int completedStages,
                              List<AutoBuildProblem> problems) {

    public AutoBuildResult {
        problems = List.copyOf(problems);
    }

    public static AutoBuildResult failed(AutoBuildProblem problem) {
        return new AutoBuildResult(false, 0, 0, 0, List.of(problem));
    }

    public AutoBuildResult failedWith(AutoBuildProblem problem) {
        ArrayList<AutoBuildProblem> updatedProblems = new ArrayList<>(problems);
        updatedProblems.add(problem);
        return new AutoBuildResult(false, placed, removed, completedStages, updatedProblems);
    }

    public Component summary() {
        if (success) {
            return Component.translatable("gtpm.multiblock.autobuild.success", placed, removed, completedStages);
        }
        if (!problems.isEmpty()) {
            return problems.getFirst().message();
        }
        return Component.translatable("gtpm.multiblock.autobuild.failed");
    }
}

package com.gregtechceu.gtceu.api.multiblock.autobuild;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import java.util.List;

/**
 * Immutable input for a multiblock controller automatic build request.
 *
 * @param structureName   selected structure name, usually {@link MultiblockControllerMachine#DEFAULT_STRUCTURE}
 * @param options         user-selected build behavior
 * @param materialSources material sources used in order during planning and execution
 */
public record AutoBuildRequest(String structureName, AutoBuildOptions options,
                               List<AutoBuildMaterialSource> materialSources) {

    public AutoBuildRequest {
        materialSources = List.copyOf(materialSources);
    }

    public static AutoBuildRequest main(AutoBuildOptions options, List<AutoBuildMaterialSource> materialSources) {
        return new AutoBuildRequest(MultiblockControllerMachine.DEFAULT_STRUCTURE, options, materialSources);
    }
}

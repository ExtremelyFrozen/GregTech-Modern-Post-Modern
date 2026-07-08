package com.gregtechceu.gtceu.api.machine.feature.multiblock;

/**
 * Narrow contract used to decouple the LDLib2 parallel hatch UI action from concrete part construction.
 */
public interface ParallelHatch {

    /**
     * Returns the currently configured parallel amount for this hatch.
     */
    int getCurrentParallel();

    /**
     * Updates the currently configured parallel amount for this hatch.
     */
    void setCurrentParallel(int parallelAmount);
}

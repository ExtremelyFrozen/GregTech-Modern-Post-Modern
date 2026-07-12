package com.gregtechceu.gtceu.api.machine.feature.multiblock;

/**
 * Narrow contract for the LDLib2 pump hatch config action.
 *
 * <p>
 * The action depends on this dedicated pump hatch contract so tests and handlers do not need to construct a
 * concrete part, while avoiding any expansion of the action to every controllable holder.
 * </p>
 */
public interface PumpHatch {

    /**
     * Returns whether this pump hatch is allowed to perform automatic fluid transfer.
     */
    boolean isWorkingEnabled();

    /**
     * Updates whether this pump hatch is allowed to perform automatic fluid transfer.
     */
    void setWorkingEnabled(boolean isWorkingAllowed);
}

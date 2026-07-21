package com.gregtechceu.gtceu.api.machine.feature.multiblock;

/**
 * Narrow contract for the LDLib2 steam item bus config action.
 *
 * <p>
 * The action depends on this dedicated steam item bus contract so tests and handlers do not need to construct a
 * concrete part, while avoiding any expansion of the action to every controllable holder.
 * </p>
 */
public interface SteamItemBus {

    /**
     * Returns whether this steam item bus is allowed to perform automatic item transfer.
     */
    boolean isWorkingEnabled();

    /**
     * Updates whether this steam item bus is allowed to perform automatic item transfer.
     */
    void setWorkingEnabled(boolean isWorkingAllowed);
}

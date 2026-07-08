package com.gregtechceu.gtceu.api.machine.feature;

/**
 * Exposes auto-output toggle state independently of the concrete machine trait implementation.
 *
 * <p>LDLib2 Fancy auto-output actions use this narrow capability so dispatch validation and mutation can share one
 * contract between real auto-output traits and test holders.
 */
public interface AutoOutputMachine {

    /**
     * Returns whether this holder can auto-output item stacks.
     */
    boolean supportsAutoOutputItems();

    /**
     * Returns whether this holder can auto-output fluids.
     */
    boolean supportsAutoOutputFluids();

    /**
     * Returns the current item auto-output enabled state.
     */
    boolean isAutoOutputItems();

    /**
     * Returns the current fluid auto-output enabled state.
     */
    boolean isAutoOutputFluids();

    /**
     * Updates the item auto-output enabled state.
     */
    void setAllowAutoOutputItems(boolean allow);

    /**
     * Updates the fluid auto-output enabled state.
     */
    void setAllowAutoOutputFluids(boolean allow);
}

package com.gregtechceu.gtceu.api.machine.feature.multiblock;

/**
 * Exposes batch mode state independently of the legacy multiblock Fancy UI implementation.
 *
 * <p>
 * LDLib2 action handlers and future migrated machines use this narrow capability instead of depending on a
 * concrete legacy Fancy UI machine class.
 */
public interface BatchModeMachine {

    /**
     * Returns whether this machine definition supports batch mode.
     */
    boolean supportsBatchMode();

    /**
     * Returns the current batch mode enabled state.
     */
    boolean isBatchEnabled();

    /**
     * Updates the current batch mode enabled state.
     */
    void setBatchEnabled(boolean batchEnabled);
}

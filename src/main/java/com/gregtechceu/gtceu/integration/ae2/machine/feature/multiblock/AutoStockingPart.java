package com.gregtechceu.gtceu.integration.ae2.machine.feature.multiblock;

/**
 * AE2 auto-stocking threshold capability separated from the legacy multiblock and Fancy UI lifecycle.
 *
 * <p>LDLib2 action handlers and future migrated parts use this narrow contract instead of depending on
 * {@link IMEStockingPart}, which still carries the old controller lifecycle and Fancy inheritance chain.
 */
public interface AutoStockingPart {

    /**
     * Target storage type used to select auto-stocking UI copy.
     */
    enum StockingTarget {

        /**
         * Auto-stocking item bus thresholds.
         */
        ITEM,

        /**
         * Auto-stocking fluid hatch thresholds.
         */
        FLUID
    }

    /**
     * Returns the minimum visible stock amount required before a configured stack is exposed.
     */
    int getMinStackSize();

    /**
     * Updates the minimum visible stock amount required before a configured stack is exposed.
     */
    void setMinStackSize(int minStackSize);

    /**
     * Returns the number of ticks between AE2 auto-stocking refresh cycles.
     */
    int getTicksPerCycle();

    /**
     * Updates the number of ticks between AE2 auto-stocking refresh cycles.
     */
    void setTicksPerCycle(int ticksPerCycle);

    /**
     * Returns whether this auto-stocking part targets item or fluid storage.
     */
    StockingTarget getStockingTarget();
}

package com.gregtechceu.gtceu.api.machine.feature.multiblock;

/**
 * Narrow state contract for machines or parts that expose distinct bus or part state.
 *
 * <p>
 * This interface decouples distinct state from the legacy Fancy multiblock part lifecycle, allowing LDLib2 action
 * handlers and future migrated parts to share the capability without depending on {@link IMultiPart}.
 */
public interface DistinctPart {

    /**
     * Returns whether this holder currently separates recipes by distinct input bus.
     */
    boolean isDistinct();

    /**
     * Updates whether this holder separates recipes by distinct input bus.
     *
     * @param isDistinct {@code true} to enable distinct bus mode.
     */
    void setDistinct(boolean isDistinct);
}

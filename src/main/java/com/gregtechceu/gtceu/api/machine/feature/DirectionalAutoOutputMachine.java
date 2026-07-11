package com.gregtechceu.gtceu.api.machine.feature;

import net.minecraft.core.Direction;

import org.jetbrains.annotations.Nullable;

/**
 * Exposes directional auto-output state to validated LDLib2 machine actions.
 *
 * <p>
 * Directional Fancy pages need the output face and output-side input policy in addition to the common auto-output
 * toggles. This contract keeps those actions independent of the concrete machine trait implementation.
 */
public interface DirectionalAutoOutputMachine extends AutoOutputMachine {

    /**
     * Returns the configured item output face, or {@code null} when item output has no face.
     */
    @Nullable
    Direction getItemOutputDirection();

    /**
     * Returns the configured fluid output face, or {@code null} when fluid output has no face.
     */
    @Nullable
    Direction getFluidOutputDirection();

    /**
     * Returns whether the requested item output face satisfies the machine's facing and custom validators.
     */
    boolean canSetItemOutputDirection(@Nullable Direction direction);

    /**
     * Returns whether the requested fluid output face satisfies the machine's facing and custom validators.
     */
    boolean canSetFluidOutputDirection(@Nullable Direction direction);

    /**
     * Updates the item output face after the caller has validated it.
     */
    void setItemOutputDirection(@Nullable Direction direction);

    /**
     * Updates the fluid output face after the caller has validated it.
     */
    void setFluidOutputDirection(@Nullable Direction direction);

    /**
     * Returns whether item input is accepted from the configured item output face.
     */
    boolean allowsItemInputFromOutputSide();

    /**
     * Returns whether fluid input is accepted from the configured fluid output face.
     */
    boolean allowsFluidInputFromOutputSide();

    /**
     * Updates the global item input policy for the configured item output face.
     */
    void setAllowItemInputFromOutputSide(boolean allow);

    /**
     * Updates the global fluid input policy for the configured fluid output face.
     */
    void setAllowFluidInputFromOutputSide(boolean allow);
}

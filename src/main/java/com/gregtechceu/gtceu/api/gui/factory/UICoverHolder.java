package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.api.cover.CoverBehavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

/**
 * Holds the stable cover identity used to open and validate a cover UI.
 *
 * <p>The holder keeps cover screens from depending on a {@link CoverBehavior} instance as the UI factory payload while
 * the widgets still build the remaining legacy UI trees.
 */
public interface UICoverHolder {

    /**
     * Returns the block position of the coverable holder that owned the cover when the UI opened.
     */
    BlockPos getPos();

    /**
     * Returns the side where the cover UI was opened.
     */
    Direction getSide();

    /**
     * Returns the cover definition id captured when the UI opened.
     */
    ResourceLocation getCoverDefinitionId();

    /**
     * Resolves the current cover instance and rejects replacements with a different definition id.
     */
    @Nullable
    CoverBehavior getCover();
}

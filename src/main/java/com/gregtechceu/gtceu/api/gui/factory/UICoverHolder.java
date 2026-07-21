package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.api.cover.CoverBehavior;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Holds the stable cover identity and action session used to validate a cover UI.
 *
 * <p>
 * The holder keeps cover screens from depending on a {@link CoverBehavior} instance as the UI factory payload while
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
     * Returns the opaque session id assigned when this cover menu opened.
     *
     * <p>
     * The id correlates client actions with the current server menu. It is not an authorization decision by itself.
     */
    UUID getActionSessionId();

    /**
     * Resolves the current cover instance. Server-side opened holders reject any replacement, including one with the
     * same definition id.
     */
    @Nullable
    CoverBehavior getCover();
}

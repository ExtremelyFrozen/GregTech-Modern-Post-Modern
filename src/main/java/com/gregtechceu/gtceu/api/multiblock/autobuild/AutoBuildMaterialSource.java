package com.gregtechceu.gtceu.api.multiblock.autobuild;

import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Provides material transactions for automatic multiblock building.
 *
 * Implementations must not mutate backing storage while reserving. Mutation is only allowed from
 * {@link Reservation#commit()} or {@link Session#insert(ItemStack, boolean)} with {@code simulate == false}.
 */
public interface AutoBuildMaterialSource {

    /**
     * Opens a per-stage reservation session.
     */
    Session openSession();

    /**
     * Describes why this source cannot be used before a build starts.
     *
     * @return null when the source is available
     */
    @Nullable
    default AutoBuildProblem unavailableProblem() {
        return null;
    }

    interface Session {

        /**
         * Reserves one item matching any candidate stack, without mutating backing storage.
         */
        @Nullable
        Reservation reserve(List<ItemStack> candidates);

        /**
         * Inserts a returned drop into this source.
         *
         * @return the remaining stack that could not be inserted
         */
        ItemStack insert(ItemStack stack, boolean simulate);
    }

    interface Reservation {

        /**
         * A single item stack that should be used for placement.
         */
        ItemStack stack();

        /**
         * Commits this reservation to backing storage.
         */
        boolean commit();
    }
}

package com.gregtechceu.gtceu.api.cover.filter;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Matches resources and exposes an LDLib2 configurator for filter items.
 */
public interface Filter<T, S extends Filter<T, S>> extends Predicate<T> {

    /**
     * Builds the LDLib2 configurator at the requested fixed-position offset.
     */
    UIElement openLDLib2Configurator(int x, int y);

    /**
     * Registers the callback used to persist and propagate filter changes.
     */
    void setOnUpdated(Consumer<S> onUpdated);

    /**
     * Returns whether this filter rejects matching resources instead of accepting them.
     */
    default boolean isBlackList() {
        return false;
    }

    /**
     * Returns whether this filter has no configured matching rules.
     */
    default boolean isBlank() {
        return false;
    }
}

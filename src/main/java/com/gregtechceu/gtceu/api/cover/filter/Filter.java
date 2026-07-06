package com.gregtechceu.gtceu.api.cover.filter;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import java.util.function.Consumer;
import java.util.function.Predicate;

public interface Filter<T, S extends Filter<T, S>> extends Predicate<T> {

    WidgetGroup openConfigurator(int x, int y);

    default boolean supportsLDLib2Configurator() {
        return false;
    }

    default UIElement openLDLib2Configurator(int x, int y) {
        throw new UnsupportedOperationException("LDLib2 configurator is not supported by this filter");
    }

    void setOnUpdated(Consumer<S> onUpdated);

    default boolean isBlackList() {
        return false;
    }

    default boolean isBlank() {
        return false;
    }
}

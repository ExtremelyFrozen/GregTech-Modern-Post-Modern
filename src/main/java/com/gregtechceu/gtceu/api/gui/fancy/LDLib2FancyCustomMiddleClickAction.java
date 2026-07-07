package com.gregtechceu.gtceu.api.gui.fancy;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

/**
 * Handles middle-click shortcut actions on an LDLib2 Fancy configurator tab.
 *
 * <p>Implementations use this when the collapsed tab should expose a direct reset or alternate action without opening
 * the expanded configurator body.
 */
public interface LDLib2FancyCustomMiddleClickAction {

    /**
     * Handles a middle-click event for the owning Fancy tab.
     *
     * @param event LDLib2 mouse event carrying cursor and button data.
     */
    void onMiddleClick(UIEvent event);
}

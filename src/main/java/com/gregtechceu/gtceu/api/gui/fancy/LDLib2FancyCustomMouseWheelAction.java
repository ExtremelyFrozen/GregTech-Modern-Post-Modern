package com.gregtechceu.gtceu.api.gui.fancy;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

/**
 * Handles mouse-wheel shortcut actions on an LDLib2 Fancy configurator tab.
 *
 * <p>
 * Implementations use this when the collapsed tab itself is the shortcut target and the action should be handled
 * before the event reaches surrounding UI elements.
 */
public interface LDLib2FancyCustomMouseWheelAction {

    /**
     * Handles a mouse-wheel event for the owning Fancy tab.
     *
     * @param event LDLib2 mouse-wheel event carrying cursor and wheel delta data.
     * @return {@code true} when the shortcut consumed the event.
     */
    boolean mouseWheelMove(UIEvent event);
}

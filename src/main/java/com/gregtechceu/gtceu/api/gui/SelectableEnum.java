package com.gregtechceu.gtceu.api.gui;

import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

/**
 * Display contract for GTM enum selectors shared by legacy widgets and LDLib2 elements.
 */
public interface SelectableEnum {

    /**
     * Returns the tooltip translation key shown by GTM enum selectors.
     */
    String getTooltip();

    /**
     * Returns the icon shown by GTM enum selectors.
     */
    IGuiTexture getIcon();
}

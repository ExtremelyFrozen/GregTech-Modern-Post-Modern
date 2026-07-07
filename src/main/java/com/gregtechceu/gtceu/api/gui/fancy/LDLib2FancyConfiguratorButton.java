package com.gregtechceu.gtceu.api.gui.fancy;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

import net.minecraft.network.chat.Component;

/**
 * Describes a collapsed Fancy configurator that reacts to a local LDLib2 click instead of opening a body.
 *
 * <p>Implementations that need to mutate server state must dispatch a GTM action packet from the click handler.
 */
public interface LDLib2FancyConfiguratorButton extends LDLib2FancyConfigurator {

    /**
     * Handles the local click event for this configurator button.
     */
    void onClick(UIEvent event);

    /**
     * Button configurators do not own expandable content, so they do not expose a tab title by default.
     */
    @Override
    default Component getTitle() {
        throw new UnsupportedOperationException("Button configurators do not expose a tab title.");
    }

    /**
     * Button configurators execute click handlers directly instead of creating a nested configurator element.
     */
    @Override
    default UIElement createLDLib2Configurator() {
        throw new UnsupportedOperationException("Button configurators do not create a nested configurator element.");
    }
}

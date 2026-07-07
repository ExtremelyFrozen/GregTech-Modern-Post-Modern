package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Describes an expandable LDLib2 Fancy configurator.
 *
 * <p>The configurator owns only UI construction and metadata. Field persistence and business actions must be wired
 * through GTM sync fields or GTM action packets by concrete callers.
 */
public interface LDLib2FancyConfigurator {

    /**
     * Returns the title shown in the expanded configurator header.
     */
    Component getTitle();

    /**
     * Returns the icon shown on the configurator tab button.
     */
    IGuiTexture getIcon();

    /**
     * Returns the fixed content width for the expanded configurator body.
     */
    default int getLDLib2ConfiguratorWidth() {
        return 120;
    }

    /**
     * Returns the fixed content height for the expanded configurator body.
     */
    default int getLDLib2ConfiguratorHeight() {
        return 80;
    }

    /**
     * Builds the LDLib2 element tree for the expanded configurator body.
     */
    UIElement createLDLib2Configurator();

    /**
     * Returns tooltip text for the collapsed configurator tab.
     */
    default List<Component> getTooltips() {
        return List.of(getTitle());
    }
}

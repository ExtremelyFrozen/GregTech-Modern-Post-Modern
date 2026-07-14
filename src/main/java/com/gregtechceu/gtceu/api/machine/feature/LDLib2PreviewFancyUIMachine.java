package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyPreviewPage;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

/**
 * Supplies the LDLib2 default preview page for Fancy machines without a dedicated page body.
 *
 * <p>
 * This opt-in contract mirrors the legacy fallback preview while keeping {@link LDLib2FancyUIMachine} explicit:
 * concrete machines must choose this fallback instead of silently receiving a placeholder page.
 */
public interface LDLib2PreviewFancyUIMachine extends LDLib2FancyUIMachine {

    /**
     * Width of the legacy fallback preview content.
     */
    int PREVIEW_PAGE_WIDTH = LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH;

    /**
     * Height of the legacy fallback preview content.
     */
    int PREVIEW_PAGE_HEIGHT = LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT;

    /**
     * Builds a fixed-size LDLib2 preview page for this machine.
     *
     * @param shell Fancy shell that owns this page; the preview does not need shell state.
     * @return page root containing a client-side LDLib2 scene when the machine is remote.
     */
    @Override
    default UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
        return LDLib2FancyPreviewPage.createPreviewElement(self());
    }

    /**
     * Returns the fixed preview content width used by the Fancy shell.
     *
     * @return preview page width in pixels.
     */
    @Override
    default int getLDLib2PageWidth() {
        return PREVIEW_PAGE_WIDTH;
    }

    /**
     * Returns the fixed preview content height used by the Fancy shell.
     *
     * @return preview page height in pixels.
     */
    @Override
    default int getLDLib2PageHeight() {
        return PREVIEW_PAGE_HEIGHT;
    }
}

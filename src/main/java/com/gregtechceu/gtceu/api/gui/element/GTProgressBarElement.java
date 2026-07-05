package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.texture.GuiTextureMetadata;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import net.minecraft.MethodsReturnNonnullByDefault;

import org.w3c.dom.Element;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 progress bar element that preserves GTM recipe texture metadata.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-progress-bar", group = "gtm", registry = "ldlib2:ui_element")
public class GTProgressBarElement extends ProgressBar {

    public GTProgressBarElement() {
        barContainer.layout(layout -> layout.paddingAll(0));
        barContainer.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
    }

    @Override
    public void loadXml(Element element) {
        super.loadXml(element);
        if (element.hasAttribute("fill-direction")) {
            setFillDirection(element.getAttribute("fill-direction"));
        }
        if (element.hasAttribute("legacy-empty-bar")) {
            barBackground.style(style -> style.backgroundTexture(
                    GuiTextureMetadata.parseImageTexture(element.getAttribute("legacy-empty-bar"))));
        }
        if (element.hasAttribute("legacy-filled-bar")) {
            bar.style(style -> style.backgroundTexture(
                    GuiTextureMetadata.parseImageTexture(element.getAttribute("legacy-filled-bar"))));
        }
    }

    private void setFillDirection(String value) {
        try {
            progressBarStyle(style -> style.fillDirection(FillDirection.valueOf(value)));
        } catch (IllegalArgumentException e) {
            GTCEu.LOGGER.error("Invalid GTM progress bar fill direction '{}'", value, e);
            throw e;
        }
    }
}

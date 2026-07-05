package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.texture.GuiTextureMetadata;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import net.minecraft.MethodsReturnNonnullByDefault;

import org.w3c.dom.Element;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 container for GTM recipe progress groups converted from legacy dual progress widgets.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-dual-progress", group = "gtm", registry = "ldlib2:ui_element")
public class GTDualProgressElement extends UIElement {

    private IGuiTexture background = IGuiTexture.EMPTY;
    private float splitPoint;

    public float getSplitPoint() {
        return splitPoint;
    }

    @Override
    public void loadXml(Element element) {
        if (element.hasAttribute("split-point")) {
            splitPoint = parseSplitPoint(element.getAttribute("split-point"));
        }
        if (element.hasAttribute("legacy-background")) {
            background = GuiTextureMetadata.parseImageTexture(element.getAttribute("legacy-background"));
        }
        super.loadXml(element);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        super.drawBackgroundAdditional(guiContext);
        background.draw(guiContext, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
    }

    private float parseSplitPoint(String value) {
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            GTCEu.LOGGER.error("Invalid GTM dual progress split point '{}'", value, e);
            throw e;
        }
    }
}

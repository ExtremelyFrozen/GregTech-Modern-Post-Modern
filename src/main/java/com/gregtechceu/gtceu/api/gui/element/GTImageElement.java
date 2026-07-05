package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.api.gui.texture.GuiTextureMetadata;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import net.minecraft.MethodsReturnNonnullByDefault;

import org.w3c.dom.Element;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 element for static GTM image metadata converted from legacy recipe UI definitions.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-image", group = "gtm", registry = "ldlib2:ui_element")
public class GTImageElement extends UIElement {

    private IGuiTexture texture = IGuiTexture.EMPTY;

    @Override
    public void loadXml(Element element) {
        if (element.hasAttribute("legacy-background")) {
            texture = GuiTextureMetadata.parseImageTexture(element.getAttribute("legacy-background"));
        }
        super.loadXml(element);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        super.drawBackgroundAdditional(guiContext);
        texture.draw(guiContext, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
    }
}

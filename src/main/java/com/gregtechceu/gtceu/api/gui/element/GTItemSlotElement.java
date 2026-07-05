package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.api.gui.texture.GuiTextureMetadata;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;

import net.minecraft.MethodsReturnNonnullByDefault;

import org.w3c.dom.Element;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 item slot element for GTM recipe XML metadata.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-item-slot", group = "gtm", registry = "ldlib2:ui_element")
public class GTItemSlotElement extends ItemSlot {

    @Override
    public void loadXml(Element element) {
        super.loadXml(element);
        if (element.hasAttribute("legacy-background")) {
            getStyle().backgroundTexture(
                    GuiTextureMetadata.parseImageTexture(element.getAttribute("legacy-background")));
        }
        if (element.hasAttribute("legacy-overlay")) {
            slotStyle(style -> style.slotOverlay(
                    GuiTextureMetadata.parseImageTexture(element.getAttribute("legacy-overlay"))));
        }
        if (element.hasAttribute("draw-hover-overlay") && !XmlUtils.getAsBoolean(element, "draw-hover-overlay", true)) {
            slotStyle(style -> style.hoverOverlay(IGuiTexture.EMPTY));
        }
        if (element.hasAttribute("draw-hover-tips")) {
            slotStyle(style -> style.showItemTooltips(XmlUtils.getAsBoolean(element, "draw-hover-tips", true)));
        }
    }
}

package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Consumer;

/**
 * LDLib2 button facade for GTM pixel-positioned textured buttons.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-button", group = "gtm", registry = "ldlib2:ui_element")
public class GTButtonElement extends Button {

    public GTButtonElement() {}

    public GTButtonElement(int x, int y, int width, int height, IGuiTexture texture,
                           Consumer<UIEvent> clickHandler) {
        setButtonTexture(texture);
        setOnClick(clickHandler::accept);
        UITemplate.setLDLib2Bounds(this, x, y, width, height);
    }

    public GTButtonElement setButtonTexture(IGuiTexture texture) {
        buttonStyle(style -> style
                .baseTexture(texture)
                .hoverTexture(texture)
                .pressedTexture(texture));
        return this;
    }
}

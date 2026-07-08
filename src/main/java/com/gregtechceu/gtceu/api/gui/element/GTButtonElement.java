package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button.ButtonStyle;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement.TextStyle;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.style.LayoutStyle;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 button facade for GTM pixel-positioned textured buttons.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-button", group = "gtm", registry = "ldlib2:ui_element")
public class GTButtonElement extends Button {

    public GTButtonElement() {}

    public GTButtonElement(int x, int y, int width, int height) {
        UITemplate.setLDLib2Bounds(this, x, y, width, height);
    }

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

    public GTButtonElement setButtonTextures(IGuiTexture baseTexture, IGuiTexture hoverTexture,
                                             IGuiTexture pressedTexture) {
        buttonStyle(style -> style
                .baseTexture(baseTexture)
                .hoverTexture(hoverTexture)
                .pressedTexture(pressedTexture));
        return this;
    }

    @Override
    public GTButtonElement setText(Component text) {
        super.setText(text);
        return this;
    }

    @Override
    public GTButtonElement setText(String text) {
        super.setText(text);
        return this;
    }

    @Override
    public GTButtonElement setText(String text, boolean translate) {
        super.setText(text, translate);
        return this;
    }

    @Override
    public GTButtonElement textStyle(Consumer<TextStyle> style) {
        super.textStyle(style);
        return this;
    }

    @Override
    public GTButtonElement buttonStyle(Consumer<ButtonStyle> style) {
        super.buttonStyle(style);
        return this;
    }

    @Override
    public GTButtonElement noText() {
        super.noText();
        return this;
    }

    @Override
    public GTButtonElement enableText() {
        super.enableText();
        return this;
    }

    @Override
    public GTButtonElement layout(Consumer<LayoutStyle> layout) {
        super.layout(layout);
        return this;
    }
}

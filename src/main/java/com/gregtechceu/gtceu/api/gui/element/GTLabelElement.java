package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.api.gui.UITemplate;

import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement.TextStyle;
import com.lowdragmc.lowdraglib2.gui.ui.style.LayoutStyle;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 label facade for GTM pixel-positioned text elements.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-label", group = "gtm", registry = "ldlib2:ui_element")
public class GTLabelElement extends Label {

    public GTLabelElement() {}

    public GTLabelElement(Component text) {
        setText(text);
    }

    public GTLabelElement(String text, boolean translate) {
        setText(text, translate);
    }

    public GTLabelElement(int x, int y, int width, int height) {
        UITemplate.setLDLib2Bounds(this, x, y, width, height);
    }

    public GTLabelElement(int x, int y, int width, int height, Component text) {
        this(x, y, width, height);
        setText(text);
    }

    public GTLabelElement(int x, int y, int width, int height, String text, boolean translate) {
        this(x, y, width, height);
        setText(text, translate);
    }

    @Override
    public GTLabelElement setValue(@Nullable Component value) {
        super.setValue(value);
        return this;
    }

    @Override
    public GTLabelElement setText(Component text) {
        super.setText(text);
        return this;
    }

    @Override
    public GTLabelElement setText(String text) {
        super.setText(text);
        return this;
    }

    @Override
    public GTLabelElement setText(String text, boolean translate) {
        super.setText(text, translate);
        return this;
    }

    @Override
    public GTLabelElement textStyle(Consumer<TextStyle> style) {
        super.textStyle(style);
        return this;
    }

    @Override
    public GTLabelElement layout(Consumer<LayoutStyle> layout) {
        super.layout(layout);
        return this;
    }

    public GTLabelElement setTextColor(int color) {
        textStyle(style -> style.textColor(color));
        return this;
    }

    public GTLabelElement setTextShadow(boolean textShadow) {
        textStyle(style -> style.textShadow(textShadow));
        return this;
    }

    public GTLabelElement setTextWrap(TextWrap textWrap) {
        textStyle(style -> style.textWrap(textWrap));
        return this;
    }

    public GTLabelElement setTextAlignHorizontal(Horizontal horizontal) {
        textStyle(style -> style.textAlignHorizontal(horizontal));
        return this;
    }

    public GTLabelElement setTextAlignVertical(Vertical vertical) {
        textStyle(style -> style.textAlignVertical(vertical));
        return this;
    }

    public GTLabelElement setFontSize(float fontSize) {
        textStyle(style -> style.fontSize(fontSize));
        return this;
    }
}

package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 integer input facade for GTM pixel-positioned numeric controls.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-int-input", group = "gtm", registry = "ldlib2:ui_element")
public class GTIntInputElement extends UIElement {

    private static final int STEP_REGULAR = 1;
    private static final int STEP_SHIFT = 8;
    private static final int STEP_CTRL = 64;
    private static final int STEP_CTRL_SHIFT = 512;

    private final IntSupplier valueSupplier;
    private final IntConsumer valueConsumer;
    private final GTTextFieldElement textField;

    private int min;
    private int max = Integer.MAX_VALUE;

    public GTIntInputElement(int x, int y, int width, int height, IntSupplier valueSupplier,
                             IntConsumer valueConsumer) {
        this.valueSupplier = valueSupplier;
        this.valueConsumer = valueConsumer;
        UITemplate.setLDLib2Bounds(this, x, y, width, height);

        int buttonWidth = Mth.clamp(width / 5, 15, 40);
        int textFieldWidth = width - 2 * buttonWidth - 4;

        addChild(createStepButton(0, 0, buttonWidth, height, "-", -1));
        this.textField = createTextField(buttonWidth + 2, 0, textFieldWidth, height);
        addChild(textField);
        addChild(createStepButton(buttonWidth + textFieldWidth + 4, 0, buttonWidth, height, "+", 1));
        refreshText(false);
    }

    public GTIntInputElement setMin(int min) {
        this.min = min;
        if (max < min) {
            max = min;
        }
        updateTextFieldRange();
        setValue(valueSupplier.getAsInt());
        return this;
    }

    public GTIntInputElement setMax(int max) {
        this.max = max;
        if (min > max) {
            min = max;
        }
        updateTextFieldRange();
        setValue(valueSupplier.getAsInt());
        return this;
    }

    public GTIntInputElement setValue(int value) {
        int clamped = clamp(value);
        if (clamped != valueSupplier.getAsInt()) {
            valueConsumer.accept(clamped);
        }
        refreshText(false);
        return this;
    }

    @Override
    public void screenTick() {
        super.screenTick();
        if (!textField.isFocused()) {
            refreshText(false);
        }
    }

    private GTButtonElement createStepButton(int x, int y, int width, int height, String text, int direction) {
        GTButtonElement button = new GTButtonElement(x, y, width, height, GuiTextures.VANILLA_BUTTON,
                event -> changeValue(direction));
        button.setText(text, false);
        button.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER));
        button.style(style -> style.tooltips(Component.translatable("gui.widget.incrementButton.default_tooltip")));
        return button;
    }

    private GTTextFieldElement createTextField(int x, int y, int width, int height) {
        GTTextFieldElement field = new GTTextFieldElement(x, y, width, height);
        field.setNumbersOnlyInt(min, max);
        field.textFieldStyle(style -> style
                .textColor(0x404040)
                .textShadow(false));
        field.setTextResponder(this::onTextChanged);
        return field;
    }

    private void updateTextFieldRange() {
        textField.setNumbersOnlyInt(min, max);
    }

    private void changeValue(int direction) {
        long next = (long) valueSupplier.getAsInt() + (long) currentStep() * direction;
        setValue(clamp(next));
    }

    private int currentStep() {
        if (isCtrlDown()) {
            return isShiftDown() ? STEP_CTRL_SHIFT : STEP_CTRL;
        }
        return isShiftDown() ? STEP_SHIFT : STEP_REGULAR;
    }

    private void onTextChanged(String text) {
        try {
            setValue(Integer.parseInt(text));
        } catch (NumberFormatException e) {
            GTCEu.LOGGER.error("Invalid GTM integer input value: {}", text, e);
        }
    }

    private int clamp(long value) {
        return (int) Math.max(min, Math.min(max, value));
    }

    private int clamp(int value) {
        return Mth.clamp(value, min, max);
    }

    private void refreshText(boolean notify) {
        String value = Integer.toString(clamp(valueSupplier.getAsInt()));
        if (!value.equals(textField.getText())) {
            textField.setText(value, notify);
        }
    }
}

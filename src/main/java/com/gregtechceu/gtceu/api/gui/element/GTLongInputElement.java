package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.utils.GTMath;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Runtime-bound LDLib2 long input facade for GTM pixel-positioned numeric controls.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class GTLongInputElement extends UIElement {

    private static final long STEP_REGULAR = 1L;
    private static final long STEP_SHIFT = 8L;
    private static final long STEP_CTRL = 64L;
    private static final long STEP_CTRL_SHIFT = 512L;

    private final LongSupplier valueSupplier;
    private final LongConsumer valueConsumer;
    private final GTTextFieldElement textField;

    private long min;
    private long max = Long.MAX_VALUE;

    public GTLongInputElement(int x, int y, int width, int height, LongSupplier valueSupplier,
                              LongConsumer valueConsumer) {
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

    public GTLongInputElement setMin(long min) {
        this.min = min;
        if (max < min) {
            max = min;
        }
        updateTextFieldRange();
        setValue(valueSupplier.getAsLong());
        return this;
    }

    public GTLongInputElement setMax(long max) {
        this.max = max;
        if (min > max) {
            min = max;
        }
        updateTextFieldRange();
        setValue(valueSupplier.getAsLong());
        return this;
    }

    public GTLongInputElement setValue(long value) {
        long clamped = clamp(value);
        if (clamped != valueSupplier.getAsLong()) {
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
        field.setNumbersOnlyLong(min, max);
        field.textFieldStyle(style -> style
                .textColor(0x404040)
                .textShadow(false));
        field.setTextResponder(this::onTextChanged);
        return field;
    }

    private void updateTextFieldRange() {
        textField.setNumbersOnlyLong(min, max);
    }

    private void changeValue(int direction) {
        setValue(addClamped(valueSupplier.getAsLong(), currentStep() * direction));
    }

    private long currentStep() {
        if (isCtrlDown()) {
            return isShiftDown() ? STEP_CTRL_SHIFT : STEP_CTRL;
        }
        return isShiftDown() ? STEP_SHIFT : STEP_REGULAR;
    }

    private void onTextChanged(String text) {
        try {
            setValue(Long.parseLong(text));
        } catch (NumberFormatException e) {
            GTCEu.LOGGER.error("Invalid GTM long input value: {}", text, e);
        }
    }

    private long addClamped(long value, long delta) {
        if (delta > 0 && value > Long.MAX_VALUE - delta) {
            return max;
        }
        if (delta < 0 && value < Long.MIN_VALUE - delta) {
            return min;
        }
        return clamp(value + delta);
    }

    private long clamp(long value) {
        return GTMath.clamp(value, min, max);
    }

    private void refreshText(boolean notify) {
        String value = Long.toString(clamp(valueSupplier.getAsLong()));
        if (!value.equals(textField.getText())) {
            textField.setText(value, notify);
        }
    }
}

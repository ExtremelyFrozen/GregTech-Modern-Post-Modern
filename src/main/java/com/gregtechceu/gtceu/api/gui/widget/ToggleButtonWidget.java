package com.gregtechceu.gtceu.api.gui.widget;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.data.lang.LangHandler;

import com.lowdragmc.lowdraglib.gui.widget.SwitchWidget;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;

import java.util.List;
import java.util.function.BooleanSupplier;

public class ToggleButtonWidget extends SwitchWidget {

    private final IGuiTexture texture;
    private String tooltipText;
    private boolean isMultiLang;

    public ToggleButtonWidget(int xPosition, int yPosition, int width, int height, BooleanSupplier isPressedCondition,
                              BooleanConsumer setPressedExecutor) {
        this(xPosition, yPosition, width, height, GuiTextures.VANILLA_BUTTON, isPressedCondition, setPressedExecutor);
    }

    public ToggleButtonWidget(int xPosition, int yPosition, int width, int height, IGuiTexture buttonTexture,
                              BooleanSupplier isPressedCondition, BooleanConsumer setPressedExecutor) {
        super(xPosition, yPosition, width, height,
                (clickData, aBoolean) -> setPressedExecutor.accept(aBoolean.booleanValue()));
        texture = buttonTexture;
        setTexture(GuiTextures.buttonState(buttonTexture, false), GuiTextures.buttonState(buttonTexture, true));

        setSupplier(isPressedCondition::getAsBoolean);
    }

    public ToggleButtonWidget setShouldUseBaseBackground() {
        if (texture != null) {
            setTexture(
                    GuiTextures.group(GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0, 1, 0.5), texture),
                    GuiTextures.group(GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, 0.5, 1, 0.5), texture));
        }
        return this;
    }

    public ToggleButtonWidget setTooltipText(String tooltipText) {
        this.tooltipText = tooltipText;
        updateHoverTooltips();
        return this;
    }

    public ToggleButtonWidget isMultiLang() {
        isMultiLang = true;
        updateHoverTooltips();
        return this;
    }

    protected void updateHoverTooltips() {
        if (tooltipText != null) {
            if (!isMultiLang) {
                setHoverTooltips(tooltipText + (isPressed ? ".enabled" : ".disabled"));
            } else {
                setHoverTooltips(
                        List.copyOf(LangHandler.getMultiLang(tooltipText + (isPressed ? ".enabled" : ".disabled"))));
            }
        }
    }
}

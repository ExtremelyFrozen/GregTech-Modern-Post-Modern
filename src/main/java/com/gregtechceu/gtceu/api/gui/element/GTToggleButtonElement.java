package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.data.lang.LangHandler;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 toggle button facade that preserves GTM's legacy pressed/unpressed texture semantics.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-toggle-button", group = "gtm", registry = "ldlib2:ui_element")
public class GTToggleButtonElement extends Button {

    private IGuiTexture texture = GuiTextures.VANILLA_BUTTON;
    private BooleanSupplier pressedSupplier = () -> false;
    private Consumer<Boolean> pressedConsumer = pressed -> {};
    private boolean useBaseBackground;
    private boolean lastPressed;
    private String tooltipText;
    private boolean multiLang;

    public GTToggleButtonElement() {
        noText();
        setOnClick(event -> setPressed(!pressedSupplier.getAsBoolean()));
        refreshState();
    }

    public GTToggleButtonElement(int x, int y, int width, int height, BooleanSupplier pressedSupplier,
                                 Consumer<Boolean> pressedConsumer) {
        this(x, y, width, height, GuiTextures.VANILLA_BUTTON, pressedSupplier, pressedConsumer);
    }

    public GTToggleButtonElement(int x, int y, int width, int height, IGuiTexture texture,
                                 BooleanSupplier pressedSupplier, Consumer<Boolean> pressedConsumer) {
        this();
        setPressedSupplier(pressedSupplier);
        setPressedConsumer(pressedConsumer);
        setToggleTexture(texture);
        UITemplate.setLDLib2Bounds(this, x, y, width, height);
    }

    public GTToggleButtonElement setPressedSupplier(BooleanSupplier pressedSupplier) {
        this.pressedSupplier = pressedSupplier;
        refreshState();
        return this;
    }

    public GTToggleButtonElement setPressedConsumer(Consumer<Boolean> pressedConsumer) {
        this.pressedConsumer = pressedConsumer;
        return this;
    }

    public GTToggleButtonElement setToggleTexture(IGuiTexture texture) {
        this.texture = texture;
        refreshState();
        return this;
    }

    public GTToggleButtonElement setShouldUseBaseBackground() {
        this.useBaseBackground = true;
        refreshState();
        return this;
    }

    public GTToggleButtonElement setTooltipText(String tooltipText) {
        this.tooltipText = tooltipText;
        updateTooltips();
        return this;
    }

    public GTToggleButtonElement isMultiLang() {
        this.multiLang = true;
        updateTooltips();
        return this;
    }

    @Override
    public void screenTick() {
        boolean pressed = pressedSupplier.getAsBoolean();
        if (pressed != lastPressed) {
            refreshState();
        }
        super.screenTick();
    }

    private void setPressed(boolean pressed) {
        pressedConsumer.accept(pressed);
        refreshState();
    }

    private void refreshState() {
        lastPressed = pressedSupplier.getAsBoolean();
        IGuiTexture stateTexture = getStateTexture(lastPressed);
        buttonStyle(style -> style
                .baseTexture(stateTexture)
                .hoverTexture(stateTexture)
                .pressedTexture(stateTexture));
        updateTooltips();
    }

    private IGuiTexture getStateTexture(boolean pressed) {
        if (useBaseBackground) {
            IGuiTexture background = GuiTextures.TOGGLE_BUTTON_BACK.getSubTexture(0, pressed ? 0.5 : 0, 1, 0.5);
            return GuiTextures.group(background, texture);
        }
        return GuiTextures.buttonState(texture, pressed);
    }

    private void updateTooltips() {
        if (tooltipText == null) {
            return;
        }
        String tooltipKey = tooltipText + (lastPressed ? ".enabled" : ".disabled");
        if (multiLang) {
            List<Component> tooltips = List.copyOf(LangHandler.getMultiLang(tooltipKey));
            style(style -> style.tooltips(tooltips.toArray(Component[]::new)));
        } else {
            style(style -> style.tooltips(tooltipKey));
        }
    }
}

package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.api.gui.UITemplate;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import net.minecraft.MethodsReturnNonnullByDefault;

import java.util.function.Consumer;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 text field facade for GTM pixel-positioned input fields.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-text-field", group = "gtm", registry = "ldlib2:ui_element")
public class GTTextFieldElement extends TextField {

    public GTTextFieldElement() {}

    public GTTextFieldElement(int x, int y, int width, int height) {
        UITemplate.setLDLib2Bounds(this, x, y, width, height);
    }

    @Override
    public GTTextFieldElement setAnyString() {
        super.setAnyString();
        return this;
    }

    @Override
    public GTTextFieldElement setText(String text, boolean notify) {
        super.setText(text, notify);
        return this;
    }

    @Override
    public GTTextFieldElement setTextResponder(Consumer<String> textResponder) {
        super.setTextResponder(textResponder);
        return this;
    }

    /**
     * Keeps LDLib2's font-dependent caret placement on the logical client while preserving common event propagation.
     */
    @Override
    protected void onMouseDown(UIEvent event) {
        if (LDLib2.isRemote()) {
            super.onMouseDown(event);
        }
    }
}

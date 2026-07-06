package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.api.gui.UITemplate;

import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import net.minecraft.MethodsReturnNonnullByDefault;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 scroller view facade for GTM pixel-positioned scroll containers.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-scroller-view", group = "gtm", registry = "ldlib2:ui_element")
public class GTScrollerViewElement extends ScrollerView {

    public GTScrollerViewElement() {}

    public GTScrollerViewElement(int x, int y, int width, int height) {
        UITemplate.setLDLib2Bounds(this, x, y, width, height);
    }
}

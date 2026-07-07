package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.texture.GuiTextureMetadata;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import net.minecraft.MethodsReturnNonnullByDefault;

import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.BooleanSupplier;

/**
 * LDLib2 element for static GTM image metadata converted from legacy recipe UI definitions.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-image", group = "gtm", registry = "ldlib2:ui_element")
public class GTImageElement extends UIElement {

    private IGuiTexture texture = IGuiTexture.EMPTY;
    private @Nullable BooleanSupplier visibleSupplier;

    public GTImageElement() {}

    public GTImageElement(IGuiTexture texture) {
        setTexture(texture);
    }

    public GTImageElement(int x, int y, int width, int height, IGuiTexture texture) {
        this(texture);
        UITemplate.setLDLib2Bounds(this, x, y, width, height);
    }

    public GTImageElement setTexture(IGuiTexture texture) {
        this.texture = texture;
        return this;
    }

    public GTImageElement setVisibleSupplier(BooleanSupplier visibleSupplier) {
        this.visibleSupplier = visibleSupplier;
        setVisible(visibleSupplier.getAsBoolean());
        return this;
    }

    @Override
    public void loadXml(Element element) {
        if (element.hasAttribute("legacy-background")) {
            texture = GuiTextureMetadata.parseImageTexture(element.getAttribute("legacy-background"));
        }
        super.loadXml(element);
    }

    @Override
    public void screenTick() {
        if (visibleSupplier != null) {
            setVisible(visibleSupplier.getAsBoolean());
        }
        super.screenTick();
    }

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        super.drawBackgroundAdditional(guiContext);
        texture.draw(guiContext, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
    }
}

package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.texture.GuiTextureMetadata;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import net.minecraft.MethodsReturnNonnullByDefault;

import org.w3c.dom.Element;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * LDLib2 progress bar element that preserves GTM recipe texture metadata.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-progress-bar", group = "gtm", registry = "ldlib2:ui_element")
public class GTProgressBarElement extends ProgressBar {

    private DoubleSupplier progressSupplier;

    public GTProgressBarElement() {
        barContainer.layout(layout -> layout.paddingAll(0));
        barContainer.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
    }

    public GTProgressBarElement(DoubleSupplier progressSupplier) {
        this();
        setProgressSupplier(progressSupplier);
    }

    public GTProgressBarElement setProgressSupplier(DoubleSupplier progressSupplier) {
        this.progressSupplier = progressSupplier;
        return this;
    }

    public GTProgressBarElement setProgressSupplier(Supplier<Double> progressSupplier) {
        DoubleSupplier doubleSupplier = progressSupplier::get;
        return setProgressSupplier(doubleSupplier);
    }

    @Override
    public void loadXml(Element element) {
        super.loadXml(element);
        if (element.hasAttribute("fill-direction")) {
            setFillDirection(element.getAttribute("fill-direction"));
        }
        if (element.hasAttribute("legacy-empty-bar")) {
            barBackground.style(style -> style.backgroundTexture(
                    GuiTextureMetadata.parseImageTexture(element.getAttribute("legacy-empty-bar"))));
        }
        if (element.hasAttribute("legacy-filled-bar")) {
            bar.style(style -> style.backgroundTexture(
                    GuiTextureMetadata.parseImageTexture(element.getAttribute("legacy-filled-bar"))));
        }
    }

    @Override
    public void screenTick() {
        if (progressSupplier != null) {
            setProgress(clampProgress(progressSupplier.getAsDouble()));
        }
        super.screenTick();
    }

    private void setFillDirection(String value) {
        try {
            progressBarStyle(style -> style.fillDirection(FillDirection.valueOf(value)));
        } catch (IllegalArgumentException e) {
            GTCEu.LOGGER.error("Invalid GTM progress bar fill direction '{}'", value, e);
            throw e;
        }
    }

    private float clampProgress(double progress) {
        if (Double.isNaN(progress)) {
            GTCEu.LOGGER.error("Invalid GTM progress supplier value '{}'", progress);
            throw new IllegalArgumentException("Invalid progress supplier value: " + progress);
        }
        return (float) Math.max(0, Math.min(1, progress));
    }
}

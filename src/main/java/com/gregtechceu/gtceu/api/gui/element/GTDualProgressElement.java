package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.texture.GuiTextureMetadata;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import net.minecraft.MethodsReturnNonnullByDefault;

import org.w3c.dom.Element;

import java.util.function.DoubleSupplier;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 container for GTM recipe progress groups converted from legacy dual progress widgets.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-dual-progress", group = "gtm", registry = "ldlib2:ui_element")
public class GTDualProgressElement extends UIElement {

    private IGuiTexture background = IGuiTexture.EMPTY;
    private float splitPoint = 0.5f;
    private DoubleSupplier progressSupplier;

    public float getSplitPoint() {
        return splitPoint;
    }

    public GTDualProgressElement setProgressSupplier(DoubleSupplier progressSupplier) {
        this.progressSupplier = progressSupplier;
        bindProgressChildren();
        return this;
    }

    @Override
    public void loadXml(Element element) {
        if (element.hasAttribute("split-point")) {
            splitPoint = parseSplitPoint(element.getAttribute("split-point"));
        }
        if (element.hasAttribute("legacy-background")) {
            background = GuiTextureMetadata.parseImageTexture(element.getAttribute("legacy-background"));
        }
        super.loadXml(element);
        bindProgressChildren();
    }

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        super.drawBackgroundAdditional(guiContext);
        background.draw(guiContext, getPositionX(), getPositionY(), getSizeWidth(), getSizeHeight());
    }

    private float parseSplitPoint(String value) {
        try {
            float parsed = Float.parseFloat(value);
            if (!Float.isFinite(parsed) || parsed <= 0 || parsed >= 1) {
                GTCEu.LOGGER.error("GTM dual progress split point must be between 0 and 1, got '{}'", value);
                throw new IllegalArgumentException("Invalid dual progress split point: " + value);
            }
            return parsed;
        } catch (NumberFormatException e) {
            GTCEu.LOGGER.error("Invalid GTM dual progress split point '{}'", value, e);
            throw e;
        }
    }

    private void bindProgressChildren() {
        if (progressSupplier == null) {
            return;
        }
        var progressBars = getChildren().stream()
                .filter(GTProgressBarElement.class::isInstance)
                .map(GTProgressBarElement.class::cast)
                .toList();
        if (progressBars.size() != 2) {
            GTCEu.LOGGER.error("GTM dual progress element must have exactly 2 progress children, got {}",
                    progressBars.size());
            throw new IllegalStateException("Invalid GTM dual progress child count: " + progressBars.size());
        }
        progressBars.get(0).setProgressSupplier(this::firstProgress);
        progressBars.get(1).setProgressSupplier(this::secondProgress);
    }

    private double firstProgress() {
        double progress = progressSupplier.getAsDouble();
        return progress >= splitPoint ? 1 : progress / splitPoint;
    }

    private double secondProgress() {
        double progress = progressSupplier.getAsDouble();
        return progress >= splitPoint ? (progress - splitPoint) / (1 - splitPoint) : 0;
    }
}

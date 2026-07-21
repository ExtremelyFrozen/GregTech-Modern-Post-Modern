package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.texture.GuiTextureMetadata;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ProgressBar;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.w3c.dom.Element;

import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 progress bar element that preserves GTM recipe texture metadata.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-progress-bar", group = "gtm", registry = "ldlib2:ui_element")
public class GTProgressBarElement extends ProgressBar {

    private IGuiTexture emptyBarTexture = IGuiTexture.EMPTY;
    private IGuiTexture filledBarTexture = IGuiTexture.EMPTY;
    private DoubleSupplier progressSupplier;
    private float drawnProgress;

    public GTProgressBarElement() {
        barContainer.layout(layout -> layout.paddingAll(0));
        barContainer.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        barBackground.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        bar.style(style -> style.backgroundTexture(IGuiTexture.EMPTY));
        label.setText("");
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

    public GTProgressBarElement setProgressTexture(IGuiTexture emptyBar, IGuiTexture filledBar) {
        emptyBarTexture = emptyBar;
        filledBarTexture = filledBar;
        return this;
    }

    /**
     * Returns the texture drawn behind the filled progress region.
     *
     * @return the configured empty-bar texture
     */
    public IGuiTexture getEmptyBarTexture() {
        return emptyBarTexture;
    }

    /**
     * Returns the texture cropped to the current progress region.
     *
     * @return the configured filled-bar texture
     */
    public IGuiTexture getFilledBarTexture() {
        return filledBarTexture;
    }

    public GTProgressBarElement setFillDirection(FillDirection fillDirection) {
        progressBarStyle(style -> style.fillDirection(fillDirection));
        return this;
    }

    @Override
    public void loadXml(Element element) {
        super.loadXml(element);
        if (element.hasAttribute("fill-direction")) {
            setFillDirectionFromXml(element.getAttribute("fill-direction"));
        }
        if (element.hasAttribute("legacy-empty-bar")) {
            emptyBarTexture = GuiTextureMetadata.parseImageTexture(element.getAttribute("legacy-empty-bar"));
        }
        if (element.hasAttribute("legacy-filled-bar")) {
            filledBarTexture = GuiTextureMetadata.parseImageTexture(element.getAttribute("legacy-filled-bar"));
        }
    }

    @Override
    protected void updateProgressBarStyle(float normalizedValue) {
        drawnProgress = normalizedValue;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawBackgroundAdditional(GUIContext guiContext) {
        super.drawBackgroundAdditional(guiContext);
        float x = getPositionX();
        float y = getPositionY();
        float width = getSizeWidth();
        float height = getSizeHeight();
        emptyBarTexture.draw(guiContext, x, y, width, height);

        ProgressDrawArea drawArea = getProgressDrawArea(x, y, width, height);
        filledBarTexture.drawSubArea(guiContext.graphics, drawArea.x(), drawArea.y(), drawArea.width(),
                drawArea.height(), drawArea.drawnU(), drawArea.drawnV(), drawArea.drawnWidth(),
                drawArea.drawnHeight());
    }

    @Override
    public void screenTick() {
        if (progressSupplier != null) {
            setProgress(clampProgress(progressSupplier.getAsDouble()));
        }
        super.screenTick();
    }

    private void setFillDirectionFromXml(String value) {
        try {
            setFillDirection(FillDirection.valueOf(value));
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

    ProgressDrawArea getProgressDrawArea(float x, float y, float width, float height) {
        FillDirection fillDirection = getProgressBarStyle().fillDirection();
        float drawnU = (float) fillDirection.getDrawnU(drawnProgress);
        float drawnV = (float) fillDirection.getDrawnV(drawnProgress);
        float drawnWidth = (float) fillDirection.getDrawnWidth(drawnProgress);
        float drawnHeight = (float) fillDirection.getDrawnHeight(drawnProgress);
        return new ProgressDrawArea(x + drawnU * width, y + drawnV * height, width * drawnWidth,
                height * drawnHeight, drawnU, drawnV, drawnWidth, drawnHeight);
    }

    record ProgressDrawArea(float x, float y, float width, float height, float drawnU, float drawnV,
                            float drawnWidth, float drawnHeight) {}
}

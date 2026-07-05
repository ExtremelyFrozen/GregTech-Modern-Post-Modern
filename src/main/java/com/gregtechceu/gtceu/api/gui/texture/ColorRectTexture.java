package com.gregtechceu.gtceu.api.gui.texture;

import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.awt.Color;

/**
 * Solid color rectangle texture with the legacy GTM corner-radius helpers.
 */
public class ColorRectTexture extends TransformTexture {

    public int color;
    public float radiusLT;
    public float radiusLB;
    public float radiusRT;
    public float radiusRB;

    public ColorRectTexture() {
        this(0x4f0ffddf);
    }

    public ColorRectTexture(int color) {
        this.color = color;
    }

    public ColorRectTexture(Color color) {
        this.color = color.getRGB();
    }

    @Override
    public ColorRectTexture copy() {
        var copy = new ColorRectTexture(color);
        copy.radiusLT = radiusLT;
        copy.radiusLB = radiusLB;
        copy.radiusRT = radiusRT;
        copy.radiusRB = radiusRB;
        copy.copyTransform(this);
        return copy;
    }

    @Override
    public ColorRectTexture setColor(int color) {
        this.color = color;
        return this;
    }

    public ColorRectTexture setRadius(float radius) {
        this.radiusLB = radius;
        this.radiusRT = radius;
        this.radiusRB = radius;
        this.radiusLT = radius;
        return this;
    }

    public ColorRectTexture setLeftRadius(float radius) {
        this.radiusLB = radius;
        this.radiusLT = radius;
        return this;
    }

    public ColorRectTexture setRightRadius(float radius) {
        this.radiusRT = radius;
        this.radiusRB = radius;
        return this;
    }

    public ColorRectTexture setTopRadius(float radius) {
        this.radiusRT = radius;
        this.radiusLT = radius;
        return this;
    }

    public ColorRectTexture setBottomRadius(float radius) {
        this.radiusLB = radius;
        this.radiusRB = radius;
        return this;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawInternal(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width,
                                float height, float partialTicks) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (radiusLT <= 0 && radiusLB <= 0 && radiusRT <= 0 && radiusRB <= 0) {
            DrawerHelper.drawSolidRect(graphics, x, y, width, height, color);
            return;
        }

        float left = Math.max(radiusLT, radiusLB);
        float right = Math.max(radiusRT, radiusRB);
        float top = Math.max(radiusLT, radiusRT);
        float bottom = Math.max(radiusLB, radiusRB);
        DrawerHelper.drawSolidRect(graphics, x + left, y, Math.max(0, width - left - right), height, color);
        DrawerHelper.drawSolidRect(graphics, x, y + top, left, Math.max(0, height - top - bottom), color);
        DrawerHelper.drawSolidRect(graphics, x + width - right, y + top, right, Math.max(0, height - top - bottom),
                color);
        DrawerHelper.drawSolidRect(graphics, x + left, y, Math.max(0, width - left - right), top, color);
        DrawerHelper.drawSolidRect(graphics, x + left, y + height - bottom, Math.max(0, width - left - right), bottom,
                color);
    }
}

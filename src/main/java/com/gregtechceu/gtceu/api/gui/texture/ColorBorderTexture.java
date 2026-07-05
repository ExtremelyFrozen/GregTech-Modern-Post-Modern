package com.gregtechceu.gtceu.api.gui.texture;

import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.awt.Color;

/**
 * Solid color border texture with the LDLib2 drawing contract exposed through the GTM texture facade.
 */
public class ColorBorderTexture extends TransformTexture {

    public int color;
    public int border;

    public ColorBorderTexture() {
        this(-2, 0x4f0ffddf);
    }

    public ColorBorderTexture(int border, int color) {
        this.color = color;
        this.border = border;
    }

    public ColorBorderTexture(int border, Color color) {
        this.color = color.getRGB();
        this.border = border;
    }

    public ColorBorderTexture setBorder(int border) {
        this.border = border;
        return this;
    }

    @Override
    public ColorBorderTexture setColor(int color) {
        this.color = color;
        return this;
    }

    @Override
    public ColorBorderTexture copy() {
        var copy = new ColorBorderTexture(border, color);
        copy.copyTransform(this);
        return copy;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawInternal(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width,
                                float height, float partialTicks) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (border >= 0) {
            DrawerHelper.drawSolidRect(graphics, x - border, y + height, width + 2 * border, border, color);
            DrawerHelper.drawSolidRect(graphics, x - border, y, border, height, color);
            DrawerHelper.drawSolidRect(graphics, x + width, y, border, height, color);
            DrawerHelper.drawSolidRect(graphics, x - border, y - border, width + 2 * border, border, color);
            return;
        }
        float absBorder = Math.abs(border);
        DrawerHelper.drawSolidRect(graphics, x, y, width - absBorder, absBorder, color);
        DrawerHelper.drawSolidRect(graphics, x, y + absBorder, absBorder, height - absBorder, color);
        DrawerHelper.drawSolidRect(graphics, x + absBorder, y + height - absBorder, width - absBorder, absBorder,
                color);
        DrawerHelper.drawSolidRect(graphics, x + width - absBorder, y, absBorder, height - absBorder, color);
    }
}

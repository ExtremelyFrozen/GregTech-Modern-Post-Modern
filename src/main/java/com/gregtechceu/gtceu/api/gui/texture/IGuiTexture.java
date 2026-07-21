package com.gregtechceu.gtceu.api.gui.texture;

import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * GTM texture facade that keeps the old pixel-oriented draw contract while remaining usable by LDLib2.
 */
public interface IGuiTexture extends GTGuiTexture {

    EmptyTexture EMPTY = new EmptyTexture();

    @Override
    default IGuiTexture copy() {
        return this;
    }

    @Override
    default IGuiTexture setColor(int color) {
        return this;
    }

    @Override
    default IGuiTexture rotate(float degree) {
        return this;
    }

    @Override
    default IGuiTexture scale(float scale) {
        return this;
    }

    @Override
    default IGuiTexture transform(int xOffset, int yOffset) {
        return this;
    }

    @OnlyIn(Dist.CLIENT)
    default void draw(GuiGraphics graphics, int mouseX, int mouseY, float x, float y, int width, int height) {
        draw(graphics, mouseX, mouseY, x, y, width, height, 0);
    }

    @OnlyIn(Dist.CLIENT)
    default void updateTick() {}

    @OnlyIn(Dist.CLIENT)
    default void drawSubArea(GuiGraphics graphics, float x, float y, float width, float height, float drawnU,
                             float drawnV, float drawnWidth, float drawnHeight) {
        draw(graphics, 0, 0, x, y, (int) width, (int) height);
    }

    final class EmptyTexture implements IGuiTexture {

        @Override
        public IGuiTexture copy() {
            return this;
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public void draw(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width,
                         float height, float partialTicks) {}

        @Override
        @OnlyIn(Dist.CLIENT)
        public void draw(GUIContext context, float x, float y, float width, float height) {}
    }
}

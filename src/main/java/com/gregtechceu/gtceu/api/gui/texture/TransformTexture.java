package com.gregtechceu.gtceu.api.gui.texture;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.joml.Quaternionf;

/**
 * Pixel-oriented transform base used by GTM textures during the LDLib2 UI migration.
 */
public abstract class TransformTexture implements IGuiTexture {

    protected float xOffset;
    protected float yOffset;
    protected float scale = 1;
    protected float rotation;

    @Override
    public TransformTexture rotate(float degree) {
        rotation = degree;
        return this;
    }

    @Override
    public TransformTexture scale(float scale) {
        this.scale = scale;
        return this;
    }

    public TransformTexture transform(float xOffset, float yOffset) {
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        return this;
    }

    @Override
    public TransformTexture transform(int xOffset, int yOffset) {
        return transform((float) xOffset, (float) yOffset);
    }

    protected void copyTransform(TransformTexture texture) {
        this.xOffset = texture.xOffset;
        this.yOffset = texture.yOffset;
        this.scale = texture.scale;
        this.rotation = texture.rotation;
    }

    @OnlyIn(Dist.CLIENT)
    protected void preDraw(GuiGraphics graphics, float x, float y, float width, float height) {
        graphics.pose().pushPose();
        graphics.pose().translate(xOffset, yOffset, 0);
        graphics.pose().translate(x + width / 2f, y + height / 2f, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.pose().mulPose(new Quaternionf().rotationXYZ(0, 0, (float) Math.toRadians(rotation)));
        graphics.pose().translate(-x - width / 2f, -y - height / 2f, 0);
    }

    @OnlyIn(Dist.CLIENT)
    protected void postDraw(GuiGraphics graphics) {
        graphics.pose().popPose();
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public final void draw(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width,
                           float height, float partialTicks) {
        preDraw(graphics, x, y, width, height);
        drawInternal(graphics, mouseX, mouseY, x, y, width, height, partialTicks);
        postDraw(graphics);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public final void drawSubArea(GuiGraphics graphics, float x, float y, float width, float height, float drawnU,
                                  float drawnV, float drawnWidth, float drawnHeight) {
        preDraw(graphics, x, y, width, height);
        drawSubAreaInternal(graphics, x, y, width, height, drawnU, drawnV, drawnWidth, drawnHeight);
        postDraw(graphics);
    }

    @OnlyIn(Dist.CLIENT)
    protected abstract void drawInternal(GuiGraphics graphics, float mouseX, float mouseY, float x, float y,
                                         float width, float height, float partialTicks);

    @OnlyIn(Dist.CLIENT)
    protected void drawSubAreaInternal(GuiGraphics graphics, float x, float y, float width, float height, float drawnU,
                                       float drawnV, float drawnWidth, float drawnHeight) {
        drawInternal(graphics, 0, 0, x, y, width, height, 0);
    }
}

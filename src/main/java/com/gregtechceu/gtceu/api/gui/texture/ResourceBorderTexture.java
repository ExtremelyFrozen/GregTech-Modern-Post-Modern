package com.gregtechceu.gtceu.api.gui.texture;

import com.lowdragmc.lowdraglib2.math.Size;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Nine-sliced resource texture used by GTM GUI backgrounds and slots.
 */
public class ResourceBorderTexture extends ResourceTexture {

    public static final ResourceBorderTexture BORDERED_BACKGROUND = new ResourceBorderTexture(
            "gtpm:textures/gui/base/background.png", 16, 16, 4, 4);

    public Size borderSize;
    public Size imageSize;

    public ResourceBorderTexture() {
        this("gtpm:textures/gui/base/background.png", 16, 16, 4, 4);
    }

    public ResourceBorderTexture(String imageLocation, int imageWidth, int imageHeight, int cornerWidth,
                                 int cornerHeight) {
        super(imageLocation);
        borderSize = Size.of(cornerWidth, cornerHeight);
        imageSize = Size.of(imageWidth, imageHeight);
    }

    public ResourceBorderTexture setBorderSize(int width, int height) {
        borderSize = Size.of(width, height);
        return this;
    }

    public ResourceBorderTexture setImageSize(int width, int height) {
        imageSize = Size.of(width, height);
        return this;
    }

    @Override
    public ResourceBorderTexture copy() {
        var copy = new ResourceBorderTexture(imageLocation.toString(), imageSize.width, imageSize.height,
                borderSize.width, borderSize.height);
        copy.color = color;
        copy.dynamicColor = dynamicColor;
        copy.copyTransform(this);
        return copy;
    }

    @Override
    public ResourceBorderTexture setColor(int color) {
        super.setColor(color);
        return this;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawSubAreaInternal(GuiGraphics graphics, float x, float y, float width, float height, float drawnU,
                                       float drawnV, float drawnWidth, float drawnHeight) {
        float cornerWidth = borderSize.width * 1f / imageSize.width;
        float cornerHeight = borderSize.height * 1f / imageSize.height;

        super.drawSubAreaInternal(graphics, x, y, borderSize.width, borderSize.height, 0, 0, cornerWidth,
                cornerHeight);
        super.drawSubAreaInternal(graphics, x + width - borderSize.width, y, borderSize.width, borderSize.height,
                1 - cornerWidth, 0, cornerWidth, cornerHeight);
        super.drawSubAreaInternal(graphics, x, y + height - borderSize.height, borderSize.width, borderSize.height,
                0, 1 - cornerHeight, cornerWidth, cornerHeight);
        super.drawSubAreaInternal(graphics, x + width - borderSize.width, y + height - borderSize.height,
                borderSize.width, borderSize.height, 1 - cornerWidth, 1 - cornerHeight, cornerWidth, cornerHeight);

        super.drawSubAreaInternal(graphics, x + borderSize.width, y, width - 2 * borderSize.width, borderSize.height,
                cornerWidth, 0, 1 - 2 * cornerWidth, cornerHeight);
        super.drawSubAreaInternal(graphics, x + borderSize.width, y + height - borderSize.height,
                width - 2 * borderSize.width, borderSize.height, cornerWidth, 1 - cornerHeight,
                1 - 2 * cornerWidth, cornerHeight);
        super.drawSubAreaInternal(graphics, x, y + borderSize.height, borderSize.width, height - 2 * borderSize.height,
                0, cornerHeight, cornerWidth, 1 - 2 * cornerHeight);
        super.drawSubAreaInternal(graphics, x + width - borderSize.width, y + borderSize.height, borderSize.width,
                height - 2 * borderSize.height, 1 - cornerWidth, cornerHeight, cornerWidth, 1 - 2 * cornerHeight);

        super.drawSubAreaInternal(graphics, x + borderSize.width, y + borderSize.height,
                width - 2 * borderSize.width, height - 2 * borderSize.height, cornerWidth, cornerHeight,
                1 - 2 * cornerWidth, 1 - 2 * cornerHeight);
    }
}

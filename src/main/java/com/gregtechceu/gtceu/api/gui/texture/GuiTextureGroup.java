package com.gregtechceu.gtceu.api.gui.texture;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws multiple textures in declaration order.
 */
public class GuiTextureGroup extends TransformTexture {

    private IGuiTexture[] textures;

    public GuiTextureGroup(IGuiTexture... textures) {
        this.textures = textures;
    }

    public GuiTextureGroup setTextures(IGuiTexture... textures) {
        this.textures = textures;
        return this;
    }

    @Override
    public GuiTextureGroup copy() {
        var copy = new GuiTextureGroup(textures);
        copy.copyTransform(this);
        return copy;
    }

    @Override
    public GuiTextureGroup setColor(int color) {
        IGuiTexture[] copies = new IGuiTexture[textures.length];
        for (int i = 0; i < textures.length; i++) {
            copies[i] = textures[i].copy().setColor(color);
        }
        var copy = new GuiTextureGroup(copies);
        copy.copyTransform(this);
        return copy;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void updateTick() {
        for (IGuiTexture texture : textures) {
            texture.updateTick();
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawInternal(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width,
                                float height, float partialTicks) {
        for (IGuiTexture texture : textures) {
            texture.draw(graphics, mouseX, mouseY, x, y, width, height, partialTicks);
        }
    }
}

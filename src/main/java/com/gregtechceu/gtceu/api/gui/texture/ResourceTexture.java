package com.gregtechceu.gtceu.api.gui.texture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.tterrag.registrate.util.RegistrateDistExecutor;

import java.util.function.IntSupplier;

import static com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR;

/**
 * Texture backed by a resource location and UV sub-area.
 */
public class ResourceTexture extends TransformTexture {

    public ResourceLocation imageLocation = ResourceLocation.parse("gtpm:textures/gui/icon/gregtech_logo.png");
    public float offsetX;
    public float offsetY;
    public float imageWidth = 1;
    public float imageHeight = 1;
    protected int color = -1;
    protected IntSupplier dynamicColor = () -> color;

    public ResourceTexture(String imageLocation) {
        this(ResourceLocation.parse(imageLocation));
    }

    public ResourceTexture(ResourceLocation imageLocation) {
        this(imageLocation, 0, 0, 1, 1);
    }

    public ResourceTexture(ResourceLocation imageLocation, float offsetX, float offsetY, float width, float height) {
        this.imageLocation = imageLocation;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.imageWidth = width;
        this.imageHeight = height;
    }

    public ResourceTexture getSubTexture(float offsetX, float offsetY, float width, float height) {
        var texture = new ResourceTexture(imageLocation, this.offsetX + imageWidth * offsetX,
                this.offsetY + imageHeight * offsetY, imageWidth * width, imageHeight * height);
        texture.color = color;
        texture.dynamicColor = dynamicColor;
        texture.copyTransform(this);
        return texture;
    }

    public ResourceTexture getSubTexture(double offsetX, double offsetY, double width, double height) {
        return getSubTexture((float) offsetX, (float) offsetY, (float) width, (float) height);
    }

    @Override
    public ResourceTexture copy() {
        return getSubTexture(0, 0, 1, 1);
    }

    @Override
    public ResourceTexture setColor(int color) {
        this.color = color;
        return this;
    }

    public ResourceTexture setDynamicColor(IntSupplier color) {
        this.dynamicColor = color;
        return this;
    }

    public static ResourceTexture fromSpirit(ResourceLocation texture) {
        ResourceTexture result = new ResourceTexture(texture);
        RegistrateDistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientTextureLoader.loadSpirit(result, texture));
        return result;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawInternal(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width,
                                float height, float partialTicks) {
        drawSubAreaInternal(graphics, x, y, width, height, 0, 0, 1, 1);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawSubAreaInternal(GuiGraphics graphics, float x, float y, float width, float height, float drawnU,
                                       float drawnV, float drawnWidth, float drawnHeight) {
        float imageU = offsetX + imageWidth * drawnU;
        float imageV = offsetY + imageHeight * drawnV;
        float areaWidth = imageWidth * drawnWidth;
        float areaHeight = imageHeight * drawnHeight;

        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.Mode.QUADS, POSITION_TEX_COLOR);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, imageLocation);
        var matrix = graphics.pose().last().pose();
        int drawColor = dynamicColor.getAsInt();
        buffer.addVertex(matrix, x, y + height, 0).setUv(imageU, imageV + areaHeight).setColor(drawColor);
        buffer.addVertex(matrix, x + width, y + height, 0).setUv(imageU + areaWidth, imageV + areaHeight)
                .setColor(drawColor);
        buffer.addVertex(matrix, x + width, y, 0).setUv(imageU + areaWidth, imageV).setColor(drawColor);
        buffer.addVertex(matrix, x, y, 0).setUv(imageU, imageV).setColor(drawColor);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    @OnlyIn(Dist.CLIENT)
    private static final class ClientTextureLoader {

        private static void loadSpirit(ResourceTexture target, ResourceLocation texture) {
            var sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
            target.imageLocation = TextureAtlas.LOCATION_BLOCKS;
            target.offsetX = sprite.getU0();
            target.offsetY = sprite.getV0();
            target.imageWidth = sprite.getU1() - sprite.getU0();
            target.imageHeight = sprite.getV1() - sprite.getV0();
        }
    }
}

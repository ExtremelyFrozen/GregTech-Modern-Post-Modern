package com.gregtechceu.gtceu.api.gui.texture;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib2.utils.LocalizationUtils;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.joml.Vector4f;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Text texture facade preserving GTM's legacy text layout modes.
 */
public class TextTexture extends TransformTexture {

    public String text = "";
    public int color = -1;
    public int backgroundColor;
    public int width;
    public float rollSpeed = 1;
    public boolean dropShadow;
    public TextType type = TextType.NORMAL;
    public Supplier<String> supplier;

    @OnlyIn(Dist.CLIENT)
    private List<String> texts = Collections.singletonList("");
    private long lastTick;

    public TextTexture() {
        this("A", -1);
        setWidth(50);
    }

    public TextTexture(String text) {
        this(text, -1);
        setDropShadow(true);
    }

    public TextTexture(String text, int color) {
        this.color = color;
        updateText(text);
    }

    public TextTexture(Supplier<String> text) {
        this("", -1);
        setSupplier(text);
        setDropShadow(true);
    }

    public TextTexture setSupplier(Supplier<String> supplier) {
        this.supplier = supplier;
        return this;
    }

    @Override
    public TextTexture copy() {
        var copy = new TextTexture(text, color);
        copy.type = type;
        copy.dropShadow = dropShadow;
        copy.rollSpeed = rollSpeed;
        copy.width = width;
        copy.backgroundColor = backgroundColor;
        copy.supplier = supplier;
        copy.copyTransform(this);
        return copy;
    }

    public TextTexture updateText(String text) {
        this.text = LocalizationUtils.format(text);
        if (Minecraft.getInstance() != null) {
            rebuildLines();
        }
        return this;
    }

    public TextTexture setBackgroundColor(int color) {
        this.backgroundColor = color;
        return this;
    }

    @Override
    public TextTexture setColor(int color) {
        this.color = color;
        return this;
    }

    public TextTexture setDropShadow(boolean dropShadow) {
        this.dropShadow = dropShadow;
        return this;
    }

    public TextTexture setRollSpeed(float rollSpeed) {
        this.rollSpeed = rollSpeed;
        return this;
    }

    public TextTexture setWidth(int width) {
        this.width = width;
        rebuildLines();
        return this;
    }

    public TextTexture setType(TextType type) {
        this.type = type;
        return this;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void updateTick() {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        long tick = Minecraft.getInstance().level.getGameTime();
        if (tick == lastTick) {
            return;
        }
        lastTick = tick;
        if (supplier != null) {
            updateText(supplier.get());
        }
    }

    private void rebuildLines() {
        if (Minecraft.getInstance() == null || Minecraft.getInstance().font == null) {
            texts = Collections.singletonList(text);
            return;
        }
        if (width > 0) {
            texts = Minecraft.getInstance().font.getSplitter().splitLines(text, width, Style.EMPTY).stream()
                    .map(FormattedText::getString)
                    .collect(Collectors.toList());
            if (texts.isEmpty()) {
                texts = Collections.singletonList(text);
            }
        } else {
            texts = Collections.singletonList(text);
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawInternal(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width,
                                float height, float partialTicks) {
        updateTick();
        if (backgroundColor != 0) {
            DrawerHelper.drawSolidRect(graphics, x, y, width, height, backgroundColor);
        }
        Font font = Minecraft.getInstance().font;
        int textHeight = font.lineHeight;
        if (type == TextType.NORMAL || type == TextType.LEFT || type == TextType.RIGHT) {
            drawStaticLines(graphics, x, y, width, height, font, textHeight);
        } else if (type == TextType.HIDE || type == TextType.LEFT_HIDE) {
            drawHideText(graphics, mouseX, mouseY, x, y, width, height, font, textHeight, type == TextType.LEFT_HIDE);
        } else if (type == TextType.ROLL || type == TextType.ROLL_ALWAYS ||
                type == TextType.LEFT_ROLL || type == TextType.LEFT_ROLL_ALWAYS) {
            boolean left = type == TextType.LEFT_ROLL || type == TextType.LEFT_ROLL_ALWAYS;
            boolean always = type == TextType.ROLL_ALWAYS || type == TextType.LEFT_ROLL_ALWAYS;
            drawRollingText(graphics, mouseX, mouseY, x, y, width, height, font, textHeight, left, always);
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    @OnlyIn(Dist.CLIENT)
    private void drawStaticLines(GuiGraphics graphics, float x, float y, float width, float height, Font font,
                                 int textHeight) {
        int totalHeight = textHeight * texts.size();
        for (int i = 0; i < texts.size(); i++) {
            String line = texts.get(i);
            int lineWidth = font.width(line);
            float drawX = switch (type) {
                case LEFT -> x;
                case RIGHT -> x + width - lineWidth;
                default -> x + (width - lineWidth) / 2f;
            };
            float drawY = y + (height - totalHeight) / 2f + i * font.lineHeight;
            graphics.drawString(font, line, (int) drawX, (int) drawY, color, dropShadow);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void drawHideText(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width,
                              float height, Font font, int textHeight, boolean left) {
        if (UIElement.isMouseOverRect((int) x, (int) y, (int) width, (int) height, mouseX, mouseY) &&
                texts.size() > 1) {
            drawRollTextLine(graphics, x, y, width, height, font, textHeight, text);
            return;
        }
        String line = texts.getFirst() + (texts.size() > 1 ? ".." : "");
        if (left) {
            float drawY = y + (height - textHeight) / 2f;
            graphics.drawString(font, line, (int) x, (int) drawY, color, dropShadow);
        } else {
            drawTextLine(graphics, x, y, width, height, font, textHeight, line);
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void drawRollingText(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width,
                                 float height, Font font, int textHeight, boolean left, boolean always) {
        boolean hovered = UIElement.isMouseOverRect((int) x, (int) y, (int) width, (int) height, mouseX, mouseY);
        if (texts.size() > 1 && (always || hovered)) {
            drawRollTextLine(graphics, x, y, width, height, font, textHeight, text);
            return;
        }
        if (left) {
            float drawY = y + (height - textHeight) / 2f;
            graphics.drawString(font, texts.getFirst(), (int) x, (int) drawY, color, dropShadow);
        } else {
            drawTextLine(graphics, x, y, width, height, font, textHeight, texts.getFirst());
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void drawRollTextLine(GuiGraphics graphics, float x, float y, float width, float height, Font font,
                                  int textHeight, String line) {
        float drawY = y + (height - textHeight) / 2f;
        float textWidth = font.width(line);
        float totalWidth = width + textWidth + 10;
        float from = x + width;
        var transform = graphics.pose().last().pose();
        var realPos = transform.transform(new Vector4f(x, y, 0, 1));
        var realPos2 = transform.transform(new Vector4f(x + width, y + height, 0, 1));
        graphics.enableScissor((int) realPos.x, (int) realPos.y, (int) realPos2.x, (int) realPos2.y);
        var time = Math.abs((int) (System.currentTimeMillis() % 1000000));
        var progress = rollSpeed > 0 ? rollSpeed * time / 10 % totalWidth / totalWidth : 0.5f;
        graphics.drawString(font, line, (int) (from - progress * totalWidth), (int) drawY, color, dropShadow);
        graphics.disableScissor();
    }

    @OnlyIn(Dist.CLIENT)
    private void drawTextLine(GuiGraphics graphics, float x, float y, float width, float height, Font font,
                              int textHeight, String line) {
        int textWidth = font.width(line);
        float drawX = x + (width - textWidth) / 2f;
        float drawY = y + (height - textHeight) / 2f;
        graphics.drawString(font, line, (int) drawX, (int) drawY, color, dropShadow);
    }

    @OnlyIn(Dist.CLIENT)
    public int getLines() {
        return texts.size();
    }

    public enum TextType {
        NORMAL,
        HIDE,
        ROLL,
        ROLL_ALWAYS,
        LEFT,
        RIGHT,
        LEFT_HIDE,
        LEFT_ROLL,
        LEFT_ROLL_ALWAYS
    }
}

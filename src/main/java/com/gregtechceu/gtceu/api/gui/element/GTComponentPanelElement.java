package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.api.gui.UITemplate;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.ui.style.LayoutStyle;
import com.lowdragmc.lowdraglib2.gui.util.ClickData;
import com.lowdragmc.lowdraglib2.gui.util.UISoundUtils;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 facade for styled component text rendering and local component click handling.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-component-panel", group = "gtm", registry = "ldlib2:ui_element")
public class GTComponentPanelElement extends UIElement {

    private static final String BUTTON_PREFIX = "@!";

    private int x;
    private int y;
    private int width;
    private int height;
    private int maxWidthLimit;
    @Nullable
    private Consumer<List<Component>> textSupplier;
    @Nullable
    private BiConsumer<String, ClickData> clickHandler;
    private List<Component> lastText = new ArrayList<>();
    private List<FormattedCharSequence> cacheLines = Collections.emptyList();
    private boolean center;
    private int space = 2;

    public GTComponentPanelElement() {
        UITemplate.setLDLib2Bounds(this, 0, 0, 0, 0);
        addEventListener(UIEvents.MOUSE_DOWN, this::onMouseDown);
        addEventListener(UIEvents.HOVER_TOOLTIPS, this::onHoverTooltips);
    }

    public GTComponentPanelElement(int x, int y, Consumer<List<Component>> textSupplier) {
        this();
        moveTo(x, y);
        this.textSupplier = textSupplier;
        refreshTextFromSupplier();
    }

    public GTComponentPanelElement(int x, int y, List<Component> text) {
        this();
        moveTo(x, y);
        this.lastText = new ArrayList<>(text);
        refreshDisplayText();
    }

    public static Component withButton(Component textComponent, String componentData) {
        Style style = textComponent.getStyle()
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, BUTTON_PREFIX + componentData))
                .withColor(ChatFormatting.YELLOW);
        return textComponent.copy().withStyle(style);
    }

    public static Component withButton(Component textComponent, String componentData, int color) {
        Style style = textComponent.getStyle()
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, BUTTON_PREFIX + componentData))
                .withColor(color);
        return textComponent.copy().withStyle(style);
    }

    public static Component withHoverTextTranslate(Component textComponent, Component hover) {
        Style style = textComponent.getStyle()
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
        return textComponent.copy().withStyle(style);
    }

    public GTComponentPanelElement setMaxWidthLimit(int maxWidthLimit) {
        if (maxWidthLimit < 0) {
            throw new IllegalArgumentException("maxWidthLimit must be non-negative");
        }
        this.maxWidthLimit = maxWidthLimit;
        refreshDisplayText();
        return this;
    }

    public GTComponentPanelElement setCenter(boolean center) {
        this.center = center;
        refreshDisplayText();
        return this;
    }

    public GTComponentPanelElement setSpace(int space) {
        if (space < 0) {
            throw new IllegalArgumentException("space must be non-negative");
        }
        this.space = space;
        refreshDisplayText();
        return this;
    }

    public GTComponentPanelElement textSupplier(@Nullable Consumer<List<Component>> textSupplier) {
        this.textSupplier = textSupplier;
        refreshTextFromSupplier();
        return this;
    }

    public GTComponentPanelElement setTextSupplier(@Nullable Consumer<List<Component>> textSupplier) {
        return textSupplier(textSupplier);
    }

    public GTComponentPanelElement clickHandler(@Nullable BiConsumer<String, ClickData> clickHandler) {
        this.clickHandler = clickHandler;
        return this;
    }

    public GTComponentPanelElement setClickHandler(@Nullable BiConsumer<String, ClickData> clickHandler) {
        return clickHandler(clickHandler);
    }

    public GTComponentPanelElement setText(List<Component> text) {
        this.lastText = new ArrayList<>(text);
        refreshDisplayText();
        return this;
    }

    public int getMaxWidthLimit() {
        return maxWidthLimit;
    }

    public boolean isCenter() {
        return center;
    }

    public int getSpace() {
        return space;
    }

    public List<Component> getLastText() {
        return lastText;
    }

    public List<FormattedCharSequence> getCacheLines() {
        return cacheLines;
    }

    @Override
    public GTComponentPanelElement layout(Consumer<LayoutStyle> layout) {
        super.layout(layout);
        return this;
    }

    @Override
    public void screenTick() {
        refreshTextFromSupplier();
        super.screenTick();
    }

    @Override
    public boolean isIntersectWithPoint(double localX, double localY) {
        if (!LDLib2.isClient() || !super.isIntersectWithPoint(localX, localY)) {
            return false;
        }
        Style style = getStyleUnderMouse(localX, localY);
        return style != null && (style.getClickEvent() != null || style.getHoverEvent() != null);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void drawBackgroundAdditional(GUIContext guiContext) {
        super.drawBackgroundAdditional(guiContext);
        if (cacheLines.isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        for (int i = 0; i < cacheLines.size(); i++) {
            FormattedCharSequence cacheLine = cacheLines.get(i);
            float lineX = getLineX(font, cacheLine);
            float lineY = getPositionY() + i * (font.lineHeight + space);
            guiContext.graphics.drawString(font, cacheLine, lineX, lineY, -1, false);
        }
    }

    private void moveTo(int x, int y) {
        this.x = x;
        this.y = y;
        applyBounds();
    }

    private void refreshTextFromSupplier() {
        if (textSupplier == null) {
            return;
        }
        List<Component> textBuffer = new ArrayList<>();
        textSupplier.accept(textBuffer);
        if (!lastText.equals(textBuffer)) {
            lastText = textBuffer;
            refreshDisplayText();
        }
    }

    private void refreshDisplayText() {
        if (!LDLib2.isClient()) {
            return;
        }
        formatDisplayText();
        updateComponentTextSize();
    }

    @OnlyIn(Dist.CLIENT)
    private void formatDisplayText() {
        Font font = Minecraft.getInstance().font;
        int maxTextWidth = maxWidthLimit == 0 ? Integer.MAX_VALUE : maxWidthLimit;
        List<FormattedCharSequence> wrappedLines = new ArrayList<>();
        for (Component textComponent : lastText) {
            wrappedLines.addAll(ComponentRenderUtils.wrapComponents(textComponent, maxTextWidth, font));
        }
        cacheLines = wrappedLines;
    }

    @OnlyIn(Dist.CLIENT)
    private void updateComponentTextSize() {
        Font font = Minecraft.getInstance().font;
        int totalHeight = cacheLines.size() * (font.lineHeight + space);
        if (totalHeight > 0) {
            totalHeight -= space;
        }

        int totalWidth;
        if (center) {
            totalWidth = maxWidthLimit;
        } else {
            int maxStringWidth = 0;
            for (FormattedCharSequence line : cacheLines) {
                maxStringWidth = Math.max(font.width(line), maxStringWidth);
            }
            totalWidth = maxWidthLimit == 0 ? maxStringWidth : Math.min(maxWidthLimit, maxStringWidth);
        }

        width = totalWidth;
        height = totalHeight;
        applyBounds();
    }

    private void applyBounds() {
        UITemplate.setLDLib2Bounds(this, x, y, width, height);
    }

    @Nullable
    @OnlyIn(Dist.CLIENT)
    protected Style getStyleUnderMouse(double mouseX, double mouseY) {
        Font font = Minecraft.getInstance().font;
        int lineHeight = font.lineHeight + space;
        if (lineHeight <= 0) {
            return null;
        }

        double relativeY = mouseY - getPositionY();
        if (relativeY < 0) {
            return null;
        }

        int selectedLine = (int) (relativeY / lineHeight);
        if (selectedLine < 0 || selectedLine >= cacheLines.size()) {
            return null;
        }
        if (relativeY - selectedLine * lineHeight >= font.lineHeight) {
            return null;
        }

        FormattedCharSequence cacheLine = cacheLines.get(selectedLine);
        float lineX = getLineX(font, cacheLine);
        if (mouseX < lineX) {
            return null;
        }
        int mouseOffset = (int) (mouseX - lineX);
        return font.getSplitter().componentStyleAtWidth(cacheLine, mouseOffset);
    }

    @OnlyIn(Dist.CLIENT)
    private float getLineX(Font font, FormattedCharSequence cacheLine) {
        if (center) {
            int lineWidth = font.width(cacheLine);
            return getPositionX() + (getSizeWidth() - lineWidth) / 2f;
        }
        return getPositionX();
    }

    @OnlyIn(Dist.CLIENT)
    private void onMouseDown(UIEvent event) {
        Style style = getStyleUnderMouse(event.x, event.y);
        if (style == null || style.getClickEvent() == null) {
            event.hasHandler = false;
            return;
        }

        ClickEvent clickEvent = style.getClickEvent();
        String componentText = clickEvent.getValue();
        if (clickEvent.getAction() != ClickEvent.Action.OPEN_URL || !componentText.startsWith(BUTTON_PREFIX)) {
            event.hasHandler = false;
            return;
        }

        if (clickHandler != null) {
            clickHandler.accept(componentText.substring(BUTTON_PREFIX.length()), new ClickData());
        }
        UISoundUtils.playButtonClickSound();
        event.stopPropagation();
    }

    @OnlyIn(Dist.CLIENT)
    private void onHoverTooltips(UIEvent event) {
        ModularUI modularUI = getModularUI();
        if (modularUI == null) {
            return;
        }

        Style style = getStyleUnderMouse(modularUI.getLastMouseX(), modularUI.getLastMouseY());
        if (style == null || style.getHoverEvent() == null) {
            return;
        }

        HoverEvent hoverEvent = style.getHoverEvent();
        Component hoverTips = hoverEvent.getValue(HoverEvent.Action.SHOW_TEXT);
        if (hoverTips != null) {
            event.hoverTooltips = new HoverTooltips(List.of(hoverTips), null, null, null);
        }
    }
}

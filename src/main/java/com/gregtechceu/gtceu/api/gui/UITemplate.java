package com.gregtechceu.gtceu.api.gui;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.layout.LayoutProperties;
import com.lowdragmc.lowdraglib2.gui.ui.style.StyleValue;
import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import dev.vfyjxf.taffy.style.LengthPercentageAuto;
import dev.vfyjxf.taffy.style.TaffyDimension;
import dev.vfyjxf.taffy.style.TaffyPosition;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class UITemplate {

    public static UIElement bindPlayerInventoryLDLib2(Inventory inventoryPlayer, IGuiTexture imageLocation, int x,
                                                      int y,
                                                      boolean addHotbar) {
        UIElement root = new UIElement();
        setLDLib2Bounds(root, x, y, 162, 54 + (addHotbar ? 22 : 0));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slotIndex = col + (row + 1) * 9;
                GTItemSlotElement slot = new GTItemSlotElement()
                        .bind(new Slot(inventoryPlayer, slotIndex, 0, 0));
                setLDLib2Bounds(slot, col * 18, row * 18, 18, 18);
                slot.setBackgroundTexture(imageLocation);
                slot.slotStyle(style -> style.isPlayerSlot(true));
                slot.setId("inventory_" + slotIndex);
                root.addChild(slot);
            }
        }
        if (addHotbar) {
            for (int slotIndex = 0; slotIndex < 9; slotIndex++) {
                GTItemSlotElement slot = new GTItemSlotElement()
                        .bind(new Slot(inventoryPlayer, slotIndex, 0, 0));
                setLDLib2Bounds(slot, slotIndex * 18, 58, 18, 18);
                slot.setBackgroundTexture(imageLocation);
                slot.slotStyle(style -> style.isPlayerSlot(true));
                slot.setId("inventory_" + slotIndex);
                root.addChild(slot);
            }
        }
        return root;
    }

    public static <T extends UIElement> T setLDLib2Bounds(T element, int x, int y, int width, int height) {
        var layout = element.getLayout();
        layout.positionType(TaffyPosition.ABSOLUTE);
        layout.left(x);
        layout.top(y);
        layout.width(width);
        layout.height(height);
        return element;
    }

    /**
     * Reads the fixed bounds declared through {@link #setLDLib2Bounds(UIElement, int, int, int, int)} without
     * requiring a client layout pass or an attached ModularUI.
     *
     * @param element element whose declared layout is required.
     * @return absolute fixed pixel bounds declared by GTM.
     */
    public static LDLib2Bounds getLDLib2Bounds(UIElement element) {
        var styleBag = element.getStyleBag();
        TaffyPosition position = styleBag.computeCandidate(LayoutProperties.POSITION);
        if (position != TaffyPosition.ABSOLUTE) {
            GTCEu.LOGGER.error("LDLib2 element '{}' must declare absolute bounds, got {}", element.getId(), position);
            throw new IllegalArgumentException("LDLib2 element must declare absolute bounds: " + element.getId());
        }
        return new LDLib2Bounds(
                fixedLDLib2Offset(styleBag.computeCandidate(LayoutProperties.LEFT), "left", element.getId()),
                fixedLDLib2Offset(styleBag.computeCandidate(LayoutProperties.TOP), "top", element.getId()),
                fixedLDLib2Dimension(styleBag.computeCandidate(LayoutProperties.WIDTH), "width", element.getId()),
                fixedLDLib2Dimension(styleBag.computeCandidate(LayoutProperties.HEIGHT), "height", element.getId()));
    }

    /**
     * Reads the fixed pixel size declared by the single direct root of an LDLib2 XML document.
     *
     * @param document parsed LDLib2 XML document.
     * @return fixed root width and height.
     */
    public static LDLib2Size getLDLib2RootSize(Document document) {
        Element root = getLDLib2Root(document);
        var properties = Stylesheet.parseStyleValues(root.getAttribute("style"));
        return new LDLib2Size(
                fixedLDLib2Dimension(properties.get(LayoutProperties.WIDTH), "width"),
                fixedLDLib2Dimension(properties.get(LayoutProperties.HEIGHT), "height"));
    }

    private static Element getLDLib2Root(Document document) {
        Element documentRoot = document.getDocumentElement();
        if (documentRoot == null) {
            GTCEu.LOGGER.error("LDLib2 UI XML does not contain a document root");
            throw new IllegalArgumentException("LDLib2 UI XML does not contain a document root");
        }
        Element root = null;
        for (Node child = documentRoot.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element element && element.getTagName().equals("root")) {
                if (root != null) {
                    GTCEu.LOGGER.error("LDLib2 UI XML contains more than one direct root element");
                    throw new IllegalArgumentException("LDLib2 UI XML contains more than one direct root element");
                }
                root = element;
            }
        }
        if (root == null) {
            GTCEu.LOGGER.error("LDLib2 UI XML does not contain a direct root element");
            throw new IllegalArgumentException("LDLib2 UI XML does not contain a direct root element");
        }
        return root;
    }

    private static int fixedLDLib2Offset(@Nullable LengthPercentageAuto offset, String property, String elementId) {
        if (offset == null || !offset.isLength()) {
            GTCEu.LOGGER.error("LDLib2 element '{}' must declare a fixed pixel {}, got {}",
                    elementId, property, offset);
            throw new IllegalArgumentException("LDLib2 element must declare a fixed pixel " + property + ": " +
                    elementId);
        }
        return Math.round(offset.getValue());
    }

    private static int fixedLDLib2Dimension(@Nullable TaffyDimension dimension, String property, String elementId) {
        if (dimension == null || !dimension.isLength()) {
            GTCEu.LOGGER.error("LDLib2 element '{}' must declare a fixed pixel {}, got {}",
                    elementId, property, dimension);
            throw new IllegalArgumentException("LDLib2 element must declare a fixed pixel " + property + ": " +
                    elementId);
        }
        return Math.round(dimension.getValue());
    }

    private static int fixedLDLib2Dimension(@Nullable StyleValue<?> styleValue, String property) {
        if (styleValue == null || !(styleValue.compute() instanceof TaffyDimension dimension)) {
            GTCEu.LOGGER.error("LDLib2 XML root must declare a fixed pixel {}, got {}", property, styleValue);
            throw new IllegalArgumentException("LDLib2 XML root must declare a fixed pixel " + property);
        }
        return fixedLDLib2Dimension(dimension, property, "XML root");
    }

    /**
     * Absolute fixed pixel bounds declared on a programmatically constructed LDLib2 element.
     *
     * @param x      left offset.
     * @param y      top offset.
     * @param width  element width.
     * @param height element height.
     */
    public record LDLib2Bounds(int x, int y, int width, int height) {}

    /**
     * Fixed pixel size declared by an LDLib2 XML root.
     *
     * @param width  root width.
     * @param height root height.
     */
    public record LDLib2Size(int width, int height) {}
}

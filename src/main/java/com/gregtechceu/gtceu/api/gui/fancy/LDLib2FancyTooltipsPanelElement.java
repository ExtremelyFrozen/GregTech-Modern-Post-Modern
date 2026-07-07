package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * LDLib2 right-side tooltip indicator panel for Fancy pages.
 */
public class LDLib2FancyTooltipsPanelElement extends UIElement {

    private static final int ICON_SIZE = 20;
    private static final int ICON_GAP = 2;

    private final List<IFancyTooltip> tooltips = new ArrayList<>();
    private int x;
    private int y;

    public LDLib2FancyTooltipsPanelElement(int x, int y) {
        this.x = x;
        this.y = y;
        UITemplate.setLDLib2Bounds(this, x, y, ICON_SIZE, 0);
    }

    public void clear() {
        tooltips.clear();
        refreshTooltips();
    }

    public void attachTooltips(IFancyTooltip... tooltips) {
        this.tooltips.addAll(Arrays.asList(tooltips));
        refreshTooltips();
    }

    public void moveTo(int x, int y) {
        this.x = x;
        this.y = y;
        refreshTooltips();
    }

    @Override
    public void screenTick() {
        refreshTooltips();
        super.screenTick();
    }

    private void refreshTooltips() {
        clearAllChildren();
        int offsetY = 0;
        for (IFancyTooltip tooltip : tooltips) {
            if (tooltip.showFancyTooltip()) {
                GTImageElement icon = new GTImageElement(0, offsetY, ICON_SIZE, ICON_SIZE,
                        tooltip.getFancyTooltipIcon());
                icon.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> applyTooltip(event, tooltip));
                addChild(icon);
                offsetY += ICON_SIZE + ICON_GAP;
            }
        }
        if (offsetY > 0) {
            offsetY -= ICON_GAP;
        }
        UITemplate.setLDLib2Bounds(this, x, y, ICON_SIZE, offsetY);
    }

    private static void applyTooltip(UIEvent event, IFancyTooltip tooltip) {
        event.hoverTooltips = new HoverTooltips(tooltip.getFancyTooltip(), tooltip.getFancyComponent(), null,
                ItemStack.EMPTY);
    }
}

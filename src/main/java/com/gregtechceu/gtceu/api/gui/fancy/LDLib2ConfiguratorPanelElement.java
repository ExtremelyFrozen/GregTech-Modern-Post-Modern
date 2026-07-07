package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.texture.TextTexture;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * LDLib2 left-side Fancy configurator panel.
 */
public class LDLib2ConfiguratorPanelElement extends UIElement {

    private static final int TAB_SIZE = 24;
    private static final int TAB_GAP = 2;
    private static final int ICON_SIZE = 16;
    private static final int EXPANDED_GAP = 2;

    private final List<Tab> tabs = new ArrayList<>();
    private final MachineUIHolder holder;
    private int x;
    private int y;
    private int border = 4;
    private IGuiTexture texture = GuiTextures.BACKGROUND;

    @Nullable
    private Tab expanded;

    public LDLib2ConfiguratorPanelElement(MachineUIHolder holder, int x, int y) {
        this.holder = holder;
        this.x = x;
        this.y = y;
        UITemplate.setLDLib2Bounds(this, x, y, TAB_SIZE, 0);
        style(style -> style.overflowVisible(true));
    }

    /**
     * Returns the machine holder captured when the owning Fancy screen opened.
     */
    public MachineUIHolder getHolder() {
        return holder;
    }

    public void clear() {
        clearAllChildren();
        tabs.clear();
        expanded = null;
        updatePanelBounds();
    }

    public int getTabSize() {
        return TAB_SIZE;
    }

    public int getPanelContentHeight() {
        return Math.max(0, tabs.size() * (TAB_SIZE + TAB_GAP) - TAB_GAP);
    }

    public LDLib2ConfiguratorPanelElement setBorder(int border) {
        if (border < 0) {
            throw new IllegalArgumentException("border must be non-negative");
        }
        this.border = border;
        rebuildTabs();
        return this;
    }

    public LDLib2ConfiguratorPanelElement setTexture(IGuiTexture texture) {
        this.texture = texture;
        rebuildTabs();
        return this;
    }

    public void moveTo(int x, int y) {
        this.x = x;
        this.y = y;
        updatePanelBounds();
    }

    public void attachConfigurators(LDLib2FancyConfigurator... configurators) {
        for (LDLib2FancyConfigurator configurator : configurators) {
            Tab tab = new Tab(configurator, tabs.size());
            tabs.add(tab);
            addChild(tab);
        }
        updatePanelBounds();
    }

    public void expandTab(Tab tab) {
        expanded = tab;
        int index = 0;
        for (Tab otherTab : tabs) {
            if (otherTab == tab) {
                otherTab.expand();
            } else {
                otherTab.collapseTo(0, index * (TAB_SIZE + TAB_GAP));
                index++;
            }
        }
    }

    public void collapseTab() {
        for (int index = 0; index < tabs.size(); index++) {
            tabs.get(index).collapseTo(0, index * (TAB_SIZE + TAB_GAP));
        }
        expanded = null;
    }

    private void rebuildTabs() {
        List<LDLib2FancyConfigurator> configurators = tabs.stream().map(tab -> tab.configurator).toList();
        clear();
        attachConfigurators(configurators.toArray(LDLib2FancyConfigurator[]::new));
    }

    private void updatePanelBounds() {
        UITemplate.setLDLib2Bounds(this, x, y, TAB_SIZE, getPanelContentHeight());
    }

    /**
     * A collapsed or expanded configurator tab in the LDLib2 Fancy configurator panel.
     */
    public class Tab extends UIElement {

        private final LDLib2FancyConfigurator configurator;
        private final GTButtonElement button;
        private final GTImageElement icon;

        @Nullable
        private final UIElement view;

        private Tab(LDLib2FancyConfigurator configurator, int index) {
            this.configurator = configurator;
            UITemplate.setLDLib2Bounds(this, 0, index * (TAB_SIZE + TAB_GAP), TAB_SIZE, TAB_SIZE);
            style(style -> style
                    .backgroundTexture(texture)
                    .overflowVisible(true));

            this.button = new GTButtonElement(0, 0, TAB_SIZE, TAB_SIZE, IGuiTexture.EMPTY,
                    this::onClick);
            button.noText();
            button.style(style -> style.zIndex(310));
            button.addEventListener(UIEvents.HOVER_TOOLTIPS, this::applyTooltip);

            this.icon = new GTImageElement(TAB_SIZE - ICON_SIZE - border, border, ICON_SIZE, ICON_SIZE,
                    configurator.getIcon());
            icon.setAllowHitTest(false);
            button.addChild(icon);
            addChild(button);

            if (configurator instanceof LDLib2FancyConfiguratorButton) {
                this.view = null;
            } else {
                this.view = createView(configurator);
                this.view.setVisible(false);
                this.view.setActive(false);
                addChild(this.view);
            }
        }

        @Override
        public void screenTick() {
            icon.setTexture(configurator.getIcon());
            super.screenTick();
        }

        private UIElement createView(LDLib2FancyConfigurator configurator) {
            int width = configurator.getLDLib2ConfiguratorWidth() + border * 2;
            int height = configurator.getLDLib2ConfiguratorHeight() + TAB_SIZE + border;
            UIElement createdView = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, width, height);
            createdView.style(style -> style
                    .backgroundTexture(texture)
                    .overflowVisible(true));

            UIElement configuratorBody = configurator.createLDLib2Configurator();
            UITemplate.setLDLib2Bounds(configuratorBody, border, TAB_SIZE,
                    configurator.getLDLib2ConfiguratorWidth(),
                    configurator.getLDLib2ConfiguratorHeight());
            createdView.addChild(configuratorBody);
            createdView.addChild(new GTImageElement(border + ICON_SIZE + 1, border,
                    configurator.getLDLib2ConfiguratorWidth() - TAB_SIZE - 1,
                    TAB_SIZE - border,
                    GuiTextures.text(configurator.getTitle().getString())
                            .setType(TextTexture.TextType.LEFT_HIDE)
                            .setWidth(configurator.getLDLib2ConfiguratorWidth() - TAB_SIZE)));
            return createdView;
        }

        private void onClick(UIEvent event) {
            if (configurator instanceof LDLib2FancyConfiguratorButton button) {
                button.onClick(event);
                return;
            }
            if (expanded == this) {
                collapseTab();
            } else {
                expandTab(this);
            }
        }

        private void expand() {
            if (view == null) {
                return;
            }
            int viewWidth = configurator.getLDLib2ConfiguratorWidth() + border * 2;
            int viewHeight = configurator.getLDLib2ConfiguratorHeight() + TAB_SIZE + border;
            int expandedX = -viewWidth + (tabs.size() > 1 ? -EXPANDED_GAP : TAB_SIZE);
            UITemplate.setLDLib2Bounds(this, expandedX, 0, viewWidth, viewHeight);
            positionButton(viewWidth - TAB_SIZE);
            view.setVisible(true);
            view.setActive(true);
            style(style -> style.zIndex(300));
        }

        private void collapseTo(int x, int y) {
            if (view != null) {
                view.setVisible(false);
                view.setActive(false);
            }
            UITemplate.setLDLib2Bounds(this, x, y, TAB_SIZE, TAB_SIZE);
            positionButton(0);
            style(style -> style.zIndex(0));
        }

        private void applyTooltip(UIEvent event) {
            event.hoverTooltips = new HoverTooltips(configurator.getTooltips(), null, null, ItemStack.EMPTY);
        }

        private void positionButton(int x) {
            UITemplate.setLDLib2Bounds(button, x, 0, TAB_SIZE, TAB_SIZE);
        }
    }
}

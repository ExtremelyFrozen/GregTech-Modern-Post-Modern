package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * LDLib2 tab strip used by the Fancy shell for local page navigation.
 */
public class LDLib2FancyTabsElement extends UIElement {

    private static final int TAB_SIZE = 24;
    private static final int TAB_ICON_SIZE = 16;

    private final Consumer<LDLib2FancyUIProvider> onTabClick;
    private final boolean vertical;
    private final List<LDLib2FancyUIProvider> subTabs = new ArrayList<>();
    private final Map<LDLib2FancyUIProvider, Map<ResourceLocation, LDLib2FancyUIProvider>> subTabCache = new IdentityHashMap<>();

    @Nullable
    private LDLib2FancyUIProvider mainTab;
    @Nullable
    private LDLib2FancyUIProvider selectedTab;

    private IGuiTexture tabTexture = GuiTextures.resource("gtpm:textures/gui/tab/tabs_top.png")
            .getSubTexture(1 / 3f, 0, 1 / 3f, 0.5f);
    private IGuiTexture tabHoverTexture = GuiTextures.resource("gtpm:textures/gui/tab/tabs_top.png")
            .getSubTexture(1 / 3f, 0.5f, 1 / 3f, 0.5f);
    private IGuiTexture tabPressedTexture = tabHoverTexture;

    public LDLib2FancyTabsElement(Consumer<LDLib2FancyUIProvider> onTabClick, int x, int y, int width, int height,
                                  boolean vertical) {
        this.onTabClick = onTabClick;
        this.vertical = vertical;
        UITemplate.setLDLib2Bounds(this, x, y, width, height);
        style(style -> style.overflowVisible(true));
        if (vertical) {
            var tabsLeft = GuiTextures.resource("gtpm:textures/gui/tab/tabs_left.png");
            setTabTexture(tabsLeft.getSubTexture(0, 1 / 3f, 0.5f, 1 / 3f));
            setTabHoverTexture(tabsLeft.getSubTexture(0.5f, 1 / 3f, 0.5f, 1 / 3f));
            setTabPressedTexture(tabsLeft.getSubTexture(0.5f, 1 / 3f, 0.5f, 1 / 3f));
        }
    }

    public LDLib2FancyTabsElement setTabTexture(IGuiTexture tabTexture) {
        this.tabTexture = tabTexture;
        rebuildTabs();
        return this;
    }

    public LDLib2FancyTabsElement setTabHoverTexture(IGuiTexture tabHoverTexture) {
        this.tabHoverTexture = tabHoverTexture;
        rebuildTabs();
        return this;
    }

    public LDLib2FancyTabsElement setTabPressedTexture(IGuiTexture tabPressedTexture) {
        this.tabPressedTexture = tabPressedTexture;
        rebuildTabs();
        return this;
    }

    public void setMainTab(LDLib2FancyUIProvider mainTab) {
        this.mainTab = mainTab;
        if (this.selectedTab == null) {
            this.selectedTab = mainTab;
        }
        rebuildTabs();
    }

    public void clearSubTabs() {
        subTabs.clear();
        rebuildTabs();
    }

    public void attachSubTab(LDLib2FancyUIProvider subTab) {
        subTabs.add(subTab);
        rebuildTabs();
    }

    /**
     * Attaches a sub-tab provider that remains stable for the current home page and cache key.
     *
     * <p>
     * The Fancy shell caches pages by provider identity. Reusing the provider prevents repeated home-page setup from
     * creating duplicate cached pages while keeping identical keys isolated between different home providers.
     *
     * @return the provider created for this home page and key, or the previously cached instance.
     */
    public LDLib2FancyUIProvider attachCachedSubTab(
                                                    ResourceLocation key,
                                                    Supplier<? extends LDLib2FancyUIProvider> factory) {
        LDLib2FancyUIProvider homeTab = mainTab;
        if (homeTab == null) {
            throw new IllegalStateException("Cannot cache a Fancy sub-tab before setting the main tab.");
        }

        Map<ResourceLocation, LDLib2FancyUIProvider> homeCache = subTabCache.computeIfAbsent(homeTab,
                ignored -> new HashMap<>());
        LDLib2FancyUIProvider subTab = homeCache.get(key);
        if (subTab == null) {
            subTab = factory.get();
            if (subTab == null) {
                throw new IllegalStateException("Fancy sub-tab factory returned null for " + key + ".");
            }
            homeCache.put(key, subTab);
        }
        attachSubTab(subTab);
        return subTab;
    }

    public void selectTab(LDLib2FancyUIProvider selectedTab) {
        this.selectedTab = selectedTab;
        rebuildTabs();
    }

    public int getTabSize() {
        return TAB_SIZE;
    }

    private void rebuildTabs() {
        clearAllChildren();
        if (mainTab == null) {
            return;
        }

        addTabButton(mainTab, 0);
        for (int index = 0; index < subTabs.size(); index++) {
            addTabButton(subTabs.get(index), index + 1);
        }
    }

    private void addTabButton(LDLib2FancyUIProvider tab, int index) {
        int x = vertical ? 0 : 8 + index * TAB_SIZE;
        int y = vertical ? 8 + index * TAB_SIZE : 0;
        GTButtonElement button = new GTButtonElement(x, y, TAB_SIZE, TAB_SIZE);
        button.noText();
        if (tab == selectedTab) {
            button.setButtonTextures(tabPressedTexture, tabPressedTexture, tabPressedTexture);
        } else {
            button.setButtonTextures(tabTexture, tabHoverTexture, tabPressedTexture);
        }
        button.setOnClick(event -> onTabClicked(tab));
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> applyTabTooltips(event, tab));
        GTImageElement icon = new GTImageElement((TAB_SIZE - TAB_ICON_SIZE) / 2,
                (TAB_SIZE - TAB_ICON_SIZE) / 2,
                TAB_ICON_SIZE,
                TAB_ICON_SIZE,
                tab.getTabIcon());
        icon.setAllowHitTest(false);
        button.addChild(icon);
        addChild(button);
    }

    private void onTabClicked(LDLib2FancyUIProvider tab) {
        if (tab == selectedTab) {
            return;
        }
        selectedTab = tab;
        rebuildTabs();
        onTabClick.accept(tab);
    }

    private static void applyTabTooltips(UIEvent event, LDLib2FancyUIProvider tab) {
        List<Component> tooltips = tab.getTabTooltips();
        var tooltipComponent = tab.getTabTooltipComponent();
        if (!tooltips.isEmpty() || tooltipComponent != null) {
            event.hoverTooltips = new HoverTooltips(tooltips, tooltipComponent, null, ItemStack.EMPTY);
        }
    }
}

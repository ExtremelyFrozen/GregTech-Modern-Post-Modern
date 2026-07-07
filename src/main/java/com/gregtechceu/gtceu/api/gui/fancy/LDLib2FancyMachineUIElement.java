package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.world.entity.player.Inventory;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * LDLib2 Fancy shell that owns local navigation, title bar, side tabs, configurator panel, and tooltip panel.
 */
public class LDLib2FancyMachineUIElement extends UIElement {

    private static final int MIN_PAGE_WIDTH = 172;
    private static final int MIN_PAGE_HEIGHT = 86;
    private static final int PLAYER_INVENTORY_WIDTH = 162;
    private static final int PLAYER_INVENTORY_ELEMENT_HEIGHT = 76;
    private static final int PLAYER_INVENTORY_FRAME_HEIGHT = 82;
    private static final int DEFAULT_BORDER = 4;

    private final LDLib2FancyUIProvider mainPage;
    private final MachineUIHolder holder;
    private final UIElement pageContainer;
    private final LDLib2FancyTitleBarElement titleBar;
    private final LDLib2FancyTabsElement sideTabsElement;
    private final LDLib2ConfiguratorPanelElement configuratorPanel;
    private final LDLib2FancyTooltipsPanelElement tooltipsPanel;
    private final LDLib2FancyPageSwitcher pageSwitcher;
    private final Map<LDLib2FancyUIProvider, UIElement> pageCache = new IdentityHashMap<>();
    private final Deque<NavigationEntry> previousPages = new ArrayDeque<>();

    @Nullable
    private final UIElement playerInventory;
    private int border = DEFAULT_BORDER;
    private LDLib2FancyUIProvider currentPage;
    private LDLib2FancyUIProvider currentHomePage;
    private List<LDLib2FancyUIProvider> allPages = List.of();

    private record NavigationEntry(LDLib2FancyUIProvider page, LDLib2FancyUIProvider homePage,
                                   Runnable onNavigation) {}

    public LDLib2FancyMachineUIElement(LDLib2FancyUIProvider mainPage, Inventory inventory, MachineUIHolder holder,
                                       int width, int height) {
        this.mainPage = mainPage;
        this.holder = holder;
        UITemplate.setLDLib2Bounds(this, 0, 0, width, height);
        style(style -> style
                .backgroundTexture(GuiTextures.BACKGROUND)
                .overflowVisible(true));

        this.pageContainer = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, width, height);
        addChild(pageContainer);

        if (mainPage.hasPlayerInventory()) {
            this.playerInventory = UITemplate.bindPlayerInventoryLDLib2(inventory, GuiTextures.SLOT, 0, 0, true);
            addChild(playerInventory);
        } else {
            this.playerInventory = null;
        }

        this.titleBar = new LDLib2FancyTitleBarElement(width, event -> navigateBack(), event -> openPageSwitcher());
        this.sideTabsElement = new LDLib2FancyTabsElement(this::navigate, -20, 0, 24, height, true);
        this.tooltipsPanel = new LDLib2FancyTooltipsPanelElement(width + 2, 2);
        this.configuratorPanel = new LDLib2ConfiguratorPanelElement(-26, height);
        this.pageSwitcher = new LDLib2FancyPageSwitcher(this::switchPage);
        addChildren(titleBar, sideTabsElement, tooltipsPanel, configuratorPanel);

        initializePages();
    }

    /**
     * Returns the machine holder captured when this LDLib2 Fancy screen was opened.
     */
    public MachineUIHolder getHolder() {
        return holder;
    }

    public LDLib2ConfiguratorPanelElement getConfiguratorPanel() {
        return configuratorPanel;
    }

    public LDLib2FancyTooltipsPanelElement getTooltipsPanel() {
        return tooltipsPanel;
    }

    public LDLib2FancyTabsElement getSideTabsElement() {
        return sideTabsElement;
    }

    public LDLib2FancyMachineUIElement setBorder(int border) {
        if (border < 0) {
            throw new IllegalArgumentException("border must be non-negative");
        }
        this.border = border;
        setupFancyUI(currentPage, currentPage.hasPlayerInventory());
        return this;
    }

    protected void navigate(LDLib2FancyUIProvider newPage) {
        navigate(newPage, currentHomePage);
    }

    protected void navigate(LDLib2FancyUIProvider nextPage, LDLib2FancyUIProvider nextHomePage) {
        if (nextPage != mainPage) {
            if (!previousPages.isEmpty() && previousPages.peek().page == nextPage) {
                previousPages.pop();
            } else {
                previousPages.push(new NavigationEntry(currentPage, currentHomePage, () -> {}));
            }
        } else {
            previousPages.clear();
        }
        performNavigation(nextPage, nextHomePage);
    }

    protected void navigateBack() {
        NavigationEntry navigationEntry = previousPages.pop();
        performNavigation(navigationEntry.page, navigationEntry.homePage);
        navigationEntry.onNavigation.run();
    }

    protected void performNavigation(LDLib2FancyUIProvider nextPage, LDLib2FancyUIProvider nextHomePage) {
        if (currentHomePage != nextHomePage) {
            setupSideTabs(nextHomePage);
        }

        currentPage = nextPage;
        currentHomePage = nextHomePage;

        if (currentPage != currentHomePage) {
            setupFancyUI(currentHomePage);
        }
        setupFancyUI(nextPage, nextPage.hasPlayerInventory());
    }

    protected void openPageSwitcher() {
        pageSwitcher.setPageList(allPages, currentHomePage);
        if (currentPage != currentHomePage && !previousPages.isEmpty()) {
            previousPages.pop();
        }

        sideTabsElement.setVisible(false);
        sideTabsElement.setActive(false);

        previousPages.push(new NavigationEntry(currentHomePage, currentHomePage, () -> {
            sideTabsElement.setVisible(true);
            sideTabsElement.setActive(true);
        }));

        currentPage = pageSwitcher;
        currentHomePage = pageSwitcher;
        setupFancyUI(pageSwitcher);
    }

    protected void switchPage(LDLib2FancyUIProvider nextHomePage) {
        currentHomePage = mainPage;
        currentPage = mainPage;
        previousPages.clear();
        sideTabsElement.setVisible(true);
        sideTabsElement.setActive(true);
        setupSideTabs(currentHomePage);
        navigate(nextHomePage, nextHomePage);
    }

    protected void setupFancyUI(LDLib2FancyUIProvider fancyUI) {
        setupFancyUI(fancyUI, fancyUI.hasPlayerInventory());
    }

    protected void setupFancyUI(LDLib2FancyUIProvider fancyUI, boolean showInventory) {
        clearUI();
        sideTabsElement.selectTab(fancyUI);
        titleBar.updateState(fancyUI, !previousPages.isEmpty(),
                allPages.size() > 1 && currentPage != pageSwitcher);

        UIElement page = getOrCreatePage(fancyUI);
        int pageWidth = fancyUI.getLDLib2PageWidth();
        int pageHeight = fancyUI.getLDLib2PageHeight();
        int contentWidth = Math.max(MIN_PAGE_WIDTH, pageWidth + border * 2);
        int contentHeight = Math.max(MIN_PAGE_HEIGHT, pageHeight + border * 2);
        int rootHeight = contentHeight + (showInventory && playerInventory != null ? PLAYER_INVENTORY_FRAME_HEIGHT : 0);

        UITemplate.setLDLib2Bounds(this, 0, 0, contentWidth, rootHeight);
        UITemplate.setLDLib2Bounds(pageContainer, 0, 0, contentWidth, contentHeight);
        pageContainer.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND));
        UITemplate.setLDLib2Bounds(sideTabsElement, -20, 0, 24, contentHeight);
        tooltipsPanel.moveTo(contentWidth + 2, 2);
        titleBar.resize(contentWidth);

        setupInventoryPosition(showInventory, contentWidth, contentHeight);

        for (UIElement cachedPage : pageCache.values()) {
            cachedPage.setVisible(cachedPage == page);
            cachedPage.setActive(cachedPage == page);
        }
        UITemplate.setLDLib2Bounds(page, (contentWidth - pageWidth) / 2, (contentHeight - pageHeight) / 2,
                pageWidth, pageHeight);

        fancyUI.attachConfigurators(configuratorPanel);
        configuratorPanel.moveTo(-26, rootHeight - configuratorPanel.getPanelContentHeight() - border);
        fancyUI.attachTooltips(tooltipsPanel);
    }

    protected void clearUI() {
        for (UIElement page : pageCache.values()) {
            page.setVisible(false);
            page.setActive(false);
        }
        configuratorPanel.clear();
        tooltipsPanel.clear();
    }

    protected void initializePages() {
        allPages = Stream.concat(Stream.of(mainPage), mainPage.getSubTabs().stream()).toList();
        for (LDLib2FancyUIProvider page : allPages) {
            UIElement createdPage = getOrCreatePage(page);
            createdPage.setVisible(false);
            createdPage.setActive(false);
        }
        performNavigation(mainPage, mainPage);
    }

    protected UIElement getOrCreatePage(LDLib2FancyUIProvider fancyUI) {
        return pageCache.computeIfAbsent(fancyUI, key -> {
            UIElement createdPage = key.createLDLib2MainPage(this);
            pageContainer.addChild(createdPage);
            return createdPage;
        });
    }

    protected void setupSideTabs(LDLib2FancyUIProvider homePage) {
        sideTabsElement.setMainTab(homePage);
        sideTabsElement.clearSubTabs();
        homePage.attachSideTabs(sideTabsElement);
    }

    private void setupInventoryPosition(boolean showInventory, int contentWidth, int contentHeight) {
        if (playerInventory == null) {
            return;
        }
        UITemplate.setLDLib2Bounds(playerInventory, (contentWidth - PLAYER_INVENTORY_WIDTH) / 2, contentHeight,
                PLAYER_INVENTORY_WIDTH, PLAYER_INVENTORY_ELEMENT_HEIGHT);
        playerInventory.setVisible(showInventory);
        playerInventory.setActive(showInventory);
    }
}

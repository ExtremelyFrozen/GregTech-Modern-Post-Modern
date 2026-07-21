package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * LDLib2 page switcher page for the Fancy shell.
 */
public class LDLib2FancyPageSwitcher implements LDLib2FancyUIProvider {

    private static final int WIDTH = 176;
    private static final int HEIGHT = 166;
    private static final int BUTTON_SIZE = 25;
    private static final int ICON_SIZE = 17;
    private static final int COLUMN_WIDTH = 30;
    private static final int ROW_HEIGHT = 30;
    private static final int MAX_COLUMNS = 5;
    private static final int TEXT_COLOR = 0x404040;

    private final Consumer<LDLib2FancyUIProvider> onPageSwitched;
    private final UIElement container;
    private final GTScrollerViewElement scrollableGroup;

    private List<LDLib2FancyUIProvider> pages = List.of();
    private LDLib2FancyUIProvider currentPage;

    public LDLib2FancyPageSwitcher(Consumer<LDLib2FancyUIProvider> onPageSwitched) {
        this.onPageSwitched = onPageSwitched;
        this.container = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, WIDTH, HEIGHT);
        this.scrollableGroup = new GTScrollerViewElement(10, 10, 156, 146);
        container.addChild(scrollableGroup);
    }

    public void setPageList(List<LDLib2FancyUIProvider> allPages, LDLib2FancyUIProvider currentPage) {
        this.pages = allPages;
        this.currentPage = currentPage;
        rebuildPageButtons();
    }

    @Override
    public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
        return container;
    }

    private void rebuildPageButtons() {
        scrollableGroup.clearAllChildren();
        var groupedPages = pages.stream().collect(Collectors.groupingBy(page -> {
            PageGroupingData groupingData = page.getPageGroupingData();
            return groupingData == null ? new PageGroupingData(null, -1) : groupingData;
        }));

        int currentY = 0;
        for (PageGroupingData group : groupedPages.keySet().stream()
                .sorted(Comparator.comparingInt(PageGroupingData::groupPositionWeight))
                .toList()) {
            if (group.groupKey() != null) {
                GTLabelElement label = new GTLabelElement(0, currentY, 150, 10, group.groupKey(), true);
                label.textStyle(style -> style
                        .textColor(TEXT_COLOR)
                        .textShadow(false)
                        .textAlignHorizontal(Horizontal.LEFT)
                        .textAlignVertical(Vertical.CENTER));
                scrollableGroup.addChild(label);
                currentY += 12;
            }

            List<LDLib2FancyUIProvider> groupPages = groupedPages.get(group);
            for (int index = 0; index < groupPages.size(); index++) {
                LDLib2FancyUIProvider page = groupPages.get(index);
                int x = index % MAX_COLUMNS * COLUMN_WIDTH;
                int y = currentY + index / MAX_COLUMNS * ROW_HEIGHT;
                scrollableGroup.addChild(createPageButton(page, x, y));
            }
            if (!groupPages.isEmpty()) {
                currentY += ((groupPages.size() - 1) / MAX_COLUMNS + 1) * ROW_HEIGHT;
            }
        }
    }

    @Override
    public IGuiTexture getTabIcon() {
        return GuiTextures.text("+").setDropShadow(false).setColor(0x000000);
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gtpm.gui.title_bar.page_switcher");
    }

    @Override
    public int getLDLib2PageWidth() {
        return WIDTH;
    }

    @Override
    public int getLDLib2PageHeight() {
        return HEIGHT;
    }

    @Override
    public boolean hasPlayerInventory() {
        return false;
    }

    private UIElement createPageButton(LDLib2FancyUIProvider page, int x, int y) {
        UIElement pageElement = UITemplate.setLDLib2Bounds(new UIElement(), x, y, BUTTON_SIZE, BUTTON_SIZE);
        GTButtonElement button = new GTButtonElement(0, 0, BUTTON_SIZE, BUTTON_SIZE);
        button.noText();
        if (page == currentPage) {
            button.setButtonTextures(GuiTextures.BUTTON, GuiTextures.BUTTON, GuiTextures.BUTTON);
        } else {
            button.setButtonTexture(GuiTextures.BUTTON);
        }
        button.setOnClick(event -> onPageSwitched.accept(page));
        button.style(style -> style.tooltips(page.getTitle()));
        GTImageElement icon = new GTImageElement(4, 4, ICON_SIZE, ICON_SIZE, page.getTabIcon());
        icon.setAllowHitTest(false);
        button.addChild(icon);
        pageElement.addChild(button);
        return pageElement;
    }
}

package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.texture.TextTexture;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * LDLib2 title bar for Fancy pages.
 */
public class LDLib2FancyTitleBarElement extends UIElement {

    private static final int BORDER_SIZE = 3;
    private static final int HORIZONTAL_MARGIN = 8;
    private static final int HEIGHT = 16;
    private static final int BUTTON_WIDTH = 18;
    private static final int TITLE_COLOR = 0x000000;
    private static final float ROLL_SPEED = 0.7f;

    private final int innerHeight = HEIGHT - BORDER_SIZE;
    private final UIElement buttonGroup;
    private final GTButtonElement backButton;
    private final GTButtonElement menuButton;
    private final UIElement mainSection;
    private final GTImageElement tabIcon;
    private final GTImageElement tabTitle;

    private int width;
    private boolean showBackButton;
    private boolean showMenuButton;
    private TextTexture titleText = GuiTextures.text("");

    public LDLib2FancyTitleBarElement(int parentWidth, Consumer<UIEvent> onBackClicked,
                                      Consumer<UIEvent> onMenuClicked) {
        UITemplate.setLDLib2Bounds(this, HORIZONTAL_MARGIN, -HEIGHT, parentWidth, HEIGHT);
        style(style -> style.overflowVisible(true));
        this.width = parentWidth - 2 * HORIZONTAL_MARGIN;

        this.buttonGroup = UITemplate.setLDLib2Bounds(new UIElement(), 0, BORDER_SIZE, width, innerHeight);
        buttonGroup.style(style -> style.backgroundTexture(GuiTextures.TITLE_BAR_BACKGROUND));
        addChild(buttonGroup);

        this.backButton = createTextButton(0, onBackClicked, Component.literal("<"),
                Component.translatable("gtpm.gui.title_bar.back"));
        this.menuButton = createTextButton(width - BUTTON_WIDTH, onMenuClicked, Component.literal("+"),
                Component.translatable("gtpm.gui.title_bar.page_switcher"));
        buttonGroup.addChildren(backButton, menuButton);

        this.mainSection = UITemplate.setLDLib2Bounds(new UIElement(), BUTTON_WIDTH, 0, width, HEIGHT);
        mainSection.style(style -> style.backgroundTexture(GuiTextures.TITLE_BAR_BACKGROUND));
        addChild(mainSection);

        this.tabIcon = new GTImageElement(BORDER_SIZE + 1, BORDER_SIZE + 1,
                innerHeight - 2, innerHeight - 2, IGuiTexture.EMPTY);
        this.tabTitle = new GTImageElement(BORDER_SIZE + innerHeight, BORDER_SIZE, 0, HEIGHT - BORDER_SIZE,
                IGuiTexture.EMPTY);
        tabIcon.setAllowHitTest(false);
        tabTitle.setAllowHitTest(false);
        mainSection.addChildren(tabIcon, tabTitle);
    }

    public void updateState(LDLib2FancyUIProvider currentPage, boolean showBackButton, boolean showMenuButton) {
        this.showBackButton = showBackButton;
        this.showMenuButton = showMenuButton;

        titleText = GuiTextures.text(currentPage.getTitle().copy().getString())
                .setDropShadow(false)
                .setColor(TITLE_COLOR)
                .setType(TextTexture.TextType.ROLL);
        titleText.setRollSpeed(ROLL_SPEED);

        tabIcon.setTexture(currentPage.getTabIcon());
        tabTitle.setTexture(titleText);

        backButton.setVisible(showBackButton);
        backButton.setActive(showBackButton);
        menuButton.setVisible(showMenuButton);
        menuButton.setActive(showMenuButton);

        resize(getSizeWidthValue() + 2 * HORIZONTAL_MARGIN);
    }

    public void resize(int parentWidth) {
        this.width = parentWidth - 2 * HORIZONTAL_MARGIN;
        UITemplate.setLDLib2Bounds(this, HORIZONTAL_MARGIN, -HEIGHT, parentWidth, HEIGHT);

        int hiddenButtons = 2;
        if (showBackButton) {
            hiddenButtons--;
        }
        if (showMenuButton) {
            hiddenButtons--;
        }

        int buttonGroupWidth = this.width - BUTTON_WIDTH * hiddenButtons;
        UITemplate.setLDLib2Bounds(buttonGroup, showBackButton ? 0 : BUTTON_WIDTH, BORDER_SIZE,
                buttonGroupWidth, innerHeight);
        UITemplate.setLDLib2Bounds(menuButton, buttonGroupWidth - BUTTON_WIDTH, 0, BUTTON_WIDTH, innerHeight);

        int mainSectionWidth = this.width - BUTTON_WIDTH * 2;
        int titleWidth = mainSectionWidth - 2 * BORDER_SIZE - innerHeight;
        UITemplate.setLDLib2Bounds(mainSection, BUTTON_WIDTH, 0, mainSectionWidth, HEIGHT);
        UITemplate.setLDLib2Bounds(tabTitle, BORDER_SIZE + innerHeight, BORDER_SIZE, titleWidth,
                HEIGHT - BORDER_SIZE);
        titleText.setWidth(titleWidth);
    }

    private int getSizeWidthValue() {
        return width;
    }

    private static GTButtonElement createTextButton(int x, Consumer<UIEvent> clickHandler, Component text,
                                                    Component tooltip) {
        GTButtonElement button = new GTButtonElement(x, 0, BUTTON_WIDTH, HEIGHT - BORDER_SIZE,
                IGuiTexture.EMPTY, clickHandler);
        button.setText(text);
        button.textStyle(style -> style
                .textColor(TITLE_COLOR)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER));
        button.style(style -> style.tooltips(tooltip));
        return button;
    }
}

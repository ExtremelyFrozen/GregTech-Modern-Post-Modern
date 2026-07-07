package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfigurator;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.integration.xei.GTXEIHelper;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;

/**
 * LDLib2 Fancy configurator for displaying a small item inventory grid.
 */
public class LDLib2FancyInvConfigurator implements LDLib2FancyConfigurator {

    private final CustomItemStackHandler inventory;
    private final Component title;
    private List<Component> tooltips = Collections.emptyList();

    public LDLib2FancyInvConfigurator(CustomItemStackHandler inventory, Component title) {
        this.inventory = inventory;
        this.title = title;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public IGuiTexture getIcon() {
        return GuiTextures.BUTTON_ITEM_OUTPUT;
    }

    @Override
    public List<Component> getTooltips() {
        return tooltips;
    }

    public LDLib2FancyInvConfigurator setTooltips(List<Component> tooltips) {
        this.tooltips = tooltips;
        return this;
    }

    @Override
    public int getLDLib2ConfiguratorWidth() {
        return 18 * getRowSize() + 16;
    }

    @Override
    public int getLDLib2ConfiguratorHeight() {
        return 18 * getColSize() + 16;
    }

    @Override
    public UIElement createLDLib2Configurator() {
        UIElement group = new UIElement();
        UITemplate.setLDLib2Bounds(group, 0, 0, getLDLib2ConfiguratorWidth(), getLDLib2ConfiguratorHeight());

        int rowSize = getRowSize();
        int colSize = getColSize();
        UIElement container = new UIElement();
        UITemplate.setLDLib2Bounds(container, 4, 4, 18 * rowSize + 8, 18 * colSize + 8);
        container.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));

        int index = 0;
        for (int y = 0; y < colSize; y++) {
            for (int x = 0; x < rowSize; x++) {
                GTItemSlotElement slot = new GTItemSlotElement(inventory, index++);
                UITemplate.setLDLib2Bounds(slot, 4 + x * 18, 4 + y * 18, 18, 18);
                slot.setBackgroundTexture(GuiTextures.SLOT);
                slot.setIngredientIO(GTXEIHelper.input());
                container.addChild(slot);
            }
        }

        group.addChild(container);
        return group;
    }

    private int getRowSize() {
        if (inventory.getSlots() == 8) {
            return 4;
        }
        return (int) Math.sqrt(inventory.getSlots());
    }

    private int getColSize() {
        if (inventory.getSlots() == 8) {
            return 2;
        }
        return getRowSize();
    }
}

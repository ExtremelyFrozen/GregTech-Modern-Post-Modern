package com.gregtechceu.gtceu.common.item.behavior;

import com.gregtechceu.gtceu.api.cover.filter.FluidFilter;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.factory.HeldItemUIHolder;
import com.gregtechceu.gtceu.api.item.component.IItemUIFactory;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

import static com.gregtechceu.gtceu.api.gui.UITemplate.setLDLib2Bounds;

public record FluidFilterBehaviour(Function<ItemStack, FluidFilter> filterCreator) implements IItemUIFactory {

    @Override
    public void onAttached(Item item) {
        IItemUIFactory.super.onAttached(item);
        FluidFilter.FILTERS.put(item, filterCreator);
    }

    @Override
    public boolean canCreateLDLib2UI(HeldItemUIHolder holder, Player entityPlayer) {
        return ItemStack.isSameItem(holder.getHeld(), holder.getOpenedStack()) &&
                FluidFilter.loadFilter(holder.getHeld()).supportsLDLib2Configurator();
    }

    @Override
    public boolean isLDLib2UIStillValid(HeldItemUIHolder holder, Player entityPlayer) {
        return ItemStack.isSameItem(holder.getHeld(), holder.getOpenedStack());
    }

    @Override
    public UI createLDLib2UI(HeldItemUIHolder holder, Player entityPlayer) {
        var held = holder.getHeld();
        UIElement root = new UIElement();
        setLDLib2Bounds(root, 0, 0, 176, 157);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND));
        root.addChild(createLDLib2Label(held.getDescriptionId()));
        root.addChild(FluidFilter.loadFilter(held).openLDLib2Configurator((176 - 80) / 2, (60 - 55) / 2 + 15));
        root.addChild(UITemplate.bindPlayerInventoryLDLib2(entityPlayer.getInventory(), GuiTextures.SLOT, 7, 75, true));
        return UI.of(root);
    }

    private static GTLabelElement createLDLib2Label(String descriptionId) {
        GTLabelElement label = new GTLabelElement(5, 5, 166, 10, descriptionId, true);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }
}

package com.gregtechceu.gtceu.common.item.behavior;

import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.factory.HeldItemUIHolder;
import com.gregtechceu.gtceu.api.item.component.IItemUIFactory;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

import static com.gregtechceu.gtceu.api.gui.UITemplate.setLDLib2Bounds;

public record ItemFilterBehaviour(Function<ItemStack, ItemFilter> filterCreator) implements IItemUIFactory {

    @Override
    public void onAttached(Item item) {
        IItemUIFactory.super.onAttached(item);
        ItemFilter.FILTERS.put(item, filterCreator);
    }

    @Override
    public ModularUI createUI(HeldItemUIHolder holder, Player entityPlayer) {
        var held = holder.getHeld();
        return new ModularUI(176, 157, holder, entityPlayer)
                .background(GuiTextures.BACKGROUND)
                .widget(new LabelWidget(5, 5, held.getDescriptionId()))
                .widget(ItemFilter.loadFilter(held).openConfigurator((176 - 80) / 2, (60 - 55) / 2 + 15))
                .widget(UITemplate.bindPlayerInventory(entityPlayer.getInventory(), GuiTextures.SLOT, 7, 75, true));
    }

    @Override
    public boolean canCreateLDLib2UI(HeldItemUIHolder holder, Player entityPlayer) {
        return ItemStack.isSameItem(holder.getHeld(), holder.getOpenedStack()) &&
                ItemFilter.loadFilter(holder.getHeld()).supportsLDLib2Configurator();
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
        root.addChild(ItemFilter.loadFilter(held).openLDLib2Configurator((176 - 80) / 2, (60 - 55) / 2 + 15));
        root.addChild(UITemplate.bindPlayerInventoryLDLib2(entityPlayer.getInventory(), GuiTextures.SLOT, 7, 75, true));
        return UI.of(root);
    }

    private static TextElement createLDLib2Label(String descriptionId) {
        TextElement label = new TextElement();
        label.setText(descriptionId, true);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        setLDLib2Bounds(label, 5, 5, 166, 10);
        return label;
    }
}

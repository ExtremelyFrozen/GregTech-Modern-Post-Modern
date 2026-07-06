package com.gregtechceu.gtceu.api.gui;

import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import dev.vfyjxf.taffy.style.TaffyPosition;

public class UITemplate {

    public static WidgetGroup bindPlayerInventory(Inventory inventoryPlayer, IGuiTexture imageLocation, int x, int y,
                                                  boolean addHotbar) {
        WidgetGroup group = new WidgetGroup(x, y, 162, 54 + (addHotbar ? 22 : 0));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                group.addWidget(new SlotWidget(inventoryPlayer, col + (row + 1) * 9, col * 18, row * 18)
                        .setBackgroundTexture(imageLocation)
                        .setLocationInfo(true, false));
            }
        }
        if (addHotbar) {
            for (int slot = 0; slot < 9; slot++) {
                group.addWidget(new SlotWidget(inventoryPlayer, slot, slot * 18, 58)
                        .setBackgroundTexture(imageLocation)
                        .setLocationInfo(true, true));
            }
        }
        return group;
    }

    public static UIElement bindPlayerInventoryLDLib2(Inventory inventoryPlayer, IGuiTexture imageLocation, int x, int y,
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

    private static void setLDLib2Bounds(UIElement element, int x, int y, int width, int height) {
        element.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(x);
            layout.top(y);
            layout.width(width);
            layout.height(height);
        });
    }
}

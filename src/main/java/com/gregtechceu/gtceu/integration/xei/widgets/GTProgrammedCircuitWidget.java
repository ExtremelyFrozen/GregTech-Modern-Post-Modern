package com.gregtechceu.gtceu.integration.xei.widgets;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.integration.xei.GTXEIHelper;
import com.gregtechceu.gtceu.integration.xei.GTXEIIngredientRole;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.world.item.ItemStack;

import dev.vfyjxf.taffy.style.TaffyPosition;

import java.util.function.Supplier;
import java.util.stream.Stream;

public class GTProgrammedCircuitWidget {

    public static final int WIDTH = 150;
    public static final int HEIGHT = 80;

    private static final int SLOT_SIZE = 18;
    private static final int BACKGROUND_X = 39;
    private static final int BACKGROUND_Y = 0;
    private static final int BACKGROUND_SIZE = 36;
    private static final int SLOT_COLUMNS = 8;
    private static final int SLOT_ROWS = 4;
    private static final int CIRCUIT_COUNT = SLOT_COLUMNS * SLOT_ROWS;

    public ModularUI createModularUI() {
        return ModularUI.of(createUI());
    }

    public UI createUI() {
        UIElement root = new UIElement();
        root.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.width(WIDTH);
            layout.height(HEIGHT);
        });

        root.addChild(createBackgroundElement());

        CustomItemStackHandler handler = new CustomItemStackHandler(CIRCUIT_COUNT);
        for (int j = 0; j < SLOT_ROWS; j++) {
            for (int i = 0; i < SLOT_COLUMNS; i++) {
                int circuit = i + j * SLOT_COLUMNS;
                handler.setStackInSlot(circuit, IntCircuitBehaviour.stack(1 + circuit));
                root.addChild(createCircuitSlot(handler, circuit, i, j));
            }
        }

        return UI.of(root);
    }

    private static UIElement createBackgroundElement() {
        UIElement element = new UIElement();
        element.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(BACKGROUND_X);
            layout.top(BACKGROUND_Y);
            layout.width(BACKGROUND_SIZE);
            layout.height(BACKGROUND_SIZE);
        });
        element.getStyle().backgroundTexture(GuiTextures.SLOT);
        return element;
    }

    private static GTItemSlotElement createCircuitSlot(CustomItemStackHandler handler, int circuit,
                                                       int column, int row) {
        GTXEIIngredientRole primaryRole = circuit == CIRCUIT_COUNT - 1 ? GTXEIHelper.output() : GTXEIHelper.input();
        Supplier<Stream<ItemStack>> stackSupplier = () -> Stream.of(IntCircuitBehaviour.stack(circuit + 1));

        GTItemSlotElement slot = new GTItemSlotElement(handler, circuit);
        slot.layout(layout -> {
            layout.positionType(TaffyPosition.ABSOLUTE);
            layout.left(3 + SLOT_SIZE * column);
            layout.top(SLOT_SIZE * row);
            layout.width(SLOT_SIZE);
            layout.height(SLOT_SIZE);
        });
        slot.setBackgroundTexture(GuiTextures.SLOT);
        slot.setCanTakeItems(false);
        slot.setCanPutItems(false);
        slot.xeiRecipeSlot(primaryRole, 1.0f, 1, stackSupplier);
        slot.xeiRecipeIngredient(primaryRole, stackSupplier);
        if (GTXEIHelper.isInput(primaryRole)) {
            slot.xeiRecipeIngredient(GTXEIHelper.output(), stackSupplier);
        }
        slot.setIngredientIO(primaryRole);
        return slot;
    }
}

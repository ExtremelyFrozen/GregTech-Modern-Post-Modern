package com.gregtechceu.gtceu.integration.xei.widgets;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.integration.xei.GTXEIHelper;

import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.neoforged.neoforge.items.ItemStackHandler;

public class GTProgrammedCircuitWidget extends WidgetGroup {

    public GTProgrammedCircuitWidget() {
        super(0, 0, 150, 80);
        setClientSideWidget();
        setRecipe();
    }

    public void setRecipe() {
        addWidget(new ImageWidget(39, 0, 36, 36, GuiTextures.SLOT));

        ItemStackHandler handler = new CustomItemStackHandler(32);
        for (int j = 0; j < 4; j++) {
            for (int i = 0; i < 8; i++) {
                int circuit = i + j * 8;
                handler.setStackInSlot(circuit, IntCircuitBehaviour.stack(1 + circuit));
                var slot = new SlotWidget(handler, circuit, 3 + 18 * i, 18 * j, false, false)
                        .setIngredientIO(circuit == 31 ? GTXEIHelper.output() : GTXEIHelper.input());
                if (circuit != 31) {
                    addWidget(new GTRecipeIngredientSlotWidget(slot, GTXEIHelper.output()));
                }
                addWidget(slot);
            }
        }
    }
}

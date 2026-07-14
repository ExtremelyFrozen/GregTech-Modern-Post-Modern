package com.gregtechceu.gtceu.integration.ae2.gui.element;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputWaitingListEntry;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEFluidKey;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Fluid-key projection of the shared opening-scoped ME output waiting-list element.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class MEFluidOutputWaitingListElement extends MEOutputWaitingListElement {

    /** Creates the fluid viewport at the supplied page-relative position. */
    public MEFluidOutputWaitingListElement(int x, int y, MachineUIHolder targetHolder,
                                           MachineUIHolder actionHolder,
                                           BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                           BooleanSupplier canSendAction) {
        super(x, y, targetHolder, actionHolder, actionSender, canSendAction);
    }

    /** Creates the fluid viewport at the legacy page position of {@code 5,20}. */
    public MEFluidOutputWaitingListElement(MachineUIHolder targetHolder, MachineUIHolder actionHolder,
                                           BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                           BooleanSupplier canSendAction) {
        super(targetHolder, actionHolder, actionSender, canSendAction);
    }

    @Override
    protected UIElement createRow(MEOutputWaitingListEntry entry) {
        if (!(entry.key() instanceof AEFluidKey fluidKey)) {
            throw new IllegalArgumentException("ME fluid output waiting-list row received non-fluid key " +
                    entry.key());
        }

        UIElement row = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, ROW_WIDTH, ROW_HEIGHT);
        GTImageElement amountBackground = new GTImageElement(18, 0, 140, ROW_HEIGHT,
                GuiTextures.NUMBER_BACKGROUND);
        amountBackground.setAllowHitTest(false);
        row.addChild(amountBackground);

        GTFluidSlotElement fluid = new GTFluidSlotElement()
                .setFluid(fluidKey.toStack(1))
                .setCapacity(1)
                .setShowAmount(false)
                .setAllowClickFilled(false)
                .setAllowClickDrained(false)
                .setIngredientIO(IngredientIO.NONE)
                .setBackgroundTexture(GuiTextures.FLUID_SLOT)
                .setOnAddedTooltips((slot, tooltips) -> tooltips.add(
                        Component.literal(String.format("%,d mB", entry.amount()))));
        row.addChild(UITemplate.setLDLib2Bounds(fluid, 0, 0, 18, ROW_HEIGHT));

        GTLabelElement amount = new GTLabelElement(21, 0, 134, ROW_HEIGHT,
                Component.literal(String.format("x%,d", entry.amount())))
                .setTextColor(-1)
                .setTextShadow(true)
                .setTextAlignHorizontal(Horizontal.LEFT)
                .setTextAlignVertical(Vertical.CENTER);
        amount.setAllowHitTest(false);
        row.addChild(amount);
        return row;
    }

    @Override
    protected void validateEntry(MEOutputWaitingListEntry entry) {
        if (!(entry.key() instanceof AEFluidKey)) {
            throw new IllegalArgumentException(
                    "ME fluid output waiting-list publication contains non-fluid key " + entry.key());
        }
    }
}

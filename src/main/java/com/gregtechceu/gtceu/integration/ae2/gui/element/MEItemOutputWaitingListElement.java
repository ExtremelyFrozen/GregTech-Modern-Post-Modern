package com.gregtechceu.gtceu.integration.ae2.gui.element;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
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

import appeng.api.stacks.AEItemKey;

import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Item-key projection of the shared opening-scoped ME output waiting-list element.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class MEItemOutputWaitingListElement extends MEOutputWaitingListElement {

    /** Creates the item viewport at the supplied page-relative position. */
    public MEItemOutputWaitingListElement(int x, int y, MachineUIHolder targetHolder,
                                          MachineUIHolder actionHolder,
                                          BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                          BooleanSupplier canSendAction) {
        super(x, y, targetHolder, actionHolder, actionSender, canSendAction);
    }

    /** Creates the item viewport at the legacy page position of {@code 5,20}. */
    public MEItemOutputWaitingListElement(MachineUIHolder targetHolder, MachineUIHolder actionHolder,
                                          BiConsumer<MachineUIHolder, SyncActionData> actionSender,
                                          BooleanSupplier canSendAction) {
        super(targetHolder, actionHolder, actionSender, canSendAction);
    }

    @Override
    protected UIElement createRow(MEOutputWaitingListEntry entry) {
        if (!(entry.key() instanceof AEItemKey itemKey)) {
            throw new IllegalArgumentException("ME item output waiting-list row received non-item key " +
                    entry.key());
        }

        UIElement row = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, ROW_WIDTH, ROW_HEIGHT);
        GTImageElement amountBackground = new GTImageElement(18, 0, 140, ROW_HEIGHT,
                GuiTextures.NUMBER_BACKGROUND);
        amountBackground.setAllowHitTest(false);
        row.addChild(amountBackground);

        GTItemSlotElement item = new GTItemSlotElement()
                .setCanPutItems(false)
                .setCanTakeItems(false)
                .setIngredientIO(IngredientIO.NONE)
                .setBackgroundTexture(GuiTextures.SLOT)
                .setOnAddedTooltips((slot, tooltips) -> tooltips.add(
                        Component.literal(String.format("%,d", entry.amount()))));
        item.setItem(itemKey.toStack(1), false);
        row.addChild(UITemplate.setLDLib2Bounds(item, 0, 0, 18, ROW_HEIGHT));

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
        if (!(entry.key() instanceof AEItemKey)) {
            throw new IllegalArgumentException(
                    "ME item output waiting-list publication contains non-item key " + entry.key());
        }
    }
}

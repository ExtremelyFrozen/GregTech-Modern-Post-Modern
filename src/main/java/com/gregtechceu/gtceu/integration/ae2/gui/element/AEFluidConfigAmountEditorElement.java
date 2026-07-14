package com.gregtechceu.gtceu.integration.ae2.gui.element;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTIntInputElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;

import lombok.Getter;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Centered positive-integer editor for the selected ordinary ME fluid configuration slot.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class AEFluidConfigAmountEditorElement extends UIElement {

    private final IntSupplier selectedSlotSupplier;
    private final AEFluidConfigAmountProjection amountProjection;
    private final BooleanSupplier canSendAction;
    @Getter
    private final GTIntInputElement amountInput;

    /**
     * Creates an 80x30 editor that projects the latest request until the immutable snapshot confirms it.
     */
    AEFluidConfigAmountEditorElement(IntSupplier selectedSlotSupplier,
                                     AEFluidConfigAmountProjection amountProjection,
                                     BooleanSupplier canSendAction) {
        this.selectedSlotSupplier = selectedSlotSupplier;
        this.amountProjection = amountProjection;
        this.canSendAction = canSendAction;
        UITemplate.setLDLib2Bounds(this, 35, 29, 80, 30);

        addChild(new GTImageElement(0, 0, 80, 30, GuiTextures.NUMBER_BACKGROUND));
        addChild(new GTLabelElement(3, 2, 74, 10, Component.literal("Amount")));
        this.amountInput = new GTIntInputElement(3, 14, 74, 14,
                this::selectedAmount, this::setSelectedAmount)
                .setMin(1)
                .setMax(Integer.MAX_VALUE);
        addChild(amountInput);
        refreshVisibility();
    }

    @Override
    public void screenTick() {
        refreshVisibility();
        super.screenTick();
    }

    private int selectedAmount() {
        return amountProjection.projectedAmount(selectedSlotSupplier.getAsInt()).orElse(1);
    }

    private void setSelectedAmount(int amount) {
        amountProjection.requestAmount(selectedSlotSupplier.getAsInt(), amount);
    }

    private void refreshVisibility() {
        boolean visible = amountProjection.projectedAmount(selectedSlotSupplier.getAsInt()).isPresent();
        setVisible(visible);
        setActive(visible && canSendAction.getAsBoolean());
    }
}

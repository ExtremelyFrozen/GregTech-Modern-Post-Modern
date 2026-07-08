package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.api.gui.GuiTextures;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 phantom fluid slot element backed by external state.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-phantom-fluid-slot", group = "gtm", registry = "ldlib2:ui_element")
public class GTPhantomFluidSlotElement extends GTFluidSlotElement {

    private Supplier<FluidStack> fluidSupplier;
    private Consumer<FluidStack> fluidConsumer;
    private IntSupplier maxAmountSupplier;
    private boolean suppressUpdate;

    public GTPhantomFluidSlotElement() {
        this(() -> FluidStack.EMPTY, fluid -> {}, () -> 1);
    }

    public GTPhantomFluidSlotElement(Supplier<FluidStack> fluidSupplier,
                                     Consumer<FluidStack> fluidConsumer,
                                     IntSupplier maxAmountSupplier) {
        this.fluidSupplier = fluidSupplier;
        this.fluidConsumer = fluidConsumer;
        this.maxAmountSupplier = maxAmountSupplier;
        setBackgroundTexture(GuiTextures.SLOT);
        xeiPhantom();
        addEventListener(UIEvents.MOUSE_WHEEL, this::adjustFluidAmount);
        refreshFromSupplier();
    }

    public GTPhantomFluidSlotElement setFluidSupplier(Supplier<FluidStack> fluidSupplier) {
        this.fluidSupplier = fluidSupplier;
        return refreshFromSupplier();
    }

    public GTPhantomFluidSlotElement setFluidConsumer(Consumer<FluidStack> fluidConsumer) {
        this.fluidConsumer = fluidConsumer;
        return this;
    }

    public GTPhantomFluidSlotElement setMaxAmountSupplier(IntSupplier maxAmountSupplier) {
        this.maxAmountSupplier = maxAmountSupplier;
        return refreshFromSupplier();
    }

    public GTPhantomFluidSlotElement refreshFromSupplier() {
        suppressUpdate = true;
        int maxAmount = getMaxAmount();
        setCapacity(maxAmount);
        setShowAmount(maxAmount > 1);
        setFluid(fluidSupplier.get());
        suppressUpdate = false;
        return this;
    }

    @Override
    public GTPhantomFluidSlotElement setFluid(FluidStack fluid) {
        FluidStack normalized = normalizeFluid(fluid);
        super.setFluid(normalized);
        if (!suppressUpdate) {
            fluidConsumer.accept(copyFluid(normalized));
        }
        return this;
    }

    private void adjustFluidAmount(UIEvent event) {
        FluidStack current = getFluid();
        if (current.isEmpty() || event.deltaY == 0) {
            return;
        }

        int delta = getModifiedChangeAmount(event.deltaY > 0 ? 1 : -1, event);
        int amount = (int) Math.min(Math.max((long) current.getAmount() + delta, 0L), getMaxAmount());
        if (amount <= 0) {
            setFluid(FluidStack.EMPTY);
        } else {
            FluidStack adjusted = current.copy();
            adjusted.setAmount(amount);
            setFluid(adjusted);
        }
        event.stopPropagation();
    }

    private int getModifiedChangeAmount(int amount, UIEvent event) {
        if (event.isShiftDown()) {
            amount *= 10;
        }
        if (event.isCtrlDown()) {
            amount *= 100;
        }
        if (!event.isAltDown()) {
            amount *= 1000;
        }
        return amount;
    }

    private FluidStack normalizeFluid(FluidStack fluid) {
        int maxAmount = getMaxAmount();
        if (fluid.isEmpty() || maxAmount <= 0) {
            return FluidStack.EMPTY;
        }

        FluidStack normalized = fluid.copy();
        int amount = Math.min(Math.max(normalized.getAmount(), 0), maxAmount);
        if (amount <= 0) {
            return FluidStack.EMPTY;
        }
        normalized.setAmount(amount);
        return normalized;
    }

    private FluidStack copyFluid(FluidStack fluid) {
        return fluid.isEmpty() ? FluidStack.EMPTY : fluid.copy();
    }

    private int getMaxAmount() {
        return Math.max(maxAmountSupplier.getAsInt(), 0);
    }
}

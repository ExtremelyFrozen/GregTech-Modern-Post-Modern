package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.api.gui.GuiTextures;

import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 phantom item slot element backed by external state.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-phantom-item-slot", group = "gtm", registry = "ldlib2:ui_element")
public class GTPhantomItemSlotElement extends GTItemSlotElement {

    private Supplier<ItemStack> itemSupplier;
    private Consumer<ItemStack> itemConsumer;
    private IntSupplier maxStackSizeSupplier;

    public GTPhantomItemSlotElement() {
        this(() -> ItemStack.EMPTY, stack -> {}, () -> 64);
    }

    public GTPhantomItemSlotElement(Supplier<ItemStack> itemSupplier,
                                    Consumer<ItemStack> itemConsumer,
                                    IntSupplier maxStackSizeSupplier) {
        this.itemSupplier = itemSupplier;
        this.itemConsumer = itemConsumer;
        this.maxStackSizeSupplier = maxStackSizeSupplier;
        setBackgroundTexture(GuiTextures.SLOT);
        xeiPhantom();
        registerValueListener(stack -> this.itemConsumer.accept(copyItem(stack)));
        refreshFromSupplier();
    }

    public GTPhantomItemSlotElement setItemSupplier(Supplier<ItemStack> itemSupplier) {
        this.itemSupplier = itemSupplier;
        return refreshFromSupplier();
    }

    public GTPhantomItemSlotElement setItemConsumer(Consumer<ItemStack> itemConsumer) {
        this.itemConsumer = itemConsumer;
        return this;
    }

    public GTPhantomItemSlotElement setMaxStackSizeSupplier(IntSupplier maxStackSizeSupplier) {
        this.maxStackSizeSupplier = maxStackSizeSupplier;
        return refreshFromSupplier();
    }

    public GTPhantomItemSlotElement refreshFromSupplier() {
        setItem(itemSupplier.get(), false);
        return this;
    }

    @Override
    public GTPhantomItemSlotElement setItem(ItemStack item) {
        return setItem(item, true);
    }

    @Override
    public GTPhantomItemSlotElement setItem(ItemStack itemStack, boolean notify) {
        setValue(itemStack, notify);
        return this;
    }

    @Override
    public GTPhantomItemSlotElement setValue(@Nullable ItemStack value, boolean notify) {
        super.setValue(normalizeItem(value), notify);
        return this;
    }

    private ItemStack normalizeItem(@Nullable ItemStack stack) {
        int maxStackSize = getMaxStackSize();
        if (stack == null || stack.isEmpty() || maxStackSize <= 0) {
            return ItemStack.EMPTY;
        }

        ItemStack normalized = stack.copy();
        normalized.setCount(Math.min(Math.max(normalized.getCount(), 1), maxStackSize));
        return normalized;
    }

    private ItemStack copyItem(ItemStack stack) {
        return stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    private int getMaxStackSize() {
        return maxStackSizeSupplier.getAsInt();
    }
}

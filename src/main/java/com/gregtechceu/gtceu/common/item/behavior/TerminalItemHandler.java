package com.gregtechceu.gtceu.common.item.behavior;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.items.ItemStackHandler;

final class TerminalItemHandler extends ItemStackHandler {

    static final int SIZE = 27;

    private final ItemStack terminal;

    TerminalItemHandler(ItemStack terminal) {
        super(NonNullList.withSize(SIZE, ItemStack.EMPTY));
        this.terminal = terminal;
        ItemContainerContents contents = terminal.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        contents.copyInto(stacks);
    }

    @Override
    protected void onContentsChanged(int slot) {
        terminal.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(stacks));
    }
}

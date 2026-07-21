package com.gregtechceu.gtceu.api.transfer.item;

import com.gregtechceu.gtceu.api.transfer.DataComponentTransfer;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.datacomponents.TransferData;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.items.ItemStackHandler;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class CustomItemStackHandler extends ItemStackHandler implements DataComponentTransfer {

    @Getter
    @Setter
    protected @NotNull Runnable onContentsChanged = () -> {};
    @Getter
    @Setter
    protected Predicate<ItemStack> filter = stack -> true;

    public CustomItemStackHandler() {
        super();
    }

    public CustomItemStackHandler(int size) {
        super(size);
    }

    public CustomItemStackHandler(ItemStack itemStack) {
        this(NonNullList.of(ItemStack.EMPTY, itemStack));
    }

    public CustomItemStackHandler(NonNullList<ItemStack> stacks) {
        super(stacks);
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return filter.test(stack);
    }

    @Override
    public void onContentsChanged(int slot) {
        onContentsChanged.run();
    }

    /**
     * Don't use unless necessary.<br>
     * (A good use case is loading/saving this container's items from/to a {@link ItemContainerContents} component)
     *
     * @return the internal list of items in this handler
     */
    @ApiStatus.Internal
    public NonNullList<ItemStack> getStacks() {
        return this.stacks;
    }

    public void clear() {
        stacks.clear();
        onContentsChanged.run();
    }

    public void dropInventoryInWorld(Level world, BlockPos pos) {
        for (ItemStack stack : stacks) {
            Block.popResource(world, pos, stack);
        }
        clear();
    }

    @Override
    public DataComponentMap exportComponents() {
        return DataComponentMap.builder()
                .set(GTDataComponents.TRANSFER_ITEM_HANDLER.get(),
                        new TransferData.ItemHandler(stacks.size(), stacks))
                .build();
    }

    @Override
    public void importComponents(DataComponentMap components) {
        TransferData.ItemHandler data = components.get(GTDataComponents.TRANSFER_ITEM_HANDLER.get());
        if (data == null) {
            throw new IllegalArgumentException("Item handler component data is missing transfer_item_handler");
        }
        if (data.slots() != stacks.size()) {
            throw new IllegalArgumentException("Item handler expected " + stacks.size() + " slots but received " +
                    data.slots());
        }
        for (int i = 0; i < data.stacks().size(); i++) {
            stacks.set(i, data.stacks().get(i).copy());
        }
        onContentsChanged.run();
    }
}

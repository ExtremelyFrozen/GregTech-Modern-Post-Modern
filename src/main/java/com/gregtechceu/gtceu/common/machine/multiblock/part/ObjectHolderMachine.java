package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.BlockableSlotWidget;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerList;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.Position;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ObjectHolderMachine extends MultiblockPartMachine {

    @SaveField
    private final ObjectInputHandler inputItemHandler;
    @SaveField
    private final DataItemHandler dataItemHandler;
    @SaveField
    @SyncToClient
    private boolean isLocked;

    public ObjectHolderMachine(BlockEntityCreationInfo info) {
        super(info);
        inputItemHandler = attachTrait(new ObjectInputHandler());
        dataItemHandler = attachTrait(new DataItemHandler());
    }

    public void setLocked(boolean locked) {
        isLocked = locked;
        syncDataHolder.markClientSyncFieldDirty("isLocked");
    }

    public boolean isLocked() {
        return isLocked;
    }

    public ItemStack getHeldItem(boolean remove) {
        return getStack(inputItemHandler, remove);
    }

    public void setHeldItem(ItemStack heldItem) {
        inputItemHandler.setStackInSlot(0, heldItem);
    }

    public ItemStack getDataItem(boolean remove) {
        return getStack(dataItemHandler, remove);
    }

    public void setDataItem(ItemStack dataItem) {
        dataItemHandler.setStackInSlot(0, dataItem);
    }

    private ItemStack getStack(NotifiableItemStackHandler handler, boolean remove) {
        ItemStack stackInSlot = handler.getStackInSlot(0);
        if (remove && !stackInSlot.isEmpty()) {
            handler.setStackInSlot(0, ItemStack.EMPTY);
        }
        return stackInSlot;
    }

    @Override
    public Widget createUIWidget() {
        return new WidgetGroup(new Position(0, 0))
                .addWidget(new ImageWidget(46, 15, 84, 60, GuiTextures.PROGRESS_BAR_RESEARCH_STATION_BASE))
                .addWidget(new BlockableSlotWidget(inputItemHandler, 0, 79, 36)
                        .setIsBlocked(this::isLocked)
                        .setBackground(GuiTextures.SLOT, GuiTextures.RESEARCH_STATION_OVERLAY))
                .addWidget(new BlockableSlotWidget(dataItemHandler, 0, 15, 36)
                        .setIsBlocked(this::isLocked)
                        .setBackground(GuiTextures.SLOT, GuiTextures.DATA_ORB_OVERLAY));
    }

    @Override
    public void setFrontFacing(Direction frontFacing) {
        super.setFrontFacing(frontFacing);
        var controllers = getControllers();
        for (var controller : controllers) {
            if (controller != null && controller.isFormed()) {
                controller.checkPatternWithLock(getSubstructureName(controller));
            }
        }
    }

    @Override
    public List<RecipeHandlerList> getRecipeHandlers() {
        return List.of(
                RecipeHandlerList.of(IO.IN, getPaintingColor(), inputItemHandler),
                RecipeHandlerList.of(IO.BOTH, getPaintingColor(), dataItemHandler));
    }

    @MustBeInvokedByOverriders
    @Override
    public void removedFromController(MultiblockControllerMachine controller, String structureName) {
        super.removedFromController(controller, structureName);
        setLocked(false);
    }

    private boolean isDataItemFacing(@Nullable Direction direction) {
        return direction == getFrontFacing() || direction == getFrontFacing().getOpposite();
    }

    private class ObjectInputHandler extends NotifiableItemStackHandler {

        public ObjectInputHandler() {
            super(1, IO.IN, IO.BOTH, ObjectHolderItemStackHandler::new);
            capabilityValidator = direction -> direction == null || !isDataItemFacing(direction);
            setFilter(stack -> stack.isEmpty() || !stack.has(GTDataComponents.DATA_ITEM));
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.isEmpty() || !stack.has(GTDataComponents.DATA_ITEM);
        }
    }

    private class DataItemHandler extends NotifiableItemStackHandler {

        public DataItemHandler() {
            super(1, IO.BOTH, IO.BOTH, ObjectHolderItemStackHandler::new);
            capabilityValidator = direction -> direction == null || isDataItemFacing(direction);
            setFilter(stack -> stack.isEmpty() || stack.has(GTDataComponents.DATA_ITEM));
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isLocked()) {
                return super.insertItem(slot, stack, simulate);
            }
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!isLocked()) {
                return super.extractItem(slot, amount, simulate);
            }
            return ItemStack.EMPTY;
        }

        @Override
        public List<SizedIngredient> handleRecipeInner(IO io, GTRecipe recipe, List<SizedIngredient> left,
                                                       boolean simulate) {
            if (io == IO.OUT && simulate) {
                return List.of();
            }
            if (io == IO.OUT && !simulate) {
                setLocked(false);
            }
            List<SizedIngredient> result = super.handleRecipeInner(io, recipe, left, simulate);
            if (result.isEmpty() && !simulate && (io == IO.IN || io == IO.OUT)) {
                setLocked(io == IO.IN);
            }
            return result;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.isEmpty() || stack.has(GTDataComponents.DATA_ITEM);
        }
    }

    private static class ObjectHolderItemStackHandler extends CustomItemStackHandler {

        public ObjectHolderItemStackHandler(int size) {
            super(size);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    }
}

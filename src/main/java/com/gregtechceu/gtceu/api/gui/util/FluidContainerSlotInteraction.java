package com.gregtechceu.gtceu.api.gui.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidActionResult;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Executes the authoritative cursor-container transaction for one independently exposed fluid slot.
 *
 * @param fluidTank         single-slot fluid handler receiving or supplying the container fluid
 * @param allowClickFilled  whether stored fluid may fill cursor containers
 * @param allowClickDrained whether cursor containers may empty into the handler
 */
@SuppressWarnings("resource")
public record FluidContainerSlotInteraction(IFluidHandler fluidTank, boolean allowClickFilled,
                                            boolean allowClickDrained) {

    /**
     * Applies one normal or Shift interaction and updates the player's carried stack exactly once.
     */
    public void click(ServerPlayer player, boolean shiftDown) {
        ItemStack currentStack = player.containerMenu.getCarried();
        if (FluidUtil.getFluidHandler(currentStack).isEmpty()) {
            return;
        }

        int maxAttempts = shiftDown ? currentStack.getCount() : 1;
        FluidStack initialFluid = fluidTank.getFluidInTank(0).copy();
        if (allowClickFilled && !initialFluid.isEmpty() &&
                fillContainers(player, currentStack, maxAttempts, initialFluid)) {
            return;
        }
        if (allowClickDrained) {
            emptyContainers(player, currentStack, maxAttempts);
        }
    }

    private boolean fillContainers(ServerPlayer player, ItemStack currentStack, int maxAttempts,
                                   FluidStack initialFluid) {
        boolean performedFill = false;
        ItemStack filledResult = ItemStack.EMPTY;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            FluidActionResult simulated = FluidUtil.tryFillContainer(
                    currentStack, fluidTank, Integer.MAX_VALUE, null, false);
            if (!simulated.isSuccess()) {
                break;
            }

            ItemStack result = FluidUtil.tryFillContainer(
                    currentStack, fluidTank, Integer.MAX_VALUE, null, true).getResult();
            performedFill = true;
            currentStack.shrink(1);
            filledResult = mergeOrStoreResult(player, filledResult, result);
        }
        if (!performedFill) {
            return false;
        }

        SoundEvent sound = initialFluid.getFluid().getFluidType().getSound(initialFluid, SoundActions.BUCKET_FILL);
        if (sound == null) {
            sound = SoundEvents.BUCKET_FILL;
        }
        player.level().playSound(null, player, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        finish(player, currentStack, filledResult);
        return true;
    }

    private void emptyContainers(ServerPlayer player, ItemStack currentStack, int maxAttempts) {
        boolean performedEmptying = false;
        ItemStack drainedResult = ItemStack.EMPTY;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int remainingCapacity = fluidTank.getTankCapacity(0) - fluidTank.getFluidInTank(0).getAmount();
            FluidActionResult simulated = FluidUtil.tryEmptyContainer(
                    currentStack, fluidTank, remainingCapacity, null, false);
            if (!simulated.isSuccess()) {
                break;
            }

            ItemStack result = FluidUtil.tryEmptyContainer(
                    currentStack, fluidTank, remainingCapacity, null, true).getResult();
            performedEmptying = true;
            currentStack.shrink(1);
            drainedResult = mergeOrStoreResult(player, drainedResult, result);
        }
        if (!performedEmptying) {
            return;
        }

        FluidStack filledFluid = fluidTank.getFluidInTank(0);
        SoundEvent sound = filledFluid.getFluid().getFluidType().getSound(filledFluid, SoundActions.BUCKET_EMPTY);
        if (sound == null) {
            sound = SoundEvents.BUCKET_EMPTY;
        }
        player.level().playSound(null, player, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
        finish(player, currentStack, drainedResult);
    }

    private static ItemStack mergeOrStoreResult(ServerPlayer player, ItemStack storedResult,
                                                ItemStack currentResult) {
        if (storedResult.isEmpty()) {
            return currentResult.copy();
        }
        if (ItemStack.isSameItemSameComponents(storedResult, currentResult)) {
            int availableSpace = storedResult.getMaxStackSize() - storedResult.getCount();
            if (currentResult.getCount() <= availableSpace) {
                storedResult.grow(currentResult.getCount());
            } else {
                player.getInventory().placeItemBackInInventory(currentResult);
            }
            return storedResult;
        }
        player.getInventory().placeItemBackInInventory(storedResult);
        return currentResult.copy();
    }

    private static void finish(ServerPlayer player, ItemStack currentStack, ItemStack resultStack) {
        if (currentStack.isEmpty()) {
            player.containerMenu.setCarried(resultStack);
        } else {
            player.containerMenu.setCarried(currentStack);
            player.getInventory().placeItemBackInInventory(resultStack);
        }
        player.containerMenu.broadcastChanges();
    }
}

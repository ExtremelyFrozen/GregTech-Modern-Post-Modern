package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.data.GTMenuTypes;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;

import java.util.UUID;

/**
 * Owns the opening-data protocol for LDLib2 machine menus with acknowledged runtime item slots.
 */
public final class DynamicItemSlotMachineUIMenuType {

    private DynamicItemSlotMachineUIMenuType() {}

    /**
     * Opens a server-captured machine menu whose session identity is sent to the client.
     */
    public static boolean openUI(MetaMachine machine, ServerPlayer player) {
        if (!(machine instanceof LDLib2DynamicItemSlotMachineUIProvider provider)) {
            return false;
        }
        DynamicItemSlotMachineUIHolder holder = new DynamicItemSlotMachineUIHolder(player, machine);
        if (!provider.canCreateLDLib2UI(player, holder)) {
            return false;
        }
        try {
            if (player.openMenu(holder).isEmpty()) {
                holder.abortOpening(player);
                return false;
            }
            holder.publishInitialManifest(player);
            return true;
        } catch (RuntimeException exception) {
            try {
                holder.abortOpening(player);
            } catch (RuntimeException closeException) {
                exception.addSuppressed(closeException);
            }
            GTCEu.LOGGER.error("Failed to open dynamic item-slot machine menu at {} for {}",
                    machine.getBlockPos(), player.getGameProfile().getName(), exception);
            return false;
        }
    }

    /**
     * Reconstructs the client menu from the exact identity written by its server holder.
     */
    public static ModularUIContainerMenu create(int windowId, Inventory inventory, RegistryFriendlyByteBuf data) {
        BlockPos pos = data.readBlockPos();
        ResourceLocation machineDefinitionId = data.readResourceLocation();
        UUID menuSessionId = data.readUUID();
        DynamicItemSlotMachineUIHolder holder = DynamicItemSlotMachineUIHolder.client(
                inventory.player, pos, machineDefinitionId, menuSessionId);
        return new GTDynamicItemSlotContainerMenu(
                GTMenuTypes.DYNAMIC_ITEM_SLOT_MACHINE_UI.get(), windowId, inventory, holder);
    }
}

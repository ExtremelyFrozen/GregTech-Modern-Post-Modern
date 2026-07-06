package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.common.network.packets.CPacketItemActionToServer;

import com.lowdragmc.lowdraglib2.gui.factory.HeldItemUIMenuType;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Centralizes held item UI opening while GTM migrates item screens from LDLib to LDLib2.
 */
public final class HeldItemUIHelper {

    private HeldItemUIHelper() {}

    /**
     * Opens the UI for the item currently held in {@code hand}.
     *
     * @return {@code true} when the menu was opened by the current bridge implementation.
     */
    public static boolean open(ServerPlayer player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof LDLib2HeldItemUIProvider uiProvider &&
                uiProvider.canCreateLDLib2UI(player, new HeldItemUIHolderContext(player, hand))) {
            return HeldItemUIMenuType.openUI(player, hand);
        }
        return false;
    }

    /**
     * Sends a held item UI sync action to the server.
     */
    public static void sendAction(HeldItemUIHolder holder, SyncActionData action) {
        PacketDistributor.sendToServer(new CPacketItemActionToServer(holder.getHand(), holder.getOpenedStack(), action));
    }
}

package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.common.network.packets.CPacketCoverActionToServer;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Centralizes cover UI opening while GTM migrates cover screens to an LDLib2 holder.
 */
public final class CoverUIHelper {

    private CoverUIHelper() {}

    /**
     * Opens the UI for a cover attached to a block face.
     *
     * @return {@code true} when the menu was opened by the current bridge implementation.
     */
    public static boolean open(CoverBehavior cover, ServerPlayer player) {
        return CoverUIFactory.INSTANCE.openUI(cover, player);
    }

    /**
     * Sends a cover UI sync action to the server.
     */
    public static void sendAction(UICoverHolder holder, SyncActionData action) {
        PacketDistributor.sendToServer(new CPacketCoverActionToServer(holder.getPos(), holder.getSide(),
                holder.getCoverDefinitionId(), action));
    }
}

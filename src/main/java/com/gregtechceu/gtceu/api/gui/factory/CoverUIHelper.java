package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.common.network.packets.CPacketCoverActionToServer;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

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
        if (GTCoverUIMenuType.openUI(cover, player)) {
            return true;
        }
        if (cover instanceof IUICover) {
            return CoverUIFactory.INSTANCE.openUI(cover, player);
        }
        return false;
    }

    /**
     * Creates the parallel LDLib2 UI when the cover exposes the LDLib2 provider contract.
     *
     * @return the LDLib2 UI, or {@code null} when the cover has not migrated to the parallel provider contract.
     */
    @Nullable
    public static ModularUI createLDLib2UI(CoverBehavior cover, Player player) {
        if (cover instanceof LDLib2CoverUIProvider uiProvider) {
            LDLib2CoverUIHolderContext holder = new LDLib2CoverUIHolderContext(player, cover);
            if (!uiProvider.canCreateLDLib2UI(player, holder)) {
                return null;
            }
            var ui = uiProvider.createLDLib2UI(player, holder);
            if (ui == null) {
                throw new IllegalStateException("Cover LDLib2 UI provider returned null.");
            }
            return ModularUI.of(ui, player);
        }
        return null;
    }

    /**
     * Sends a cover UI sync action to the server.
     */
    public static void sendAction(UICoverHolder holder, SyncActionData action) {
        PacketDistributor.sendToServer(new CPacketCoverActionToServer(holder.getPos(), holder.getSide(),
                holder.getCoverDefinitionId(), action));
    }
}

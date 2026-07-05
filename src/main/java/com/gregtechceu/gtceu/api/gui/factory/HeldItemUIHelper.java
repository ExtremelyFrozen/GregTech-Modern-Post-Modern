package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.common.network.packets.CPacketItemActionToServer;

import com.lowdragmc.lowdraglib2.gui.factory.HeldItemUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

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
        if (player.getItemInHand(hand).getItem() instanceof LDLib2HeldItemUIProvider) {
            return HeldItemUIMenuType.openUI(player, hand);
        }
        return GTHeldItemUIFactory.INSTANCE.openUI(player, hand);
    }

    /**
     * Creates the parallel LDLib2 UI when the held item exposes the LDLib2 provider contract.
     *
     * @return the LDLib2 UI, or {@code null} when the held item has not migrated to the parallel provider contract.
     */
    @Nullable
    public static ModularUI createLDLib2UI(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof LDLib2HeldItemUIProvider uiProvider) {
            HeldItemUIHolderContext holder = new HeldItemUIHolderContext(player, hand);
            return Objects.requireNonNull(uiProvider.createLDLib2UI(player, holder),
                    "LDLib2 held item UI provider returned null");
        }
        return null;
    }

    /**
     * Sends a held item UI sync action to the server.
     */
    public static void sendAction(HeldItemUIHolder holder, SyncActionData action) {
        PacketDistributor.sendToServer(new CPacketItemActionToServer(holder.getHand(), holder.getOpenedStack(), action));
    }
}

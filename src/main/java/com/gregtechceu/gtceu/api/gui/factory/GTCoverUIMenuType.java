package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.common.data.GTMenuTypes;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;

import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;

/**
 * LDLib2 menu type adapter for GTM cover UIs.
 */
public final class GTCoverUIMenuType {

    private GTCoverUIMenuType() {}

    public static boolean openUI(CoverBehavior cover, ServerPlayer player) {
        if (cover instanceof LDLib2CoverUIProvider uiProvider) {
            LDLib2CoverUIHolderContext holder = new LDLib2CoverUIHolderContext(player, cover);
            if (uiProvider.canCreateLDLib2UI(player, holder)) {
                return player.openMenu(holder).isPresent();
            }
        }
        return false;
    }

    public static ModularUIContainerMenu create(int windowId, Inventory inventory, RegistryFriendlyByteBuf data) {
        LDLib2CoverUIHolderContext holder = new LDLib2CoverUIHolderContext(inventory.player,
                data.readBlockPos(), data.readEnum(Direction.class), data.readResourceLocation(), data.readUUID());
        return new GTCoverUIContainerMenu(GTMenuTypes.COVER_UI.get(), windowId, inventory, holder);
    }
}

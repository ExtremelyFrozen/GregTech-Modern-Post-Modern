package com.gregtechceu.gtceu.api.gui.factory;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

/**
 * LDLib2 container menu that owns the server-side close lifecycle for a cover UI.
 */
public final class GTCoverUIContainerMenu extends ModularUIContainerMenu {

    private final LDLib2CoverUIHolderContext holder;

    public GTCoverUIContainerMenu(MenuType<ModularUIContainerMenu> menuType, int containerId,
                                  Inventory inventory, LDLib2CoverUIHolderContext holder) {
        super(menuType, containerId, inventory, holder);
        this.holder = holder;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        holder.close(player);
    }
}

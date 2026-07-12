package com.gregtechceu.gtceu.api.gui.factory;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;

import org.jetbrains.annotations.ApiStatus;

import java.util.UUID;

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

    /**
     * Validates that a client action belongs to this player's active cover menu and its exact opened cover.
     */
    @ApiStatus.Internal
    public boolean matchesActionSession(ServerPlayer player, BlockPos pos, Direction side,
                                        ResourceLocation coverDefinitionId, UUID actionSessionId) {
        return player.containerMenu == this &&
                holder.matchesActionSession(player, pos, side, coverDefinitionId, actionSessionId) &&
                player.containerMenu == this;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        holder.close(player);
    }
}

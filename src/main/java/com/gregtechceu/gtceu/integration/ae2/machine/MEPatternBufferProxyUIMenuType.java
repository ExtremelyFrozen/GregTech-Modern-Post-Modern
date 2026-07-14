package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.common.data.GTMenuTypes;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

import java.util.UUID;
import java.util.function.Supplier;

/** Owns the explicit opening-data protocol for ME Pattern Buffer Proxy menus. */
public final class MEPatternBufferProxyUIMenuType {

    public static final Supplier<MenuType<ModularUIContainerMenu>> MENU_TYPE = GTMenuTypes.MENUS.register(
            "me_pattern_buffer_proxy_ui",
            () -> IMenuTypeExtension.create(MEPatternBufferProxyUIMenuType::create));

    private MEPatternBufferProxyUIMenuType() {}

    /** Loads this AE2-only menu registration inside the optional integration boundary. */
    public static void initialize() {}

    /** Opens a server-captured Proxy menu instead of rebuilding its link from asynchronous client block data. */
    public static boolean openUI(MEPatternBufferProxyPartMachine proxy, ServerPlayer player) {
        MEPatternBufferProxyUIHolder holder = proxy.createLDLib2UIHolder(player);
        if (!holder.isOpeningValid(player)) {
            return false;
        }
        try {
            if (player.openMenu(holder).isEmpty()) {
                holder.abortOpening(player);
                return false;
            }
            holder.sendOpeningSnapshot(player);
            return true;
        } catch (RuntimeException exception) {
            try {
                holder.abortOpening(player);
            } catch (RuntimeException closeException) {
                exception.addSuppressed(closeException);
            }
            GTCEu.LOGGER.error("Failed to open Pattern Buffer Proxy menu at {} for {}",
                    proxy.getBlockPos(), player.getGameProfile().getName(), exception);
            return false;
        }
    }

    /** Reconstructs the client holder from the exact data written by the server opening. */
    public static ModularUIContainerMenu create(int windowId, Inventory inventory, RegistryFriendlyByteBuf data) {
        BlockPos proxyPos = data.readBlockPos();
        ResourceLocation proxyDefinitionId = data.readResourceLocation();
        MEPatternBufferProxyOpeningIdentity opening = MEPatternBufferProxyOpeningIdentity.STREAM_CODEC.decode(data);
        UUID menuSessionId = data.readUUID();
        MEPatternBufferProxyViewSnapshot snapshot = MEPatternBufferProxyViewSnapshot.STREAM_CODEC.decode(data);
        MEPatternBufferProxyUIHolder holder = MEPatternBufferProxyUIHolder.client(
                inventory.player, proxyPos, proxyDefinitionId, opening, menuSessionId, snapshot);
        try {
            return new MEPatternBufferProxyUIContainerMenu(MENU_TYPE.get(), windowId, inventory, holder);
        } catch (RuntimeException | Error exception) {
            try {
                holder.close(inventory.player);
            } catch (RuntimeException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }
}

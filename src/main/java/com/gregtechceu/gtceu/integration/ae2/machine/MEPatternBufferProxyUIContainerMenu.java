package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.common.network.packets.SPacketMEPatternBufferProxyViewToClient;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/**
 * LDLib2 container that binds linked-buffer field updates to one active Proxy menu session.
 */
public final class MEPatternBufferProxyUIContainerMenu extends ModularUIContainerMenu {

    private final MEPatternBufferProxyUIHolder holder;
    private BlockState lastSentBlockState;

    public MEPatternBufferProxyUIContainerMenu(MenuType<ModularUIContainerMenu> menuType, int containerId,
                                               Inventory inventory, MEPatternBufferProxyUIHolder holder) {
        super(menuType, containerId, inventory, holder);
        this.holder = holder;
        lastSentBlockState = holder.getOpeningSnapshot().blockState();
    }

    /** Forwards the exact GT client-sync delta collected by the linked server Buffer. */
    void sendBufferChanges(MEPatternBufferPartMachine buffer, DataComponentMap changes) {
        if (!(inventory.player instanceof ServerPlayer player) || player.containerMenu != this ||
                buffer != holder.getPatternBufferView() || !holder.isOpeningValid(player)) {
            buffer.removeProxyUIMenu(this);
            return;
        }
        sendUpdate(player, MEPatternBufferProxyViewSnapshot.update(buffer, changes));
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(inventory.player instanceof ServerPlayer player) || player.containerMenu != this ||
                !holder.isOpeningValid(player)) {
            return;
        }
        BlockState currentState = holder.getPatternBufferView().getBlockState();
        if (!currentState.equals(lastSentBlockState)) {
            sendUpdate(player, MEPatternBufferProxyViewSnapshot.update(
                    holder.getPatternBufferView(), DataComponentMap.EMPTY));
        }
    }

    /** Applies a packet only when its container, session, and immutable opening all match this client menu. */
    public boolean applyClientUpdate(Player player, int containerId, UUID menuSessionId,
                                     MEPatternBufferProxyOpeningIdentity opening,
                                     MEPatternBufferProxyViewSnapshot update) {
        if (!player.level().isClientSide || player.containerMenu != this || this.containerId != containerId ||
                !holder.getMenuSessionId().equals(menuSessionId) ||
                !holder.getOpeningIdentity().equals(opening) || !holder.isOpeningValid(player)) {
            return false;
        }
        holder.applyClientUpdate(update);
        lastSentBlockState = update.blockState();
        return true;
    }

    /** Delivers the full view after the vanilla opening packet has installed the matching client menu. */
    void sendOpeningSnapshot(ServerPlayer player) {
        if (inventory.player != player || player.containerMenu != this || !holder.isOpeningValid(player)) {
            throw new IllegalStateException("Pattern Buffer Proxy opening snapshot lost its active menu.");
        }
        sendUpdate(player, holder.getOpeningSnapshot());
    }

    /** Closes a server menu whose detached client projection can no longer be kept coherent. */
    void closeAfterSyncFailure() {
        if (inventory.player instanceof ServerPlayer player && player.containerMenu == this) {
            player.closeContainer();
        } else {
            holder.close(inventory.player);
        }
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        holder.close(player);
    }

    private void sendUpdate(ServerPlayer player, MEPatternBufferProxyViewSnapshot update) {
        PacketDistributor.sendToPlayer(player, new SPacketMEPatternBufferProxyViewToClient(
                containerId, holder.getMenuSessionId(), holder.getOpeningIdentity(), update));
        lastSentBlockState = update.blockState();
    }
}

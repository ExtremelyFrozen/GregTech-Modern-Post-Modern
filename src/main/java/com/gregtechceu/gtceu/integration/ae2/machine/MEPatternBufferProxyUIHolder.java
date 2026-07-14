package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;

import com.lowdragmc.lowdraglib2.gui.factory.IContainerUIHolder;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * GTM-owned menu holder that transports one authoritative Pattern Buffer Proxy opening to the client.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class MEPatternBufferProxyUIHolder
                                                implements MEPatternBufferProxyUIContext, MenuProvider,
                                                IContainerUIHolder {

    private final Player player;
    @Getter
    private final BlockPos pos;
    @Getter
    private final ResourceLocation machineDefinitionId;
    @Getter
    private final MEPatternBufferProxyOpeningIdentity openingIdentity;
    @Getter
    private final UUID menuSessionId;
    @Getter
    private final MEPatternBufferPartMachine patternBufferView;
    @Getter
    private final MEPatternBufferProxyViewSnapshot openingSnapshot;
    @Nullable
    private final MEPatternBufferProxyPartMachine openedProxy;
    @Nullable
    private MEPatternBufferProxyUIContainerMenu serverMenu;
    private boolean closed;

    MEPatternBufferProxyUIHolder(ServerPlayer player, MEPatternBufferProxyPartMachine proxy,
                                 MEPatternBufferPartMachine buffer,
                                 MEPatternBufferProxyOpeningIdentity openingIdentity,
                                 MEPatternBufferProxyViewSnapshot openingSnapshot) {
        this(player, proxy.getBlockPos(), proxy.getDefinition().getId(), openingIdentity, UUID.randomUUID(),
                buffer, openingSnapshot, proxy);
    }

    private MEPatternBufferProxyUIHolder(Player player, BlockPos pos, ResourceLocation machineDefinitionId,
                                         MEPatternBufferProxyOpeningIdentity openingIdentity, UUID menuSessionId,
                                         MEPatternBufferPartMachine patternBufferView,
                                         MEPatternBufferProxyViewSnapshot openingSnapshot,
                                         @Nullable MEPatternBufferProxyPartMachine openedProxy) {
        if (!machineDefinitionId.equals(GTAEMachines.ME_PATTERN_BUFFER_PROXY.getId())) {
            throw new IllegalArgumentException("Pattern Buffer Proxy menu holder received the wrong definition.");
        }
        if (!patternBufferView.getBlockPos().equals(openingIdentity.bufferPos())) {
            throw new IllegalArgumentException(
                    "Pattern Buffer Proxy view position does not match its opening identity.");
        }
        this.player = player;
        this.pos = pos;
        this.machineDefinitionId = machineDefinitionId;
        this.openingIdentity = openingIdentity;
        this.menuSessionId = menuSessionId;
        this.patternBufferView = patternBufferView;
        this.openingSnapshot = openingSnapshot;
        this.openedProxy = openedProxy;
    }

    /** Reconstructs a client holder exclusively from server-owned opening data. */
    static MEPatternBufferProxyUIHolder client(Player player, BlockPos pos, ResourceLocation machineDefinitionId,
                                               MEPatternBufferProxyOpeningIdentity openingIdentity,
                                               UUID menuSessionId,
                                               MEPatternBufferProxyViewSnapshot openingSnapshot) {
        MEPatternBufferPartMachine projection = openingSnapshot.createDetachedView(
                player.level(), openingIdentity.bufferPos());
        try {
            return new MEPatternBufferProxyUIHolder(player, pos, machineDefinitionId, openingIdentity, menuSessionId,
                    projection, openingSnapshot, null);
        } catch (RuntimeException | Error exception) {
            try {
                MEPatternBufferProxyViewSnapshot.discardDetachedView(projection);
            } catch (RuntimeException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }

    @Nullable
    @Override
    public MetaMachine getMachine() {
        Level level = player.level();
        if (!level.isLoaded(pos)) {
            return null;
        }
        MetaMachine machine = MetaMachine.getMachine(level, pos);
        if (machine == null || !machine.getDefinition().getId().equals(machineDefinitionId)) {
            return null;
        }
        return machine;
    }

    @Override
    public boolean isOpeningValid(Player player) {
        if (closed || this.player != player || !(getMachine() instanceof MEPatternBufferProxyPartMachine proxy) ||
                proxy.getDefinition() != GTAEMachines.ME_PATTERN_BUFFER_PROXY) {
            return false;
        }
        if (player.level().isClientSide) {
            return openedProxy == null;
        }
        return openedProxy == proxy && player instanceof ServerPlayer serverPlayer &&
                proxy.canExecuteMEPatternBufferProxyAction(serverPlayer, openingIdentity) &&
                proxy.getBuffer() == patternBufferView;
    }

    @Override
    public boolean isStillValid(Player player) {
        return isOpeningValid(player);
    }

    @Override
    public Component getDisplayName() {
        return GTAEMachines.ME_PATTERN_BUFFER_PROXY.getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        if (!(player instanceof ServerPlayer) || this.player != player || serverMenu != null) {
            throw new IllegalStateException("Pattern Buffer Proxy holder can create exactly one server menu.");
        }
        requireOpeningValid("before menu creation");
        MEPatternBufferProxyUIContainerMenu menu = new MEPatternBufferProxyUIContainerMenu(
                MEPatternBufferProxyUIMenuType.MENU_TYPE.get(), containerId, playerInventory, this);
        requireOpeningValid("after menu creation");
        patternBufferView.addProxyUIMenu(menu);
        serverMenu = menu;
        return menu;
    }

    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        if (menu != serverMenu) {
            throw new IllegalStateException("Pattern Buffer Proxy opening data does not belong to its server menu.");
        }
        buffer.writeBlockPos(pos);
        buffer.writeResourceLocation(machineDefinitionId);
        MEPatternBufferProxyOpeningIdentity.STREAM_CODEC.encode(buffer, openingIdentity);
        buffer.writeUUID(menuSessionId);
        MEPatternBufferProxyViewSnapshot.STREAM_CODEC.encode(buffer, openingSnapshot.withoutFieldData());
    }

    @Override
    public ModularUI createUI(Player player) {
        if (this.player != player || !(getMachine() instanceof MEPatternBufferProxyPartMachine proxy) ||
                !proxy.canCreateLDLib2UI(player, this)) {
            throw new IllegalStateException("Pattern Buffer Proxy rejected its opened menu holder.");
        }
        UI ui = proxy.createLDLib2UI(player, this);
        return ModularUI.of(ui, player);
    }

    /** Applies one session-validated server update to the client projection. */
    void applyClientUpdate(MEPatternBufferProxyViewSnapshot update) {
        if (!player.level().isClientSide) {
            throw new IllegalStateException("Pattern Buffer Proxy view updates are client-only.");
        }
        update.applyTo(patternBufferView, player.level().registryAccess());
    }

    /** Sends the full initial view only after the vanilla opening packet has installed this menu on the client. */
    void sendOpeningSnapshot(ServerPlayer player) {
        MEPatternBufferProxyUIContainerMenu menu = serverMenu;
        if (this.player != player || menu == null || player.containerMenu != menu) {
            throw new IllegalStateException(
                    "Pattern Buffer Proxy opening snapshot requires its installed server menu.");
        }
        menu.sendOpeningSnapshot(player);
    }

    /** Closes an installed menu or unregisters a partially created server opening. */
    void abortOpening(ServerPlayer player) {
        if (this.player != player) {
            throw new IllegalArgumentException("Pattern Buffer Proxy opening belongs to another player.");
        }
        MEPatternBufferProxyUIContainerMenu menu = serverMenu;
        if (menu != null && player.containerMenu == menu) {
            player.closeContainer();
        } else {
            close(player);
        }
    }

    /** Unregisters the server observer or releases the client projection exactly once. */
    void close(Player player) {
        if (closed) {
            return;
        }
        closed = true;
        if (openedProxy != null) {
            MEPatternBufferProxyUIContainerMenu menu = serverMenu;
            if (menu != null) {
                patternBufferView.removeProxyUIMenu(menu);
            }
        } else {
            MEPatternBufferProxyViewSnapshot.discardDetachedView(patternBufferView);
        }
    }

    private void requireOpeningValid(String phase) {
        if (!isOpeningValid(player)) {
            throw new IllegalStateException("Pattern Buffer Proxy opening became invalid " + phase + '.');
        }
    }
}

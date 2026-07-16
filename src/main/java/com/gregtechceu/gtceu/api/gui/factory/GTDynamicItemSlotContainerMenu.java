package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.element.GTDynamicItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTDynamicItemSlotSessionElement;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotClientOpening;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotManifest;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotManifestSequence;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotOpeningToken;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotSelection;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotServerOpening;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotTransition;
import com.gregtechceu.gtceu.common.network.packets.CPacketDynamicItemSlotActivatedToServer;
import com.gregtechceu.gtceu.common.network.packets.CPacketDynamicItemSlotPreparedToServer;
import com.gregtechceu.gtceu.common.network.packets.CPacketDynamicItemSlotSelectionToServer;
import com.gregtechceu.gtceu.common.network.packets.SPacketDynamicItemSlotActivationToClient;
import com.gregtechceu.gtceu.common.network.packets.SPacketDynamicItemSlotManifestToClient;
import com.gregtechceu.gtceu.common.network.packets.SPacketDynamicItemSlotSelectionToClient;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * LDLib2 container that owns the ordered dynamic-slot handshake and rejects unacknowledged Vanilla interactions.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class GTDynamicItemSlotContainerMenu extends ModularUIContainerMenu {

    private final DynamicItemSlotMachineUIHolder holder;
    private final GTDynamicItemSlotSessionElement sessionElement;
    @Getter
    private final UUID menuSessionId;
    @Nullable
    private final DynamicItemSlotClientOpening clientOpening;
    @Nullable
    private final DynamicItemSlotServerOpening serverOpening;

    /**
     * Installs the fixed UI first, captures its slot prefix, and creates exactly one side-specific opening state.
     */
    public GTDynamicItemSlotContainerMenu(MenuType<ModularUIContainerMenu> menuType, int containerId,
                                          Inventory inventory, DynamicItemSlotMachineUIHolder holder) {
        super(menuType, containerId, inventory, holder);
        this.holder = holder;
        this.menuSessionId = holder.getMenuSessionId();
        this.sessionElement = findSessionElement();
        int baseSlotCount = slots.size();
        if (inventory.player.level().isClientSide) {
            clientOpening = new DynamicItemSlotClientOpening(containerId, menuSessionId, baseSlotCount);
            serverOpening = null;
        } else {
            clientOpening = null;
            serverOpening = new DynamicItemSlotServerOpening(containerId, menuSessionId, baseSlotCount);
        }
    }

    /**
     * Publishes the first or next authoritative source snapshot after this server menu is installed.
     */
    public boolean publishManifest(ServerPlayer player) {
        DynamicItemSlotServerOpening opening = requireServerOpening(player);
        try {
            DynamicItemSlotManifest manifest = DynamicItemSlotManifestSequence.advance(
                    opening.activeManifest(), sessionElement.sourceRevision(), slotsBeforeDynamicBindings(),
                    sessionElement.definitions());
            DynamicItemSlotTransition transition = opening.beginManifest(manifest);
            if (transition != DynamicItemSlotTransition.ACCEPTED) {
                rejectServerTransition(player, "publish MANIFEST", transition);
                return false;
            }
            applyInteractionState(opening::isBindingInteractive);
            PacketDistributor.sendToPlayer(player, new SPacketDynamicItemSlotManifestToClient(
                    DynamicItemSlotOpeningToken.of(containerId, menuSessionId, manifest), manifest));
            return true;
        } catch (RuntimeException exception) {
            closeAfterFailure(player, "publishing dynamic item slot manifest", exception);
            return false;
        }
    }

    /**
     * Applies MANIFEST on the client, appends disabled ranges, and sends PREPARED only after every target resolves.
     */
    public void receiveManifest(Player player, DynamicItemSlotOpeningToken token,
                                DynamicItemSlotManifest manifest) {
        DynamicItemSlotClientOpening opening = requireClientOpening(player);
        try {
            DynamicItemSlotTransition transition = opening.receiveManifest(token, manifest);
            if (!continueClientTransition(player, "MANIFEST", transition)) {
                return;
            }
            if (transition == DynamicItemSlotTransition.DUPLICATE && opening.pendingManifest().isEmpty()) {
                return;
            }
            prepareClientManifest(player, opening);
        } catch (RuntimeException exception) {
            closeAfterFailure(player, "applying dynamic item slot manifest", exception);
        }
    }

    /**
     * Retries a pending client PREPARED step after an asynchronously restored UUID target becomes available.
     */
    public void retryClientPreparation(Player player) {
        DynamicItemSlotClientOpening opening = requireClientOpening(player);
        if (!opening.requiresPreparation()) {
            return;
        }
        try {
            prepareClientManifest(player, opening);
        } catch (RuntimeException exception) {
            closeAfterFailure(player, "retrying dynamic item slot preparation", exception);
        }
    }

    /**
     * Publishes a changed source revision after the previous manifest is fully active.
     */
    public void refreshManifest(ServerPlayer player) {
        DynamicItemSlotServerOpening opening = requireServerOpening(player);
        if (opening.pendingManifest().isPresent()) {
            return;
        }
        Optional<DynamicItemSlotManifest> activeManifest = opening.activeManifest();
        if (activeManifest.isEmpty()) {
            return;
        }
        try {
            if (sessionElement.sourceRevision() != activeManifest.orElseThrow().sourceRevision()) {
                publishManifest(player);
            }
        } catch (RuntimeException exception) {
            closeAfterFailure(player, "refreshing dynamic item slot manifest", exception);
        }
    }

    /**
     * Handles PREPARED by appending the same disabled ranges, sending a full snapshot, then ACTIVATE.
     */
    public void receivePrepared(ServerPlayer player, DynamicItemSlotOpeningToken token) {
        DynamicItemSlotServerOpening opening = requireServerOpening(player);
        try {
            DynamicItemSlotTransition transition = opening.receivePreparedAcknowledgement(token);
            if (!continueServerTransition(player, "PREPARED", transition)) {
                return;
            }
            Optional<DynamicItemSlotManifest> pendingManifest = opening.pendingManifest();
            if (pendingManifest.isPresent()) {
                opening.bindingsToAppend().forEach(sessionElement::appendBinding);
                DynamicItemSlotTransition appendTransition = opening.markDisabledSlotsAppended(
                        token, sessionElement.appendedBindingIds());
                if (!continueServerTransition(player, "disabled slot append", appendTransition)) {
                    return;
                }
                applyInteractionState(opening::isBindingInteractive);
                sendAllDataToRemote();
                DynamicItemSlotTransition snapshotTransition = opening.markFullSnapshotSent(token);
                if (!continueServerTransition(player, "full container snapshot", snapshotTransition)) {
                    return;
                }
            } else if (opening.activeToken().filter(token::equals).isEmpty()) {
                rejectServerTransition(player, "delayed PREPARED", DynamicItemSlotTransition.REJECTED);
                return;
            } else {
                sendAllDataToRemote();
            }
            PacketDistributor.sendToPlayer(player,
                    new SPacketDynamicItemSlotActivationToClient(token, opening.selectedBindingId()));
        } catch (RuntimeException exception) {
            closeAfterFailure(player, "handling dynamic item slot PREPARED", exception);
        }
    }

    /**
     * Applies ACTIVATE on the client and returns ACTIVATED after interaction and page state are installed.
     */
    public void receiveActivation(Player player, DynamicItemSlotOpeningToken token,
                                  Optional<UUID> selectedBindingId) {
        DynamicItemSlotClientOpening opening = requireClientOpening(player);
        try {
            DynamicItemSlotTransition transition = opening.receiveActivation(token, selectedBindingId);
            if (!continueClientTransition(player, "ACTIVATE", transition)) {
                return;
            }
            DynamicItemSlotManifest activeManifest = opening.activeManifest().orElseThrow();
            applyInteractionState(opening::isBindingInteractive);
            sessionElement.applySelection(opening.selectedBindingId(), activeManifest);
            PacketDistributor.sendToServer(new CPacketDynamicItemSlotActivatedToServer(token));
        } catch (RuntimeException exception) {
            closeAfterFailure(player, "applying dynamic item slot ACTIVATE", exception);
        }
    }

    /**
     * Commits ACTIVATED on the server; no dynamic slot is server-interactive before this method succeeds.
     */
    public void receiveActivated(ServerPlayer player, DynamicItemSlotOpeningToken token) {
        DynamicItemSlotServerOpening opening = requireServerOpening(player);
        try {
            DynamicItemSlotTransition transition = opening.receiveActivatedAcknowledgement(token);
            if (!continueServerTransition(player, "ACTIVATED", transition)) {
                return;
            }
            DynamicItemSlotManifest activeManifest = opening.activeManifest().orElseThrow();
            applyInteractionState(opening::isBindingInteractive);
            sessionElement.applySelection(opening.selectedBindingId(), activeManifest);
        } catch (RuntimeException exception) {
            closeAfterFailure(player, "handling dynamic item slot ACTIVATED", exception);
        }
    }

    /**
     * Starts a client page request and immediately disables interaction until the matching server ACK arrives.
     */
    public boolean requestSelection(UUID bindingId) {
        Player player = inventory.player;
        DynamicItemSlotClientOpening opening = requireClientOpening(player);
        Optional<DynamicItemSlotOpeningToken> token = opening.activeToken();
        Optional<DynamicItemSlotSelection> selection = opening.requestSelection(bindingId);
        if (token.isEmpty() || selection.isEmpty()) {
            GTCEu.LOGGER.warn("Dynamic item-slot menu {} rejected local selection request for binding {}",
                    containerId, bindingId);
            return false;
        }
        applyInteractionState(opening::isBindingInteractive);
        PacketDistributor.sendToServer(new CPacketDynamicItemSlotSelectionToServer(
                token.orElseThrow(), selection.orElseThrow()));
        return true;
    }

    /**
     * Validates a page request against the current UUID target and sends the authoritative selection ACK.
     */
    public void receiveSelectionRequest(ServerPlayer player, DynamicItemSlotOpeningToken token,
                                        DynamicItemSlotSelection selection) {
        DynamicItemSlotServerOpening opening = requireServerOpening(player);
        try {
            DynamicItemSlotTransition transition = opening.receiveSelectionRequest(
                    token, selection, sessionElement::isBindingSelectable);
            if (!continueServerTransition(player, "selection request", transition)) {
                return;
            }
            DynamicItemSlotManifest activeManifest = opening.activeManifest().orElseThrow();
            applyInteractionState(opening::isBindingInteractive);
            sessionElement.applySelection(opening.selectedBindingId(), activeManifest);
            PacketDistributor.sendToPlayer(player, new SPacketDynamicItemSlotSelectionToClient(token, selection));
        } catch (RuntimeException exception) {
            closeAfterFailure(player, "handling dynamic item slot selection", exception);
        }
    }

    /**
     * Applies only the exact pending page ACK and then re-enables the confirmed binding on the client.
     */
    public void receiveSelectionAcknowledgement(Player player, DynamicItemSlotOpeningToken token,
                                                DynamicItemSlotSelection selection) {
        DynamicItemSlotClientOpening opening = requireClientOpening(player);
        try {
            DynamicItemSlotTransition transition = opening.receiveSelectionAcknowledgement(token, selection);
            if (!continueClientTransition(player, "selection acknowledgement", transition)) {
                return;
            }
            DynamicItemSlotManifest activeManifest = opening.activeManifest().orElseThrow();
            applyInteractionState(opening::isBindingInteractive);
            sessionElement.applySelection(opening.selectedBindingId(), activeManifest);
        } catch (RuntimeException exception) {
            closeAfterFailure(player, "applying dynamic item slot selection acknowledgement", exception);
        }
    }

    /**
     * Rejects invalid future slot ids and every click type targeting a disabled dynamic slot.
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (!isValidClickSlotId(slotId, button, clickType)) {
            GTCEu.LOGGER.warn("Dynamic item-slot menu {} rejected slot id {} for {} from {}",
                    containerId, slotId, clickType, player.getGameProfile().getName());
            resetQuickCraft();
            return;
        }
        if (slotId >= 0 && isDisabledDynamicSlot(slots.get(slotId))) {
            resetQuickCraft();
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    /**
     * Rejects direct or Vanilla quick-move calls whose source slot is invalid or disabled.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int slotId) {
        if (slotId < 0 || slotId >= slots.size()) {
            GTCEu.LOGGER.warn("Dynamic item-slot menu {} rejected quick-move source slot {} from {}",
                    containerId, slotId, player.getGameProfile().getName());
            return ItemStack.EMPTY;
        }
        return isDisabledDynamicSlot(slots.get(slotId)) ? ItemStack.EMPTY : super.quickMoveStack(player, slotId);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !isDisabledDynamicSlot(slot) && super.canDragTo(slot);
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !isDisabledDynamicSlot(slot) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    protected boolean isValidQuickMoveDestination(Slot candidateSlot, ItemStack stackToMove,
                                                  boolean fromPlayerSide) {
        return !isDisabledDynamicSlot(candidateSlot) &&
                super.isValidQuickMoveDestination(candidateSlot, stackToMove, fromPlayerSide);
    }

    @Override
    public boolean stillValid(Player player) {
        return holder.isStillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        abortOpening(player);
        holder.close(player);
    }

    /**
     * Permanently disables this protocol state without invoking menu removal callbacks.
     */
    void abortOpening(Player player) {
        if (inventory.player != player) {
            throw new IllegalArgumentException("Dynamic item-slot menu belongs to another player.");
        }
        terminateOpenings();
        applyInteractionState(bindingId -> false);
    }

    private void prepareClientManifest(Player player, DynamicItemSlotClientOpening opening) {
        DynamicItemSlotManifest pendingManifest = opening.pendingManifest().orElseThrow();
        opening.bindingsToAppend().forEach(sessionElement::appendBinding);
        applyInteractionState(opening::isBindingInteractive);
        DynamicItemSlotOpeningToken token = DynamicItemSlotOpeningToken.of(containerId, menuSessionId, pendingManifest);
        DynamicItemSlotTransition preparation = opening.completePreparation(
                token,
                sessionElement.appendedBindingIds(),
                sessionElement.resolvedPresentBindingIds(pendingManifest));
        if (preparation == DynamicItemSlotTransition.REJECTED) {
            GTCEu.LOGGER.debug("Dynamic item-slot menu {} is waiting for all targets before PREPARED epoch {}",
                    containerId, pendingManifest.epoch());
            return;
        }
        if (!continueClientTransition(player, "PREPARED", preparation)) {
            return;
        }
        PacketDistributor.sendToServer(new CPacketDynamicItemSlotPreparedToServer(token));
    }

    private GTDynamicItemSlotSessionElement findSessionElement() {
        List<GTDynamicItemSlotSessionElement> sessions = getModularUI().getAllElements().stream()
                .filter(GTDynamicItemSlotSessionElement.class::isInstance)
                .map(GTDynamicItemSlotSessionElement.class::cast)
                .toList();
        if (sessions.size() != 1) {
            throw new IllegalStateException(
                    "dynamic item-slot UI must contain exactly one session element, found " + sessions.size());
        }
        return sessions.getFirst();
    }

    private int slotsBeforeDynamicBindings() {
        return clientOpening != null ? clientOpening.pendingManifest()
                .map(DynamicItemSlotManifest::baseSlotCount)
                .or(() -> clientOpening.activeManifest().map(DynamicItemSlotManifest::baseSlotCount))
                .orElse(slots.size()) :
                serverOpening != null ? serverOpening.pendingManifest()
                        .map(DynamicItemSlotManifest::baseSlotCount)
                        .or(() -> serverOpening.activeManifest().map(DynamicItemSlotManifest::baseSlotCount))
                        .orElse(slots.size()) : slots.size();
    }

    private DynamicItemSlotClientOpening requireClientOpening(Player player) {
        if (!player.level().isClientSide || inventory.player != player || player.containerMenu != this ||
                clientOpening == null) {
            throw new IllegalStateException("dynamic item slot client message does not belong to this menu");
        }
        return clientOpening;
    }

    private DynamicItemSlotServerOpening requireServerOpening(ServerPlayer player) {
        if (inventory.player != player || player.containerMenu != this || serverOpening == null) {
            throw new IllegalStateException("dynamic item slot server message does not belong to this menu");
        }
        return serverOpening;
    }

    private boolean continueClientTransition(Player player, String message,
                                             DynamicItemSlotTransition transition) {
        if (transition == DynamicItemSlotTransition.ACCEPTED ||
                transition == DynamicItemSlotTransition.DUPLICATE) {
            return true;
        }
        if (transition == DynamicItemSlotTransition.REJECTED ||
                transition == DynamicItemSlotTransition.CLOSE_OPENING) {
            closeAfterProtocolConflict(player, message, transition);
        } else {
            GTCEu.LOGGER.warn("Dynamic item-slot menu {} ignored stale client {}", containerId, message);
        }
        return false;
    }

    private boolean continueServerTransition(ServerPlayer player, String message,
                                             DynamicItemSlotTransition transition) {
        if (transition == DynamicItemSlotTransition.ACCEPTED ||
                transition == DynamicItemSlotTransition.DUPLICATE) {
            return true;
        }
        if (transition == DynamicItemSlotTransition.REJECTED ||
                transition == DynamicItemSlotTransition.CLOSE_OPENING) {
            closeAfterProtocolConflict(player, message, transition);
        } else {
            GTCEu.LOGGER.warn("Dynamic item-slot menu {} ignored stale server {}", containerId, message);
        }
        return false;
    }

    private void rejectServerTransition(ServerPlayer player, String message,
                                        DynamicItemSlotTransition transition) {
        closeAfterProtocolConflict(player, message, transition);
    }

    private void closeAfterProtocolConflict(Player player, String message,
                                            DynamicItemSlotTransition transition) {
        GTCEu.LOGGER.warn("Dynamic item-slot menu {} closed after {} returned {} for {}",
                containerId, message, transition, player.getGameProfile().getName());
        terminateOpenings();
        applyInteractionState(bindingId -> false);
        if (player.containerMenu == this) {
            player.closeContainer();
        }
    }

    private void closeAfterFailure(Player player, String operation, RuntimeException exception) {
        GTCEu.LOGGER.error("Dynamic item-slot menu {} failed while {} for {}",
                containerId, operation, player.getGameProfile().getName(), exception);
        terminateOpenings();
        applyInteractionState(bindingId -> false);
        if (player.containerMenu == this) {
            player.closeContainer();
        }
    }

    private void terminateOpenings() {
        if (clientOpening != null) {
            clientOpening.terminate();
        }
        if (serverOpening != null) {
            serverOpening.terminate();
        }
    }

    private void applyInteractionState(Predicate<UUID> interactiveBinding) {
        resetQuickCraft();
        sessionElement.applyInteractionState(interactiveBinding);
    }

    private boolean isDisabledDynamicSlot(Slot slot) {
        return asModularUIHolderMenu().getItemSlot(slot) instanceof GTDynamicItemSlotElement dynamicSlot &&
                !dynamicSlot.isInteractionEnabled();
    }

    private boolean isValidClickSlotId(int slotId, int button, ClickType clickType) {
        if (clickType == ClickType.QUICK_CRAFT) {
            int header = getQuickcraftHeader(button);
            return header == 1 ? slotId >= 0 && slotId < slots.size() :
                    (header == 0 || header == 2) && slotId == -999;
        }
        return slotId >= 0 && slotId < slots.size() ||
                slotId == -999 && (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE);
    }
}

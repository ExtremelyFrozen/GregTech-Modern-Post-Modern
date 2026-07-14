package com.gregtechceu.gtceu.integration.ae2.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner;
import com.gregtechceu.gtceu.common.network.packets.SPacketMEOutputWaitingListToClient;
import com.gregtechceu.gtceu.integration.ae2.utils.KeyStorage;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Publishes one ME output storage as opening-scoped full and same-tick delta chunks.
 *
 * <p>
 * Subscriptions are keyed by player, exact active menu, and client opening UUID. Every send revalidates that the
 * player's current LDLib2 menu still contains the exact part receiver owned by this publisher.
 * </p>
 */
public final class MEOutputWaitingListPublisher {

    private static final int FULL_REQUEST_COOLDOWN_TICKS = 20;
    private static final int DEFAULT_MAX_DELTA_ENTRIES_PER_TICK = MEOutputWaitingListUpdate.MAX_CHUNK_ENTRIES *
            MEOutputWaitingListUpdate.MAX_CHUNK_COUNT;

    private final MetaMachine owner;
    private final MEOutputWaitingListActionTarget targetOwner;
    private final KeyStorage storage;
    private final BiConsumer<ServerPlayer, SPacketMEOutputWaitingListToClient> packetSender;
    private final int maxDeltaEntriesPerTick;
    private final Map<UUID, Subscription> subscriptions = new LinkedHashMap<>();
    private final Map<UUID, FullRequestCooldown> fullRequestCooldowns = new LinkedHashMap<>();
    private final List<KeyStorage.Change> pendingChanges = new ArrayList<>();
    private long revision;
    private @Nullable TickableSubscription tickSubscription;

    /**
     * Binds one publisher to its exact machine and ordered key storage.
     */
    public MEOutputWaitingListPublisher(MetaMachine owner, KeyStorage storage) {
        this(owner, storage, PacketDistributor::sendToPlayer, DEFAULT_MAX_DELTA_ENTRIES_PER_TICK);
    }

    MEOutputWaitingListPublisher(MetaMachine owner, KeyStorage storage,
                                 BiConsumer<ServerPlayer, SPacketMEOutputWaitingListToClient> packetSender) {
        this(owner, storage, packetSender, DEFAULT_MAX_DELTA_ENTRIES_PER_TICK);
    }

    MEOutputWaitingListPublisher(MetaMachine owner, KeyStorage storage,
                                 BiConsumer<ServerPlayer, SPacketMEOutputWaitingListToClient> packetSender,
                                 int maxDeltaEntriesPerTick) {
        if (owner == null) {
            throw new IllegalArgumentException("ME output waiting-list owner must be present.");
        }
        if (storage == null) {
            throw new IllegalArgumentException("ME output waiting-list storage must be present.");
        }
        if (packetSender == null) {
            throw new IllegalArgumentException("ME output waiting-list packet sender must be present.");
        }
        if (maxDeltaEntriesPerTick <= 0 || maxDeltaEntriesPerTick > DEFAULT_MAX_DELTA_ENTRIES_PER_TICK) {
            throw new IllegalArgumentException("ME output waiting-list delta limit must be between 1 and " +
                    DEFAULT_MAX_DELTA_ENTRIES_PER_TICK + ": " + maxDeltaEntriesPerTick);
        }
        if (!(owner instanceof MEOutputWaitingListActionTarget actionTarget)) {
            throw new IllegalArgumentException("ME output waiting-list owner must expose its persistent target.");
        }
        this.owner = owner;
        this.targetOwner = actionTarget;
        this.storage = storage;
        this.packetSender = packetSender;
        this.maxDeltaEntriesPerTick = maxDeltaEntriesPerTick;
        storage.setOnViewChanged(this::queueChanges);
    }

    /**
     * Returns whether the player's current LDLib2 menu contains the exact owner receiver.
     */
    public boolean canRequestFull(ServerPlayer player) {
        return currentMenu(player) != null;
    }

    /**
     * Registers the current menu opening and sends or schedules its complete authoritative state.
     */
    public boolean requestFull(ServerPlayer player, UUID openingId, int requestSequence) {
        if (openingId == null) {
            throw new IllegalArgumentException("ME output waiting-list opening UUID must be present.");
        }
        if (requestSequence < 0) {
            throw new IllegalArgumentException("ME output waiting-list request sequence must be non-negative: " +
                    requestSequence);
        }
        ModularUIContainerMenu menu = currentMenu(player);
        if (menu == null) {
            return false;
        }

        UUID playerId = player.getUUID();
        Subscription previous = subscriptions.get(playerId);
        long gameTime = player.serverLevel().getGameTime();
        if (previous != null && previous.menu == menu) {
            if (!previous.openingId.equals(openingId)) {
                GTCEu.LOGGER.warn("ME output waiting-list rejected a second opening in container {} for {}",
                        menu.containerId, player.getGameProfile().getName());
                return false;
            }
            if (requestSequence <= previous.requestSequence) {
                return true;
            }
            Subscription updated = previous.withRequestSequence(requestSequence);
            if (previous.fullPending || !pendingChanges.isEmpty() || !canSendFull(playerId, gameTime)) {
                subscriptions.put(playerId, updated.withFullPending());
                ensureTicking();
                return true;
            }
            return sendFull(playerId, updated, gameTime, previous);
        }

        Subscription subscription = new Subscription(player, menu, openingId, requestSequence, false);
        if (!pendingChanges.isEmpty() || !canSendFull(playerId, gameTime)) {
            subscriptions.put(playerId, subscription.withFullPending());
            ensureTicking();
            return true;
        }
        return sendFull(playerId, subscription, gameTime, previous);
    }

    /**
     * Returns the current logical revision for focused protocol tests and diagnostics.
     */
    public long revision() {
        return revision;
    }

    int subscriptionCount() {
        return subscriptions.size();
    }

    int cooldownCount() {
        return fullRequestCooldowns.size();
    }

    private void queueChanges(KeyStorage.ChangeBatch batch) {
        if (owner.isRemote()) {
            return;
        }
        pendingChanges.addAll(batch.changes());
        ensureTicking();
    }

    private void ensureTicking() {
        if (tickSubscription == null || !tickSubscription.isStillSubscribed()) {
            tickSubscription = owner.subscribeServerTick(this::serverTick);
        }
    }

    void serverTick() {
        pruneSubscriptions();
        pruneExpiredCooldowns();
        if (!pendingChanges.isEmpty()) {
            flushPendingChanges();
        }
        if (pendingChanges.isEmpty()) {
            sendPendingFulls();
        }
        if (subscriptions.isEmpty() && pendingChanges.isEmpty() && fullRequestCooldowns.isEmpty() &&
                tickSubscription != null) {
            tickSubscription.unsubscribe();
            tickSubscription = null;
        }
    }

    void flushPendingChanges() {
        if (pendingChanges.isEmpty()) {
            return;
        }
        if (revision == Long.MAX_VALUE) {
            String message = "ME output waiting-list revision overflow at " + owner.getBlockPos();
            GTCEu.LOGGER.error(message);
            return;
        }
        long nextRevision = revision + 1;
        int entryCount = Math.min(pendingChanges.size(), maxDeltaEntriesPerTick);
        List<MEOutputWaitingListUpdate> chunks;
        try {
            List<MEOutputWaitingListEntry> entries = pendingChanges.subList(0, entryCount).stream()
                    .map(change -> new MEOutputWaitingListEntry(change.key(), change.amount()))
                    .toList();
            chunks = chunkUpdates(MEOutputWaitingListUpdate.Mode.DELTA, nextRevision, entries);
        } catch (RuntimeException exception) {
            GTCEu.LOGGER.error("Failed to build ME output waiting-list delta at {}",
                    owner.getBlockPos(), exception);
            return;
        }
        revision = nextRevision;
        pendingChanges.subList(0, entryCount).clear();

        Iterator<Map.Entry<UUID, Subscription>> iterator = subscriptions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Subscription> entry = iterator.next();
            Subscription subscription = entry.getValue();
            if (!isCurrent(entry.getKey(), subscription)) {
                iterator.remove();
                continue;
            }
            if (subscription.fullPending) {
                continue;
            }
            try {
                sendChunks(subscription, chunks);
            } catch (RuntimeException exception) {
                GTCEu.LOGGER.error("Failed to send ME output waiting-list delta at {} to {}",
                        owner.getBlockPos(), subscription.player.getGameProfile().getName(), exception);
                iterator.remove();
            }
        }
    }

    private void sendPendingFulls() {
        if (!pendingChanges.isEmpty()) {
            return;
        }
        List<MEOutputWaitingListUpdate> chunks = null;
        Iterator<Map.Entry<UUID, Subscription>> iterator = subscriptions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Subscription> entry = iterator.next();
            UUID playerId = entry.getKey();
            Subscription subscription = entry.getValue();
            if (!isCurrent(playerId, subscription)) {
                iterator.remove();
                continue;
            }
            long gameTime = subscription.player.serverLevel().getGameTime();
            if (!subscription.fullPending || !canSendFull(playerId, gameTime)) {
                continue;
            }
            if (chunks == null) {
                try {
                    chunks = fullChunks();
                } catch (RuntimeException exception) {
                    GTCEu.LOGGER.error("Failed to build pending ME output waiting-list full state at {}",
                            owner.getBlockPos(), exception);
                    return;
                }
            }
            try {
                sendChunks(subscription, chunks);
                entry.setValue(subscription.withoutFullPending());
                fullRequestCooldowns.put(playerId, new FullRequestCooldown(subscription.player, gameTime));
            } catch (RuntimeException exception) {
                GTCEu.LOGGER.error("Failed to send pending ME output waiting-list full state at {} to {}",
                        owner.getBlockPos(), subscription.player.getGameProfile().getName(), exception);
                iterator.remove();
            }
        }
    }

    private boolean canSendFull(UUID playerId, long gameTime) {
        FullRequestCooldown cooldown = fullRequestCooldowns.get(playerId);
        if (cooldown == null) {
            return true;
        }
        if (!cooldown.isExpired(gameTime)) {
            return false;
        }
        fullRequestCooldowns.remove(playerId);
        return true;
    }

    private boolean sendFull(UUID playerId, Subscription subscription, long gameTime,
                             @Nullable Subscription previous) {
        try {
            sendChunks(subscription, fullChunks());
            subscriptions.put(playerId, subscription.withoutFullPending());
            fullRequestCooldowns.put(playerId, new FullRequestCooldown(subscription.player, gameTime));
            ensureTicking();
            return true;
        } catch (RuntimeException exception) {
            if (previous == null) {
                subscriptions.remove(playerId);
            } else {
                subscriptions.put(playerId, previous);
            }
            GTCEu.LOGGER.error("Failed to send ME output waiting-list full state at {} to {}",
                    owner.getBlockPos(), subscription.player.getGameProfile().getName(), exception);
            return false;
        }
    }

    private List<MEOutputWaitingListUpdate> fullChunks() {
        List<MEOutputWaitingListEntry> entries = storage.snapshot().stream()
                .map(change -> new MEOutputWaitingListEntry(change.key(), change.amount()))
                .toList();
        return chunkUpdates(MEOutputWaitingListUpdate.Mode.FULL, revision, entries);
    }

    static List<MEOutputWaitingListUpdate> chunkUpdates(MEOutputWaitingListUpdate.Mode mode, long revision,
                                                        List<MEOutputWaitingListEntry> entries) {
        int chunkCount = entries.isEmpty() ? 1 :
                (entries.size() - 1) / MEOutputWaitingListUpdate.MAX_CHUNK_ENTRIES + 1;
        if (chunkCount > MEOutputWaitingListUpdate.MAX_CHUNK_COUNT) {
            throw new IllegalArgumentException("ME output waiting-list publication exceeds the chunk-count bound: " +
                    chunkCount);
        }
        List<MEOutputWaitingListUpdate> chunks = new ArrayList<>(chunkCount);
        for (int chunkIndex = 0; chunkIndex < chunkCount; chunkIndex++) {
            int from = chunkIndex * MEOutputWaitingListUpdate.MAX_CHUNK_ENTRIES;
            int to = Math.min(entries.size(), from + MEOutputWaitingListUpdate.MAX_CHUNK_ENTRIES);
            List<MEOutputWaitingListEntry> chunkEntries = entries.subList(from, to);
            chunks.add(mode == MEOutputWaitingListUpdate.Mode.FULL ?
                    MEOutputWaitingListUpdate.full(revision, chunkIndex, chunkCount, chunkEntries) :
                    MEOutputWaitingListUpdate.delta(revision, chunkIndex, chunkCount, chunkEntries));
        }
        return List.copyOf(chunks);
    }

    private void pruneSubscriptions() {
        subscriptions.entrySet().removeIf(entry -> !isCurrent(entry.getKey(), entry.getValue()));
    }

    private void pruneExpiredCooldowns() {
        fullRequestCooldowns.values().removeIf(FullRequestCooldown::isExpired);
    }

    private boolean isCurrent(UUID playerId, Subscription subscription) {
        if (!subscription.player.getUUID().equals(playerId)) {
            return false;
        }
        if (subscription.player.containerMenu != subscription.menu) {
            return false;
        }
        ModularUIContainerMenu menu = currentMenu(subscription.player);
        return menu == subscription.menu;
    }

    private @Nullable ModularUIContainerMenu currentMenu(ServerPlayer player) {
        if (!MachineOwner.canOpenOwnerMachine(player, owner)) {
            return null;
        }
        if (!(player.containerMenu instanceof ModularUIContainerMenu menu)) {
            return null;
        }
        if (MEOutputWaitingListRoute.resolve(player, menu.containerId, owner) == null) {
            return null;
        }
        return menu;
    }

    private void sendChunks(Subscription subscription, List<MEOutputWaitingListUpdate> chunks) {
        for (MEOutputWaitingListUpdate update : chunks) {
            packetSender.accept(subscription.player, new SPacketMEOutputWaitingListToClient(
                    subscription.menu.containerId, targetOwner.getWaitingListTarget(), subscription.openingId,
                    subscription.requestSequence, update));
        }
    }

    private record Subscription(ServerPlayer player, ModularUIContainerMenu menu, UUID openingId,
                                int requestSequence, boolean fullPending) {

        private Subscription withRequestSequence(int requestSequence) {
            return new Subscription(player, menu, openingId, requestSequence, fullPending);
        }

        private Subscription withFullPending() {
            return fullPending ? this : new Subscription(player, menu, openingId, requestSequence, true);
        }

        private Subscription withoutFullPending() {
            return fullPending ? new Subscription(player, menu, openingId, requestSequence, false) : this;
        }
    }

    private record FullRequestCooldown(ServerPlayer player, long successfulFullTick) {

        private boolean isExpired() {
            return isExpired(player.serverLevel().getGameTime());
        }

        private boolean isExpired(long gameTime) {
            return gameTime - successfulFullTick >= FULL_REQUEST_COOLDOWN_TICKS;
        }
    }
}

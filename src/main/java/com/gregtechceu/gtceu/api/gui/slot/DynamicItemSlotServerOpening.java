package com.gregtechceu.gtceu.api.gui.slot;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Sequences slot append, full container synchronization, activation, and page selection for one server menu.
 */
public final class DynamicItemSlotServerOpening {

    private final int containerId;
    private final UUID menuSessionId;
    private final int baseSlotCount;
    private final Set<UUID> appendedBindingIds = new HashSet<>();
    private final Set<UUID> seenManifestNonces = new HashSet<>();
    private Phase phase = Phase.ACTIVE;
    @Nullable
    private DynamicItemSlotManifest activeManifest;
    @Nullable
    private DynamicItemSlotManifest pendingManifest;
    @Nullable
    private UUID selectedBindingId;
    @Nullable
    private DynamicItemSlotSelection confirmedSelection;
    private boolean closed;

    /**
     * Creates the authoritative state for the fixed slot prefix already built by its menu.
     */
    public DynamicItemSlotServerOpening(int containerId, UUID menuSessionId, int baseSlotCount) {
        validateOpening(containerId, menuSessionId, baseSlotCount);
        this.containerId = containerId;
        this.menuSessionId = menuSessionId;
        this.baseSlotCount = baseSlotCount;
    }

    /**
     * Offers the next cumulative manifest and disables interaction until the full handshake completes.
     */
    public DynamicItemSlotTransition beginManifest(DynamicItemSlotManifest manifest) {
        if (closed) {
            return DynamicItemSlotTransition.CLOSE_OPENING;
        }
        if (matches(activeManifest, manifest)) {
            return DynamicItemSlotTransition.DUPLICATE;
        }
        if (matches(pendingManifest, manifest)) {
            return DynamicItemSlotTransition.DUPLICATE;
        }
        if (seenManifestNonces.contains(manifest.manifestNonce())) {
            return closeOpening();
        }
        if (phase != Phase.ACTIVE) {
            if (pendingManifest != null && manifest.epoch() == pendingManifest.epoch()) {
                return closeOpening();
            }
            return manifest.epoch() <= activeEpoch() ?
                    DynamicItemSlotTransition.STALE : DynamicItemSlotTransition.REJECTED;
        }
        if (manifest.epoch() < activeEpoch()) {
            return DynamicItemSlotTransition.STALE;
        }
        if (manifest.epoch() == activeEpoch()) {
            return closeOpening();
        }
        if (!DynamicItemSlotManifestProgression.isValid(activeManifest, manifest, baseSlotCount)) {
            return closeOpening();
        }

        pendingManifest = manifest;
        seenManifestNonces.add(manifest.manifestNonce());
        phase = Phase.WAITING_PREPARED;
        if (selectedBindingId != null && findPresentBinding(manifest, selectedBindingId).isEmpty()) {
            selectedBindingId = null;
        }
        return DynamicItemSlotTransition.ACCEPTED;
    }

    /**
     * Accepts PREPARED once and exposes only the append-only tail to the menu.
     */
    public DynamicItemSlotTransition receivePreparedAcknowledgement(DynamicItemSlotOpeningToken token) {
        DynamicItemSlotTransition identityDecision = classifyIdentity(token);
        if (identityDecision != DynamicItemSlotTransition.ACCEPTED) {
            return identityDecision;
        }
        if (matches(activeManifest, token)) {
            return DynamicItemSlotTransition.DUPLICATE;
        }
        DynamicItemSlotTransition pendingDecision = classifyPendingToken(token);
        if (pendingDecision != DynamicItemSlotTransition.ACCEPTED) {
            return pendingDecision;
        }
        if (phase == Phase.WAITING_PREPARED) {
            phase = Phase.APPENDING;
            return DynamicItemSlotTransition.ACCEPTED;
        }
        return phase == Phase.APPENDING || phase == Phase.SNAPSHOT_REQUIRED ||
                phase == Phase.WAITING_ACTIVATED ? DynamicItemSlotTransition.DUPLICATE :
                        DynamicItemSlotTransition.REJECTED;
    }

    /**
     * Returns the ranges that the server has not appended to its Vanilla menu yet.
     */
    public List<DynamicItemSlotBinding> bindingsToAppend() {
        if (closed || phase != Phase.APPENDING || pendingManifest == null) {
            return List.of();
        }
        return pendingManifest.bindings().stream()
                .filter(binding -> !appendedBindingIds.contains(binding.bindingId()))
                .toList();
    }

    /**
     * Returns the layout currently waiting for PREPARED, append, snapshot, or ACTIVATED.
     */
    public Optional<DynamicItemSlotManifest> pendingManifest() {
        return closed ? Optional.empty() : Optional.ofNullable(pendingManifest);
    }

    /**
     * Records an exact disabled append and requires a full container snapshot as the next operation.
     */
    public DynamicItemSlotTransition markDisabledSlotsAppended(DynamicItemSlotOpeningToken token,
                                                               Set<UUID> currentBindingIds) {
        DynamicItemSlotTransition identityDecision = classifyIdentity(token);
        if (identityDecision != DynamicItemSlotTransition.ACCEPTED) {
            return identityDecision;
        }
        if (matches(activeManifest, token)) {
            return appendedBindingIds.equals(currentBindingIds) ? DynamicItemSlotTransition.DUPLICATE :
                    closeOpening();
        }
        DynamicItemSlotTransition pendingDecision = classifyPendingToken(token);
        if (pendingDecision != DynamicItemSlotTransition.ACCEPTED) {
            return pendingDecision;
        }
        Set<UUID> expectedBindingIds = bindingIds(pendingManifest);
        if (phase == Phase.SNAPSHOT_REQUIRED || phase == Phase.WAITING_ACTIVATED) {
            return expectedBindingIds.equals(currentBindingIds) ? DynamicItemSlotTransition.DUPLICATE :
                    closeOpening();
        }
        if (phase != Phase.APPENDING || !expectedBindingIds.equals(currentBindingIds)) {
            return DynamicItemSlotTransition.REJECTED;
        }

        appendedBindingIds.clear();
        appendedBindingIds.addAll(expectedBindingIds);
        phase = Phase.SNAPSHOT_REQUIRED;
        return DynamicItemSlotTransition.ACCEPTED;
    }

    /**
     * Marks that sendAllDataToRemote completed before the activation payload is enqueued.
     */
    public DynamicItemSlotTransition markFullSnapshotSent(DynamicItemSlotOpeningToken token) {
        DynamicItemSlotTransition identityDecision = classifyIdentity(token);
        if (identityDecision != DynamicItemSlotTransition.ACCEPTED) {
            return identityDecision;
        }
        if (matches(activeManifest, token)) {
            return DynamicItemSlotTransition.DUPLICATE;
        }
        DynamicItemSlotTransition pendingDecision = classifyPendingToken(token);
        if (pendingDecision != DynamicItemSlotTransition.ACCEPTED) {
            return pendingDecision;
        }
        if (phase == Phase.WAITING_ACTIVATED) {
            return DynamicItemSlotTransition.DUPLICATE;
        }
        if (phase != Phase.SNAPSHOT_REQUIRED) {
            return DynamicItemSlotTransition.REJECTED;
        }
        phase = Phase.WAITING_ACTIVATED;
        return DynamicItemSlotTransition.ACCEPTED;
    }

    /**
     * Commits a layout only after the client confirms that activation was applied.
     */
    public DynamicItemSlotTransition receiveActivatedAcknowledgement(DynamicItemSlotOpeningToken token) {
        DynamicItemSlotTransition identityDecision = classifyIdentity(token);
        if (identityDecision != DynamicItemSlotTransition.ACCEPTED) {
            return identityDecision;
        }
        if (matches(activeManifest, token)) {
            return DynamicItemSlotTransition.DUPLICATE;
        }
        DynamicItemSlotTransition pendingDecision = classifyPendingToken(token);
        if (pendingDecision != DynamicItemSlotTransition.ACCEPTED) {
            return pendingDecision;
        }
        if (phase != Phase.WAITING_ACTIVATED) {
            return DynamicItemSlotTransition.REJECTED;
        }

        activeManifest = pendingManifest;
        pendingManifest = null;
        phase = Phase.ACTIVE;
        if (selectedBindingId != null && findPresentBinding(activeManifest, selectedBindingId).isEmpty()) {
            selectedBindingId = null;
        }
        return DynamicItemSlotTransition.ACCEPTED;
    }

    /**
     * Confirms a selectable active binding before the client is allowed to switch pages.
     */
    public DynamicItemSlotTransition receiveSelectionRequest(DynamicItemSlotOpeningToken token,
                                                             DynamicItemSlotSelection selection,
                                                             Predicate<DynamicItemSlotBinding> selectable) {
        DynamicItemSlotTransition activeDecision = classifyActiveToken(token);
        if (activeDecision != DynamicItemSlotTransition.ACCEPTED) {
            return activeDecision;
        }
        if (phase != Phase.ACTIVE) {
            return DynamicItemSlotTransition.STALE;
        }
        if (confirmedSelection != null) {
            if (selection.sequence() < confirmedSelection.sequence()) {
                return DynamicItemSlotTransition.STALE;
            }
            if (selection.sequence() == confirmedSelection.sequence()) {
                return selection.equals(confirmedSelection) ? DynamicItemSlotTransition.DUPLICATE :
                        closeOpening();
            }
            if (selection.sequence() != confirmedSelection.sequence() + 1) {
                return closeOpening();
            }
        } else if (selection.sequence() != 0) {
            return closeOpening();
        }

        Optional<UUID> requestedBindingId = selection.bindingId();
        if (requestedBindingId.isPresent()) {
            Optional<DynamicItemSlotBinding> binding = findPresentBinding(
                    activeManifest, requestedBindingId.orElseThrow());
            if (binding.isEmpty() || !selectable.test(binding.orElseThrow())) {
                return DynamicItemSlotTransition.REJECTED;
            }
        }
        selectedBindingId = requestedBindingId.orElse(null);
        confirmedSelection = selection;
        return DynamicItemSlotTransition.ACCEPTED;
    }

    /**
     * Returns whether the active layout and server-confirmed page permit this binding to handle a slot click.
     */
    public boolean isBindingInteractive(UUID bindingId) {
        return !closed && phase == Phase.ACTIVE && pendingManifest == null && bindingId.equals(selectedBindingId) &&
                findPresentBinding(activeManifest, bindingId).isPresent();
    }

    /**
     * Returns the token currently waiting for PREPARED or ACTIVATED.
     */
    public Optional<DynamicItemSlotOpeningToken> pendingToken() {
        return closed || pendingManifest == null ? Optional.empty() : Optional.of(token(pendingManifest));
    }

    /**
     * Returns the token of the layout whose ACTIVATED acknowledgement has completed.
     */
    public Optional<DynamicItemSlotOpeningToken> activeToken() {
        return closed || activeManifest == null ? Optional.empty() : Optional.of(token(activeManifest));
    }

    /**
     * Returns the layout committed by the last accepted ACTIVATED acknowledgement.
     */
    public Optional<DynamicItemSlotManifest> activeManifest() {
        return closed ? Optional.empty() : Optional.ofNullable(activeManifest);
    }

    /**
     * Returns the page selection the server will include in ACTIVATE and selection acknowledgements.
     */
    public Optional<UUID> selectedBindingId() {
        return closed ? Optional.empty() : Optional.ofNullable(selectedBindingId);
    }

    /**
     * Returns whether a protocol conflict permanently closed this state object.
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Permanently terminates this opening after a menu-level failure or normal removal.
     */
    public void terminate() {
        closed = true;
    }

    private DynamicItemSlotTransition classifyIdentity(DynamicItemSlotOpeningToken token) {
        if (closed) {
            return DynamicItemSlotTransition.CLOSE_OPENING;
        }
        return token.containerId() == containerId && token.menuSessionId().equals(menuSessionId) ?
                DynamicItemSlotTransition.ACCEPTED : closeOpening();
    }

    private DynamicItemSlotTransition classifyPendingToken(DynamicItemSlotOpeningToken token) {
        DynamicItemSlotTransition identityDecision = classifyIdentity(token);
        if (identityDecision != DynamicItemSlotTransition.ACCEPTED) {
            return identityDecision;
        }
        if (pendingManifest == null) {
            return token.epoch() <= activeEpoch() ?
                    DynamicItemSlotTransition.STALE : DynamicItemSlotTransition.REJECTED;
        }
        if (token.epoch() < pendingManifest.epoch()) {
            return DynamicItemSlotTransition.STALE;
        }
        return token.matches(containerId, menuSessionId, pendingManifest) ?
                DynamicItemSlotTransition.ACCEPTED :
                token.epoch() == pendingManifest.epoch() ? closeOpening() :
                        DynamicItemSlotTransition.REJECTED;
    }

    private DynamicItemSlotTransition classifyActiveToken(DynamicItemSlotOpeningToken token) {
        DynamicItemSlotTransition identityDecision = classifyIdentity(token);
        if (identityDecision != DynamicItemSlotTransition.ACCEPTED) {
            return identityDecision;
        }
        if (activeManifest == null) {
            return DynamicItemSlotTransition.REJECTED;
        }
        if (token.epoch() < activeManifest.epoch()) {
            return DynamicItemSlotTransition.STALE;
        }
        return token.matches(containerId, menuSessionId, activeManifest) ?
                DynamicItemSlotTransition.ACCEPTED :
                token.epoch() == activeManifest.epoch() ? closeOpening() :
                        DynamicItemSlotTransition.REJECTED;
    }

    private DynamicItemSlotTransition closeOpening() {
        closed = true;
        return DynamicItemSlotTransition.CLOSE_OPENING;
    }

    private DynamicItemSlotOpeningToken token(DynamicItemSlotManifest manifest) {
        return DynamicItemSlotOpeningToken.of(containerId, menuSessionId, manifest);
    }

    private long activeEpoch() {
        return activeManifest == null ? 0 : activeManifest.epoch();
    }

    private static Set<UUID> bindingIds(DynamicItemSlotManifest manifest) {
        Set<UUID> bindingIds = new HashSet<>();
        for (DynamicItemSlotBinding binding : manifest.bindings()) {
            bindingIds.add(binding.bindingId());
        }
        return bindingIds;
    }

    private static Optional<DynamicItemSlotBinding> findPresentBinding(
                                                                       @Nullable DynamicItemSlotManifest manifest,
                                                                       UUID bindingId) {
        if (manifest == null) {
            return Optional.empty();
        }
        return manifest.bindings().stream()
                .filter(binding -> binding.present() && binding.bindingId().equals(bindingId))
                .findFirst();
    }

    private static boolean matches(@Nullable DynamicItemSlotManifest manifest,
                                   DynamicItemSlotManifest candidate) {
        return manifest != null && manifest.equals(candidate);
    }

    private static boolean matches(@Nullable DynamicItemSlotManifest manifest,
                                   DynamicItemSlotOpeningToken token) {
        return manifest != null && manifest.epoch() == token.epoch() &&
                manifest.manifestNonce().equals(token.manifestNonce());
    }

    private static void validateOpening(int containerId, UUID menuSessionId, int baseSlotCount) {
        if (containerId < 0) {
            throw new IllegalArgumentException("containerId must be non-negative: " + containerId);
        }
        if (menuSessionId == null) {
            throw new IllegalArgumentException("menuSessionId must not be null");
        }
        if (baseSlotCount < 0 || baseSlotCount > DynamicItemSlotManifest.MAX_TOTAL_SLOT_COUNT) {
            throw new IllegalArgumentException("invalid baseSlotCount: " + baseSlotCount);
        }
    }

    private enum Phase {
        ACTIVE,
        WAITING_PREPARED,
        APPENDING,
        SNAPSHOT_REQUIRED,
        WAITING_ACTIVATED
    }
}

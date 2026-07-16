package com.gregtechceu.gtceu.api.gui.slot;

import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Applies manifests and server acknowledgements for one client menu without enabling unconfirmed slot bindings.
 */
public final class DynamicItemSlotClientOpening {

    private final int containerId;
    private final UUID menuSessionId;
    private final int baseSlotCount;
    private final Set<UUID> appendedBindingIds = new HashSet<>();
    private final Set<UUID> seenManifestNonces = new HashSet<>();
    @Nullable
    private DynamicItemSlotManifest activeManifest;
    @Nullable
    private DynamicItemSlotManifest pendingManifest;
    private boolean prepared;
    @Nullable
    private UUID selectedBindingId;
    @Nullable
    private DynamicItemSlotSelection pendingSelection;
    @Nullable
    private DynamicItemSlotSelection confirmedSelection;
    private long nextSelectionSequence;
    private boolean closed;

    /**
     * Creates the client state for the fixed slot prefix already built by its menu.
     */
    public DynamicItemSlotClientOpening(int containerId, UUID menuSessionId, int baseSlotCount) {
        validateOpening(containerId, menuSessionId, baseSlotCount);
        this.containerId = containerId;
        this.menuSessionId = menuSessionId;
        this.baseSlotCount = baseSlotCount;
    }

    /**
     * Accepts only the next cumulative manifest for this exact opening.
     */
    public DynamicItemSlotTransition receiveManifest(DynamicItemSlotOpeningToken token,
                                                     DynamicItemSlotManifest manifest) {
        DynamicItemSlotTransition identityDecision = classifyIdentity(token);
        if (identityDecision != DynamicItemSlotTransition.ACCEPTED) {
            return identityDecision;
        }
        if (!token.matches(containerId, menuSessionId, manifest)) {
            return closeOpening();
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

        long activeEpoch = activeEpoch();
        if (manifest.epoch() < activeEpoch) {
            return DynamicItemSlotTransition.STALE;
        }
        if (manifest.epoch() == activeEpoch) {
            return closeOpening();
        }
        if (pendingManifest != null) {
            return manifest.epoch() == pendingManifest.epoch() ?
                    closeOpening() : DynamicItemSlotTransition.REJECTED;
        }
        if (!DynamicItemSlotManifestProgression.isValid(activeManifest, manifest, baseSlotCount)) {
            return closeOpening();
        }

        pendingManifest = manifest;
        seenManifestNonces.add(manifest.manifestNonce());
        prepared = false;
        pendingSelection = null;
        if (selectedBindingId != null && findPresentBinding(manifest, selectedBindingId).isEmpty()) {
            selectedBindingId = null;
        }
        return DynamicItemSlotTransition.ACCEPTED;
    }

    /**
     * Returns the append-only tail that still needs disabled client slot elements.
     */
    public List<DynamicItemSlotBinding> bindingsToAppend() {
        if (closed || pendingManifest == null) {
            return List.of();
        }
        return pendingManifest.bindings().stream()
                .filter(binding -> !appendedBindingIds.contains(binding.bindingId()))
                .toList();
    }

    /**
     * Returns the layout currently waiting for PREPARED and ACTIVATE.
     */
    public Optional<DynamicItemSlotManifest> pendingManifest() {
        return closed ? Optional.empty() : Optional.ofNullable(pendingManifest);
    }

    /**
     * Returns whether unresolved targets still prevent the client from sending PREPARED.
     */
    public boolean requiresPreparation() {
        return !closed && pendingManifest != null && !prepared;
    }

    /**
     * Completes PREPARED only after every reserved range exists and every present target resolves locally.
     */
    public DynamicItemSlotTransition completePreparation(DynamicItemSlotOpeningToken token,
                                                         Set<UUID> currentBindingIds,
                                                         Set<UUID> resolvedPresentBindingIds) {
        DynamicItemSlotTransition pendingDecision = classifyPendingToken(token);
        if (pendingDecision != DynamicItemSlotTransition.ACCEPTED) {
            return pendingDecision;
        }
        if (prepared) {
            return DynamicItemSlotTransition.DUPLICATE;
        }

        Set<UUID> expectedBindingIds = bindingIds(pendingManifest, false);
        Set<UUID> expectedPresentBindingIds = bindingIds(pendingManifest, true);
        if (!expectedBindingIds.equals(currentBindingIds) ||
                !expectedPresentBindingIds.equals(resolvedPresentBindingIds)) {
            return DynamicItemSlotTransition.REJECTED;
        }
        appendedBindingIds.clear();
        appendedBindingIds.addAll(expectedBindingIds);
        prepared = true;
        return DynamicItemSlotTransition.ACCEPTED;
    }

    /**
     * Commits the pending layout only after PREPARED and returns a duplicate for an already active token.
     */
    public DynamicItemSlotTransition receiveActivation(DynamicItemSlotOpeningToken token,
                                                       Optional<UUID> activatedBindingId) {
        if (activatedBindingId == null) {
            return closeOpening();
        }
        DynamicItemSlotTransition identityDecision = classifyIdentity(token);
        if (identityDecision != DynamicItemSlotTransition.ACCEPTED) {
            return identityDecision;
        }
        if (matches(activeManifest, token)) {
            return activatedBindingId.equals(selectedBindingId()) ? DynamicItemSlotTransition.DUPLICATE :
                    closeOpening();
        }
        DynamicItemSlotTransition pendingDecision = classifyPendingToken(token);
        if (pendingDecision != DynamicItemSlotTransition.ACCEPTED) {
            return pendingDecision;
        }
        if (!prepared) {
            return DynamicItemSlotTransition.REJECTED;
        }
        if (!activatedBindingId.equals(selectedBindingId())) {
            return closeOpening();
        }
        if (activatedBindingId.isPresent() &&
                findPresentBinding(pendingManifest, activatedBindingId.orElseThrow()).isEmpty()) {
            return closeOpening();
        }

        activeManifest = pendingManifest;
        pendingManifest = null;
        prepared = false;
        pendingSelection = null;
        selectedBindingId = activatedBindingId.orElse(null);
        return DynamicItemSlotTransition.ACCEPTED;
    }

    /**
     * Creates a selection request without changing the interactive binding before the server ACK.
     */
    public Optional<DynamicItemSlotSelection> requestSelection(UUID bindingId) {
        if (closed || pendingManifest != null || pendingSelection != null ||
                findPresentBinding(activeManifest, bindingId).isEmpty()) {
            return Optional.empty();
        }
        DynamicItemSlotSelection selection = new DynamicItemSlotSelection(nextSelectionSequence, bindingId);
        nextSelectionSequence = Math.incrementExact(nextSelectionSequence);
        pendingSelection = selection;
        return Optional.of(selection);
    }

    /**
     * Switches the client page only when the ACK matches its exact active manifest and pending request.
     */
    public DynamicItemSlotTransition receiveSelectionAcknowledgement(DynamicItemSlotOpeningToken token,
                                                                     DynamicItemSlotSelection selection) {
        DynamicItemSlotTransition activeDecision = classifyActiveToken(token);
        if (activeDecision != DynamicItemSlotTransition.ACCEPTED) {
            return activeDecision;
        }
        if (pendingManifest != null) {
            return DynamicItemSlotTransition.STALE;
        }
        if (selection.equals(confirmedSelection)) {
            return DynamicItemSlotTransition.DUPLICATE;
        }
        if (pendingSelection == null) {
            return confirmedSelection != null && selection.sequence() < confirmedSelection.sequence() ?
                    DynamicItemSlotTransition.STALE : DynamicItemSlotTransition.REJECTED;
        }
        if (selection.sequence() < pendingSelection.sequence()) {
            return DynamicItemSlotTransition.STALE;
        }
        if (!selection.equals(pendingSelection)) {
            return selection.sequence() == pendingSelection.sequence() ?
                    closeOpening() : DynamicItemSlotTransition.REJECTED;
        }
        if (findPresentBinding(activeManifest, selection.bindingId()).isEmpty()) {
            return closeOpening();
        }

        selectedBindingId = selection.bindingId();
        confirmedSelection = selection;
        pendingSelection = null;
        return DynamicItemSlotTransition.ACCEPTED;
    }

    /**
     * Returns whether both the layout and selected page have been acknowledged by the server.
     */
    public boolean isBindingInteractive(UUID bindingId) {
        return !closed && pendingManifest == null && pendingSelection == null && bindingId.equals(selectedBindingId) &&
                findPresentBinding(activeManifest, bindingId).isPresent();
    }

    /**
     * Returns the token of the active manifest, when the first handshake has completed.
     */
    public Optional<DynamicItemSlotOpeningToken> activeToken() {
        return closed || activeManifest == null ? Optional.empty() : Optional.of(token(activeManifest));
    }

    /**
     * Returns the layout committed by the last accepted ACTIVATE message.
     */
    public Optional<DynamicItemSlotManifest> activeManifest() {
        return closed ? Optional.empty() : Optional.ofNullable(activeManifest);
    }

    /**
     * Returns the page selection most recently confirmed by the server.
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

    private static boolean matches(@Nullable DynamicItemSlotManifest manifest,
                                   DynamicItemSlotManifest candidate) {
        return manifest != null && manifest.equals(candidate);
    }

    private static boolean matches(@Nullable DynamicItemSlotManifest manifest,
                                   DynamicItemSlotOpeningToken token) {
        return manifest != null && manifest.epoch() == token.epoch() &&
                manifest.manifestNonce().equals(token.manifestNonce());
    }

    private static Set<UUID> bindingIds(DynamicItemSlotManifest manifest, boolean presentOnly) {
        Set<UUID> bindingIds = new HashSet<>();
        for (DynamicItemSlotBinding binding : manifest.bindings()) {
            if (!presentOnly || binding.present()) {
                bindingIds.add(binding.bindingId());
            }
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
}

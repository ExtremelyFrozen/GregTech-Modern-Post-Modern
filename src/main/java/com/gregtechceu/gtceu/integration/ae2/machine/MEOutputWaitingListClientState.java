package com.gregtechceu.gtceu.integration.ae2.machine;

import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reassembles bounded waiting-list chunks and applies complete publications atomically on the client.
 *
 * <p>
 * Full chunks may arrive in any order. Delta chunks must use the revision immediately following the currently
 * applied revision; a gap requests a new full snapshot. Delta operations retain their publication order, so a
 * tombstone followed by a positive amount for the same key restores that key at the end of the insertion order.
 * </p>
 */
public final class MEOutputWaitingListClientState {

    private long revision = -1;
    private List<MEOutputWaitingListEntry> entries = List.of();
    private @Nullable PendingPublication pending;

    /**
     * Returns the most recently applied revision, or {@code -1} before the first full publication.
     */
    public long revision() {
        return revision;
    }

    /**
     * Returns the immutable client view in first-insertion order.
     */
    public List<MEOutputWaitingListEntry> entries() {
        return entries;
    }

    /**
     * Accepts one chunk and reports whether it applied, is awaiting peers, was stale, or requires a full resync.
     */
    public ApplyResult accept(MEOutputWaitingListUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("ME output waiting-list update must be present.");
        }
        if (update.mode() == MEOutputWaitingListUpdate.Mode.DELTA) {
            return acceptDelta(update);
        }
        return acceptFull(update, false);
    }

    /**
     * Accepts one server-authoritative full chunk, replacing any partial publication with a different mode or
     * revision and permitting the completed snapshot to establish any valid revision.
     */
    public ApplyResult acceptAuthoritativeFull(MEOutputWaitingListUpdate update) {
        if (update == null) {
            throw new IllegalArgumentException("Authoritative ME output waiting-list update must be present.");
        }
        if (update.mode() != MEOutputWaitingListUpdate.Mode.FULL) {
            throw new IllegalArgumentException("Authoritative ME output waiting-list update must be a full snapshot.");
        }
        return acceptFull(update, true);
    }

    /**
     * Returns whether a publication is currently waiting for more chunks.
     */
    public boolean hasPendingPublication() {
        return pending != null;
    }

    /**
     * Discards an incomplete publication without changing the last atomically applied state.
     */
    public void discardPendingPublication() {
        pending = null;
    }

    /**
     * Clears both applied and partially assembled state when a client opening is replaced.
     */
    public void reset() {
        revision = -1;
        entries = List.of();
        pending = null;
    }

    private ApplyResult acceptFull(MEOutputWaitingListUpdate update, boolean authoritative) {
        if (!authoritative && update.revision() <= revision) {
            return ApplyResult.IGNORED;
        }
        if (!authoritative && pending != null && pending.revision > update.revision()) {
            return ApplyResult.IGNORED;
        }
        if (pending == null || pending.mode != MEOutputWaitingListUpdate.Mode.FULL ||
                pending.revision != update.revision()) {
            pending = new PendingPublication(update);
        }
        return collectAndApply(update);
    }

    private ApplyResult acceptDelta(MEOutputWaitingListUpdate update) {
        if (update.revision() <= revision) {
            return ApplyResult.IGNORED;
        }
        if (revision < 0 || update.revision() != revision + 1) {
            pending = null;
            return ApplyResult.REQUEST_FULL;
        }
        if (pending != null && (pending.mode != MEOutputWaitingListUpdate.Mode.DELTA ||
                pending.revision != update.revision())) {
            pending = null;
            return ApplyResult.REQUEST_FULL;
        }
        if (pending == null) {
            pending = new PendingPublication(update);
        }
        return collectAndApply(update);
    }

    private ApplyResult collectAndApply(MEOutputWaitingListUpdate update) {
        PendingPublication publication = pending;
        if (publication == null) {
            throw new IllegalStateException("ME output waiting-list publication disappeared during chunk assembly.");
        }
        if (publication.chunkCount != update.chunkCount()) {
            pending = null;
            return ApplyResult.REQUEST_FULL;
        }
        ChunkResult chunkResult = publication.add(update);
        if (chunkResult == ChunkResult.CONFLICT) {
            pending = null;
            return ApplyResult.REQUEST_FULL;
        }
        if (chunkResult == ChunkResult.DUPLICATE) {
            return ApplyResult.IGNORED;
        }
        if (!publication.isComplete()) {
            return ApplyResult.WAITING_FOR_CHUNKS;
        }

        List<MEOutputWaitingListEntry> assembled = publication.assemble();
        if (publication.mode == MEOutputWaitingListUpdate.Mode.FULL) {
            entries = applyFull(assembled);
        } else {
            entries = applyDelta(assembled);
        }
        revision = publication.revision;
        pending = null;
        return ApplyResult.APPLIED;
    }

    private static List<MEOutputWaitingListEntry> applyFull(List<MEOutputWaitingListEntry> fullEntries) {
        List<MEOutputWaitingListEntry> result = new ArrayList<>(fullEntries.size());
        Set<AEKey> keys = new HashSet<>(fullEntries.size());
        for (MEOutputWaitingListEntry entry : fullEntries) {
            if (!keys.add(entry.key())) {
                throw new IllegalArgumentException("ME output waiting-list full publication repeats AE key " +
                        entry.key() + '.');
            }
            result.add(entry);
        }
        return List.copyOf(result);
    }

    private List<MEOutputWaitingListEntry> applyDelta(List<MEOutputWaitingListEntry> deltaEntries) {
        Map<AEKey, Long> result = new LinkedHashMap<>(entries.size() + deltaEntries.size());
        for (MEOutputWaitingListEntry existing : entries) {
            result.put(existing.key(), existing.amount());
        }
        for (MEOutputWaitingListEntry change : deltaEntries) {
            if (change.amount() == 0) {
                result.remove(change.key());
            } else {
                result.put(change.key(), change.amount());
            }
        }

        List<MEOutputWaitingListEntry> applied = new ArrayList<>(result.size());
        for (Map.Entry<AEKey, Long> entry : result.entrySet()) {
            applied.add(new MEOutputWaitingListEntry(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(applied);
    }

    /**
     * Result of accepting one waiting-list update chunk.
     */
    public enum ApplyResult {
        /**
         * The complete publication was applied atomically.
         */
        APPLIED,
        /**
         * More chunks from the same publication are required.
         */
        WAITING_FOR_CHUNKS,
        /**
         * The chunk was an exact duplicate or belonged to an already applied revision.
         */
        IGNORED,
        /**
         * The revision or chunk contract was broken and the opening must request a new full publication.
         */
        REQUEST_FULL,
    }

    private enum ChunkResult {
        ADDED,
        DUPLICATE,
        CONFLICT,
    }

    private static final class PendingPublication {

        private final MEOutputWaitingListUpdate.Mode mode;
        private final long revision;
        private final int chunkCount;
        private final List<@Nullable List<MEOutputWaitingListEntry>> chunks;
        private int receivedChunks;

        private PendingPublication(MEOutputWaitingListUpdate first) {
            mode = first.mode();
            revision = first.revision();
            chunkCount = first.chunkCount();
            chunks = new ArrayList<>(chunkCount);
            for (int index = 0; index < chunkCount; index++) {
                chunks.add(null);
            }
        }

        private ChunkResult add(MEOutputWaitingListUpdate update) {
            List<MEOutputWaitingListEntry> existing = chunks.get(update.chunkIndex());
            if (existing != null) {
                if (!existing.equals(update.entries())) {
                    return ChunkResult.CONFLICT;
                }
                return ChunkResult.DUPLICATE;
            }
            chunks.set(update.chunkIndex(), update.entries());
            receivedChunks++;
            return ChunkResult.ADDED;
        }

        private boolean isComplete() {
            return receivedChunks == chunkCount;
        }

        private List<MEOutputWaitingListEntry> assemble() {
            List<MEOutputWaitingListEntry> assembled = new ArrayList<>();
            for (List<MEOutputWaitingListEntry> chunk : chunks) {
                if (chunk == null) {
                    throw new IllegalStateException("ME output waiting-list publication assembled before completion.");
                }
                assembled.addAll(chunk);
            }
            return assembled;
        }
    }
}

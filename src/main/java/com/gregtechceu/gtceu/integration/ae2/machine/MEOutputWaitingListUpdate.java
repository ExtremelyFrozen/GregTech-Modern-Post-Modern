package com.gregtechceu.gtceu.integration.ae2.machine;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import appeng.api.stacks.AEKey;
import io.netty.handler.codec.DecoderException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * One bounded full or delta chunk for an opening-scoped ME output waiting list.
 *
 * <p>
 * Every logical publication uses one revision. Full publications may establish any non-negative revision, while
 * delta publications are applied only when their revision immediately follows the receiver's current revision.
 * </p>
 *
 * @param mode       whether this chunk replaces the complete list or applies ordered changes
 * @param revision   logical publication revision shared by all of its chunks
 * @param chunkIndex zero-based chunk index within this publication
 * @param chunkCount total number of chunks in this publication
 * @param entries    immutable entries carried by this chunk
 */
public record MEOutputWaitingListUpdate(Mode mode, long revision, int chunkIndex, int chunkCount,
                                        List<MEOutputWaitingListEntry> entries) {

    /**
     * Maximum entries accepted in one network chunk.
     */
    public static final int MAX_CHUNK_ENTRIES = 64;

    /**
     * Maximum chunks accepted for one logical publication, bounding receiver allocation.
     */
    public static final int MAX_CHUNK_COUNT = 16_384;

    /**
     * Registry-aware codec with explicit bounds checked before allocating the entry list.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, MEOutputWaitingListUpdate> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public MEOutputWaitingListUpdate decode(RegistryFriendlyByteBuf buffer) {
            Mode mode = Mode.decode(buffer.readUnsignedByte());
            long revision = buffer.readVarLong();
            int chunkIndex = buffer.readVarInt();
            int chunkCount = buffer.readVarInt();
            int entryCount = buffer.readVarInt();
            if (entryCount < 0 || entryCount > MAX_CHUNK_ENTRIES) {
                throw new DecoderException("ME output waiting-list chunk entry count is out of bounds: " +
                        entryCount);
            }
            List<MEOutputWaitingListEntry> entries = new ArrayList<>(entryCount);
            for (int index = 0; index < entryCount; index++) {
                entries.add(MEOutputWaitingListEntry.STREAM_CODEC.decode(buffer));
            }
            return new MEOutputWaitingListUpdate(mode, revision, chunkIndex, chunkCount, entries);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, MEOutputWaitingListUpdate value) {
            buffer.writeByte(value.mode.wireId);
            buffer.writeVarLong(value.revision);
            buffer.writeVarInt(value.chunkIndex);
            buffer.writeVarInt(value.chunkCount);
            buffer.writeVarInt(value.entries.size());
            for (MEOutputWaitingListEntry entry : value.entries) {
                MEOutputWaitingListEntry.STREAM_CODEC.encode(buffer, entry);
            }
        }
    };

    /**
     * Validates publication bounds and isolates the immutable chunk from caller-owned collections.
     */
    public MEOutputWaitingListUpdate {
        if (mode == null) {
            throw new IllegalArgumentException("ME output waiting-list update mode must be present.");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("ME output waiting-list revision must be non-negative: " + revision);
        }
        if (chunkCount < 1 || chunkCount > MAX_CHUNK_COUNT) {
            throw new IllegalArgumentException("ME output waiting-list chunk count is out of bounds: " + chunkCount);
        }
        if (chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException("ME output waiting-list chunk index " + chunkIndex +
                    " is outside chunk count " + chunkCount + '.');
        }
        if (entries == null) {
            throw new IllegalArgumentException("ME output waiting-list entries must be present.");
        }
        if (entries.size() > MAX_CHUNK_ENTRIES) {
            throw new IllegalArgumentException("ME output waiting-list chunk exceeds " + MAX_CHUNK_ENTRIES +
                    " entries: " + entries.size());
        }
        entries = List.copyOf(entries);
        validateEntries(mode, chunkIndex, chunkCount, entries);
    }

    /**
     * Creates one validated full-snapshot chunk.
     */
    public static MEOutputWaitingListUpdate full(long revision, int chunkIndex, int chunkCount,
                                                 List<MEOutputWaitingListEntry> entries) {
        return new MEOutputWaitingListUpdate(Mode.FULL, revision, chunkIndex, chunkCount, entries);
    }

    /**
     * Creates one validated delta chunk containing ordered positive writes and zero-amount tombstones.
     */
    public static MEOutputWaitingListUpdate delta(long revision, int chunkIndex, int chunkCount,
                                                  List<MEOutputWaitingListEntry> entries) {
        return new MEOutputWaitingListUpdate(Mode.DELTA, revision, chunkIndex, chunkCount, entries);
    }

    private static void validateEntries(Mode mode, int chunkIndex, int chunkCount,
                                        List<MEOutputWaitingListEntry> entries) {
        if (mode == Mode.DELTA && entries.isEmpty()) {
            throw new IllegalArgumentException("Every ME output waiting-list delta chunk must contain a real change.");
        }
        if (mode == Mode.FULL && entries.isEmpty() && (chunkIndex != 0 || chunkCount != 1)) {
            throw new IllegalArgumentException("Only a single full chunk may represent an empty waiting list.");
        }

        Set<AEKey> fullKeys = mode == Mode.FULL ? new HashSet<>(entries.size()) : Set.of();
        for (MEOutputWaitingListEntry entry : entries) {
            if (entry == null) {
                throw new IllegalArgumentException("ME output waiting-list chunk contains a missing entry.");
            }
            if (mode == Mode.FULL && entry.amount() == 0) {
                throw new IllegalArgumentException("ME output waiting-list full entry amount must be positive.");
            }
            if (mode == Mode.FULL && !fullKeys.add(entry.key())) {
                throw new IllegalArgumentException("ME output waiting-list full chunk repeats AE key " +
                        entry.key() + '.');
            }
        }
    }

    /**
     * Distinguishes complete replacement chunks from incremental final-state chunks.
     */
    public enum Mode {

        /**
         * Replaces the entire client list once every chunk for the revision arrives.
         */
        FULL(0),
        /**
         * Applies ordered positive writes and tombstones once every chunk for the revision arrives.
         */
        DELTA(1);

        private final int wireId;

        Mode(int wireId) {
            this.wireId = wireId;
        }

        private static Mode decode(int wireId) {
            return switch (wireId) {
                case 0 -> FULL;
                case 1 -> DELTA;
                default -> throw new DecoderException("Unknown ME output waiting-list update mode: " + wireId);
            };
        }
    }
}

package com.gregtechceu.gtceu.integration.ae2.machine;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import appeng.api.stacks.AEKey;
import io.netty.handler.codec.DecoderException;

/**
 * One authoritative entry in an ME output machine waiting-list update.
 *
 * @param key    exact AE key, including its data components
 * @param amount complete long amount, or zero when a delta removes the key
 */
public record MEOutputWaitingListEntry(AEKey key, long amount) {

    /**
     * Registry-aware wire codec that preserves the complete AE key and long amount.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, MEOutputWaitingListEntry> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public MEOutputWaitingListEntry decode(RegistryFriendlyByteBuf buffer) {
            AEKey key = AEKey.STREAM_CODEC.decode(buffer);
            if (key == null) {
                throw new DecoderException("ME output waiting-list entry contains an unknown AE key type.");
            }
            return new MEOutputWaitingListEntry(key, buffer.readVarLong());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, MEOutputWaitingListEntry value) {
            AEKey.STREAM_CODEC.encode(buffer, value.key);
            buffer.writeVarLong(value.amount);
        }
    };

    /**
     * Rejects missing keys and negative amounts at the protocol boundary.
     */
    public MEOutputWaitingListEntry {
        if (key == null) {
            throw new IllegalArgumentException("ME output waiting-list key must be present.");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("ME output waiting-list amount must be non-negative: " + amount);
        }
    }
}

package com.gregtechceu.gtceu.api.gui.slot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Correlates a client page-selection request with the binding confirmed by the server.
 *
 * @param sequence  opening-local monotonically increasing request sequence
 * @param bindingId lifecycle binding requested for the visible interactive page
 */
public record DynamicItemSlotSelection(long sequence, UUID bindingId) {

    /** Network codec for selection requests and acknowledgements. */
    public static final StreamCodec<RegistryFriendlyByteBuf, DynamicItemSlotSelection> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public @NotNull DynamicItemSlotSelection decode(RegistryFriendlyByteBuf buffer) {
            return new DynamicItemSlotSelection(buffer.readVarLong(), buffer.readUUID());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, DynamicItemSlotSelection value) {
            buffer.writeVarLong(value.sequence());
            buffer.writeUUID(value.bindingId());
        }
    };

    public DynamicItemSlotSelection {
        if (sequence < 0) {
            throw new IllegalArgumentException("selection sequence must be non-negative: " + sequence);
        }
        if (bindingId == null) {
            throw new IllegalArgumentException("selection bindingId must not be null");
        }
    }
}

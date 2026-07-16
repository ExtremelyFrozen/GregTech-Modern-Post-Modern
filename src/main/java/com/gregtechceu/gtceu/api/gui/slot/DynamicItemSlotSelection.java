package com.gregtechceu.gtceu.api.gui.slot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Correlates a client page-selection request with the binding confirmed by the server.
 *
 * @param sequence  opening-local monotonically increasing request sequence
 * @param bindingId lifecycle binding requested for the visible interactive page, or empty for the overview
 */
public record DynamicItemSlotSelection(long sequence, Optional<UUID> bindingId) {

    /**
     * Network codec for selection requests and acknowledgements.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, DynamicItemSlotSelection> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public @NotNull DynamicItemSlotSelection decode(RegistryFriendlyByteBuf buffer) {
            long sequence = buffer.readVarLong();
            Optional<UUID> bindingId = buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty();
            return new DynamicItemSlotSelection(sequence, bindingId);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, DynamicItemSlotSelection value) {
            buffer.writeVarLong(value.sequence());
            buffer.writeBoolean(value.bindingId().isPresent());
            value.bindingId().ifPresent(buffer::writeUUID);
        }
    };

    public DynamicItemSlotSelection {
        if (sequence < 0) {
            throw new IllegalArgumentException("selection sequence must be non-negative: " + sequence);
        }
        if (bindingId == null) {
            throw new IllegalArgumentException("selection bindingId optional must not be null");
        }
    }
}

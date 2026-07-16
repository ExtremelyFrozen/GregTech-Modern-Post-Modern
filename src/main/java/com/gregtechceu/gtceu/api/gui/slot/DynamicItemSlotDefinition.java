package com.gregtechceu.gtceu.api.gui.slot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Describes the slot capacity requested by one logical target before a menu assigns stable slot ids.
 *
 * @param targetId  stable identity of the target that owns the slots
 * @param slotCount number of slots requested by the target
 */
public record DynamicItemSlotDefinition(UUID targetId, int slotCount) {

    /**
     * Maximum number of slots that one target may request.
     */
    public static final int MAX_SLOT_COUNT = 256;

    /**
     * Network codec for a bounded target slot definition.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, DynamicItemSlotDefinition> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public @NotNull DynamicItemSlotDefinition decode(RegistryFriendlyByteBuf buffer) {
            return new DynamicItemSlotDefinition(buffer.readUUID(), buffer.readVarInt());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, DynamicItemSlotDefinition value) {
            buffer.writeUUID(value.targetId());
            buffer.writeVarInt(value.slotCount());
        }
    };

    public DynamicItemSlotDefinition {
        if (targetId == null) {
            throw new IllegalArgumentException("targetId must not be null");
        }
        validateSlotCount(slotCount);
    }

    static void validateSlotCount(int slotCount) {
        if (slotCount < 1 || slotCount > MAX_SLOT_COUNT) {
            throw new IllegalArgumentException("slotCount must be between 1 and " + MAX_SLOT_COUNT + ": " + slotCount);
        }
    }
}

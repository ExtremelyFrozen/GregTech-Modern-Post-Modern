package com.gregtechceu.gtceu.api.gui.slot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Identifies one manifest transition inside one exact container-menu opening.
 *
 * @param containerId   Vanilla container id assigned to the opening
 * @param menuSessionId unpredictable identity assigned by the authoritative holder
 * @param epoch         strictly increasing manifest epoch
 * @param manifestNonce unpredictable identity of the manifest at that epoch
 */
public record DynamicItemSlotOpeningToken(int containerId, UUID menuSessionId, long epoch, UUID manifestNonce) {

    /** Network codec shared by every handshake and selection packet. */
    public static final StreamCodec<RegistryFriendlyByteBuf, DynamicItemSlotOpeningToken> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public @NotNull DynamicItemSlotOpeningToken decode(RegistryFriendlyByteBuf buffer) {
            return new DynamicItemSlotOpeningToken(
                    buffer.readVarInt(), buffer.readUUID(), buffer.readVarLong(), buffer.readUUID());
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, DynamicItemSlotOpeningToken value) {
            buffer.writeVarInt(value.containerId());
            buffer.writeUUID(value.menuSessionId());
            buffer.writeVarLong(value.epoch());
            buffer.writeUUID(value.manifestNonce());
        }
    };

    public DynamicItemSlotOpeningToken {
        if (containerId < 0) {
            throw new IllegalArgumentException("containerId must be non-negative: " + containerId);
        }
        if (menuSessionId == null) {
            throw new IllegalArgumentException("menuSessionId must not be null");
        }
        if (epoch < 1) {
            throw new IllegalArgumentException("epoch must be positive: " + epoch);
        }
        if (manifestNonce == null) {
            throw new IllegalArgumentException("manifestNonce must not be null");
        }
    }

    /** Creates the token carried by a manifest sent for this opening. */
    public static DynamicItemSlotOpeningToken of(int containerId, UUID menuSessionId,
                                                 DynamicItemSlotManifest manifest) {
        return new DynamicItemSlotOpeningToken(
                containerId, menuSessionId, manifest.epoch(), manifest.manifestNonce());
    }

    /** Returns whether the token names the supplied opening and manifest exactly. */
    public boolean matches(int expectedContainerId, UUID expectedMenuSessionId,
                           DynamicItemSlotManifest manifest) {
        return containerId == expectedContainerId && menuSessionId.equals(expectedMenuSessionId) &&
                epoch == manifest.epoch() && manifestNonce.equals(manifest.manifestNonce());
    }
}

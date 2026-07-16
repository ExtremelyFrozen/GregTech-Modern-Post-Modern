package com.gregtechceu.gtceu.api.gui.slot;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Declares one ordered, opening-scoped transition of stable dynamic menu slot ranges.
 *
 * <p>
 * Bindings include active lifecycles and tombstones. Their order is authoritative because it determines every
 * dynamic Vanilla slot id after the fixed {@code baseSlotCount} range.
 * </p>
 *
 * @param previousEpoch  epoch acknowledged before this transition
 * @param epoch          epoch introduced by this transition
 * @param manifestNonce  unique identity of this manifest transmission
 * @param sourceRevision authoritative source revision used to build the manifest
 * @param baseSlotCount  number of fixed menu slots preceding all dynamic ranges
 * @param bindings       immutable ordered lifecycle bindings
 */
public record DynamicItemSlotManifest(long previousEpoch, long epoch, UUID manifestNonce, long sourceRevision,
                                      int baseSlotCount, List<DynamicItemSlotBinding> bindings) {

    /**
     * Maximum number of lifecycle bindings retained by one opening.
     */
    public static final int MAX_BINDING_COUNT = 4_096;

    /**
     * Maximum total menu slot count supported by the protocol.
     */
    public static final int MAX_TOTAL_SLOT_COUNT = Short.MAX_VALUE;

    /**
     * Bounded codec that rejects hostile collection sizes before allocating the binding list.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, DynamicItemSlotManifest> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public @NotNull DynamicItemSlotManifest decode(RegistryFriendlyByteBuf buffer) {
            long previousEpoch = buffer.readVarLong();
            long epoch = buffer.readVarLong();
            UUID manifestNonce = buffer.readUUID();
            long sourceRevision = buffer.readVarLong();
            int baseSlotCount = buffer.readVarInt();
            int bindingCount = buffer.readVarInt();

            validateHeader(previousEpoch, epoch, manifestNonce, sourceRevision, baseSlotCount);
            validateBindingCount(bindingCount);

            List<DynamicItemSlotBinding> bindings = new ArrayList<>(bindingCount);
            for (int index = 0; index < bindingCount; index++) {
                bindings.add(DynamicItemSlotBinding.STREAM_CODEC.decode(buffer));
            }
            return new DynamicItemSlotManifest(
                    previousEpoch, epoch, manifestNonce, sourceRevision, baseSlotCount, bindings);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, DynamicItemSlotManifest value) {
            buffer.writeVarLong(value.previousEpoch());
            buffer.writeVarLong(value.epoch());
            buffer.writeUUID(value.manifestNonce());
            buffer.writeVarLong(value.sourceRevision());
            buffer.writeVarInt(value.baseSlotCount());
            buffer.writeVarInt(value.bindings().size());
            for (DynamicItemSlotBinding binding : value.bindings()) {
                DynamicItemSlotBinding.STREAM_CODEC.encode(buffer, binding);
            }
        }
    };

    public DynamicItemSlotManifest {
        validateHeader(previousEpoch, epoch, manifestNonce, sourceRevision, baseSlotCount);
        if (bindings == null) {
            throw new IllegalArgumentException("bindings must not be null");
        }
        validateBindingCount(bindings.size());

        Set<UUID> bindingIds = new HashSet<>(bindings.size());
        Set<UUID> presentTargetIds = new HashSet<>(bindings.size());
        int expectedFirstSlotId = baseSlotCount;
        for (DynamicItemSlotBinding binding : bindings) {
            if (binding == null) {
                throw new IllegalArgumentException("bindings must not contain null");
            }
            if (!bindingIds.add(binding.bindingId())) {
                throw new IllegalArgumentException("duplicate bindingId: " + binding.bindingId());
            }
            if (binding.present() && !presentTargetIds.add(binding.targetId())) {
                throw new IllegalArgumentException("duplicate present targetId: " + binding.targetId());
            }
            if (binding.firstSlotId() != expectedFirstSlotId) {
                throw new IllegalArgumentException(
                        "binding " + binding.bindingId() + " starts at " + binding.firstSlotId() +
                                " instead of the next slot id " + expectedFirstSlotId);
            }

            long nextSlotId = (long) expectedFirstSlotId + binding.slotCount();
            if (nextSlotId > MAX_TOTAL_SLOT_COUNT) {
                throw new IllegalArgumentException(
                        "base and dynamic slots exceed " + MAX_TOTAL_SLOT_COUNT + ": " + nextSlotId);
            }
            expectedFirstSlotId = (int) nextSlotId;
        }
        bindings = List.copyOf(bindings);
    }

    /**
     * Returns the fixed and dynamic slot count represented by this manifest.
     */
    public int totalSlotCount() {
        if (bindings.isEmpty()) {
            return baseSlotCount;
        }
        DynamicItemSlotBinding last = bindings.getLast();
        return last.firstSlotId() + last.slotCount();
    }

    private static void validateHeader(long previousEpoch, long epoch, UUID manifestNonce, long sourceRevision,
                                       int baseSlotCount) {
        if (previousEpoch < 0) {
            throw new IllegalArgumentException("previousEpoch must be non-negative: " + previousEpoch);
        }
        if (previousEpoch == Long.MAX_VALUE || epoch != previousEpoch + 1) {
            throw new IllegalArgumentException(
                    "epoch must immediately follow previousEpoch: " + previousEpoch + " -> " + epoch);
        }
        if (manifestNonce == null) {
            throw new IllegalArgumentException("manifestNonce must not be null");
        }
        if (sourceRevision < 0) {
            throw new IllegalArgumentException("sourceRevision must be non-negative: " + sourceRevision);
        }
        if (baseSlotCount < 0 || baseSlotCount > MAX_TOTAL_SLOT_COUNT) {
            throw new IllegalArgumentException(
                    "baseSlotCount must be between 0 and " + MAX_TOTAL_SLOT_COUNT + ": " + baseSlotCount);
        }
    }

    private static void validateBindingCount(int bindingCount) {
        if (bindingCount < 0 || bindingCount > MAX_BINDING_COUNT) {
            throw new IllegalArgumentException(
                    "binding count must be between 0 and " + MAX_BINDING_COUNT + ": " + bindingCount);
        }
    }
}

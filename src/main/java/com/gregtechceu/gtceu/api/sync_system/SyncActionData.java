package com.gregtechceu.gtceu.api.sync_system;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * Network data for one client-requested sync action.
 *
 * @param actionId the registered server action identifier.
 * @param sequence the client sequence number used to order or correlate action requests.
 * @param payload  the typed action arguments encoded with the existing data component sync format.
 */
public record SyncActionData(ResourceLocation actionId, int sequence, DataComponentMap payload) {

    /**
     * Encodes action data with the same registry-aware buffer style as field sync packets.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncActionData> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public SyncActionData decode(RegistryFriendlyByteBuf buffer) {
            ResourceLocation actionId = ResourceLocation.STREAM_CODEC.decode(buffer);
            int sequence = buffer.readVarInt();
            DataComponentMap payload = SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.decode(buffer);
            return new SyncActionData(actionId, sequence, payload);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, SyncActionData value) {
            ResourceLocation.STREAM_CODEC.encode(buffer, value.actionId);
            buffer.writeVarInt(value.sequence);
            SyncFieldData.DATA_COMPONENT_MAP_STREAM_CODEC.encode(buffer, value.payload);
        }
    };
}

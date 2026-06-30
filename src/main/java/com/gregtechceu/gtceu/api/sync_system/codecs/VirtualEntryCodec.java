package com.gregtechceu.gtceu.api.sync_system.codecs;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;

import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

public final class VirtualEntryCodec implements ContextualFieldCodec<VirtualEntry> {

    public static final Class<VirtualEntry> TYPE = VirtualEntry.class;
    public static final VirtualEntryCodec INSTANCE = new VirtualEntryCodec();

    private static final String TYPE_KEY = "type";
    private static final String DATA_KEY = "data";

    private VirtualEntryCodec() {}

    @Override
    public Tag serializeNBT(VirtualEntry value, Context<VirtualEntry> context) {
        throw unsupportedNbt(context.fieldName());
    }

    @Override
    public JsonElement serializeField(VirtualEntry value, Context<VirtualEntry> context) {
        JsonObject json = new JsonObject();
        json.addProperty(TYPE_KEY, value.getType().getId().toString());
        json.add(DATA_KEY, value.serializeJson(context.lookup()));
        return json;
    }

    @Override
    public @Nullable VirtualEntry deserializeNBT(Tag tag, Context<VirtualEntry> context) {
        throw unsupportedNbt(context.fieldName());
    }

    @Override
    public @Nullable VirtualEntry deserializeField(JsonElement value, Context<VirtualEntry> context) {
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException("Sync: virtual entry field " + context.fieldName() +
                    " must be encoded as an object");
        }

        JsonObject json = value.getAsJsonObject();
        ResourceLocation typeId = ResourceLocation.parse(json.get(TYPE_KEY).getAsString());
        EntryTypes<? extends VirtualEntry> type = EntryTypes.fromString(typeId.toString());
        if (type == null) {
            GTCEu.LOGGER.error("Sync: unknown virtual entry type {} for field {}", typeId, context.fieldName());
            return null;
        }

        VirtualEntry entry = context.currentValue();
        if (entry == null || entry.getType() != type) {
            entry = type.createInstance();
        }
        entry.deserializeJson(context.lookup(), json.get(DATA_KEY));
        return entry;
    }

    private static UnsupportedOperationException unsupportedNbt(String fieldName) {
        String message = "Sync: field %s uses VirtualEntry and must be serialized as DataComponentMap"
                .formatted(fieldName);
        GTCEu.LOGGER.error(message);
        return new UnsupportedOperationException(message);
    }
}

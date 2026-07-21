package com.gregtechceu.gtceu.api.sync_system.codecs;

import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;
import com.gregtechceu.gtceu.api.transfer.DataComponentTransfer;

import net.minecraft.core.component.DataComponentMap;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

public final class DataComponentTransferCodec implements ContextualFieldCodec<DataComponentTransfer> {

    public static final Class<DataComponentTransfer> TYPE = DataComponentTransfer.class;
    public static final DataComponentTransferCodec INSTANCE = new DataComponentTransferCodec();

    private DataComponentTransferCodec() {}

    @Override
    public JsonElement serializeField(DataComponentTransfer value, Context<DataComponentTransfer> context) {
        return DataComponentMap.CODEC
                .encodeStart(context.lookup().createSerializationContext(JsonOps.INSTANCE), value.exportComponents())
                .getOrThrow();
    }

    @Override
    public DataComponentTransfer deserializeField(JsonElement value, Context<DataComponentTransfer> context) {
        DataComponentTransfer transfer = context.currentValue();
        if (transfer == null) {
            throw new IllegalArgumentException("Sync: data component transfer field " + context.fieldName() +
                    " requires an existing field instance");
        }
        DataComponentMap components = DataComponentMap.CODEC
                .parse(context.lookup().createSerializationContext(JsonOps.INSTANCE), value)
                .getOrThrow();
        transfer.importComponents(components);
        return transfer;
    }
}

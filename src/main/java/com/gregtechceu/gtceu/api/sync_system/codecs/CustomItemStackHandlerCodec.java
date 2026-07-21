package com.gregtechceu.gtceu.api.sync_system.codecs;

import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.datacomponents.TransferData;

import net.minecraft.core.component.DataComponentMap;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

public final class CustomItemStackHandlerCodec implements ContextualFieldCodec<CustomItemStackHandler> {

    public static final Class<CustomItemStackHandler> TYPE = CustomItemStackHandler.class;
    public static final CustomItemStackHandlerCodec INSTANCE = new CustomItemStackHandlerCodec();

    private CustomItemStackHandlerCodec() {}

    @Override
    public JsonElement serializeField(CustomItemStackHandler value, Context<CustomItemStackHandler> context) {
        return DataComponentMap.CODEC
                .encodeStart(context.lookup().createSerializationContext(JsonOps.INSTANCE), value.exportComponents())
                .getOrThrow();
    }

    @Override
    public CustomItemStackHandler deserializeField(JsonElement value, Context<CustomItemStackHandler> context) {
        DataComponentMap components = DataComponentMap.CODEC
                .parse(context.lookup().createSerializationContext(JsonOps.INSTANCE), value)
                .getOrThrow();
        TransferData.ItemHandler data = components.get(GTDataComponents.TRANSFER_ITEM_HANDLER.get());
        if (data == null) {
            throw new IllegalArgumentException("Sync: item handler field " + context.fieldName() +
                    " is missing transfer_item_handler");
        }
        CustomItemStackHandler handler = context.currentValue();
        if (handler == null) {
            handler = new CustomItemStackHandler(data.slots());
        }

        handler.importComponents(components);
        return handler;
    }
}

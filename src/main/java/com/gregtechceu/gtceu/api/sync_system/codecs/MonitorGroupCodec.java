package com.gregtechceu.gtceu.api.sync_system.codecs;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.Nullable;

public final class MonitorGroupCodec implements ContextualFieldCodec<MonitorGroup> {

    public static final Class<MonitorGroup> TYPE = MonitorGroup.class;
    public static final MonitorGroupCodec INSTANCE = new MonitorGroupCodec();

    private MonitorGroupCodec() {}

    @Override
    public Tag serializeNBT(MonitorGroup value, Context<MonitorGroup> context) {
        throw unsupportedNbt(context.fieldName());
    }

    @Override
    public JsonElement serializeField(MonitorGroup value, Context<MonitorGroup> context) {
        JsonObject json = new JsonObject();
        json.addProperty("name", value.getName());

        JsonArray positions = new JsonArray();
        value.getMonitorPositions().forEach(pos -> positions.add(BlockPos.CODEC
                .encodeStart(context.lookup().createSerializationContext(JsonOps.INSTANCE), pos)
                .getOrThrow()));
        json.add("positions", positions);

        if (value.getTargetRaw() != null) {
            json.add("targetPos", BlockPos.CODEC
                    .encodeStart(context.lookup().createSerializationContext(JsonOps.INSTANCE), value.getTargetRaw())
                    .getOrThrow());
            if (value.getTargetCoverSide() != null) {
                json.add("targetSide", Direction.CODEC
                        .encodeStart(JsonOps.INSTANCE, value.getTargetCoverSide())
                        .getOrThrow());
            }
        }

        json.addProperty("dataSlot", value.getDataSlot());
        json.add("items", serializeItems(value.getItemStackHandler(), context));
        json.add("placeholderSlots", serializeItems(value.getPlaceholderSlotsHandler(), context));
        return json;
    }

    @Override
    public @Nullable MonitorGroup deserializeNBT(Tag tag, Context<MonitorGroup> context) {
        throw unsupportedNbt(context.fieldName());
    }

    @Override
    public @Nullable MonitorGroup deserializeField(JsonElement value, Context<MonitorGroup> context) {
        if (!value.isJsonObject()) return null;

        JsonObject json = value.getAsJsonObject();
        CustomItemStackHandler handler = deserializeItems(json.getAsJsonArray("items"), context,
                MonitorGroup.createModuleHandler());
        CustomItemStackHandler placeholderSlotsHandler = deserializeItems(json.getAsJsonArray("placeholderSlots"),
                context, new CustomItemStackHandler(8));
        var group = new MonitorGroup(json.get("name").getAsString(), handler, placeholderSlotsHandler);

        JsonArray positions = json.getAsJsonArray("positions");
        for (JsonElement position : positions) {
            group.add(BlockPos.CODEC.parse(context.lookup().createSerializationContext(JsonOps.INSTANCE), position)
                    .getOrThrow());
        }

        if (json.has("targetPos")) {
            group.setTarget(BlockPos.CODEC
                    .parse(context.lookup().createSerializationContext(JsonOps.INSTANCE), json.get("targetPos"))
                    .getOrThrow());
            if (json.has("targetSide")) {
                group.setTargetCoverSide(Direction.CODEC.parse(JsonOps.INSTANCE, json.get("targetSide")).getOrThrow());
            }
            if (json.has("dataSlot")) {
                group.setDataSlot(json.get("dataSlot").getAsInt());
            }
        }
        return group;
    }

    private static JsonArray serializeItems(CustomItemStackHandler handler, Context<MonitorGroup> context) {
        JsonArray json = new JsonArray();
        for (ItemStack stack : handler.getStacks()) {
            json.add(ItemStack.OPTIONAL_CODEC
                    .encodeStart(context.lookup().createSerializationContext(JsonOps.INSTANCE), stack)
                    .getOrThrow());
        }
        return json;
    }

    private static CustomItemStackHandler deserializeItems(@Nullable JsonArray json,
                                                           Context<MonitorGroup> context,
                                                           CustomItemStackHandler handler) {
        if (json == null) {
            return handler;
        }

        int size = Math.min(json.size(), handler.getSlots());
        for (int i = 0; i < size; i++) {
            ItemStack stack = ItemStack.OPTIONAL_CODEC
                    .parse(context.lookup().createSerializationContext(JsonOps.INSTANCE), json.get(i))
                    .getOrThrow();
            handler.setStackInSlot(i, stack);
        }
        return handler;
    }

    private static UnsupportedOperationException unsupportedNbt(String fieldName) {
        String message = "Sync: field %s uses MonitorGroup and must be serialized as DataComponentMap"
                .formatted(fieldName);
        GTCEu.LOGGER.error(message);
        return new UnsupportedOperationException(message);
    }
}

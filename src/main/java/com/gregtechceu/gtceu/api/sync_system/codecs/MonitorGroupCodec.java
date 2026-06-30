package com.gregtechceu.gtceu.api.sync_system.codecs;

import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;

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
    public @Nullable MonitorGroup deserializeField(JsonElement value, Context<MonitorGroup> context) {
        if (!value.isJsonObject()) return null;

        JsonObject json = value.getAsJsonObject();
        CustomItemStackHandler handler = deserializeItems(json.get("items"), context,
                MonitorGroup.createModuleHandler());
        CustomItemStackHandler placeholderSlotsHandler = deserializeItems(json.get("placeholderSlots"),
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

    private static JsonElement serializeItems(CustomItemStackHandler handler, Context<MonitorGroup> context) {
        return DataComponentMap.CODEC
                .encodeStart(context.lookup().createSerializationContext(JsonOps.INSTANCE), handler.exportComponents())
                .getOrThrow();
    }

    private static CustomItemStackHandler deserializeItems(@Nullable JsonElement json,
                                                           Context<MonitorGroup> context,
                                                           CustomItemStackHandler handler) {
        if (json == null) {
            throw new IllegalArgumentException("Sync: monitor group is missing item handler data");
        }
        DataComponentMap components = DataComponentMap.CODEC
                .parse(context.lookup().createSerializationContext(JsonOps.INSTANCE), json)
                .getOrThrow();
        handler.importComponents(components);
        return handler;
    }
}

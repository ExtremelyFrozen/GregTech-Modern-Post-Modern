package com.gregtechceu.gtceu.api.sync_system.codecs;

import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
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
    public CompoundTag serializeNBT(MonitorGroup value, Context<MonitorGroup> context) {
        CompoundTag tag = new CompoundTag();
        tag.putString("name", value.getName());
        ListTag list = new ListTag();
        value.getMonitorPositions().forEach(pos -> list.add(NbtUtils.writeBlockPos(pos)));
        if (value.getTargetRaw() != null) {
            tag.put("targetPos", NbtUtils.writeBlockPos(value.getTargetRaw()));
            if (value.getTargetCoverSide() != null) {
                tag.putString("targetSide", value.getTargetCoverSide().getSerializedName());
            }
        }
        tag.put("positions", list);
        tag.putInt("dataSlot", value.getDataSlot());
        tag.put("items", value.getItemStackHandler().serializeNBT(context.lookup()));
        tag.put("placeholderSlots", value.getPlaceholderSlotsHandler().serializeNBT(context.lookup()));
        return tag;
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
        if (!(tag instanceof CompoundTag compoundTag)) return null;
        CustomItemStackHandler handler = new CustomItemStackHandler();
        CustomItemStackHandler placeholderSlotsHandler = new CustomItemStackHandler();
        handler.deserializeNBT(context.lookup(), compoundTag.getCompound("items"));
        placeholderSlotsHandler.deserializeNBT(context.lookup(), compoundTag.getCompound("placeholderSlots"));
        var group = new MonitorGroup(compoundTag.getString("name"), handler, placeholderSlotsHandler);
        ListTag list = compoundTag.getList("positions", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            int[] aint = list.getIntArray(i);
            if (aint.length != 3) continue;
            group.add(new BlockPos(aint[0], aint[1], aint[2]));
        }
        if (compoundTag.contains("targetPos", Tag.TAG_COMPOUND)) {
            group.setTarget(NbtUtils.readBlockPos(compoundTag, "targetPos").orElse(BlockPos.ZERO));
            if (compoundTag.contains("targetSide", Tag.TAG_STRING)) {
                group.setTargetCoverSide(Direction.byName(compoundTag.getString("targetSide")));
            }
            if (compoundTag.contains("dataSlot", Tag.TAG_INT)) {
                group.setDataSlot(compoundTag.getInt("dataSlot"));
            }
        }
        return group;
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
}

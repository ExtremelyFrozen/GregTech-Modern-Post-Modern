package com.gregtechceu.gtceu.api.sync_system.codecs;

import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.Nullable;

public final class CustomItemStackHandlerCodec implements ContextualFieldCodec<CustomItemStackHandler> {

    public static final Class<CustomItemStackHandler> TYPE = CustomItemStackHandler.class;
    public static final CustomItemStackHandlerCodec INSTANCE = new CustomItemStackHandlerCodec();

    private static final String SLOTS = "slots";
    private static final String STACKS = "stacks";

    private CustomItemStackHandlerCodec() {}

    @Override
    public Tag serializeNBT(CustomItemStackHandler value, Context<CustomItemStackHandler> context) {
        return JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, serializeField(value, context));
    }

    @Override
    public JsonElement serializeField(CustomItemStackHandler value, Context<CustomItemStackHandler> context) {
        NonNullList<ItemStack> stacks = value.getStacks();
        JsonObject json = new JsonObject();
        json.addProperty(SLOTS, stacks.size());

        JsonArray encodedStacks = new JsonArray(stacks.size());
        for (ItemStack stack : stacks) {
            encodedStacks.add(ItemStack.OPTIONAL_CODEC
                    .encodeStart(context.lookup().createSerializationContext(JsonOps.INSTANCE), stack)
                    .getOrThrow());
        }
        json.add(STACKS, encodedStacks);
        return json;
    }

    @Override
    public @Nullable CustomItemStackHandler deserializeNBT(Tag tag, Context<CustomItemStackHandler> context) {
        return deserializeField(NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag), context);
    }

    @Override
    public CustomItemStackHandler deserializeField(JsonElement value, Context<CustomItemStackHandler> context) {
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException("Sync: item handler field " + context.fieldName() +
                    " must be encoded as an object");
        }

        JsonObject json = value.getAsJsonObject();
        int slots = json.get(SLOTS).getAsInt();
        JsonArray encodedStacks = json.getAsJsonArray(STACKS);
        if (encodedStacks.size() != slots) {
            throw new IllegalArgumentException("Sync: item handler field " + context.fieldName() +
                    " encoded " + encodedStacks.size() + " stacks for " + slots + " slots");
        }

        CustomItemStackHandler handler = context.currentValue();
        if (handler == null) {
            handler = new CustomItemStackHandler(slots);
        } else if (handler.getStacks().size() != slots) {
            throw new IllegalArgumentException("Sync: item handler field " + context.fieldName() +
                    " expected " + handler.getStacks().size() + " slots but received " + slots);
        }

        NonNullList<ItemStack> stacks = handler.getStacks();
        for (int i = 0; i < slots; i++) {
            stacks.set(i, ItemStack.OPTIONAL_CODEC
                    .parse(context.lookup().createSerializationContext(JsonOps.INSTANCE), encodedStacks.get(i))
                    .getOrThrow());
        }
        handler.getOnContentsChanged().run();
        return handler;
    }
}

package com.gregtechceu.gtceu.api.misc.virtualregistry.entries;

import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class VirtualItemStorage extends VirtualEntry {

    protected static final int DEFAULT_SLOT_AMOUNT = 1;

    @NotNull
    @Getter
    private final CustomItemStackHandler handler;

    protected static final String ITEM_KEY = "items";

    public VirtualItemStorage() {
        this(DEFAULT_SLOT_AMOUNT);
    }

    public VirtualItemStorage(int slots) {
        handler = new CustomItemStackHandler(slots);
    }

    @Override
    public EntryTypes<? extends VirtualEntry> getType() {
        return EntryTypes.ENDER_ITEM;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VirtualItemStorage other)) return false;
        return other.handler == this.handler;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = VirtualItemStorage.super.serializeNBT(provider);
        tag.put(ITEM_KEY, handler.serializeNBT(provider));
        return tag;
    }

    @Override
    public JsonElement serializeJson(HolderLookup.Provider provider) {
        JsonObject json = super.serializeJson(provider).getAsJsonObject();
        JsonArray items = new JsonArray();
        for (ItemStack stack : handler.getStacks()) {
            items.add(encodeJson(provider, ItemStack.OPTIONAL_CODEC, stack));
        }
        json.add(ITEM_KEY, items);
        return json;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag nbt) {
        super.deserializeNBT(provider, nbt);
        handler.deserializeNBT(provider, nbt.getCompound(ITEM_KEY));
    }

    @Override
    public void deserializeJson(HolderLookup.Provider provider, JsonElement data) {
        super.deserializeJson(provider, data);
        JsonObject json = data.getAsJsonObject();
        if (!json.has(ITEM_KEY)) {
            return;
        }
        JsonArray items = json.getAsJsonArray(ITEM_KEY);
        int size = Math.min(items.size(), handler.getSlots());
        for (int i = 0; i < size; i++) {
            handler.setStackInSlot(i, decodeJson(provider, ItemStack.OPTIONAL_CODEC, items.get(i)));
        }
    }

    @Override
    public boolean canRemove() {
        return super.canRemove() && isEmpty();
    }

    public boolean isEmpty() {
        for (int i = 0; i < handler.getSlots(); i++) {
            if (!handler.getStackInSlot(i).isEmpty()) return false;
        }
        return true;
    }
}

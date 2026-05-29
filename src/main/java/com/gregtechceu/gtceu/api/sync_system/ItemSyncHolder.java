package com.gregtechceu.gtceu.api.sync_system;

import com.gregtechceu.gtceu.common.data.item.GTDataComponents;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import lombok.Getter;

public final class ItemSyncHolder implements ISyncManaged {

    @Getter
    private final SyncDataHolder syncDataHolder;

    public ItemSyncHolder(ISyncManaged owner) {
        this.syncDataHolder = new SyncDataHolder(owner);
    }

    public void saveToStack(ItemStack stack, HolderLookup.Provider registries) {
        CompoundTag data = syncDataHolder.serializeToItemNBT(registries);
        if (!data.isEmpty()) {
            stack.set(GTDataComponents.BLOCK_ITEM_DATA, data);
        }
    }

    public void loadFromStack(ItemStack stack, HolderLookup.Provider registries, boolean clientSide) {
        CompoundTag data = stack.get(GTDataComponents.BLOCK_ITEM_DATA);
        if (data == null || data.isEmpty()) {
            return;
        }

        syncDataHolder.deserializeItemNBT(registries, data);
        if (clientSide) {
            syncDataHolder.deserializeNBT(registries, data, true);
        }
    }

    public boolean scanChanges(HolderLookup.Provider registries) {
        return syncDataHolder.scanAndMarkChanges(registries);
    }

    public void flushToStack(ItemStack stack) {
        CompoundTag pending = syncDataHolder.getPendingChanges();
        if (pending.isEmpty()) {
            return;
        }

        CompoundTag existing = stack.get(GTDataComponents.BLOCK_ITEM_DATA);
        stack.set(GTDataComponents.BLOCK_ITEM_DATA, existing != null ? existing.merge(pending) : pending);
    }

    public void applyServerUpdate(HolderLookup.Provider registries, CompoundTag tag) {
        syncDataHolder.applyServerUpdate(registries, tag);
    }

    @Override
    public @org.jetbrains.annotations.Nullable ISyncManaged getParentSyncObject() {
        return null;
    }

    @Override
    public void scheduleRenderUpdate() {}

    @Override
    public void markAsChanged() {}
}

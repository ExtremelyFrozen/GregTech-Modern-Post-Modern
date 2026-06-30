package com.gregtechceu.gtceu.api.misc.virtualregistry.entries;

import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.datacomponents.VirtualEntryData;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;

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
    public DataComponentMap exportComponents(HolderLookup.Provider provider) {
        return putBaseComponent(DataComponentMap.builder())
                .set(GTDataComponents.VIRTUAL_ITEM_STORAGE.get(), new VirtualEntryData.Items(handler.getStacks()))
                .build();
    }

    @Override
    public void importComponents(HolderLookup.Provider provider, DataComponentMap components) {
        super.importComponents(provider, components);
        VirtualEntryData.Items data = components.get(GTDataComponents.VIRTUAL_ITEM_STORAGE.get());
        if (data == null) {
            throw new IllegalArgumentException("Virtual item storage entry is missing item data component");
        }
        int size = Math.min(data.stacks().size(), handler.getSlots());
        for (int i = 0; i < size; i++) {
            handler.setStackInSlot(i, data.stacks().get(i));
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

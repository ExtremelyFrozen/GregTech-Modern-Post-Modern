package com.gregtechceu.gtceu.api.misc.virtualregistry.entries;

import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.datacomponents.VirtualEntryData;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;

import it.unimi.dsi.fastutil.objects.Object2ShortMap;
import it.unimi.dsi.fastutil.objects.Object2ShortOpenHashMap;
import lombok.Getter;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class VirtualRedstone extends VirtualEntry {

    @Getter
    private final Object2ShortMap<UUID> members = new Object2ShortOpenHashMap<>();

    public VirtualRedstone() {}

    public int getSignal() {
        return members.values().intStream().max().orElse(0);
    }

    public void addMember(UUID uuid) {
        members.put(uuid, (short) 0);
    }

    public void setSignal(UUID uuid, int signal) {
        if (!members.containsKey(uuid)) return;
        members.put(uuid, (short) signal);
    }

    public void removeMember(UUID uuid) {
        members.removeShort(uuid);
    }

    @Override
    public EntryTypes<? extends VirtualEntry> getType() {
        return EntryTypes.ENDER_REDSTONE;
    }

    @Override
    public DataComponentMap exportComponents(HolderLookup.@NonNull Provider provider) {
        List<VirtualEntryData.Member> data = new ArrayList<>(members.size());
        for (var entry : members.object2ShortEntrySet()) {
            data.add(new VirtualEntryData.Member(entry.getKey(), entry.getShortValue()));
        }
        return putBaseComponent(DataComponentMap.builder())
                .set(GTDataComponents.VIRTUAL_REDSTONE.get(), new VirtualEntryData.Redstone(data))
                .build();
    }

    @Override
    public void importComponents(HolderLookup.Provider provider, DataComponentMap components) {
        super.importComponents(provider, components);
        VirtualEntryData.Redstone data = components.getOrDefault(GTDataComponents.VIRTUAL_REDSTONE.get(),
                VirtualEntryData.Redstone.EMPTY);
        members.clear();
        for (VirtualEntryData.Member member : data.members()) {
            members.put(member.id(), member.signal());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VirtualRedstone other)) return false;
        return other.members == this.members;
    }

    @Override
    public boolean canRemove() {
        return super.canRemove() && members.isEmpty();
    }
}

package com.gregtechceu.gtceu.api.sync_system.codecs;

import com.gregtechceu.gtceu.api.machine.trait.MachineTraitHolder;
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Objects;

public final class MachineTraitHolderCodec implements ContextualFieldCodec<MachineTraitHolder> {

    public static final Class<MachineTraitHolder> TYPE = MachineTraitHolder.class;
    public static final MachineTraitHolderCodec INSTANCE = new MachineTraitHolderCodec();

    private MachineTraitHolderCodec() {}

    @Override
    public Tag serializeNBT(MachineTraitHolder value, Context<MachineTraitHolder> context) {
        return value.serializeSyncData(context.lookup(), context.isClientSync(), context.isClientFullSyncUpdate());
    }

    @Override
    public boolean shouldSyncField(MachineTraitHolder value, Context<MachineTraitHolder> context, boolean fullSync,
                                   boolean manuallyDirty) {
        if (!context.isClientSync()) return fullSync || manuallyDirty;
        return value.scanAndMarkClientChanges(context.lookup(), fullSync || manuallyDirty);
    }

    @Override
    public MachineTraitHolder deserializeNBT(Tag tag, Context<MachineTraitHolder> context) {
        MachineTraitHolder holder = Objects.requireNonNull(context.currentValue());
        holder.deserializeSyncData(context.lookup(), (CompoundTag) tag, context.isClientSync());
        return holder;
    }
}

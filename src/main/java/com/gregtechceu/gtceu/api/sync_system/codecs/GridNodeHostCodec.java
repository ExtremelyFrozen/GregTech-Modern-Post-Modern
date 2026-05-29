package com.gregtechceu.gtceu.api.sync_system.codecs;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHostTrait;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import org.jetbrains.annotations.Nullable;

public final class GridNodeHostCodec implements ContextualFieldCodec<Object> {

    public static final GridNodeHostCodec INSTANCE = new GridNodeHostCodec();

    private GridNodeHostCodec() {}

    @Override
    public Tag serializeNBT(Object value, Context<Object> context) {
        if (GTCEu.Mods.isAE2Loaded() && context.currentValue() instanceof GridNodeHostTrait connectedBlockEntity) {
            var compound = new CompoundTag();
            connectedBlockEntity.getMainNode().saveToNBT(compound);
            return compound;
        }
        return new CompoundTag();
    }

    @Override
    public @Nullable Object deserializeNBT(Tag tag, Context<Object> context) {
        if (GTCEu.Mods.isAE2Loaded() &&
                context.currentValue() instanceof GridNodeHostTrait connectedBlockEntity &&
                tag instanceof CompoundTag compoundTag) {
            connectedBlockEntity.getMainNode().loadFromNBT(compoundTag);
            return context.currentValue();
        }
        return null;
    }
}

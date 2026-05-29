package com.gregtechceu.gtceu.api.sync_system.codecs;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

public final class CoverBehaviorCodec implements ContextualFieldCodec<CoverBehavior> {

    public static final Class<CoverBehavior> TYPE = CoverBehavior.class;
    public static final CoverBehaviorCodec INSTANCE = new CoverBehaviorCodec();

    private CoverBehaviorCodec() {}

    @Override
    public Tag serializeNBT(@Nullable CoverBehavior value, Context<CoverBehavior> context) {
        if (value != null) {
            return serialize(value, context.isClientSync(), context.isClientFullSyncUpdate(), context.lookup());
        }
        return new CompoundTag();
    }

    @Override
    public @Nullable CoverBehavior deserializeNBT(Tag tag, Context<CoverBehavior> context) {
        if (tag instanceof CompoundTag compoundTag && context.holder() instanceof ICoverable coverable) {
            return deserialize(compoundTag, coverable, context.currentValue(), context.isClientSync(),
                    context.lookup());
        }
        GTCEu.LOGGER.error("Sync: Object attempting to sync cover does not implement ICoverable {}", context);
        return null;
    }

    private static CompoundTag serialize(CoverBehavior cover, boolean isSync, boolean fullSync,
                                         HolderLookup.Provider lookup) {
        var compound = new CompoundTag();
        compound.putInt("side", cover.attachedSide.ordinal());
        compound.putString("coverType", cover.coverDefinition.getId().toString());
        compound.put("data", cover.getSyncDataHolder().serializeNBT(lookup, isSync, fullSync));
        return compound;
    }

    public static @Nullable CoverBehavior deserialize(CompoundTag tag, ICoverable holder, @Nullable CoverBehavior cover,
                                                      boolean isSync, HolderLookup.Provider lookup) {
        if (tag.contains("payload") && tag.contains("uid")) {
            tag.putInt("side", tag.getCompound("uid").getInt("side"));
            tag.putString("coverType", tag.getCompound("uid").getString("id"));
            tag.put("data", tag.getCompound("payload").getCompound("d"));
        }

        Direction side = Direction.values()[tag.getInt("side")];

        if (tag.isEmpty() || tag.getString("coverType").isEmpty()) {
            holder.setCoverAtSide(null, side);
            return null;
        }
        ResourceLocation coverType = ResourceLocation.tryParse(tag.getString("coverType"));
        if (cover == null || !cover.coverDefinition.getId().equals(coverType)) {
            var coverReg = GTRegistries.COVERS.get(coverType);
            if (coverReg == null) {
                GTCEu.LOGGER.error("Error during NBT load: unknown cover type {} ({})", coverType,
                        tag.getString("coverType"));
                return null;
            }
            holder.setCoverAtSide(coverReg.createCoverBehavior(holder, side), side);
        }

        CoverBehavior newCover = holder.getCoverAtSide(side);
        if (newCover == null) return null;
        newCover.getSyncDataHolder().deserializeNBT(lookup, tag.getCompound("data"), isSync);

        if (!isSync && newCover.getAttachItem() == ItemStack.EMPTY) {
            GTCEu.LOGGER.error("Invalid cover save state, this should never happen unless loading corrupted data.");
            holder.setCoverAtSide(null, side);
        }

        return newCover;
    }
}

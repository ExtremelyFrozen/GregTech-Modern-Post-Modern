package com.gregtechceu.gtceu.api.sync_system.codecs;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.jetbrains.annotations.Nullable;

public final class CoverBehaviorCodec implements ContextualFieldCodec<CoverBehavior> {

    public static final Class<CoverBehavior> TYPE = CoverBehavior.class;
    public static final CoverBehaviorCodec INSTANCE = new CoverBehaviorCodec();

    private CoverBehaviorCodec() {}

    @Override
    public Tag serializeNBT(@Nullable CoverBehavior value, Context<CoverBehavior> context) {
        if (value == null) {
            var nullTag = new CompoundTag();
            nullTag.putBoolean("null", true);
            return nullTag;
        }

        return serialize(value, context.isClientSync(), context.isClientFullSyncUpdate(), context.lookup());
    }

    @Override
    public JsonElement serializeField(@Nullable CoverBehavior value, Context<CoverBehavior> context) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }

        JsonObject json = new JsonObject();
        json.addProperty("side", value.attachedSide.ordinal());
        json.addProperty("coverType", value.coverDefinition.getId().toString());
        json.add("data", DataComponentMap.CODEC
                .encodeStart(context.lookup().createSerializationContext(JsonOps.INSTANCE),
                        value.getSyncDataHolder().serializeToComponents(context.lookup(), context.isClientSync(),
                                context.isClientFullSyncUpdate()))
                .getOrThrow());
        return json;
    }

    @Override
    public @Nullable CoverBehavior deserializeNBT(Tag tag, Context<CoverBehavior> context) {
        if (tag instanceof CompoundTag compoundTag) {
            if (compoundTag.getBoolean("null")) {
                return null;
            }
            if (context.holder() instanceof ICoverable coverable) {
                return deserialize(compoundTag, coverable, context.currentValue(), context.isClientSync(),
                        context.lookup());
            }
        }
        GTCEu.LOGGER.error("Sync: Object attempting to sync cover does not implement ICoverable {}", context);
        return null;
    }

    @Override
    public @Nullable CoverBehavior deserializeField(JsonElement value, Context<CoverBehavior> context) {
        if (value.isJsonNull()) {
            return null;
        }
        if (!value.isJsonObject() || !(context.holder() instanceof ICoverable coverable)) {
            GTCEu.LOGGER.error("Sync: Object attempting to sync cover does not implement ICoverable {}", context);
            return null;
        }

        JsonObject json = value.getAsJsonObject();
        Direction side = Direction.values()[json.get("side").getAsInt()];
        String coverTypeName = json.get("coverType").getAsString();
        if (coverTypeName.isEmpty()) {
            coverable.setCoverAtSide(null, side);
            return null;
        }

        ResourceLocation coverType = ResourceLocation.parse(coverTypeName);
        CoverBehavior cover = context.currentValue();
        if (cover == null || !cover.coverDefinition.getId().equals(coverType)) {
            var coverReg = GTRegistries.COVERS.get(coverType);
            if (coverReg == null) {
                GTCEu.LOGGER.error("Error during component load: unknown cover type {}", coverType);
                return null;
            }
            coverable.setCoverAtSide(coverReg.createCoverBehavior(coverable, side), side);
        }

        CoverBehavior newCover = coverable.getCoverAtSide(side);
        if (newCover == null) return null;
        DataComponentMap components = DataComponentMap.CODEC
                .parse(context.lookup().createSerializationContext(JsonOps.INSTANCE), json.get("data"))
                .getOrThrow();
        newCover.getSyncDataHolder().deserializeComponents(context.lookup(), components, context.isClientSync());
        return newCover;
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

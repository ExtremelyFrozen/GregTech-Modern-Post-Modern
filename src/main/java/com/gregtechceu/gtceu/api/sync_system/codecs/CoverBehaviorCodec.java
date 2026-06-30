package com.gregtechceu.gtceu.api.sync_system.codecs;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceLocation;

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
}

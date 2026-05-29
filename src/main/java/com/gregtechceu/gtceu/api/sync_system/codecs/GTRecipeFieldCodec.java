package com.gregtechceu.gtceu.api.sync_system.codecs;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class GTRecipeFieldCodec implements ContextualFieldCodec<GTRecipe> {

    public static final Class<GTRecipe> TYPE = GTRecipe.class;
    public static final GTRecipeFieldCodec INSTANCE = new GTRecipeFieldCodec();

    private GTRecipeFieldCodec() {}

    @Override
    public Tag serializeNBT(GTRecipe value, Context<GTRecipe> context) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", value.id.toString());
        tag.put("recipe",
                GTRecipeSerializer.CODEC.encode(value, NbtOps.INSTANCE, NbtOps.INSTANCE.mapBuilder())
                        .build(new CompoundTag()).result().orElse(new CompoundTag()));
        tag.putInt("parallels", value.parallels);
        tag.putInt("ocLevel", value.ocLevel);
        return tag;
    }

    @Override
    public @Nullable GTRecipe deserializeNBT(Tag tag, Context<GTRecipe> context) {
        if (tag instanceof CompoundTag comp && comp.isEmpty()) return null;
        GTRecipe result = null;
        if (tag instanceof CompoundTag compoundTag) {
            var recipeTag = compoundTag.get("recipe");
            var mapResult = NbtOps.INSTANCE.getMap(Objects.requireNonNull(recipeTag)).result();
            if (mapResult.isPresent()) {
                result = GTRecipeSerializer.CODEC.decode(NbtOps.INSTANCE, mapResult.get()).result().orElse(null);
            }
            if (result != null) {
                result.id = ResourceLocation.parse(compoundTag.getString("id"));
                result.parallels = compoundTag.contains("parallels") ? compoundTag.getInt("parallels") : 1;
                result.ocLevel = compoundTag.getInt("ocLevel");
            }
        }
        return result;
    }
}

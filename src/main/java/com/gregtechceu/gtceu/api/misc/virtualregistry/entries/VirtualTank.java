package com.gregtechceu.gtceu.api.misc.virtualregistry.entries;

import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

public class VirtualTank extends VirtualEntry {

    public static final int DEFAULT_CAPACITY = 160_000; // 160B for per second transfer
    protected static final String CAPACITY_KEY = "capacity";
    protected static final String FLUID_KEY = "fluid";
    @NotNull
    @Getter
    private final FluidTank fluidTank;
    private int capacity;

    public VirtualTank(int capacity) {
        this.capacity = capacity;
        fluidTank = new FluidTank(this.capacity);
    }

    public VirtualTank() {
        this(DEFAULT_CAPACITY);
    }

    @Override
    public EntryTypes<VirtualTank> getType() {
        return EntryTypes.ENDER_FLUID;
    }

    public void setFluid(FluidStack fluid) {
        this.fluidTank.setFluid(fluid);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof VirtualTank other)) return false;
        return this.fluidTank == other.fluidTank;
    }

    @Override
    public CompoundTag serializeNBT(HolderLookup.@NotNull Provider registries) {
        var tag = super.serializeNBT(registries);
        tag.putInt(CAPACITY_KEY, this.capacity);

        if (!this.fluidTank.getFluid().isEmpty()) {
            tag.put(FLUID_KEY, FluidStack.CODEC.encodeStart(NbtOps.INSTANCE, this.fluidTank.getFluid()).getOrThrow());
        }
        return tag;
    }

    @Override
    public JsonElement serializeJson(HolderLookup.@NotNull Provider registries) {
        JsonObject json = super.serializeJson(registries).getAsJsonObject();
        json.addProperty(CAPACITY_KEY, this.capacity);
        if (!this.fluidTank.getFluid().isEmpty()) {
            json.add(FLUID_KEY, encodeJson(registries, FluidStack.CODEC, this.fluidTank.getFluid()));
        }
        return json;
    }

    @Override
    public void deserializeNBT(HolderLookup.@NotNull Provider registries, CompoundTag nbt) {
        super.deserializeNBT(registries, nbt);
        this.capacity = nbt.getInt(CAPACITY_KEY);

        if (nbt.contains(FLUID_KEY))
            setFluid(FluidStack.CODEC.parse(NbtOps.INSTANCE, nbt.getCompound(FLUID_KEY)).getOrThrow());
    }

    @Override
    public void deserializeJson(HolderLookup.@NotNull Provider registries, JsonElement data) {
        super.deserializeJson(registries, data);
        JsonObject json = data.getAsJsonObject();
        this.capacity = json.get(CAPACITY_KEY).getAsInt();
        if (json.has(FLUID_KEY)) {
            setFluid(decodeJson(registries, FluidStack.CODEC, json.get(FLUID_KEY)));
        }
    }

    @Override
    public boolean canRemove() {
        return super.canRemove() && this.fluidTank.isEmpty();
    }
}

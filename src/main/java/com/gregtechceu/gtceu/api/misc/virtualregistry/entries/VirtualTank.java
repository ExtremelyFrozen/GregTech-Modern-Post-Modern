package com.gregtechceu.gtceu.api.misc.virtualregistry.entries;

import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.datacomponents.VirtualEntryData;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;

public class VirtualTank extends VirtualEntry {

    public static final int DEFAULT_CAPACITY = 160_000; // 160B for per second transfer
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
    public DataComponentMap exportComponents(HolderLookup.@NotNull Provider registries) {
        return putBaseComponent(DataComponentMap.builder())
                .set(GTDataComponents.VIRTUAL_TANK.get(), new VirtualEntryData.Tank(this.capacity,
                        this.fluidTank.getFluid()))
                .build();
    }

    @Override
    public void importComponents(HolderLookup.@NotNull Provider registries, DataComponentMap components) {
        super.importComponents(registries, components);
        VirtualEntryData.Tank data = components.get(GTDataComponents.VIRTUAL_TANK.get());
        if (data == null) {
            throw new IllegalArgumentException("Virtual tank entry is missing tank data component");
        }
        this.capacity = data.capacity();
        this.fluidTank.setCapacity(this.capacity);
        setFluid(data.fluid());
    }

    @Override
    public boolean canRemove() {
        return super.canRemove() && this.fluidTank.isEmpty();
    }
}

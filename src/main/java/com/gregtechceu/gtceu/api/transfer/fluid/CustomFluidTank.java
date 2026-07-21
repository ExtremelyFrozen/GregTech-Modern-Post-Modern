package com.gregtechceu.gtceu.api.transfer.fluid;

import com.gregtechceu.gtceu.api.transfer.DataComponentTransfer;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.datacomponents.TransferData;

import net.minecraft.core.component.DataComponentMap;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class CustomFluidTank extends FluidTank implements IFluidHandlerModifiable, DataComponentTransfer {

    @Getter
    @Setter
    protected @NotNull Runnable onContentsChanged = () -> {};

    public CustomFluidTank(int capacity) {
        this(capacity, e -> true);
    }

    public CustomFluidTank(int capacity, Predicate<FluidStack> validator) {
        super(capacity, validator);
    }

    public CustomFluidTank(FluidStack stack) {
        super(stack.getAmount());
        setFluid(stack);
    }

    @Override
    protected void onContentsChanged() {
        onContentsChanged.run();
    }

    @Override
    public void setFluidInTank(int tank, FluidStack stack) {
        setFluid(stack);
    }

    @Override
    public void setFluid(FluidStack stack) {
        super.setFluid(stack);
        this.onContentsChanged();
    }

    @Override
    public DataComponentMap exportComponents() {
        return DataComponentMap.builder()
                .set(GTDataComponents.TRANSFER_FLUID_TANK.get(),
                        new TransferData.FluidTank(getCapacity(), getFluid()))
                .build();
    }

    @Override
    public void importComponents(DataComponentMap components) {
        TransferData.FluidTank data = components.get(GTDataComponents.TRANSFER_FLUID_TANK.get());
        if (data == null) {
            throw new IllegalArgumentException("Fluid tank component data is missing transfer_fluid_tank");
        }
        if (data.capacity() != getCapacity()) {
            throw new IllegalArgumentException("Fluid tank expected capacity " + getCapacity() +
                    " but received " + data.capacity());
        }
        setFluid(data.fluid());
    }
}

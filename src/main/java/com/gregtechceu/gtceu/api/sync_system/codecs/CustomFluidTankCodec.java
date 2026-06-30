package com.gregtechceu.gtceu.api.sync_system.codecs;

import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;

import net.neoforged.neoforge.fluids.FluidStack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

public final class CustomFluidTankCodec implements ContextualFieldCodec<CustomFluidTank> {

    public static final Class<CustomFluidTank> TYPE = CustomFluidTank.class;
    public static final CustomFluidTankCodec INSTANCE = new CustomFluidTankCodec();

    private static final String CAPACITY = "capacity";
    private static final String FLUID = "fluid";

    private CustomFluidTankCodec() {}

    @Override
    public JsonElement serializeField(CustomFluidTank value, Context<CustomFluidTank> context) {
        JsonObject json = new JsonObject();
        json.addProperty(CAPACITY, value.getCapacity());
        json.add(FLUID, FluidStack.OPTIONAL_CODEC
                .encodeStart(context.lookup().createSerializationContext(JsonOps.INSTANCE), value.getFluid())
                .getOrThrow());
        return json;
    }

    @Override
    public CustomFluidTank deserializeField(JsonElement value, Context<CustomFluidTank> context) {
        if (!value.isJsonObject()) {
            throw new IllegalArgumentException("Sync: fluid tank field " + context.fieldName() +
                    " must be encoded as an object");
        }

        JsonObject json = value.getAsJsonObject();
        int capacity = json.get(CAPACITY).getAsInt();
        CustomFluidTank tank = context.currentValue();
        if (tank == null) {
            tank = new CustomFluidTank(capacity);
        } else if (tank.getCapacity() != capacity) {
            throw new IllegalArgumentException("Sync: fluid tank field " + context.fieldName() +
                    " expected capacity " + tank.getCapacity() + " but received " + capacity);
        }

        tank.setFluid(FluidStack.OPTIONAL_CODEC
                .parse(context.lookup().createSerializationContext(JsonOps.INSTANCE), json.get(FLUID))
                .getOrThrow());
        return tank;
    }
}

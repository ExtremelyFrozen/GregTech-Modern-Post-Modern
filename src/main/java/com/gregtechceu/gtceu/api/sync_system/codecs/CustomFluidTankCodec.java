package com.gregtechceu.gtceu.api.sync_system.codecs;

import com.gregtechceu.gtceu.api.sync_system.ContextualFieldCodec;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.datacomponents.TransferData;

import net.minecraft.core.component.DataComponentMap;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

public final class CustomFluidTankCodec implements ContextualFieldCodec<CustomFluidTank> {

    public static final Class<CustomFluidTank> TYPE = CustomFluidTank.class;
    public static final CustomFluidTankCodec INSTANCE = new CustomFluidTankCodec();

    private CustomFluidTankCodec() {}

    @Override
    public JsonElement serializeField(CustomFluidTank value, Context<CustomFluidTank> context) {
        return DataComponentMap.CODEC
                .encodeStart(context.lookup().createSerializationContext(JsonOps.INSTANCE), value.exportComponents())
                .getOrThrow();
    }

    @Override
    public CustomFluidTank deserializeField(JsonElement value, Context<CustomFluidTank> context) {
        DataComponentMap components = DataComponentMap.CODEC
                .parse(context.lookup().createSerializationContext(JsonOps.INSTANCE), value)
                .getOrThrow();
        TransferData.FluidTank data = components.get(GTDataComponents.TRANSFER_FLUID_TANK.get());
        CustomFluidTank tank = context.currentValue();
        if (data == null && tank == null) {
            throw new IllegalArgumentException("Sync: fluid tank field " + context.fieldName() +
                    " is missing transfer_fluid_tank");
        }
        if (tank == null) {
            tank = new CustomFluidTank(data.capacity());
        }

        tank.importComponents(components);
        return tank;
    }
}

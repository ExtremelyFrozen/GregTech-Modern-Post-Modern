package com.gregtechceu.gtceu.integration.ae2.utils;

import com.gregtechceu.gtceu.api.transfer.DataComponentTransfer;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.datacomponents.AE2GridNodeData;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;

import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.me.ManagedGridNode;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

public class SerializableManagedGridNode extends ManagedGridNode implements DataComponentTransfer {

    public <T> SerializableManagedGridNode(T nodeOwner, IGridNodeListener<? super T> listener) {
        super(nodeOwner, listener);
    }

    public CompoundTag exportAENbt() {
        CompoundTag tag = new CompoundTag();
        super.saveToNBT(tag);
        return tag;
    }

    public void importAENbt(CompoundTag tag) {
        super.loadFromNBT(tag);
    }

    @Override
    public DataComponentMap exportComponents() {
        return exportComponents(this);
    }

    public static DataComponentMap exportComponents(IManagedGridNode node) {
        CompoundTag tag = new CompoundTag();
        node.saveToNBT(tag);
        String payload = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag).toString();
        return DataComponentMap.builder()
                .set(GTDataComponents.AE2_GRID_NODE.get(), new AE2GridNodeData(payload))
                .build();
    }

    @Override
    public void importComponents(DataComponentMap components) {
        importComponents(this, components);
    }

    public static void importComponents(IManagedGridNode node, DataComponentMap components) {
        AE2GridNodeData data = components.get(GTDataComponents.AE2_GRID_NODE.get());
        if (data == null) {
            throw new IllegalArgumentException("AE2 grid node component map is missing ae2_grid_node");
        }
        node.loadFromNBT((CompoundTag) JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE,
                JsonParser.parseString(data.payload())));
    }
}

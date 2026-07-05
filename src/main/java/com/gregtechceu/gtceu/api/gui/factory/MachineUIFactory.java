package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class MachineUIFactory extends UIFactory<MachineUIHolderContext> {

    public static final MachineUIFactory INSTANCE = new MachineUIFactory();

    public MachineUIFactory() {
        super(GTCEu.id("machine"));
    }

    public boolean openUI(MetaMachine machine, ServerPlayer player) {
        return openUI(new MachineUIHolderContext(player, machine), player);
    }

    @Override
    protected ModularUI createUITemplate(MachineUIHolderContext holder, Player entityPlayer) {
        if (holder == null) return null;
        if (holder.getMachine() instanceof IUIMachine machine) {
            return machine.createUI(entityPlayer);
        }
        return null;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    protected MachineUIHolderContext readHolderFromSyncData(RegistryFriendlyByteBuf syncData) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;
        var player = minecraft.player;
        if (player == null) return null;
        var pos = syncData.readBlockPos();
        var machineDefinitionId = syncData.readResourceLocation();
        return new MachineUIHolderContext(player, pos, machineDefinitionId);
    }

    @Override
    protected void writeHolderToSyncData(RegistryFriendlyByteBuf syncData, MachineUIHolderContext holder) {
        syncData.writeBlockPos(holder.getPos());
        syncData.writeResourceLocation(holder.getMachineDefinitionId());
    }
}

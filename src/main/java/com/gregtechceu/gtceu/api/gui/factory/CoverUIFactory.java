package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class CoverUIFactory extends UIFactory<UICoverHolderContext> {

    public static final CoverUIFactory INSTANCE = new CoverUIFactory();

    public CoverUIFactory() {
        super(GTCEu.id("cover"));
    }

    public boolean openUI(CoverBehavior cover, ServerPlayer player) {
        return openUI(new UICoverHolderContext(player, cover), player);
    }

    @Override
    protected ModularUI createUITemplate(UICoverHolderContext holder, Player entityPlayer) {
        if (holder == null) return null;
        return holder.createUI(entityPlayer);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    protected UICoverHolderContext readHolderFromSyncData(RegistryFriendlyByteBuf syncData) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;
        var player = minecraft.player;
        if (player == null) return null;
        var pos = syncData.readBlockPos();
        var side = syncData.readEnum(Direction.class);
        var coverDefinitionId = syncData.readResourceLocation();
        return new UICoverHolderContext(player, pos, side, coverDefinitionId);
    }

    @Override
    protected void writeHolderToSyncData(RegistryFriendlyByteBuf syncData, UICoverHolderContext holder) {
        syncData.writeBlockPos(holder.getPos());
        syncData.writeEnum(holder.getSide());
        syncData.writeResourceLocation(holder.getCoverDefinitionId());
    }
}

package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.GTCEu;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Opens legacy GTM held item screens without exposing LDLib's held item factory to item behavior APIs.
 */
public final class GTHeldItemUIFactory extends UIFactory<HeldItemUIHolderContext> {

    public static final GTHeldItemUIFactory INSTANCE = new GTHeldItemUIFactory();

    public GTHeldItemUIFactory() {
        super(GTCEu.id("held_item"));
    }

    public boolean openUI(ServerPlayer player, InteractionHand hand) {
        return openUI(new HeldItemUIHolderContext(player, hand), player);
    }

    @Override
    protected ModularUI createUITemplate(HeldItemUIHolderContext holder, Player entityPlayer) {
        return holder.createUI(entityPlayer);
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    protected HeldItemUIHolderContext readHolderFromSyncData(RegistryFriendlyByteBuf syncData) {
        var player = Minecraft.getInstance().player;
        if (player == null) return null;
        return new HeldItemUIHolderContext(player, syncData.readEnum(InteractionHand.class));
    }

    @Override
    protected void writeHolderToSyncData(RegistryFriendlyByteBuf syncData, HeldItemUIHolderContext holder) {
        syncData.writeEnum(holder.getHand());
    }
}

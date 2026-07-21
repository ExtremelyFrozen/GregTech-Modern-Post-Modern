package com.gregtechceu.gtceu.client;

import com.gregtechceu.gtceu.common.cover.ender.EnderLinkChannelListReceiver;
import com.gregtechceu.gtceu.common.network.packets.SPacketEnderLinkChannelsToClient;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.google.gson.JsonElement;

import java.util.List;

/**
 * Applies Ender link channel list responses to the currently opened LDLib2 cover UI.
 */
public final class EnderLinkChannelListClientHandler {

    private EnderLinkChannelListClientHandler() {}

    public static void handle(SPacketEnderLinkChannelsToClient packet, IPayloadContext context) {
        HolderLookup.Provider registries = context.player().registryAccess();
        List<JsonElement> entries = packet.entries();
        Minecraft.getInstance().execute(() -> receive(packet, entries, registries));
    }

    private static void receive(SPacketEnderLinkChannelsToClient packet, List<JsonElement> entries,
                                HolderLookup.Provider registries) {
        if (!(Minecraft.getInstance().screen instanceof ModularUIContainerScreen screen)) {
            return;
        }

        var modularUI = screen.getMenu().getModularUI();
        var receivers = modularUI.ui.rootElement.selfAndAllChildren()
                .filter(EnderLinkChannelListReceiver.class::isInstance)
                .map(EnderLinkChannelListReceiver.class::cast)
                .toList();
        for (EnderLinkChannelListReceiver receiver : receivers) {
            if (receiver.acceptsEnderLinkChannelList(packet.pos(), packet.side(), packet.coverDefinitionId())) {
                receiver.receiveEnderLinkChannelList(entries, registries);
            }
        }
    }
}

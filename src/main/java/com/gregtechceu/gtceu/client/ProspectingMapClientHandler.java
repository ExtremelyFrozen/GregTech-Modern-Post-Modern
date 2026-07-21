package com.gregtechceu.gtceu.client;

import com.gregtechceu.gtceu.api.gui.element.ProspectingMapElement;
import com.gregtechceu.gtceu.common.network.packets.SPacketProspectingMapData;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ProspectingMapClientHandler {

    private ProspectingMapClientHandler() {}

    public static void handle(SPacketProspectingMapData packet, IPayloadContext context) {
        Minecraft.getInstance().execute(() -> receive(packet));
    }

    private static void receive(SPacketProspectingMapData packet) {
        if (!(Minecraft.getInstance().screen instanceof ModularUIContainerScreen screen)) {
            return;
        }

        var modularUI = screen.getMenu().getModularUI();
        var receivers = modularUI.ui.rootElement.selfAndAllChildren()
                .filter(ProspectingMapElement.class::isInstance)
                .map(ProspectingMapElement.class::cast)
                .toList();
        for (ProspectingMapElement receiver : receivers) {
            if (receiver.acceptsProspectingPacket(packet.hand(), packet.openedStack(), packet.mode())) {
                receiver.receiveProspectingPacket(packet.packet());
            }
        }
    }
}

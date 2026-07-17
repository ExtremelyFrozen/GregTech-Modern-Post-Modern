package com.gregtechceu.gtceu.api.machine.feature.multiblock;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.feature.IMachineFeature;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import com.lowdragmc.lowdraglib2.gui.util.ClickData;

import net.minecraft.network.chat.Component;

import java.util.List;

public interface IDisplayUIMachine extends IMachineFeature {

    default void addDisplayText(List<Component> textList) {
        for (var part : self().getParts()) {
            part.addMultiText(textList);
        }
    }

    default void handleDisplayClick(String componentData, ClickData clickData) {}

    default IGuiTexture getScreenTexture() {
        return GuiTextures.DISPLAY;
    }

    @Override
    default MultiblockControllerMachine self() {
        return (MultiblockControllerMachine) this;
    }
}

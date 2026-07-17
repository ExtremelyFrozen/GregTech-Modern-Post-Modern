package com.gregtechceu.gtceu.common.machine.multiblock.part.monitor;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

public class MonitorPartMachine extends MonitorComponentPartMachine {

    public MonitorPartMachine(BlockEntityCreationInfo info) {
        super(info);
    }

    @Override
    public boolean isMonitor() {
        return true;
    }

    @Override
    public IGuiTexture getComponentIcon() {
        return GuiTextures.spirit(GTCEu.id("item/computer_monitor_cover"));
    }
}

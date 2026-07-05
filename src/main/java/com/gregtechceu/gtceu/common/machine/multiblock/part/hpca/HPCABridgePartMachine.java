package com.gregtechceu.gtceu.common.machine.multiblock.part.hpca;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.common.machine.trait.hpca.HPCAComponentTrait;

import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

public class HPCABridgePartMachine extends HPCAComponentPartMachine {

    public HPCABridgePartMachine(BlockEntityCreationInfo info) {
        super(info, new HPCAComponentTrait(GTValues.VA[GTValues.IV], GTValues.VA[GTValues.IV], false, true));
    }

    @Override
    public boolean isAdvanced() {
        return true;
    }

    @Override
    public IGuiTexture getComponentIcon() {
        return GuiTextures.HPCA_ICON_BRIDGE_COMPONENT;
    }
}

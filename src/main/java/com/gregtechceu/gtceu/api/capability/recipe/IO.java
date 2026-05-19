package com.gregtechceu.gtceu.api.capability.recipe;

import com.gregtechceu.gtceu.api.gui.widget.EnumSelectorWidget;

import com.lowdragmc.lowdraglib.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;

import lombok.Getter;

/**
 * The capability can be input or output or both
 */
public enum IO implements EnumSelectorWidget.SelectableEnum {

    IN("gtpm.io.import", "import"),
    OUT("gtpm.io.export", "export"),
    BOTH("gtpm.io.both", "both"),
    NONE("gtpm.io.none", "none");

    @Getter
    public final String tooltip;
    @Getter
    public final IGuiTexture icon;

    IO(String tooltip, String textureName) {
        this.tooltip = tooltip;
        this.icon = new ResourceTexture("gtpm:textures/gui/icon/io_mode/" + textureName + ".png");
    }

    public boolean support(IO io) {
        if (io == this) return true;
        if (io == NONE) return false;
        return this == BOTH;
    }
}

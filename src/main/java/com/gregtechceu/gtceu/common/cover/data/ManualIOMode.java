package com.gregtechceu.gtceu.common.cover.data;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.SelectableEnum;

import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

public enum ManualIOMode implements SelectableEnum {

    DISABLED("disabled"),
    FILTERED("filtered"),
    UNFILTERED("unfiltered");

    public static final ManualIOMode[] VALUES = values();

    public final String localeName;

    ManualIOMode(String localeName) {
        this.localeName = localeName;
    }

    @Override
    public String getTooltip() {
        return "cover.universal.manual_import_export.mode." + localeName;
    }

    @Override
    public IGuiTexture getIcon() {
        return GuiTextures.resource("gtpm:textures/gui/icon/manual_io_mode/" + localeName + ".png");
    }
}

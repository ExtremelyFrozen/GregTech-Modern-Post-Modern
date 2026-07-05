package com.gregtechceu.gtceu.common.cover.data;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.EnumSelectorWidget;

import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

import lombok.Getter;

public enum BucketMode implements EnumSelectorWidget.SelectableEnum {

    BUCKET("cover.bucket.mode.bucket", "textures/item/water_bucket", 1000),
    MILLI_BUCKET("cover.bucket.mode.milli_bucket", "gtpm:textures/gui/icon/bucket_mode/water_drop", 1);

    @Getter
    public final String tooltip;
    @Getter
    public final IGuiTexture icon;

    public final int multiplier;

    BucketMode(String tooltip, String textureName, int multiplier) {
        this.tooltip = tooltip;
        this.icon = GuiTextures.resource(textureName + ".png").scale(16F / 20F);
        this.multiplier = multiplier;
    }
}

package com.gregtechceu.gtceu.api.gui;

import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

public class SteamTexture {

    private static final String BRONZE = "bronze";
    private static final String STEEL = "steel";

    private final IGuiTexture bronzeTexture;
    private final IGuiTexture steelTexture;

    private SteamTexture(IGuiTexture bronzeTexture, IGuiTexture steelTexture) {
        this.bronzeTexture = bronzeTexture;
        this.steelTexture = steelTexture;
    }

    public static SteamTexture fullImage(String path) {
        return new SteamTexture(
                GuiTextures.resource(String.format(path, BRONZE)),
                GuiTextures.resource(String.format(path, STEEL)));
    }

    public IGuiTexture get(boolean isHighPressure) {
        return isHighPressure ? steelTexture : bronzeTexture;
    }
}

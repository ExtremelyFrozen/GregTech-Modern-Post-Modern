package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfiguratorButton;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * LDLib2 Fancy collapsed button configurator with caller-owned click behavior.
 *
 * <p>
 * Callers that mutate business state must dispatch a GTM action packet from the click handler.
 */
public class LDLib2ButtonConfigurator implements LDLib2FancyConfiguratorButton {

    private final IGuiTexture icon;
    private final Consumer<UIEvent> onClick;
    private List<Component> tooltips = Collections.emptyList();

    public LDLib2ButtonConfigurator(IGuiTexture icon, Consumer<UIEvent> onClick) {
        this.icon = icon;
        this.onClick = onClick;
    }

    @Override
    public IGuiTexture getIcon() {
        return icon;
    }

    @Override
    public List<Component> getTooltips() {
        return tooltips;
    }

    public LDLib2ButtonConfigurator setTooltips(List<Component> tooltips) {
        this.tooltips = tooltips;
        return this;
    }

    @Override
    public void onClick(UIEvent event) {
        onClick.accept(event);
    }
}

package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.SelectableEnum;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyConfiguratorButton;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

import com.lowdragmc.lowdraglib2.gui.util.ClickData;

import net.minecraft.network.chat.Component;

import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;

public class FancySelectorConfigurator<T extends Enum<T> & SelectableEnum>
                                      implements IFancyConfiguratorButton {

    private final List<T> values;
    private final Consumer<T> onChanged;
    private int selected;

    @Setter
    @Accessors(chain = true)
    private Function<T, List<Component>> tooltip = t -> Collections.singletonList(Component.empty());

    public FancySelectorConfigurator(T[] values, T initialValue, Consumer<T> onChanged) {
        this.values = List.of(values);
        this.onChanged = onChanged;
        setSelected(initialValue);
        onChanged.accept(getCurrentValue());
    }

    @Override
    public IGuiTexture getIcon() {
        return GuiTextures.group(GuiTextures.VANILLA_BUTTON, getCurrentValue().getIcon());
    }

    @Override
    public List<Component> getTooltips() {
        return this.tooltip.apply(getCurrentValue());
    }

    @Override
    public void onClick(ClickData clickData) {
        selected = (selected + 1) % values.size();
        onChanged.accept(getCurrentValue());
    }

    private T getCurrentValue() {
        return values.get(selected);
    }

    private void setSelected(T value) {
        selected = values.indexOf(value);
        if (selected == -1) {
            throw new NoSuchElementException(value + " is not a possible value for this selector.");
        }
    }
}

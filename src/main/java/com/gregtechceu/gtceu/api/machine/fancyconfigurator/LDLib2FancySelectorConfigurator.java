package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.SelectableEnum;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfiguratorButton;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * LDLib2 Fancy collapsed selector for enum-backed machine options.
 *
 * <p>The selector only computes the requested next value. Callers that mutate business state must send a GTM action
 * packet or update a GTM sync field from the change callback.
 */
public class LDLib2FancySelectorConfigurator<T extends Enum<T> & SelectableEnum>
                                            implements LDLib2FancyConfiguratorButton {

    private final List<T> values;
    private final Supplier<T> valueSupplier;
    private final BiConsumer<UIEvent, T> onChanged;
    private Function<T, List<Component>> tooltip = value -> List.of(Component.translatable(value.getTooltip()));

    /**
     * Creates a selector over the supplied enum values.
     *
     * @param values selectable enum values in display and cycle order.
     * @param valueSupplier supplies the current client-visible value.
     * @param onChanged receives the requested next value when clicked.
     */
    public LDLib2FancySelectorConfigurator(T[] values, Supplier<T> valueSupplier, BiConsumer<UIEvent, T> onChanged) {
        this.values = List.of(values);
        this.valueSupplier = valueSupplier;
        this.onChanged = onChanged;
        requireSelected(valueSupplier.get());
    }

    /**
     * Sets tooltip text based on the current selected value.
     */
    public LDLib2FancySelectorConfigurator<T> setTooltip(Function<T, List<Component>> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    /**
     * Returns the button and enum icon for the current value.
     */
    @Override
    public IGuiTexture getIcon() {
        return GuiTextures.group(GuiTextures.VANILLA_BUTTON, currentValue().getIcon());
    }

    /**
     * Returns tooltip text for the current value.
     */
    @Override
    public List<Component> getTooltips() {
        return tooltip.apply(currentValue());
    }

    /**
     * Requests the next enum value in cycle order.
     */
    @Override
    public void onClick(UIEvent event) {
        int currentIndex = requireSelected(currentValue());
        onChanged.accept(event, values.get((currentIndex + 1) % values.size()));
    }

    private T currentValue() {
        return valueSupplier.get();
    }

    private int requireSelected(T value) {
        int selected = values.indexOf(value);
        if (selected == -1) {
            throw new NoSuchElementException(value + " is not a possible value for this selector.");
        }
        return selected;
    }
}

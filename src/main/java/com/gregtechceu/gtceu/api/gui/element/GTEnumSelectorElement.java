package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.data.lang.LangHandler;

import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * LDLib2 enum selector facade backed by GTM textured button semantics.
 */
public class GTEnumSelectorElement<T extends Enum<T>> extends GTButtonElement {

    private final List<T> values;
    private final Supplier<T> selectedSupplier;
    private final Consumer<T> onChanged;
    private final Function<T, IGuiTexture> iconGetter;
    private final Function<T, String> tooltipKeyGetter;
    private T lastSelected;

    public GTEnumSelectorElement(int x, int y, int width, int height, T[] values, Supplier<T> selectedSupplier,
                                 Consumer<T> onChanged, Function<T, IGuiTexture> iconGetter,
                                 Function<T, String> tooltipKeyGetter) {
        this(x, y, width, height, Arrays.asList(values), selectedSupplier, onChanged, iconGetter, tooltipKeyGetter);
    }

    public GTEnumSelectorElement(int x, int y, int width, int height, List<T> values, Supplier<T> selectedSupplier,
                                 Consumer<T> onChanged, Function<T, IGuiTexture> iconGetter,
                                 Function<T, String> tooltipKeyGetter) {
        this.values = List.copyOf(values);
        if (this.values.isEmpty()) {
            throw new IllegalArgumentException("Enum selector requires at least one value.");
        }
        this.selectedSupplier = selectedSupplier;
        this.onChanged = onChanged;
        this.iconGetter = iconGetter;
        this.tooltipKeyGetter = tooltipKeyGetter;
        noText();
        setOnClick(event -> cycle());
        UITemplate.setLDLib2Bounds(this, x, y, width, height);
        refreshState();
    }

    @Override
    public void screenTick() {
        T selected = selectedSupplier.get();
        if (selected != lastSelected) {
            refreshState(selected);
        }
        super.screenTick();
    }

    private void cycle() {
        T selected = selectedSupplier.get();
        int selectedIndex = getSelectedIndex(selected);
        T next = values.get((selectedIndex + 1) % values.size());
        onChanged.accept(next);
        refreshState();
    }

    private void refreshState() {
        refreshState(selectedSupplier.get());
    }

    private void refreshState(T selected) {
        getSelectedIndex(selected);
        lastSelected = selected;
        setButtonTexture(GuiTextures.group(GuiTextures.VANILLA_BUTTON, iconGetter.apply(selected)));
        List<Component> tooltips = List.copyOf(LangHandler.getSingleOrMultiLang(tooltipKeyGetter.apply(selected)));
        style(style -> style.tooltips(tooltips.toArray(Component[]::new)));
    }

    private int getSelectedIndex(T selected) {
        int selectedIndex = values.indexOf(selected);
        if (selectedIndex == -1) {
            throw new NoSuchElementException(selected + " is not a possible value for this selector.");
        }
        return selectedIndex;
    }
}

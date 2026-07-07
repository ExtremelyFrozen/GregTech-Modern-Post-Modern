package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.GuiTextures;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.network.chat.Component;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * LDLib2 string selector facade for GTM pixel-positioned list selection controls.
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
@LDLRegister(name = "gtm-string-selector", group = "gtm", registry = "ldlib2:ui_element")
public class GTStringSelectorElement extends Selector<String> {

    private final Supplier<String> selectedSupplier;
    private String lastSelected;

    public GTStringSelectorElement(int x, int y, int width, int height, List<String> values,
                                   Supplier<String> selectedSupplier, Consumer<String> onChanged) {
        if (values.isEmpty()) {
            throw new IllegalArgumentException("String selector requires at least one value.");
        }
        this.selectedSupplier = selectedSupplier;
        this.lastSelected = selectedSupplier.get();
        setValue(lastSelected, false);
        setCandidateUIProvider(this::createCandidateElement);
        setCandidates(List.copyOf(values));
        setOnValueChanged(onChanged);
        selectorStyle(style -> style
                .maxItemCount(5)
                .scrollerViewHeight(75)
                .closeAfterSelect(true));
        style(style -> style.backgroundTexture(GuiTextures.BUTTON));
        UITemplate.setLDLib2Bounds(this, x, y, width, height);
        refreshSelected(true);
    }

    @Override
    public void screenTick() {
        refreshSelected(false);
        super.screenTick();
    }

    private UIElement createCandidateElement(String value) {
        GTLabelElement label = new GTLabelElement(Component.literal(value));
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(15);
        });
        label.textStyle(style -> style
                .textColor(0xFFFFFF)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private void refreshSelected(boolean force) {
        String selected = selectedSupplier.get();
        if (force || !selected.equals(lastSelected)) {
            setSelected(selected, false);
            lastSelected = selected;
        }
    }
}

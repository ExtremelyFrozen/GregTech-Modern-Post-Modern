package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfigurator;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.network.chat.Component;

import lombok.Setter;
import lombok.experimental.Accessors;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * LDLib2 Fancy configurator for display-only fluid tank grids.
 *
 * <p>
 * Bucket transfer must be wired by the owning machine through a GT action before this replaces legacy TankWidget
 * behavior.
 */
@Accessors(chain = true)
public class LDLib2FancyTankConfigurator implements LDLib2FancyConfigurator {

    private final CustomFluidTank[] tanks;
    private final Component title;
    private List<Component> tooltips = Collections.emptyList();
    @Setter
    @Nullable
    private BiPredicate<Integer, UIEvent> tankClickHandler;

    public LDLib2FancyTankConfigurator(CustomFluidTank[] tanks, Component title) {
        this.tanks = tanks;
        this.title = title;
    }

    @Override
    public Component getTitle() {
        return title;
    }

    @Override
    public IGuiTexture getIcon() {
        return GuiTextures.BUTTON_FLUID_OUTPUT;
    }

    @Override
    public List<Component> getTooltips() {
        return tooltips;
    }

    public LDLib2FancyTankConfigurator setTooltips(List<Component> tooltips) {
        this.tooltips = tooltips;
        return this;
    }

    @Override
    public int getLDLib2ConfiguratorWidth() {
        return 18 * getRowSize() + 16;
    }

    @Override
    public int getLDLib2ConfiguratorHeight() {
        return 18 * getColSize() + 16;
    }

    @Override
    public UIElement createLDLib2Configurator() {
        UIElement group = new UIElement();
        UITemplate.setLDLib2Bounds(group, 0, 0, getLDLib2ConfiguratorWidth(), getLDLib2ConfiguratorHeight());

        int rowSize = getRowSize();
        int colSize = getColSize();
        UIElement container = new UIElement();
        UITemplate.setLDLib2Bounds(container, 4, 4, 18 * rowSize + 8, 18 * colSize + 8);
        container.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));

        int index = 0;
        for (int y = 0; y < colSize; y++) {
            for (int x = 0; x < rowSize; x++) {
                int tankIndex = index++;
                GTFluidSlotElement slot = new GTFluidSlotElement()
                        .setFluidTank(tanks[tankIndex], 0)
                        .setBackgroundTexture(GuiTextures.FLUID_SLOT)
                        .setAllowClickFilled(tankClickHandler != null)
                        .setAllowClickDrained(tankClickHandler != null)
                        .setShowAmount(true);
                if (tankClickHandler != null) {
                    slot.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                        if (event.button == GLFW.GLFW_MOUSE_BUTTON_LEFT && tankClickHandler.test(tankIndex, event)) {
                            event.stopImmediatePropagation();
                            event.hasHandler = true;
                        }
                    });
                }
                UITemplate.setLDLib2Bounds(slot, 4 + x * 18, 4 + y * 18, 18, 18);
                container.addChild(slot);
            }
        }

        group.addChild(container);
        return group;
    }

    private int getRowSize() {
        if (tanks.length == 8) {
            return 4;
        }
        return (int) Math.sqrt(tanks.length);
    }

    private int getColSize() {
        if (tanks.length == 8) {
            return 2;
        }
        return getRowSize();
    }
}

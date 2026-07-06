package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.util.ClickData;
import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;

import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

public interface IFancyConfiguratorButton extends IFancyConfigurator {

    void onClick(ClickData clickData);

    /**
     * Button configurators do not own expandable content, so they do not expose a tab title by default.
     */
    @Override
    default Component getTitle() {
        throw new UnsupportedOperationException("Button configurators do not expose a tab title.");
    }

    /**
     * Button configurators execute actions directly instead of creating a nested configurator widget.
     */
    @Override
    default Widget createConfigurator() {
        throw new UnsupportedOperationException("Button configurators do not create a nested configurator widget.");
    }

    @Accessors(chain = true)
    class Toggle implements IFancyConfiguratorButton {

        IGuiTexture base;
        IGuiTexture pressed;
        BiConsumer<ClickData, Boolean> onClick;
        BooleanSupplier booleanSupplier;
        boolean isPressed;
        @Setter
        Function<Boolean, List<Component>> tooltipsSupplier = isPressed -> Collections.emptyList();

        public Toggle(IGuiTexture base, IGuiTexture pressed, BooleanSupplier booleanSupplier,
                      BiConsumer<ClickData, Boolean> onClick) {
            this.base = base;
            this.pressed = pressed;
            this.booleanSupplier = booleanSupplier;
            this.onClick = onClick;
        }

        @Override
        public List<Component> getTooltips() {
            return tooltipsSupplier.apply(isPressed);
        }

        @Override
        public void detectAndSendChange(BiConsumer<Integer, Consumer<RegistryFriendlyByteBuf>> sender) {
            var newIsPressed = booleanSupplier.getAsBoolean();
            if (newIsPressed != isPressed) {
                isPressed = newIsPressed;
                sender.accept(0, buf -> buf.writeBoolean(isPressed));
            }
        }

        @Override
        public void readUpdateInfo(int id, RegistryFriendlyByteBuf buf) {
            if (id == 0) {
                isPressed = buf.readBoolean();
            }
        }

        @Override
        public void writeInitialData(RegistryFriendlyByteBuf buffer) {
            this.isPressed = booleanSupplier.getAsBoolean();
            buffer.writeBoolean(this.isPressed);
        }

        @Override
        public void readInitialData(RegistryFriendlyByteBuf buffer) {
            this.isPressed = buffer.readBoolean();
        }

        @Override
        public IGuiTexture getIcon() {
            return isPressed ? pressed : base;
        }

        @Override
        public void onClick(ClickData clickData) {
            onClick.accept(clickData, !isPressed);
        }
    }
}

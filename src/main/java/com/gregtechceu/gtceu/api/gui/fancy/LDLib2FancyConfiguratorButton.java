package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * Describes a collapsed Fancy configurator that reacts to a local LDLib2 click instead of opening a body.
 *
 * <p>
 * Implementations that need to mutate server state must flush a GTM field update or dispatch a GTM action packet from
 * the click handler.
 */
public interface LDLib2FancyConfiguratorButton extends LDLib2FancyConfigurator {

    /**
     * Handles the local click event for this configurator button.
     */
    void onClick(UIEvent event);

    /**
     * Button configurators do not own expandable content, so they do not expose a tab title by default.
     */
    @Override
    default Component getTitle() {
        throw new UnsupportedOperationException("Button configurators do not expose a tab title.");
    }

    /**
     * Button configurators execute click handlers directly instead of creating a nested configurator element.
     */
    @Override
    default UIElement createLDLib2Configurator() {
        throw new UnsupportedOperationException("Button configurators do not create a nested configurator element.");
    }

    /**
     * LDLib2 Fancy toggle button that renders from a supplied state and delegates click handling to the caller.
     *
     * <p>
     * The toggle does not own synchronization. Callers that mutate business state must send a GTM field update or
     * action packet from the click handler.
     */
    class Toggle implements LDLib2FancyConfiguratorButton {

        private final IGuiTexture base;
        private final IGuiTexture pressed;
        private final BooleanSupplier stateSupplier;
        private final BiConsumer<UIEvent, Boolean> onClick;
        private Function<Boolean, List<Component>> tooltipsSupplier = isPressed -> Collections.emptyList();

        /**
         * Creates a toggle button with separate textures for inactive and active states.
         *
         * @param base          texture shown when the supplied state is false.
         * @param pressed       texture shown when the supplied state is true.
         * @param stateSupplier supplies the current client-visible state.
         * @param onClick       handles local clicks with the requested next state.
         */
        public Toggle(IGuiTexture base, IGuiTexture pressed, BooleanSupplier stateSupplier,
                      BiConsumer<UIEvent, Boolean> onClick) {
            this.base = base;
            this.pressed = pressed;
            this.stateSupplier = stateSupplier;
            this.onClick = onClick;
        }

        /**
         * Sets tooltip text based on the current supplied state.
         */
        public Toggle setTooltipsSupplier(Function<Boolean, List<Component>> tooltipsSupplier) {
            this.tooltipsSupplier = tooltipsSupplier;
            return this;
        }

        /**
         * Returns the tooltip text for the current state.
         */
        @Override
        public List<Component> getTooltips() {
            return tooltipsSupplier.apply(stateSupplier.getAsBoolean());
        }

        /**
         * Returns the icon matching the current state.
         */
        @Override
        public IGuiTexture getIcon() {
            return stateSupplier.getAsBoolean() ? pressed : base;
        }

        /**
         * Delegates the requested next state to the caller-owned click handler.
         */
        @Override
        public void onClick(UIEvent event) {
            onClick.accept(event, !stateSupplier.getAsBoolean());
        }
    }
}

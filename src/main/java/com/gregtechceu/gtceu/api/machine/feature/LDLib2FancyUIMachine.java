package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTooltipsPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTabsElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2MachineModeFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2WorkingEnabledFancyConfigurator;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Bridges machine-owned Fancy pages into the LDLib2 machine UI opening path.
 *
 * <p>This interface lets machines opt into the LDLib2 Fancy shell one call site at a time while the legacy
 * {@link IFancyUIMachine} contract remains available for machines that still depend on old LDLib widgets.
 */
public interface LDLib2FancyUIMachine extends IMachineFeature, LDLib2MachineUIProvider, LDLib2FancyUIProvider {

    /**
     * Builds the machine's concrete LDLib2 Fancy page body.
     *
     * <p>Implementations provide the real page element explicitly so the migration does not install a placeholder or
     * silently preserve the old {@code SceneWidget} preview path.
     */
    @Override
    UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell);

    /**
     * Accepts the opened machine holder only while it still resolves to this machine instance.
     */
    @Override
    default boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == self();
    }

    /**
     * Wraps this machine page provider in the shared LDLib2 Fancy shell.
     */
    @Override
    default UI createLDLib2UI(Player player, MachineUIHolder holder) {
        return UI.of(new LDLib2FancyMachineUIElement(this, player.getInventory(), holder, getLDLib2PageWidth(),
                getLDLib2PageHeight()));
    }

    /**
     * Uses the machine definition item as the title bar and tab icon.
     */
    @Override
    default IGuiTexture getTabIcon() {
        return GuiTextures.itemStack(self().getDefinition().getItem());
    }

    /**
     * Registers local LDLib2 side tabs for machine metadata pages.
     */
    @Override
    default void attachSideTabs(LDLib2FancyTabsElement tabs) {
        if (this instanceof IRecipeLogicMachine recipeLogicMachine &&
                recipeLogicMachine.getRecipeTypes().length > 1) {
            tabs.attachSubTab(new LDLib2MachineModeFancyConfigurator(recipeLogicMachine));
        }
    }

    /**
     * Registers common LDLib2 machine configurator buttons.
     */
    @Override
    default void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel) {
        if (this instanceof IControllable controllable) {
            configuratorPanel.attachConfigurators(new LDLib2WorkingEnabledFancyConfigurator(controllable,
                    configuratorPanel.getHolder()));
        }
    }

    /**
     * Registers the machine tooltip and all trait-provided Fancy tooltips with the LDLib2 tooltip panel.
     */
    @Override
    default void attachTooltips(LDLib2FancyTooltipsPanelElement tooltipsPanel) {
        tooltipsPanel.attachTooltips(self());
        self().getTraitHolder().getAllTraits().stream()
                .filter(IFancyTooltip.class::isInstance)
                .map(IFancyTooltip.class::cast)
                .forEach(tooltipsPanel::attachTooltips);
    }

    /**
     * Shows the machine's translated definition name when hovering its tab.
     */
    @Override
    default List<Component> getTabTooltips() {
        return List.of(Component.translatable(self().getDefinition().getDescriptionId()));
    }

    /**
     * Uses the machine's translated definition name as the Fancy title.
     */
    @Override
    default Component getTitle() {
        return Component.translatable(self().getDefinition().getDescriptionId());
    }
}

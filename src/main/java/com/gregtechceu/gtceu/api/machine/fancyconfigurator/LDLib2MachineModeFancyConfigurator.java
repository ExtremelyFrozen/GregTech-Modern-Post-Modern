package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.ColorPattern;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.texture.TextTexture;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.common.data.GTItems;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * LDLib2 Fancy page for selecting the active recipe type of a recipe logic machine.
 *
 * <p>
 * The page updates the client-side sync field and flushes it through the GTM machine sync channel.
 */
public class LDLib2MachineModeFancyConfigurator implements LDLib2FancyUIProvider {

    private static final int PAGE_WIDTH = 140;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_X = 2;
    private static final int BUTTON_Y = 2;
    private static final int BUTTON_WIDTH = 136;

    private final IRecipeLogicMachine machine;

    /**
     * Creates a mode selector page for one recipe logic machine.
     */
    public LDLib2MachineModeFancyConfigurator(IRecipeLogicMachine machine) {
        this.machine = machine;
    }

    /**
     * Builds fixed-height LDLib2 buttons that request a recipe type switch through GTM field sync.
     */
    @Override
    public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, getLDLib2PageWidth(), getLDLib2PageHeight());
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));

        for (int index = 0; index < machine.getRecipeTypes().length; index++) {
            int activeRecipeType = index;
            int y = BUTTON_Y + index * BUTTON_HEIGHT;
            GTButtonElement button = new GTButtonElement(BUTTON_X, y, BUTTON_WIDTH, BUTTON_HEIGHT, IGuiTexture.EMPTY,
                    event -> onModeClicked(shell, activeRecipeType, event));
            button.noText();
            root.addChild(button);

            GTImageElement label = new GTImageElement(BUTTON_X, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                    createModeTexture(activeRecipeType));
            label.setAllowHitTest(false);
            root.addChild(label);
        }
        return root;
    }

    /**
     * Returns the fixed content width used by the legacy machine mode selector.
     */
    @Override
    public int getLDLib2PageWidth() {
        return PAGE_WIDTH;
    }

    /**
     * Returns the height required for all machine recipe type buttons.
     */
    @Override
    public int getLDLib2PageHeight() {
        return BUTTON_HEIGHT * machine.getRecipeTypes().length + BUTTON_Y * 2;
    }

    /**
     * Returns the translated page title shown in the Fancy title bar.
     */
    @Override
    public Component getTitle() {
        return Component.translatable("gtpm.gui.machinemode.title");
    }

    /**
     * Uses the robot arm icon from the legacy machine mode tab.
     */
    @Override
    public IGuiTexture getTabIcon() {
        return GuiTextures.itemStack(GTItems.ROBOT_ARM_LV.get());
    }

    /**
     * Returns the legacy machine mode tab tooltip.
     */
    @Override
    public List<Component> getTabTooltips() {
        return List.of(Component.translatable("gtpm.gui.machinemode.tab_tooltip"));
    }

    private IGuiTexture createModeTexture(int activeRecipeType) {
        return GuiTextures.group(
                GuiTextures.VANILLA_BUTTON.copy()
                        .setDynamicColor(() -> machine.getActiveRecipeType() == activeRecipeType ?
                                ColorPattern.CYAN.color : -1),
                GuiTextures.text(machine.getRecipeTypes()[activeRecipeType].getTranslationKey())
                        .setWidth(BUTTON_WIDTH)
                        .setType(TextTexture.TextType.ROLL));
    }

    private void onModeClicked(LDLib2FancyMachineUIElement shell, int activeRecipeType, UIEvent event) {
        var openedMachine = shell.getHolder().getMachine();
        if (openedMachine == machine.self() && openedMachine.isRemote()) {
            machine.setActiveRecipeType(activeRecipeType);
            openedMachine.sendServerSyncChanges();
            event.stopImmediatePropagation();
            event.hasHandler = true;
        }
    }
}

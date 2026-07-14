package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2DirectionalFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2WorkingEnabledFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.client.gui.fancy.LDLib2PreviewSceneElement;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;

import com.tterrag.registrate.util.RegistrateDistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Opening-scoped LDLib2 implementation of the default Fancy machine preview page.
 *
 * <p>
 * The page captures its own validated holder so contextual multiblock part actions cannot accidentally use the
 * controller holder owned by the surrounding Fancy shell.
 */
public final class LDLib2FancyPreviewPage implements LDLib2FancyUIProvider {

    public static final int PREVIEW_PAGE_WIDTH = 100;
    public static final int PREVIEW_PAGE_HEIGHT = 100;

    private final MetaMachine machine;
    private final MachineUIHolder holder;
    private final LDLib2DirectionalFancyConfigurator directionalPage;
    @Nullable
    private final PageGroupingData groupingData;

    /**
     * Creates a contextual preview page bound to one opened machine holder.
     *
     * @param machine      machine rendered and configured by this page
     * @param player       player whose cover permissions apply to the directional page
     * @param holder       holder dedicated to the contextual machine rather than its controller
     * @param groupingData optional page-switcher group for multipart navigation
     */
    public LDLib2FancyPreviewPage(MetaMachine machine, Player player, MachineUIHolder holder,
                                      @Nullable PageGroupingData groupingData) {
        requireMatchingHolder(machine, holder);
        if (!(machine instanceof LDLib2FancyActionMachine)) {
            throw new IllegalArgumentException("Preview Fancy page machine must opt into LDLib2 Fancy actions.");
        }
        this.machine = machine;
        this.holder = holder;
        this.groupingData = groupingData;
        this.directionalPage = new LDLib2DirectionalFancyConfigurator(machine, player, holder);
    }

    @Override
    public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
        if (holder.getMachine() != machine) {
            throw new IllegalStateException("Preview Fancy page holder no longer resolves its opened machine.");
        }
        return createPreviewElement(machine);
    }

    /**
     * Builds the common preview root and constructs its Scene only on the physical client.
     */
    public static UIElement createPreviewElement(MetaMachine machine) {
        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0,
                PREVIEW_PAGE_WIDTH, PREVIEW_PAGE_HEIGHT);
        if (machine.isRemote()) {
            root.addChild(new GTImageElement(26, 60, 48, 16, GuiTextures.SCENE));
            RegistrateDistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> root.addChild(LDLib2PreviewSceneElement.createScene(
                            machine, PREVIEW_PAGE_WIDTH, PREVIEW_PAGE_HEIGHT)));
        }
        return root;
    }

    @Override
    public IGuiTexture getTabIcon() {
        return GuiTextures.itemStack(machine.getDefinition().getItem());
    }

    @Override
    public Component getTitle() {
        return Component.translatable(machine.getDefinition().getDescriptionId());
    }

    @Override
    public int getLDLib2PageWidth() {
        return PREVIEW_PAGE_WIDTH;
    }

    @Override
    public int getLDLib2PageHeight() {
        return PREVIEW_PAGE_HEIGHT;
    }

    @Override
    public void attachSideTabs(LDLib2FancyTabsElement tabs) {
        tabs.attachSubTab(directionalPage);
    }

    @Override
    public void attachConfigurators(LDLib2ConfiguratorPanelElement configuratorPanel) {
        if (machine instanceof IControllable controllable) {
            configuratorPanel.attachConfigurators(new LDLib2WorkingEnabledFancyConfigurator(controllable, holder));
        }
    }

    @Override
    public void attachTooltips(LDLib2FancyTooltipsPanelElement tooltipsPanel) {
        tooltipsPanel.attachTooltips(machine);
        machine.getTraitHolder().getAllTraits().stream()
                .filter(IFancyTooltip.class::isInstance)
                .map(IFancyTooltip.class::cast)
                .forEach(tooltipsPanel::attachTooltips);
    }

    @Override
    public List<Component> getTabTooltips() {
        return List.of(Component.translatable(machine.getDefinition().getDescriptionId()));
    }

    @Nullable
    @Override
    public PageGroupingData getPageGroupingData() {
        return groupingData;
    }

    private static void requireMatchingHolder(MetaMachine machine, MachineUIHolder holder) {
        if (holder.getMachine() != machine) {
            throw new IllegalArgumentException("Preview Fancy page holder must resolve the opened machine.");
        }
    }
}

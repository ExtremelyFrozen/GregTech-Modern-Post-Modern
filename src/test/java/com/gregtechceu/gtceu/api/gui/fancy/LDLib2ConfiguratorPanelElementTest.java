package com.gregtechceu.gtceu.api.gui.fancy;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement.Tab;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import org.jetbrains.annotations.Nullable;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LDLib2ConfiguratorPanelElementTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2ConfiguratorPanelElement")
    public static void expandableConfiguratorsBuildAndRebuildPanelTree(GameTestHelper helper) {
        LDLib2ConfiguratorPanelElement panel = new LDLib2ConfiguratorPanelElement(new TestMachineUIHolder(), 0, 0);

        panel.attachConfigurators(configurator("first"), configurator("second"));

        helper.assertTrue(panel.getChildren().size() == 2, "panel should contain one tab per configurator");
        helper.assertTrue(panel.getPanelContentHeight() == 50,
                "panel content height should account for two 24px tabs and one 2px gap");

        UIElement firstTab = panel.getChildren().getFirst();
        UIElement secondTab = panel.getChildren().get(1);
        assertExpandableTabInitialState(helper, firstTab, "first");
        assertExpandableTabInitialState(helper, secondTab, "second");

        panel.expandTab((Tab) firstTab);

        assertViewState(helper, firstTab.getChildren().get(1), true, true, "first expanded view");
        assertViewState(helper, secondTab.getChildren().get(1), false, false, "second collapsed view");

        panel.collapseTab();

        assertViewState(helper, firstTab.getChildren().get(1), false, false, "first collapsed view");
        assertViewState(helper, secondTab.getChildren().get(1), false, false, "second collapsed view");

        panel.setBorder(2);
        helper.assertTrue(panel.getChildren().size() == 2, "setBorder rebuild should not accumulate tab children");

        panel.setTexture(IGuiTexture.EMPTY);
        helper.assertTrue(panel.getChildren().size() == 2, "setTexture rebuild should not accumulate tab children");

        panel.clear();

        helper.assertTrue(panel.getChildren().isEmpty(), "clear should remove all tab children");
        helper.assertTrue(panel.getPanelContentHeight() == 0, "clear should reset panel content height");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2ConfiguratorPanelElement")
    public static void buttonConfiguratorsCreateButtonOnlyTabs(GameTestHelper helper) {
        LDLib2ConfiguratorPanelElement panel = new LDLib2ConfiguratorPanelElement(new TestMachineUIHolder(), 0, 0);

        panel.attachConfigurators(new ButtonOnlyConfigurator());

        helper.assertTrue(panel.getChildren().size() == 1, "panel should contain one button configurator tab");
        helper.assertTrue(panel.getChildren().getFirst().getChildren().size() == 1,
                "button configurator tab should contain only the button child");
        helper.assertTrue(panel.getChildren().getFirst().getChildren().getFirst() instanceof GTButtonElement,
                "button configurator tab child should be the tab button");
        helper.succeed();
    }

    private static void assertExpandableTabInitialState(GameTestHelper helper, UIElement tab, String name) {
        helper.assertTrue(tab.getChildren().size() == 2, name + " tab should contain button and view children");
        helper.assertTrue(tab.getChildren().getFirst() instanceof GTButtonElement,
                name + " tab first child should be the tab button");
        assertViewState(helper, tab.getChildren().get(1), false, false, name + " initial view");
    }

    private static void assertViewState(GameTestHelper helper, UIElement view, boolean visible, boolean active,
                                        String name) {
        helper.assertTrue(view.isVisible() == visible, name + " visibility should match expected state");
        helper.assertTrue(view.isActive() == active, name + " active state should match expected state");
    }

    private static LDLib2FancyConfigurator configurator(String title) {
        return new TestLDLib2FancyConfigurator(title);
    }

    private record TestLDLib2FancyConfigurator(String title) implements LDLib2FancyConfigurator {

        @Override
        public Component getTitle() {
            return Component.literal(title);
        }

        @Override
        public IGuiTexture getIcon() {
            return IGuiTexture.EMPTY;
        }

        @Override
        public UIElement createLDLib2Configurator() {
            return new UIElement();
        }
    }

    private static class ButtonOnlyConfigurator implements LDLib2FancyConfiguratorButton {

        @Override
        public IGuiTexture getIcon() {
            return IGuiTexture.EMPTY;
        }

        @Override
        public void onClick(UIEvent event) {
            event.hasHandler = true;
        }
    }

    private record TestMachineUIHolder() implements MachineUIHolder {

        @Override
        public BlockPos getPos() {
            return BlockPos.ZERO;
        }

        @Override
        public ResourceLocation getMachineDefinitionId() {
            return GTCEu.id("test_machine");
        }

        @Override
        public @Nullable MetaMachine getMachine() {
            return null;
        }
    }
}

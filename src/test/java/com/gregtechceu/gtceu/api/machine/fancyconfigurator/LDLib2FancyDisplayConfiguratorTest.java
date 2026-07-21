package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LDLib2FancyDisplayConfiguratorTest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2FancyDisplayConfigurator")
    public static void itemConfiguratorBuildsDisplayOnlySlotTree(GameTestHelper helper) {
        Component title = Component.literal("item display");
        LDLib2FancyInvConfigurator configurator = new LDLib2FancyInvConfigurator(new CustomItemStackHandler(4), title);

        assertItemTitleAndTooltips(helper, configurator, title);
        assertConfiguratorSize(helper, configurator.getLDLib2ConfiguratorWidth(),
                configurator.getLDLib2ConfiguratorHeight(), 18 * 2 + 16, 18 * 2 + 16, "4 item slots");
        assertItemTree(helper, configurator.createLDLib2Configurator(), 4, true);

        LDLib2FancyInvConfigurator eightSlotConfigurator = new LDLib2FancyInvConfigurator(
                new CustomItemStackHandler(8), Component.literal("eight item display"));
        assertConfiguratorSize(helper, eightSlotConfigurator.getLDLib2ConfiguratorWidth(),
                eightSlotConfigurator.getLDLib2ConfiguratorHeight(), 18 * 4 + 16, 18 * 2 + 16, "8 item slots");
        assertItemTree(helper, eightSlotConfigurator.createLDLib2Configurator(), 8, false);

        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2FancyDisplayConfigurator")
    public static void tankConfiguratorBuildsDisplayOnlySlotTree(GameTestHelper helper) {
        Component title = Component.literal("tank display");
        CustomFluidTank[] tanks = createTanks(4, 1000);
        LDLib2FancyTankConfigurator configurator = new LDLib2FancyTankConfigurator(tanks, title);

        assertTankTitleAndTooltips(helper, configurator, title);
        assertConfiguratorSize(helper, configurator.getLDLib2ConfiguratorWidth(),
                configurator.getLDLib2ConfiguratorHeight(), 18 * 2 + 16, 18 * 2 + 16, "4 fluid tanks");
        assertTankTree(helper, configurator.createLDLib2Configurator(), tanks, true);

        CustomFluidTank[] eightTanks = createTanks(8, 1000);
        LDLib2FancyTankConfigurator eightTankConfigurator = new LDLib2FancyTankConfigurator(
                eightTanks, Component.literal("eight tank display"));
        assertConfiguratorSize(helper, eightTankConfigurator.getLDLib2ConfiguratorWidth(),
                eightTankConfigurator.getLDLib2ConfiguratorHeight(), 18 * 4 + 16, 18 * 2 + 16, "8 fluid tanks");
        assertTankTree(helper, eightTankConfigurator.createLDLib2Configurator(), eightTanks, false);

        helper.succeed();
    }

    private static void assertItemTitleAndTooltips(GameTestHelper helper, LDLib2FancyInvConfigurator configurator,
                                                   Component title) {
        helper.assertTrue(configurator.getTitle() == title, "item configurator title should be the constructor title");
        helper.assertTrue(configurator.getTooltips().isEmpty(), "item configurator default tooltips should be empty");

        List<Component> tooltips = List.of(Component.literal("item tooltip"));
        helper.assertTrue(configurator.setTooltips(tooltips) == configurator,
                "item configurator setTooltips should return itself");
        helper.assertTrue(configurator.getTooltips().equals(tooltips),
                "item configurator setTooltips should replace tooltip list");
    }

    private static void assertTankTitleAndTooltips(GameTestHelper helper, LDLib2FancyTankConfigurator configurator,
                                                   Component title) {
        helper.assertTrue(configurator.getTitle() == title, "tank configurator title should be the constructor title");
        helper.assertTrue(configurator.getTooltips().isEmpty(), "tank configurator default tooltips should be empty");

        List<Component> tooltips = List.of(Component.literal("tank tooltip"));
        helper.assertTrue(configurator.setTooltips(tooltips) == configurator,
                "tank configurator setTooltips should return itself");
        helper.assertTrue(configurator.getTooltips().equals(tooltips),
                "tank configurator setTooltips should replace tooltip list");
    }

    private static void assertConfiguratorSize(GameTestHelper helper, int width, int height, int expectedWidth,
                                               int expectedHeight, String name) {
        helper.assertTrue(width == expectedWidth, name + " configurator width should match the display grid");
        helper.assertTrue(height == expectedHeight, name + " configurator height should match the display grid");
    }

    private static void assertItemTree(GameTestHelper helper, UIElement root, int slotCount,
                                       boolean assertSlotDetails) {
        UIElement container = assertSingleContainer(helper, root);
        List<UIElement> children = container.getChildren();
        helper.assertTrue(children.size() == slotCount, "item container should contain " + slotCount + " item slots");

        for (int slotIndex = 0; slotIndex < children.size(); slotIndex++) {
            UIElement child = children.get(slotIndex);
            if (!(child instanceof GTItemSlotElement slotElement)) {
                helper.fail("item container child " + slotIndex + " was not a GT item slot element");
                return;
            }
            if (assertSlotDetails) {
                helper.assertTrue(slotElement.getSlot().getSlotIndex() == slotIndex,
                        "item slot index should match child order " + slotIndex);
                helper.assertTrue(slotElement.getIngredientIO() == IngredientIO.INPUT,
                        "item slot ingredient IO should be INPUT");
            }
        }
    }

    private static void assertTankTree(GameTestHelper helper, UIElement root, CustomFluidTank[] tanks,
                                       boolean assertSlotDetails) {
        UIElement container = assertSingleContainer(helper, root);
        List<UIElement> children = container.getChildren();
        helper.assertTrue(children.size() == tanks.length,
                "tank container should contain " + tanks.length + " fluid slots");

        for (int tankIndex = 0; tankIndex < children.size(); tankIndex++) {
            UIElement child = children.get(tankIndex);
            if (!(child instanceof GTFluidSlotElement slotElement)) {
                helper.fail("tank container child " + tankIndex + " was not a GT fluid slot element");
                return;
            }
            if (assertSlotDetails) {
                helper.assertFalse(slotElement.isAllowClickFilled(), "fluid slot should not allow fill clicks");
                helper.assertFalse(slotElement.isAllowClickDrained(), "fluid slot should not allow drain clicks");
                helper.assertTrue(slotElement.getCapacity() == tanks[tankIndex].getCapacity(),
                        "fluid slot capacity should match tank " + tankIndex);
            }
        }
    }

    private static UIElement assertSingleContainer(GameTestHelper helper, UIElement root) {
        helper.assertTrue(root.getChildren().size() == 1, "configurator root should contain one container child");
        return root.getChildren().getFirst();
    }

    private static CustomFluidTank[] createTanks(int count, int capacity) {
        CustomFluidTank[] tanks = new CustomFluidTank[count];
        for (int index = 0; index < tanks.length; index++) {
            tanks[index] = new CustomFluidTank(capacity);
        }
        return tanks;
    }
}

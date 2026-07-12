package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.element.GTProgressBarElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolderContext;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2RecipeFancyUIMachine;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI.LDLib2RecipeUISize;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class SimpleGeneratorMachineLDLib2UITest {

    private static final int ENERGY_BAR_WIDTH = 18;
    private static final int ENERGY_BAR_HEIGHT = 60;
    private static final int RECIPE_ENERGY_GAP = 4;
    private static final int PAGE_HORIZONTAL_PADDING = 8;
    private static final int PAGE_VERTICAL_PADDING = 8;
    private static final int MIN_PAGE_WIDTH = 172;

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SimpleGeneratorMachineLDLib2UI")
    public static void recipePagePreservesGeneratorEnergyLayout(GameTestHelper helper) {
        SimpleGeneratorMachine machine = (SimpleGeneratorMachine) TestUtils.setMachine(helper,
                new BlockPos(1, 1, 1), GTMachines.COMBUSTION[GTValues.LV]);
        LDLib2RecipeFancyUIMachine uiMachine = machine;
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MachineUIHolder holder = new MachineUIHolderContext(player, machine);
        LDLib2RecipeUISize recipeSize = uiMachine.getLDLib2RecipeUISize(machine);
        int expectedWidth = Math.max(
                ENERGY_BAR_WIDTH + RECIPE_ENERGY_GAP + recipeSize.width() + PAGE_HORIZONTAL_PADDING,
                MIN_PAGE_WIDTH);
        int expectedHeight = Math.max(recipeSize.height() + PAGE_VERTICAL_PADDING,
                ENERGY_BAR_HEIGHT + PAGE_VERTICAL_PADDING);

        helper.assertTrue(uiMachine.canCreateLDLib2UI(player, holder),
                "simple generator rejected its own opened holder");
        helper.assertTrue(uiMachine.getLDLib2RecipeMachine() == machine,
                "simple generator did not expose itself as the recipe page owner");
        helper.assertTrue(uiMachine.getLDLib2PageWidth() == expectedWidth &&
                uiMachine.getLDLib2PageHeight() == expectedHeight,
                "simple generator page did not preserve its fixed energy-and-recipe dimensions");
        helper.assertTrue(uiMachine.getLDLib2RecipeTemplateX(machine, recipeSize) ==
                (expectedWidth - ENERGY_BAR_WIDTH - RECIPE_ENERGY_GAP - recipeSize.width()) / 2 +
                        ENERGY_BAR_WIDTH + RECIPE_ENERGY_GAP,
                "simple generator recipe template did not leave room for the energy bar");

        UIElement page = uiMachine.createLDLib2MainPage(null);
        List<UIElement> pageChildren = page.getChildren();
        helper.assertTrue(pageChildren.size() == 2,
                "simple generator page should contain its recipe template and energy bar");
        helper.assertTrue(pageChildren.getLast() instanceof GTProgressBarElement,
                "simple generator page did not append a GTM LDLib2 energy bar");
        GTProgressBarElement energyBar = (GTProgressBarElement) pageChildren.getLast();
        machine.energyContainer.setEnergyStored(machine.energyContainer.getEnergyCapacity() / 2);
        energyBar.screenTick();
        helper.assertTrue(Math.abs(energyBar.getValue() - 0.5f) < 0.0001f,
                "simple generator energy bar did not read the machine energy container");

        UI ui = uiMachine.createLDLib2UI(player, holder);
        helper.assertTrue(ui.getRootElement() instanceof LDLib2FancyMachineUIElement,
                "simple generator did not open through the LDLib2 Fancy shell");
        LDLib2FancyMachineUIElement shell = (LDLib2FancyMachineUIElement) ui.getRootElement();
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == 1,
                "simple generator shell did not preserve its working-enabled configurator");
        helper.succeed();
    }
}

package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolderContext;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2RecipeFancyUIMachine;
import com.gregtechceu.gtceu.api.recipe.ui.GTRecipeTypeUI.LDLib2RecipeUISize;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
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
public class SimpleTieredMachineLDLib2UITest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SimpleTieredMachineLDLib2UI")
    public static void recipePageUsesLDLib2TemplateAndBoundBatterySlot(GameTestHelper helper) {
        SimpleTieredMachine machine = (SimpleTieredMachine) TestUtils.setMachine(helper,
                new BlockPos(1, 1, 1), GTMachines.ARC_FURNACE[GTValues.LV]);
        LDLib2RecipeFancyUIMachine uiMachine = machine;
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MachineUIHolder holder = new MachineUIHolderContext(player, machine);

        helper.assertTrue(uiMachine.canCreateLDLib2UI(player, holder),
                "simple tiered machine rejected its own opened holder");
        helper.assertTrue(uiMachine.getLDLib2RecipeMachine() == machine,
                "simple tiered machine did not expose itself as the recipe page owner");

        LDLib2RecipeUISize recipeSize = uiMachine.getLDLib2RecipeUISize(machine);
        UIElement page = uiMachine.createLDLib2MainPage(null);
        List<UIElement> pageChildren = page.getChildren();

        helper.assertTrue(uiMachine.getLDLib2PageWidth() == recipeSize.width(),
                "simple tiered page width did not match its LDLib2 recipe template");
        helper.assertTrue(uiMachine.getLDLib2PageHeight() ==
                Math.max(recipeSize.height(), LDLib2RecipeFancyUIMachine.MIN_RECIPE_PAGE_HEIGHT),
                "simple tiered page height did not preserve the battery-slot minimum");
        helper.assertTrue(pageChildren.size() == 2,
                "simple tiered page should contain its recipe template and battery slot");
        helper.assertTrue(pageChildren.getLast() instanceof GTItemSlotElement,
                "simple tiered page did not append a GTM LDLib2 battery slot");

        GTItemSlotElement batterySlot = (GTItemSlotElement) pageChildren.getLast();
        helper.assertTrue(batterySlot.getSlot() instanceof ItemHandlerSlot,
                "simple tiered battery slot was not bound through the LDLib2 item-handler slot");
        ItemHandlerSlot handlerSlot = (ItemHandlerSlot) batterySlot.getSlot();
        helper.assertTrue(handlerSlot.getItemHandler() == machine.getChargerInventory() &&
                handlerSlot.getSlotIndex() == 0,
                "simple tiered battery slot did not bind charger inventory slot zero");
        helper.assertFalse(batterySlot.getFullTooltipTexts().isEmpty(),
                "simple tiered battery slot did not preserve its voltage tooltip");

        UI ui = uiMachine.createLDLib2UI(player, holder);
        helper.assertTrue(ui.getRootElement() instanceof LDLib2FancyMachineUIElement,
                "simple tiered machine did not open through the LDLib2 Fancy shell");
        LDLib2FancyMachineUIElement shell = (LDLib2FancyMachineUIElement) ui.getRootElement();
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == 4,
                "simple tiered shell did not preserve working, item/fluid auto-output, and circuit configurators");
        helper.succeed();
    }
}

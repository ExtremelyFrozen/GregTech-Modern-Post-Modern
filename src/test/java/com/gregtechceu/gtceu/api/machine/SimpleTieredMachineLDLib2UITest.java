package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.editor.EditableMachineUI;
import com.gregtechceu.gtceu.api.gui.editor.MachineUIXmlTemplates;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolderContext;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2RecipeFancyUIMachine;
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

        EditableMachineUI template = uiMachine.getLDLib2MachineUITemplate(machine);
        helper.assertTrue(template.getGroupName().equals("simple"),
                "simple machine XML metadata lost its editor template group");
        helper.assertTrue(template.getUiPath().equals(GTCEu.id("arc_furnace")),
                "simple machine XML metadata selected the wrong target path");
        helper.assertTrue(template.getXmlLocation().equals(GTCEu.id("ui/machine/arc_furnace.xml")),
                "simple machine XML metadata did not resolve assets/<namespace>/ui/machine/<path>.xml");
        UI defaultPreview = template.createDefaultUI();
        helper.assertTrue(defaultPreview.rootElement
                .selectId(MachineUIXmlTemplates.BATTERY_SLOT_ID, GTItemSlotElement.class).count() == 1,
                "default simple machine XML did not parse its battery slot");

        var machineSize = uiMachine.getLDLib2MachineUISize(machine);
        UIElement page = uiMachine.createLDLib2MainPage(null);
        List<UIElement> pageChildren = page.getChildren();

        helper.assertTrue(uiMachine.getLDLib2PageWidth() == machineSize.width() &&
                uiMachine.getLDLib2PageHeight() == machineSize.height(),
                "simple tiered page dimensions did not come from the active machine XML");
        helper.assertTrue(machineSize.height() >= 78,
                "simple tiered machine XML did not preserve the battery-slot minimum height");
        helper.assertTrue(pageChildren.size() == 2,
                "simple tiered page should contain its recipe template and battery slot");
        GTItemSlotElement batterySlot = page
                .selectId(MachineUIXmlTemplates.BATTERY_SLOT_ID, GTItemSlotElement.class)
                .findFirst()
                .orElseThrow(() -> new AssertionError("parsed simple machine XML has no battery slot"));
        helper.assertTrue(batterySlot.getSlot() instanceof ItemHandlerSlot,
                "simple tiered battery slot was not bound through the LDLib2 item-handler slot");
        ItemHandlerSlot handlerSlot = (ItemHandlerSlot) batterySlot.getSlot();
        helper.assertTrue(handlerSlot.getItemHandler() == machine.getChargerInventory() &&
                handlerSlot.getSlotIndex() == 0,
                "simple tiered battery slot did not bind charger inventory slot zero");
        helper.assertFalse(batterySlot.getFullTooltipTexts().isEmpty(),
                "simple tiered battery slot did not preserve its voltage tooltip");

        GTItemSlotElement recipeInput = page.selectId("item_in_0", GTItemSlotElement.class)
                .findFirst()
                .orElseThrow(() -> new AssertionError("parsed simple machine XML has no item input slot"));
        helper.assertTrue(recipeInput.getSlot() instanceof ItemHandlerSlot,
                "simple machine recipe input was not bound as an LDLib2 item-handler slot");
        ItemHandlerSlot recipeHandlerSlot = (ItemHandlerSlot) recipeInput.getSlot();
        helper.assertTrue(recipeHandlerSlot.getItemHandler() == machine.importItems.storage &&
                recipeHandlerSlot.getSlotIndex() == 0,
                "simple machine recipe XML did not bind item_in_0 to import slot zero");

        UI ui = uiMachine.createLDLib2UI(player, holder);
        helper.assertTrue(ui.getRootElement() instanceof LDLib2FancyMachineUIElement,
                "simple tiered machine did not open through the LDLib2 Fancy shell");
        LDLib2FancyMachineUIElement shell = (LDLib2FancyMachineUIElement) ui.getRootElement();
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == 4,
                "simple tiered shell did not preserve working, item/fluid auto-output, and circuit configurators");
        helper.succeed();
    }
}

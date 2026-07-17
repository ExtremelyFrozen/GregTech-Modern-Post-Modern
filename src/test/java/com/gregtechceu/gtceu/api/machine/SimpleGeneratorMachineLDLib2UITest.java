package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.editor.EditableMachineUI;
import com.gregtechceu.gtceu.api.gui.editor.MachineUIXmlTemplates;
import com.gregtechceu.gtceu.api.gui.element.GTProgressBarElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolderContext;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2RecipeFancyUIMachine;
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

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SimpleGeneratorMachineLDLib2UI")
    public static void recipePagePreservesGeneratorEnergyLayout(GameTestHelper helper) {
        SimpleGeneratorMachine machine = (SimpleGeneratorMachine) TestUtils.setMachine(helper,
                new BlockPos(1, 1, 1), GTMachines.COMBUSTION[GTValues.LV]);
        LDLib2RecipeFancyUIMachine uiMachine = machine;
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MachineUIHolder holder = new MachineUIHolderContext(player, machine);
        EditableMachineUI template = uiMachine.getLDLib2MachineUITemplate(machine);
        helper.assertTrue(template.getGroupName().equals("generator"),
                "generator machine XML metadata lost its editor template group");
        helper.assertTrue(template.getUiPath().equals(GTCEu.id("combustion")),
                "generator machine XML metadata selected the wrong target path");
        helper.assertTrue(template.getXmlLocation().equals(GTCEu.id("ui/machine/combustion.xml")),
                "generator machine XML metadata did not resolve assets/<namespace>/ui/machine/<path>.xml");
        UI defaultPreview = template.createDefaultUI();
        helper.assertTrue(defaultPreview.rootElement
                .selectId(MachineUIXmlTemplates.ENERGY_BAR_ID, GTProgressBarElement.class).count() == 1,
                "default generator machine XML did not parse its energy bar");
        var machineSize = uiMachine.getLDLib2MachineUISize(machine);

        helper.assertTrue(uiMachine.canCreateLDLib2UI(player, holder),
                "simple generator rejected its own opened holder");
        helper.assertTrue(uiMachine.getLDLib2RecipeMachine() == machine,
                "simple generator did not expose itself as the recipe page owner");
        helper.assertTrue(uiMachine.getLDLib2PageWidth() == machineSize.width() &&
                uiMachine.getLDLib2PageHeight() == machineSize.height(),
                "simple generator page dimensions did not come from the active machine XML");
        helper.assertTrue(machineSize.width() >= 172 && machineSize.height() >= 68,
                "generator machine XML did not preserve the energy-and-recipe minimum dimensions");

        UIElement page = uiMachine.createLDLib2MainPage(null);
        List<UIElement> pageChildren = page.getChildren();
        helper.assertTrue(pageChildren.size() == 2,
                "simple generator page should contain its recipe template and energy bar");
        GTProgressBarElement energyBar = page
                .selectId(MachineUIXmlTemplates.ENERGY_BAR_ID, GTProgressBarElement.class)
                .findFirst()
                .orElseThrow(() -> new AssertionError("parsed generator machine XML has no energy bar"));
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

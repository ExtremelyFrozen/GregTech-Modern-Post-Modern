package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyPreviewPage;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.common.data.GTMachines;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import static com.gregtechceu.gtceu.api.GTValues.IV;
import static com.gregtechceu.gtceu.api.GTValues.LV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class EnergyTransferHatchLDLib2UITest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "EnergyTransferHatchLDLib2UI")
    public static void contextualPagesPreservePreviewGroupingAndHolderIdentity(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        EnergyHatchPartMachine energyInput = createEnergyHatch(GTMachines.ENERGY_INPUT_HATCH[LV]);
        EnergyHatchPartMachine replacementEnergyInput = createEnergyHatch(GTMachines.ENERGY_INPUT_HATCH[LV]);
        LaserHatchPartMachine laserOutput = createLaserHatch(GTMachines.LASER_OUTPUT_HATCH_256[IV]);
        MutableMachineUIHolder energyHolder = new MutableMachineUIHolder(energyInput);
        MutableMachineUIHolder laserHolder = new MutableMachineUIHolder(laserOutput);

        helper.assertTrue(energyInput instanceof LDLib2FancyPartUIProvider,
                "energy hatch did not opt into contextual LDLib2 Fancy pages");
        helper.assertTrue(laserOutput instanceof LDLib2FancyPartUIProvider,
                "laser hatch did not opt into contextual LDLib2 Fancy pages");

        LDLib2FancyUIProvider energyPage = energyInput.createLDLib2FancyPage(player, energyHolder);
        LDLib2FancyUIProvider laserPage = laserOutput.createLDLib2FancyPage(player, laserHolder);
        assertPage(helper, player, energyInput, energyHolder, energyPage,
                "gtpm.multiblock.page_switcher.io.import", 1, "energy input hatch");
        assertPage(helper, player, laserOutput, laserHolder, laserPage,
                "gtpm.multiblock.page_switcher.io.export", 2, "laser output hatch");

        boolean mismatchedHolderRejected = false;
        try {
            energyInput.createLDLib2FancyPage(player, laserHolder);
        } catch (IllegalArgumentException expected) {
            mismatchedHolderRejected = true;
        }
        helper.assertTrue(mismatchedHolderRejected,
                "energy hatch contextual page accepted another machine's holder");

        energyHolder.setMachine(replacementEnergyInput);
        boolean replacedHolderRejected = false;
        try {
            new LDLib2FancyMachineUIElement(energyPage, player.getInventory(), energyHolder,
                    energyPage.getLDLib2PageWidth(), energyPage.getLDLib2PageHeight());
        } catch (IllegalStateException expected) {
            replacedHolderRejected = true;
        }
        helper.assertTrue(replacedHolderRejected,
                "cached energy hatch page accepted another instance with the same definition");
        helper.succeed();
    }

    private static void assertPage(GameTestHelper helper, ServerPlayer player, TieredIOPartMachine machine,
                                   MachineUIHolder holder, LDLib2FancyUIProvider page,
                                   String expectedGroupKey, int expectedGroupWeight, String description) {
        helper.assertTrue(page.getLDLib2PageWidth() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                page.getLDLib2PageHeight() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                description + " did not preserve the 100x100 default preview body");

        LDLib2FancyUIProvider.PageGroupingData grouping = page.getPageGroupingData();
        helper.assertTrue(grouping != null && expectedGroupKey.equals(grouping.groupKey()) &&
                grouping.groupPositionWeight() == expectedGroupWeight,
                description + " exposed incorrect IO grouping metadata");

        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
        helper.assertTrue(shell.getHolder() == holder,
                description + " Fancy shell did not retain the part holder");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == 1,
                description + " did not expose exactly one working-enabled configurator");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                description + " did not expose one stable directional side page");

        UIElement pageRoot = shell.getChildren().getFirst().getChildren().getFirst();
        UITemplate.LDLib2Bounds pageBounds = UITemplate.getLDLib2Bounds(pageRoot);
        helper.assertTrue(pageBounds.width() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                pageBounds.height() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                description + " created a preview element with incorrect bounds");
        helper.assertTrue(pageRoot.getChildren().isEmpty(),
                description + " constructed a client Scene on the GameTest server");
        helper.assertTrue(page.getTitle().equals(Component.translatable(machine.getDefinition().getDescriptionId())),
                description + " did not use its machine definition title");
    }

    private static EnergyHatchPartMachine createEnergyHatch(MachineDefinition definition) {
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (!(machine instanceof EnergyHatchPartMachine energyHatch)) {
            throw new IllegalStateException("Energy hatch definition did not create an energy hatch machine.");
        }
        return energyHatch;
    }

    private static LaserHatchPartMachine createLaserHatch(MachineDefinition definition) {
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (!(machine instanceof LaserHatchPartMachine laserHatch)) {
            throw new IllegalStateException("Laser hatch definition did not create a laser hatch machine.");
        }
        return laserHatch;
    }

    private static final class MutableMachineUIHolder implements MachineUIHolder {

        private MetaMachine machine;

        private MutableMachineUIHolder(MetaMachine machine) {
            this.machine = machine;
        }

        private void setMachine(MetaMachine machine) {
            this.machine = machine;
        }

        @Override
        public BlockPos getPos() {
            return machine.getBlockPos();
        }

        @Override
        public ResourceLocation getMachineDefinitionId() {
            return machine.getDefinition().getId();
        }

        @Override
        public MetaMachine getMachine() {
            return machine;
        }
    }
}

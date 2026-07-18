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
import com.gregtechceu.gtceu.common.data.machines.GTResearchMachines;

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

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class OpticalComputationHatchLDLib2UITest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "OpticalComputationHatchLDLib2UI")
    public static void contextualPreviewsPreserveBothPortsAndHolderIdentity(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        OpticalComputationHatchMachine transmitter = createHatch(GTResearchMachines.COMPUTATION_HATCH_TRANSMITTER);
        OpticalComputationHatchMachine receiver = createHatch(GTResearchMachines.COMPUTATION_HATCH_RECEIVER);
        OpticalComputationHatchMachine replacement = createHatch(GTResearchMachines.COMPUTATION_HATCH_TRANSMITTER);
        MutableMachineUIHolder transmitterHolder = new MutableMachineUIHolder(transmitter);
        MutableMachineUIHolder receiverHolder = new MutableMachineUIHolder(receiver);

        LDLib2FancyPartUIProvider transmitterProvider = transmitter;
        LDLib2FancyPartUIProvider receiverProvider = receiver;
        LDLib2FancyUIProvider transmitterPage = transmitterProvider.createLDLib2FancyPage(player,
                transmitterHolder);
        LDLib2FancyUIProvider receiverPage = receiverProvider.createLDLib2FancyPage(player, receiverHolder);

        assertPreviewPage(helper, player, transmitter, transmitterHolder, transmitterPage, "transmitter");
        assertPreviewPage(helper, player, receiver, receiverHolder, receiverPage, "receiver");

        boolean mismatchedHolderRejected = false;
        try {
            transmitterProvider.createLDLib2FancyPage(player, receiverHolder);
        } catch (IllegalArgumentException expected) {
            mismatchedHolderRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(mismatchedHolderRejected,
                "Optical Computation Hatch contextual page accepted another hatch holder");

        transmitterHolder.setMachine(replacement);
        boolean replacementRejected = false;
        try {
            new LDLib2FancyMachineUIElement(transmitterPage, player.getInventory(), transmitterHolder,
                    transmitterPage.getLDLib2PageWidth(), transmitterPage.getLDLib2PageHeight());
        } catch (IllegalStateException expected) {
            replacementRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(replacementRejected,
                "cached Optical Computation Hatch page accepted a replacement with the same definition");
        helper.succeed();
    }

    private static void assertPreviewPage(GameTestHelper helper, ServerPlayer player,
                                          OpticalComputationHatchMachine machine, MachineUIHolder holder,
                                          LDLib2FancyUIProvider page, String description) {
        helper.assertTrue(page.getPageGroupingData() == null,
                "Optical Computation Hatch " + description + " invented legacy-absent grouping metadata");
        helper.assertTrue(page.getLDLib2PageWidth() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                page.getLDLib2PageHeight() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                "Optical Computation Hatch " + description + " did not preserve the default preview bounds");
        helper.assertTrue(page.getTitle().equals(Component.translatable(machine.getDefinition().getDescriptionId())),
                "Optical Computation Hatch " + description + " did not use its definition title");

        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
        helper.assertTrue(shell.getHolder() == holder,
                "Optical Computation Hatch " + description + " shell lost its dedicated holder");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().isEmpty(),
                "Optical Computation Hatch " + description + " exposed an unsupported working configurator");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                "Optical Computation Hatch " + description + " did not expose one directional side page");

        UIElement pageRoot = shell.getChildren().getFirst().getChildren().getFirst();
        UITemplate.LDLib2Bounds pageBounds = UITemplate.getLDLib2Bounds(pageRoot);
        helper.assertTrue(pageBounds.width() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                pageBounds.height() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                "Optical Computation Hatch " + description + " preview root has incorrect bounds");
        helper.assertTrue(pageRoot.getChildren().isEmpty(),
                "Optical Computation Hatch " + description + " created a client Scene on the GameTest server");
    }

    private static OpticalComputationHatchMachine createHatch(MachineDefinition definition) {
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (!(machine instanceof OpticalComputationHatchMachine hatch)) {
            throw new IllegalStateException("Computation Hatch definition did not create its expected machine.");
        }
        return hatch;
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

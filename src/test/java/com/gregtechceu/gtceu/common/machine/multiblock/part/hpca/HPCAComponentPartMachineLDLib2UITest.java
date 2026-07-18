package com.gregtechceu.gtceu.common.machine.multiblock.part.hpca;

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

import java.util.ArrayList;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class HPCAComponentPartMachineLDLib2UITest {

    private static final List<MachineDefinition> COMPONENT_DEFINITIONS = List.of(
            GTResearchMachines.HPCA_EMPTY_COMPONENT,
            GTResearchMachines.HPCA_COMPUTATION_COMPONENT,
            GTResearchMachines.HPCA_ADVANCED_COMPUTATION_COMPONENT,
            GTResearchMachines.HPCA_HEAT_SINK_COMPONENT,
            GTResearchMachines.HPCA_ACTIVE_COOLER_COMPONENT,
            GTResearchMachines.HPCA_BRIDGE_COMPONENT);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "HPCAComponentPartMachineLDLib2UI")
    public static void everyComponentUsesAHolderScopedContextualPreview(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        List<HPCAComponentPartMachine> components = new ArrayList<>(COMPONENT_DEFINITIONS.size());
        List<MutableMachineUIHolder> holders = new ArrayList<>(COMPONENT_DEFINITIONS.size());
        List<LDLib2FancyUIProvider> pages = new ArrayList<>(COMPONENT_DEFINITIONS.size());

        for (MachineDefinition definition : COMPONENT_DEFINITIONS) {
            HPCAComponentPartMachine component = createComponent(definition);
            MutableMachineUIHolder holder = new MutableMachineUIHolder(component);
            LDLib2FancyPartUIProvider provider = component;
            LDLib2FancyUIProvider page = provider.createLDLib2FancyPage(player, holder);
            assertPreviewPage(helper, player, component, holder, page);
            components.add(component);
            holders.add(holder);
            pages.add(page);
        }

        boolean mismatchedHolderRejected = false;
        try {
            components.getFirst().createLDLib2FancyPage(player, holders.get(1));
        } catch (IllegalArgumentException expected) {
            mismatchedHolderRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(mismatchedHolderRejected,
                "HPCA component contextual page accepted another component holder");

        holders.getFirst().setMachine(createComponent(COMPONENT_DEFINITIONS.getFirst()));
        boolean replacementRejected = false;
        try {
            LDLib2FancyUIProvider firstPage = pages.getFirst();
            new LDLib2FancyMachineUIElement(firstPage, player.getInventory(), holders.getFirst(),
                    firstPage.getLDLib2PageWidth(), firstPage.getLDLib2PageHeight());
        } catch (IllegalStateException expected) {
            replacementRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(replacementRejected,
                "cached HPCA component page accepted a replacement with the same definition");
        helper.succeed();
    }

    private static void assertPreviewPage(GameTestHelper helper, ServerPlayer player,
                                          HPCAComponentPartMachine component, MachineUIHolder holder,
                                          LDLib2FancyUIProvider page) {
        String description = component.getDefinition().getId().toString();
        helper.assertTrue(page.getPageGroupingData() == null,
                description + " invented legacy-absent HPCA component grouping metadata");
        helper.assertTrue(page.getLDLib2PageWidth() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                page.getLDLib2PageHeight() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                description + " did not preserve the default preview bounds");
        helper.assertTrue(page.getTitle().equals(Component.translatable(component.getDefinition().getDescriptionId())),
                description + " did not use its component definition title");

        LDLib2FancyMachineUIElement shell = new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
        helper.assertTrue(shell.getHolder() == holder, description + " shell lost its dedicated component holder");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().isEmpty(),
                description + " exposed an unsupported working configurator");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                description + " did not expose one directional side page");

        UIElement pageRoot = shell.getChildren().getFirst().getChildren().getFirst();
        UITemplate.LDLib2Bounds pageBounds = UITemplate.getLDLib2Bounds(pageRoot);
        helper.assertTrue(pageBounds.width() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                pageBounds.height() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                description + " preview root has incorrect bounds");
        helper.assertTrue(pageRoot.getChildren().isEmpty(),
                description + " created a client Scene on the GameTest server");
    }

    private static HPCAComponentPartMachine createComponent(MachineDefinition definition) {
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (!(machine instanceof HPCAComponentPartMachine component)) {
            throw new IllegalStateException("HPCA component definition did not create its expected machine.");
        }
        return component;
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

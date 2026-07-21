package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyPreviewPage;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;

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

import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class TankValvePartMachineLDLib2UITest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "TankValvePartMachineLDLib2UI")
    public static void definitionsExposeHolderScopedContextualPreviewWithoutStandaloneUI(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        List<MachineDefinition> definitions = List.of(
                GTMultiMachines.WOODEN_TANK_VALVE,
                GTMultiMachines.BRONZE_TANK_VALVE,
                GTMultiMachines.STEEL_TANK_VALVE);
        List<MetaMachine> definitionInstances = List.of(
                createDefinitionInstance(definitions.get(0), helper.absolutePos(new BlockPos(2, 3, 2))),
                createDefinitionInstance(definitions.get(1), helper.absolutePos(new BlockPos(3, 3, 2))),
                createDefinitionInstance(definitions.get(2), helper.absolutePos(new BlockPos(4, 3, 2))));

        for (MetaMachine definitionInstance : definitionInstances) {
            helper.assertTrue(definitionInstance instanceof LDLib2FancyPartUIProvider,
                    "Tank Valve definition instance did not opt into contextual LDLib2 Fancy pages");
            helper.assertFalse(definitionInstance instanceof LDLib2MachineUIProvider,
                    "Tank Valve definition instance unexpectedly exposed a standalone LDLib2 UI");
        }

        MachineDefinition definition = definitions.getFirst();
        TankValvePartMachine machine = requireTankValve(definitionInstances.getFirst());
        machine.setLevel(helper.getLevel());
        MutableMachineUIHolder holder = new MutableMachineUIHolder(machine);
        LDLib2FancyPartUIProvider provider = machine;

        LDLib2FancyUIProvider page = provider.createLDLib2FancyPage(player, holder);
        helper.assertTrue(page != provider.createLDLib2FancyPage(player, holder),
                "Tank Valve reused a contextual preview page across menu openings");
        helper.assertTrue(page.getLDLib2PageWidth() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                page.getLDLib2PageHeight() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                "Tank Valve contextual preview lost its 100x100 bounds");

        Component expectedTitle = Component.translatable(definition.getDescriptionId());
        helper.assertTrue(page.getTitle().equals(expectedTitle) &&
                page.getTabTooltips().equals(List.of(expectedTitle)),
                "Tank Valve contextual preview did not use its definition title and tab tooltip");
        helper.assertTrue(page.getPageGroupingData() == null,
                "Tank Valve contextual preview invented grouping metadata");

        LDLib2FancyMachineUIElement shell = createShell(player, holder, page);
        helper.assertTrue(shell.getHolder() == holder,
                "Tank Valve contextual shell lost its opening holder");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().isEmpty(),
                "Tank Valve contextual preview exposed an unsupported working configurator");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                "Tank Valve contextual preview did not expose exactly one directional tab");

        int expectedTooltips = machine.showFancyTooltip() ? 1 : 0;
        expectedTooltips += (int) machine.getTraitHolder().getAllTraits().stream()
                .filter(IFancyTooltip.class::isInstance)
                .map(IFancyTooltip.class::cast)
                .filter(IFancyTooltip::showFancyTooltip)
                .count();
        helper.assertTrue(expectedTooltips > 0 &&
                shell.getTooltipsPanel().getChildren().size() == expectedTooltips,
                "Tank Valve contextual preview did not attach its default machine and trait tooltips");

        UIElement previewRoot = shell.getChildren().getFirst().getChildren().getFirst();
        UITemplate.LDLib2Bounds previewBounds = UITemplate.getLDLib2Bounds(previewRoot);
        helper.assertTrue(previewBounds.width() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                previewBounds.height() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                "Tank Valve contextual preview created a root with incorrect bounds");
        helper.assertTrue(previewRoot.getChildren().isEmpty(),
                "Tank Valve contextual preview constructed a client Scene on the GameTest server");

        MachineUIHolder wrongHolder = new MutableMachineUIHolder(requireTankValve(definitionInstances.get(1)));
        boolean wrongHolderRejected = false;
        try {
            provider.createLDLib2FancyPage(player, wrongHolder);
        } catch (IllegalArgumentException expected) {
            wrongHolderRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(wrongHolderRejected,
                "Tank Valve contextual preview accepted another definition instance's holder");

        LDLib2FancyUIProvider stalePage = provider.createLDLib2FancyPage(player, holder);
        TankValvePartMachine replacement = requireTankValve(
                createDefinitionInstance(definition, machine.getBlockPos()));
        replacement.setLevel(helper.getLevel());
        holder.setMachine(replacement);

        boolean staleHolderRejected = false;
        try {
            createShell(player, holder, stalePage);
        } catch (IllegalStateException expected) {
            staleHolderRejected = expected.getMessage().contains("no longer");
        }
        helper.assertTrue(staleHolderRejected,
                "Tank Valve contextual preview accepted a same-definition, same-position holder replacement");
        helper.succeed();
    }

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player, MachineUIHolder holder,
                                                           LDLib2FancyUIProvider page) {
        return new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
    }

    private static MetaMachine createDefinitionInstance(MachineDefinition definition, BlockPos pos) {
        return definition.getBlockEntityType().create(pos, definition.defaultBlockState());
    }

    private static TankValvePartMachine requireTankValve(MetaMachine machine) {
        if (machine instanceof TankValvePartMachine tankValve) {
            return tankValve;
        }
        throw new IllegalStateException("Tank Valve definition did not create its expected machine.");
    }

    private static final class MutableMachineUIHolder implements MachineUIHolder {

        private final BlockPos pos;
        private final ResourceLocation definitionId;
        private MetaMachine machine;

        private MutableMachineUIHolder(MetaMachine machine) {
            this.pos = machine.getBlockPos();
            this.definitionId = machine.getDefinition().getId();
            this.machine = machine;
        }

        @Override
        public BlockPos getPos() {
            return pos;
        }

        @Override
        public ResourceLocation getMachineDefinitionId() {
            return definitionId;
        }

        @Override
        public MetaMachine getMachine() {
            return machine;
        }

        private void setMachine(MetaMachine machine) {
            this.machine = machine;
        }
    }
}

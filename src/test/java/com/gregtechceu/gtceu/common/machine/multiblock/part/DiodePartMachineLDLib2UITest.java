package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyPreviewPage;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.common.data.GTMachines;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

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
public class DiodePartMachineLDLib2UITest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "DiodePartMachineLDLib2UI")
    public static void contextualPreviewPreservesMetadataControlsAndOpeningHolder(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MachineDefinition definition = GTMachines.DIODE[GTValues.HV];
        MetaMachine definitionInstance = createDefinitionInstance(definition,
                helper.absolutePos(new BlockPos(2, 3, 2)));
        helper.assertTrue(definitionInstance instanceof LDLib2FancyPartUIProvider,
                "Diode definition instance did not opt into contextual LDLib2 Fancy pages");

        DiodePartMachine machine = requireDiode(definitionInstance);
        machine.setLevel(helper.getLevel());
        MutableMachineUIHolder holder = new MutableMachineUIHolder(machine);
        LDLib2FancyPartUIProvider provider = machine;

        LDLib2FancyUIProvider page = provider.createLDLib2FancyPage(player, holder);
        helper.assertTrue(page != provider.createLDLib2FancyPage(player, holder),
                "Diode reused a contextual preview page across menu openings");
        helper.assertTrue(page.getLDLib2PageWidth() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                page.getLDLib2PageHeight() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                "Diode contextual preview lost its 100x100 bounds");

        Component expectedTitle = Component.translatable(definition.getDescriptionId());
        helper.assertTrue(page.getTitle().equals(expectedTitle) &&
                page.getTabTooltips().equals(List.of(expectedTitle)),
                "Diode contextual preview did not use its definition title and tab tooltip");
        LDLib2FancyUIProvider.PageGroupingData grouping = page.getPageGroupingData();
        helper.assertTrue(grouping != null &&
                "gtpm.multiblock.page_switcher.io.both".equals(grouping.groupKey()) &&
                grouping.groupPositionWeight() == 3,
                "Diode contextual preview lost its explicit bidirectional grouping");

        LDLib2FancyMachineUIElement shell = createShell(player, holder, page);
        helper.assertTrue(shell.getHolder() == holder,
                "Diode contextual shell lost its opening holder");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == 1,
                "Diode contextual preview did not expose exactly one working configurator");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                "Diode contextual preview did not expose exactly one directional tab");

        int expectedTooltips = machine.showFancyTooltip() ? 1 : 0;
        expectedTooltips += (int) machine.getTraitHolder().getAllTraits().stream()
                .filter(IFancyTooltip.class::isInstance)
                .map(IFancyTooltip.class::cast)
                .filter(IFancyTooltip::showFancyTooltip)
                .count();
        helper.assertTrue(expectedTooltips > 0 &&
                shell.getTooltipsPanel().getChildren().size() == expectedTooltips,
                "Diode contextual preview did not attach its default machine and trait tooltips");

        UIElement previewRoot = shell.getChildren().getFirst().getChildren().getFirst();
        helper.assertTrue(previewRoot.getSizeWidth() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                previewRoot.getSizeHeight() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                "Diode contextual preview created a root with incorrect bounds");
        helper.assertTrue(previewRoot.getChildren().isEmpty(),
                "Diode contextual preview constructed a client Scene on the GameTest server");

        UIElement configuratorButton = shell.getConfiguratorPanel().getChildren().getFirst()
                .getChildren().getFirst();
        helper.assertTrue(configuratorButton instanceof GTButtonElement,
                "Diode working configurator did not expose its public button entry");
        UIEvent freshClick = dispatchClick(configuratorButton);
        helper.assertFalse(freshClick.hasHandler,
                "Diode server-side working configurator click was marked as a sent client action");

        DiodePartMachine wrongMachine = requireDiode(createDefinitionInstance(
                GTMachines.DIODE[GTValues.EV], helper.absolutePos(new BlockPos(3, 3, 2))));
        MachineUIHolder wrongHolder = new MutableMachineUIHolder(wrongMachine);
        boolean wrongHolderRejected = false;
        try {
            provider.createLDLib2FancyPage(player, wrongHolder);
        } catch (IllegalArgumentException expected) {
            wrongHolderRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(wrongHolderRejected,
                "Diode contextual preview accepted another definition instance's holder");

        LDLib2FancyUIProvider stalePage = provider.createLDLib2FancyPage(player, holder);
        DiodePartMachine replacement = requireDiode(createDefinitionInstance(
                definition, machine.getBlockPos()));
        replacement.setLevel(helper.getLevel());
        holder.setMachine(replacement);

        boolean staleConfiguratorRejected = false;
        try {
            dispatchClick(configuratorButton);
        } catch (IllegalStateException expected) {
            staleConfiguratorRejected = expected.getMessage().contains("no longer");
        }
        helper.assertTrue(staleConfiguratorRejected,
                "Diode working configurator accepted a same-definition replacement holder");

        boolean stalePageRejected = false;
        try {
            createShell(player, holder, stalePage);
        } catch (IllegalStateException expected) {
            stalePageRejected = expected.getMessage().contains("no longer");
        }
        helper.assertTrue(stalePageRejected,
                "Diode contextual preview accepted a same-definition replacement holder");
        helper.succeed();
    }

    private static UIEvent dispatchClick(UIElement target) {
        UIEvent event = UIEvent.create(UIEvents.CLICK);
        event.target = target;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
        return event;
    }

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player, MachineUIHolder holder,
                                                           LDLib2FancyUIProvider page) {
        return new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
    }

    private static MetaMachine createDefinitionInstance(MachineDefinition definition, BlockPos pos) {
        return definition.getBlockEntityType().create(pos, definition.defaultBlockState());
    }

    private static DiodePartMachine requireDiode(MetaMachine machine) {
        if (machine instanceof DiodePartMachine diode) {
            return diode;
        }
        throw new IllegalStateException("Diode definition did not create its expected machine.");
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

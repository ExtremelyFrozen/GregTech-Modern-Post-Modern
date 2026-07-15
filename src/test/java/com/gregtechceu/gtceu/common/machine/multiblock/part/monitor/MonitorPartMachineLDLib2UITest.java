package com.gregtechceu.gtceu.common.machine.multiblock.part.monitor;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
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

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class MonitorPartMachineLDLib2UITest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MonitorPartMachineLDLib2UI")
    public static void monitorFamilyUsesHolderScopedContextualPreview(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MonitorPartMachine monitor = requireMonitor(createDefinitionInstance(GTMachines.MONITOR,
                helper.absolutePos(new BlockPos(2, 3, 2))));
        AdvancedMonitorPartMachine advancedMonitor = requireAdvancedMonitor(createDefinitionInstance(
                GTMachines.ADVANCED_MONITOR, helper.absolutePos(new BlockPos(3, 3, 2))));
        monitor.setLevel(helper.getLevel());
        advancedMonitor.setLevel(helper.getLevel());

        assertFamilyContract(helper, player, monitor);
        assertFamilyContract(helper, player, advancedMonitor);

        MutableMachineUIHolder holder = new MutableMachineUIHolder(monitor);
        LDLib2FancyUIProvider page = monitor.createLDLib2FancyPage(player, holder);
        helper.assertTrue(page != monitor.createLDLib2FancyPage(player, holder),
                "Monitor reused a contextual preview page across menu openings");
        helper.assertTrue(page.getLDLib2PageWidth() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                page.getLDLib2PageHeight() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                "Monitor contextual preview lost its 100x100 bounds");

        Component expectedTitle = Component.translatable(GTMachines.MONITOR.getDescriptionId());
        helper.assertTrue(page.getTitle().equals(expectedTitle) &&
                page.getTabTooltips().equals(List.of(expectedTitle)),
                "Monitor contextual preview did not use its definition title and tab tooltip");
        helper.assertTrue(page.getPageGroupingData() == null,
                "Monitor contextual preview invented legacy-absent grouping metadata");

        LDLib2FancyMachineUIElement shell = createShell(player, holder, page);
        helper.assertTrue(shell.getHolder() == holder,
                "Monitor contextual shell lost its opening holder");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().isEmpty(),
                "Monitor contextual preview exposed an unsupported configurator");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                "Monitor contextual preview did not expose exactly one directional tab");

        int expectedTooltips = monitor.showFancyTooltip() ? 1 : 0;
        expectedTooltips += (int) monitor.getTraitHolder().getAllTraits().stream()
                .filter(IFancyTooltip.class::isInstance)
                .map(IFancyTooltip.class::cast)
                .filter(IFancyTooltip::showFancyTooltip)
                .count();
        helper.assertTrue(expectedTooltips > 0 &&
                shell.getTooltipsPanel().getChildren().size() == expectedTooltips,
                "Monitor contextual preview did not attach its default machine and trait tooltips");

        UIElement previewRoot = shell.getChildren().getFirst().getChildren().getFirst();
        helper.assertTrue(previewRoot.getSizeWidth() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                previewRoot.getSizeHeight() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                "Monitor contextual preview created a root with incorrect bounds");
        helper.assertTrue(previewRoot.getChildren().isEmpty(),
                "Monitor contextual preview constructed a client Scene on the GameTest server");

        boolean wrongHolderRejected = false;
        try {
            monitor.createLDLib2FancyPage(player, new MutableMachineUIHolder(advancedMonitor));
        } catch (IllegalArgumentException expected) {
            wrongHolderRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(wrongHolderRejected,
                "Monitor contextual preview accepted another monitor definition's holder");

        LDLib2FancyUIProvider stalePage = monitor.createLDLib2FancyPage(player, holder);
        MonitorPartMachine replacement = requireMonitor(createDefinitionInstance(
                GTMachines.MONITOR, monitor.getBlockPos()));
        replacement.setLevel(helper.getLevel());
        holder.setMachine(replacement);

        boolean staleHolderRejected = false;
        try {
            createShell(player, holder, stalePage);
        } catch (IllegalStateException expected) {
            staleHolderRejected = expected.getMessage().contains("no longer");
        }
        helper.assertTrue(staleHolderRejected,
                "Monitor contextual preview accepted a same-definition replacement at the same position");
        helper.succeed();
    }

    private static void assertFamilyContract(GameTestHelper helper, Player player,
                                             MonitorComponentPartMachine machine) {
        String description = machine.getDefinition().getId().toString();
        helper.assertTrue(machine instanceof LDLib2FancyPartUIProvider,
                description + " did not explicitly opt into contextual LDLib2 Fancy pages");
        helper.assertFalse(machine instanceof LDLib2MachineUIProvider,
                description + " unexpectedly opted into a standalone LDLib2 machine UI");
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(machine.getBlockPos()), Direction.NORTH,
                machine.getBlockPos(), false);
        helper.assertFalse(machine.shouldOpenUI(player, InteractionHand.MAIN_HAND, hit),
                description + " changed its monitor click behavior");

        MutableMachineUIHolder holder = new MutableMachineUIHolder(machine);
        LDLib2FancyPartUIProvider provider = machine;
        helper.assertTrue(provider.createLDLib2FancyPage(player, holder) !=
                provider.createLDLib2FancyPage(player, holder),
                description + " reused its contextual page across menu openings");
    }

    private static LDLib2FancyMachineUIElement createShell(Player player, MachineUIHolder holder,
                                                           LDLib2FancyUIProvider page) {
        return new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
    }

    private static MetaMachine createDefinitionInstance(MachineDefinition definition, BlockPos pos) {
        return definition.getBlockEntityType().create(pos, definition.defaultBlockState());
    }

    private static MonitorPartMachine requireMonitor(MetaMachine machine) {
        if (machine instanceof MonitorPartMachine monitor && !(monitor instanceof AdvancedMonitorPartMachine)) {
            return monitor;
        }
        throw new IllegalStateException("Monitor definition did not create its expected concrete machine.");
    }

    private static AdvancedMonitorPartMachine requireAdvancedMonitor(MetaMachine machine) {
        if (machine instanceof AdvancedMonitorPartMachine monitor) {
            return monitor;
        }
        throw new IllegalStateException("Advanced monitor definition did not create its expected concrete machine.");
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

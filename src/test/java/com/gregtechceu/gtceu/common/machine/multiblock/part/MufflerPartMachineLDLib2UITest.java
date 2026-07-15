package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
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
public class MufflerPartMachineLDLib2UITest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "MufflerPartMachineLDLib2UI")
    public static void contextualPreviewPreservesHolderLifecycleAndStandaloneInventory(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MufflerPartMachine machine = createMachine();
        MufflerPartMachine replacement = createMachine();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(machine);
        MachineUIHolder replacementHolder = new MutableMachineUIHolder(replacement);

        helper.assertTrue(machine.canCreateLDLib2UI(player, holder),
                "Muffler Hatch rejected its matching standalone holder");
        helper.assertFalse(machine.canCreateLDLib2UI(player, replacementHolder),
                "Muffler Hatch accepted another machine's standalone holder");
        UIElement standaloneRoot = machine.createLDLib2UI(player, holder).getRootElement();
        helper.assertTrue(standaloneRoot.getSizeWidth() == 176 && standaloneRoot.getSizeHeight() == 148,
                "LV Muffler Hatch standalone inventory lost its bounds");
        helper.assertTrue(descendants(standaloneRoot).stream()
                .filter(GTItemSlotElement.class::isInstance)
                .count() == 40,
                "LV Muffler Hatch standalone UI lost its four recovery and 36 player slots");

        LDLib2FancyPartUIProvider provider = machine;
        LDLib2FancyUIProvider page = provider.createLDLib2FancyPage(player, holder);
        helper.assertTrue(page != provider.createLDLib2FancyPage(player, holder),
                "Muffler Hatch reused a contextual preview across openings");
        helper.assertTrue(page.getPageGroupingData() == null,
                "Muffler Hatch contextual preview invented grouping metadata");
        helper.assertTrue(page.getLDLib2PageWidth() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                page.getLDLib2PageHeight() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                "Muffler Hatch contextual preview lost its default bounds");
        helper.assertTrue(page.getTitle().equals(Component.translatable(machine.getDefinition().getDescriptionId())),
                "Muffler Hatch contextual preview did not use its definition title");

        LDLib2FancyMachineUIElement shell = createShell(player, holder, page);
        helper.assertTrue(shell.getHolder() == holder,
                "Muffler Hatch contextual shell lost its dedicated holder");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().isEmpty(),
                "Muffler Hatch exposed an unsupported working configurator");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                "Muffler Hatch did not expose exactly one contextual directional side page");
        int expectedTooltips = machine.showFancyTooltip() ? 1 : 0;
        expectedTooltips += (int) machine.getTraitHolder().getAllTraits().stream()
                .filter(IFancyTooltip.class::isInstance)
                .map(IFancyTooltip.class::cast)
                .filter(IFancyTooltip::showFancyTooltip)
                .count();
        helper.assertTrue(expectedTooltips > 0 &&
                shell.getTooltipsPanel().getChildren().size() == expectedTooltips,
                "Muffler Hatch did not attach its default machine and trait tooltips");
        UIElement previewRoot = shell.getChildren().getFirst().getChildren().getFirst();
        helper.assertTrue(previewRoot.getSizeWidth() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                previewRoot.getSizeHeight() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                "Muffler Hatch preview root has incorrect bounds");
        helper.assertTrue(previewRoot.getChildren().isEmpty(),
                "Muffler Hatch created a client Scene on the GameTest server");

        boolean mismatchedHolderRejected = false;
        try {
            provider.createLDLib2FancyPage(player, replacementHolder);
        } catch (IllegalArgumentException expected) {
            mismatchedHolderRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(mismatchedHolderRejected,
                "Muffler Hatch contextual preview accepted another machine's holder");

        LDLib2FancyUIProvider stalePage = provider.createLDLib2FancyPage(player, holder);
        holder.setMachine(replacement);
        boolean staleHolderRejected = false;
        try {
            createShell(player, holder, stalePage);
        } catch (IllegalStateException expected) {
            staleHolderRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(staleHolderRejected,
                "Muffler Hatch contextual preview accepted a same-definition holder replacement");
        helper.succeed();
    }

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player, MachineUIHolder holder,
                                                           LDLib2FancyUIProvider page) {
        return new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
    }

    private static List<UIElement> descendants(UIElement root) {
        List<UIElement> descendants = new ArrayList<>();
        collectDescendants(root, descendants);
        return descendants;
    }

    private static void collectDescendants(UIElement root, List<UIElement> descendants) {
        for (UIElement child : root.getChildren()) {
            descendants.add(child);
            collectDescendants(child, descendants);
        }
    }

    private static MufflerPartMachine createMachine() {
        MachineDefinition definition = GTMachines.MUFFLER_HATCH[GTValues.LV];
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (machine instanceof MufflerPartMachine muffler) {
            return muffler;
        }
        throw new IllegalStateException("Muffler Hatch definition did not create its expected machine.");
    }

    private static final class MutableMachineUIHolder implements MachineUIHolder {

        private MetaMachine machine;

        private MutableMachineUIHolder(MetaMachine machine) {
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

        private void setMachine(MetaMachine machine) {
            this.machine = machine;
        }
    }
}

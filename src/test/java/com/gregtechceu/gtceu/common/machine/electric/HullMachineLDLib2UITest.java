package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyPreviewPage;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.ItemStackTexture;
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

import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class HullMachineLDLib2UITest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "HullMachineLDLib2UI")
    public static void registeredTiersExposeHolderScopedContextualPreview(GameTestHelper helper) {
        for (int tier : GTValues.ALL_TIERS) {
            MetaMachine definitionInstance = createDefinitionInstance(GTMachines.HULL[tier], BlockPos.ZERO);
            helper.assertTrue(definitionInstance instanceof HullMachine &&
                    definitionInstance instanceof LDLib2FancyPartUIProvider,
                    "Hull tier did not create its contextual LDLib2 provider: " + tier);
            helper.assertTrue(((HullMachine) definitionInstance).getTier() == tier,
                    "Hull definition changed its registered tier: " + tier);
        }

        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MachineDefinition definition = GTMachines.HULL[GTValues.HV];
        HullMachine machine = requireHull(createDefinitionInstance(
                definition, helper.absolutePos(new BlockPos(2, 3, 2))));
        machine.setLevel(helper.getLevel());
        MutableMachineUIHolder holder = new MutableMachineUIHolder(machine);
        LDLib2FancyPartUIProvider provider = machine;

        LDLib2FancyUIProvider page = provider.createLDLib2FancyPage(player, holder);
        helper.assertTrue(page != provider.createLDLib2FancyPage(player, holder),
                "Hull reused a contextual preview page across menu openings");
        helper.assertTrue(page.getLDLib2PageWidth() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                page.getLDLib2PageHeight() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                "Hull contextual preview lost its 100x100 bounds");

        Component expectedTitle = Component.translatable(definition.getDescriptionId());
        helper.assertTrue(page.getTitle().equals(expectedTitle) &&
                page.getTabTooltips().equals(List.of(expectedTitle)),
                "Hull contextual preview did not use its definition title and tab tooltip");
        helper.assertTrue(page.getTabIcon() instanceof ItemStackTexture icon && icon.items.length == 1 &&
                icon.items[0].getItem() == definition.getItem(),
                "Hull contextual preview did not use its definition item icon");
        helper.assertTrue(page.getPageGroupingData() == null,
                "Hull contextual preview invented grouping metadata");

        LDLib2FancyMachineUIElement shell = createShell(player, holder, page);
        helper.assertTrue(shell.getHolder() == holder,
                "Hull contextual shell lost its opening holder");
        var shellBounds = UITemplate.getLDLib2Bounds(shell);
        helper.assertTrue(shellBounds.width() == 172 && shellBounds.height() == 190,
                "Hull contextual shell lost its preview and player-inventory bounds");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().isEmpty(),
                "Hull contextual preview exposed an unsupported working configurator");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                "Hull contextual preview did not expose exactly one directional tab");

        int expectedTooltips = machine.showFancyTooltip() ? 1 : 0;
        expectedTooltips += (int) machine.getTraitHolder().getAllTraits().stream()
                .filter(IFancyTooltip.class::isInstance)
                .map(IFancyTooltip.class::cast)
                .filter(IFancyTooltip::showFancyTooltip)
                .count();
        helper.assertTrue(expectedTooltips > 0 &&
                shell.getTooltipsPanel().getChildren().size() == expectedTooltips,
                "Hull contextual preview did not attach its default machine and trait tooltips");

        UIElement previewRoot = shell.getChildren().getFirst().getChildren().getFirst();
        var previewBounds = UITemplate.getLDLib2Bounds(previewRoot);
        helper.assertTrue(previewBounds.width() == LDLib2FancyPreviewPage.PREVIEW_PAGE_WIDTH &&
                previewBounds.height() == LDLib2FancyPreviewPage.PREVIEW_PAGE_HEIGHT,
                "Hull contextual preview created a root with incorrect bounds");
        helper.assertTrue(previewRoot.getChildren().isEmpty(),
                "Hull contextual preview constructed a client Scene on the GameTest server");

        HullMachine wrongMachine = requireHull(createDefinitionInstance(
                GTMachines.HULL[GTValues.EV], helper.absolutePos(new BlockPos(3, 3, 2))));
        MachineUIHolder wrongHolder = new MutableMachineUIHolder(wrongMachine);
        boolean wrongHolderRejected = false;
        try {
            provider.createLDLib2FancyPage(player, wrongHolder);
        } catch (IllegalArgumentException expected) {
            wrongHolderRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(wrongHolderRejected,
                "Hull contextual preview accepted another definition instance's holder");

        LDLib2FancyUIProvider stalePage = provider.createLDLib2FancyPage(player, holder);
        HullMachine replacement = requireHull(createDefinitionInstance(definition, machine.getBlockPos()));
        replacement.setLevel(helper.getLevel());
        holder.setMachine(replacement);
        boolean staleHolderRejected = false;
        try {
            createShell(player, holder, stalePage);
        } catch (IllegalStateException expected) {
            staleHolderRejected = expected.getMessage().contains("no longer");
        }
        helper.assertTrue(staleHolderRejected,
                "Hull contextual preview accepted a same-definition, same-position holder replacement");
        helper.succeed();
    }

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player, MachineUIHolder holder,
                                                           LDLib2FancyUIProvider page) {
        return new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
    }

    private static MetaMachine createDefinitionInstance(MachineDefinition definition, BlockPos pos) {
        MetaMachine machine = definition.getBlockEntityType().create(pos, definition.defaultBlockState());
        if (machine == null) {
            throw new IllegalStateException("Hull definition did not create a machine: " + definition.getId());
        }
        return machine;
    }

    private static HullMachine requireHull(MetaMachine machine) {
        if (machine instanceof HullMachine hull) {
            return hull;
        }
        throw new IllegalStateException("Hull definition did not create its expected machine.");
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

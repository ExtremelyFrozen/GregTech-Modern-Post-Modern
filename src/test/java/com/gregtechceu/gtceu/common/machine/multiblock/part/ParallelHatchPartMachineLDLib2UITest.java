package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.element.GTIntInputElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.ItemStackTexture;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.data.machines.GCYMMachines;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
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
public class ParallelHatchPartMachineLDLib2UITest {

    private static final String BATCH = "ParallelHatchPartMachineLDLib2UI";

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void standaloneAndContextualInputsPreserveBoundsAndValueSemantics(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ParallelHatchPartMachine machine = createMachine();
        machine.setLevel(helper.getLevel());
        MachineUIHolder holder = new MutableMachineUIHolder(machine);

        helper.assertTrue(machine.canCreateLDLib2UI(player, holder),
                "Parallel Hatch rejected its matching standalone holder");
        UI standalone = machine.createLDLib2UI(player, holder);
        UIElement standaloneRoot = standalone.getRootElement();
        GTIntInputElement standaloneInput = requireOnlyInput(standaloneRoot);
        helper.assertTrue(standaloneRoot.getSizeWidth() == 100 && standaloneRoot.getSizeHeight() == 20 &&
                standaloneInput.getLayoutX() == 0 && standaloneInput.getLayoutY() == 0 &&
                standaloneInput.getSizeWidth() == 100 && standaloneInput.getSizeHeight() == 20,
                "standalone Parallel Hatch input lost its 100x20 bounds");

        standaloneInput.setValue(3);
        helper.assertTrue(machine.getCurrentParallel() == 3,
                "standalone Parallel Hatch input did not update an in-range value");
        standaloneInput.setValue(0);
        helper.assertTrue(machine.getCurrentParallel() == 1,
                "standalone Parallel Hatch input lost its minimum value of 1");
        standaloneInput.setValue(8);
        helper.assertTrue(machine.getCurrentParallel() == 4,
                "standalone IV Parallel Hatch input lost its maximum value of 4");

        LDLib2FancyUIProvider page = machine.createLDLib2FancyPage(player, holder);
        LDLib2FancyMachineUIElement shell = createShell(player, holder, page);
        UIElement contextualRoot = shell.getChildren().getFirst().getChildren().getFirst();
        GTIntInputElement contextualInput = requireOnlyInput(contextualRoot);
        helper.assertTrue(page.getLDLib2PageWidth() == 100 && page.getLDLib2PageHeight() == 20 &&
                contextualRoot.getSizeWidth() == 100 && contextualRoot.getSizeHeight() == 20 &&
                contextualInput.getSizeWidth() == 100 && contextualInput.getSizeHeight() == 20,
                "contextual Parallel Hatch input lost its stable 100x20 body");

        contextualInput.setValue(2);
        helper.assertTrue(machine.getCurrentParallel() == 2,
                "contextual Parallel Hatch input did not use the existing value update path");
        contextualInput.setValue(Integer.MIN_VALUE);
        helper.assertTrue(machine.getCurrentParallel() == 1,
                "contextual Parallel Hatch input did not clamp to its existing minimum");
        contextualInput.setValue(Integer.MAX_VALUE);
        helper.assertTrue(machine.getCurrentParallel() == 4,
                "contextual Parallel Hatch input did not clamp to its existing maximum");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void contextualPageLifecycleMetadataAndPanelsAreHolderScoped(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ParallelHatchPartMachine machine = createMachine();
        machine.setLevel(helper.getLevel());
        MutableMachineUIHolder holder = new MutableMachineUIHolder(machine);
        LDLib2FancyUIProvider firstPage = machine.createLDLib2FancyPage(player, holder);
        LDLib2FancyUIProvider secondPage = machine.createLDLib2FancyPage(player, holder);

        helper.assertTrue(firstPage != secondPage,
                "Parallel Hatch reused a contextual page provider across openings");
        Component expectedTitle = Component.translatable(machine.getDefinition().getDescriptionId());
        helper.assertTrue(firstPage.getTitle().equals(expectedTitle) &&
                firstPage.getTabTooltips().equals(List.of(expectedTitle)),
                "Parallel Hatch contextual page did not use its definition title");
        helper.assertTrue(firstPage.getTabIcon() instanceof ItemStackTexture icon &&
                icon.items.length == 1 && icon.items[0].is(machine.getDefinition().getItem()),
                "Parallel Hatch contextual page did not use its definition icon");
        helper.assertTrue(firstPage.getPageGroupingData() == null,
                "Parallel Hatch contextual page invented grouping metadata");

        LDLib2FancyMachineUIElement shell = createShell(player, holder, firstPage);
        helper.assertTrue(shell.getHolder() == holder,
                "Parallel Hatch contextual shell lost its dedicated holder");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().isEmpty(),
                "Parallel Hatch exposed an unsupported working configurator");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                "Parallel Hatch did not expose exactly one contextual directional side page");
        int expectedTooltips = machine.showFancyTooltip() ? 1 : 0;
        expectedTooltips += (int) machine.getTraitHolder().getAllTraits().stream()
                .filter(IFancyTooltip.class::isInstance)
                .map(IFancyTooltip.class::cast)
                .filter(IFancyTooltip::showFancyTooltip)
                .count();
        helper.assertTrue(expectedTooltips > 0 &&
                shell.getTooltipsPanel().getChildren().size() == expectedTooltips,
                "Parallel Hatch did not attach its default machine and trait tooltips");

        ParallelHatchPartMachine replacement = createMachine();
        replacement.setLevel(helper.getLevel());
        MachineUIHolder replacementHolder = new MutableMachineUIHolder(replacement);
        boolean mismatchedContextualHolderRejected = false;
        try {
            machine.createLDLib2FancyPage(player, replacementHolder);
        } catch (IllegalArgumentException expected) {
            mismatchedContextualHolderRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(mismatchedContextualHolderRejected,
                "Parallel Hatch contextual page accepted another machine's holder");

        boolean mismatchedStandaloneHolderRejected = false;
        try {
            machine.createLDLib2UI(player, replacementHolder);
        } catch (IllegalArgumentException expected) {
            mismatchedStandaloneHolderRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(mismatchedStandaloneHolderRejected,
                "Parallel Hatch standalone page accepted another machine's holder");

        LDLib2FancyUIProvider stalePage = machine.createLDLib2FancyPage(player, holder);
        holder.setMachine(replacement);
        boolean staleHolderRejected = false;
        try {
            createShell(player, holder, stalePage);
        } catch (IllegalStateException expected) {
            staleHolderRejected = expected.getMessage().contains("no longer");
        }
        helper.assertTrue(staleHolderRejected,
                "Parallel Hatch contextual page accepted a same-definition holder replacement");
        helper.succeed();
    }

    private static GTIntInputElement requireOnlyInput(UIElement root) {
        List<GTIntInputElement> inputs = root.getChildren().stream()
                .filter(GTIntInputElement.class::isInstance)
                .map(GTIntInputElement.class::cast)
                .toList();
        if (inputs.size() != 1) {
            throw new IllegalStateException("Parallel Hatch page did not expose exactly one integer input.");
        }
        return inputs.getFirst();
    }

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player, MachineUIHolder holder,
                                                           LDLib2FancyUIProvider page) {
        return new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
    }

    private static ParallelHatchPartMachine createMachine() {
        MachineDefinition definition = GCYMMachines.PARALLEL_HATCH[GTValues.IV];
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (machine instanceof ParallelHatchPartMachine parallelHatch) {
            return parallelHatch;
        }
        throw new IllegalStateException("Parallel Hatch definition did not create its expected machine.");
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

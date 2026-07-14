package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.LaserHatchPartMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;

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

import static com.gregtechceu.gtceu.api.GTValues.IV;
import static com.gregtechceu.gtceu.api.GTValues.LV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class ActiveTransformerMachineLDLib2UITest {

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "ActiveTransformerMachineLDLib2UI")
    public static void multipartShellKeepsEveryPartPageAndMainDisplayLayout(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        EnergyHatchPartMachine firstInput = placeEnergyHatch(helper, new BlockPos(0, 1, 0),
                GTMachines.ENERGY_INPUT_HATCH[LV]);
        EnergyHatchPartMachine secondInput = placeEnergyHatch(helper, new BlockPos(0, 2, 0),
                GTMachines.ENERGY_INPUT_HATCH[LV]);
        LaserHatchPartMachine laserOutput = placeLaserHatch(helper, new BlockPos(1, 1, 0),
                GTMachines.LASER_OUTPUT_HATCH_256[IV]);
        List<IMultiPart> parts = List.of(firstInput, secondInput, laserOutput);
        TestActiveTransformerMachine transformer = new TestActiveTransformerMachine(parts);
        MachineUIHolder holder = new TestMachineUIHolder(transformer);
        TestActiveTransformerMachine replacementTransformer = new TestActiveTransformerMachine(List.of());
        MachineUIHolder replacementHolder = new TestMachineUIHolder(replacementTransformer);

        helper.assertTrue(transformer.canCreateLDLib2UI(player, holder),
                "Active Transformer rejected its matching controller holder");
        helper.assertTrue(!transformer.canCreateLDLib2UI(player, replacementHolder),
                "Active Transformer accepted another controller instance with the same definition");
        boolean replacementHolderRejected = false;
        try {
            transformer.createLDLib2UI(player, replacementHolder);
        } catch (IllegalArgumentException expected) {
            replacementHolderRejected = true;
        }
        helper.assertTrue(replacementHolderRejected,
                "Active Transformer created an LDLib2 UI for another controller instance");
        UIElement root = transformer.createLDLib2UI(player, holder).getRootElement();
        if (!(root instanceof LDLib2FancyMachineUIElement shell)) {
            throw new IllegalStateException("Active Transformer LDLib2 UI did not create a Fancy shell.");
        }

        UIElement pageContainer = shell.getChildren().getFirst();
        helper.assertTrue(pageContainer.getChildren().size() == parts.size() + 1,
                "Active Transformer did not cache one distinct home page per actual part");
        int expectedConfiguratorCount = transformer.supportsBatchMode() ? 3 : 2;
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == expectedConfiguratorCount,
                "Active Transformer did not preserve its voiding, batch, and working configurators");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                "Active Transformer did not expose its controller cover-direction page");

        UIElement mainPage = pageContainer.getChildren().getFirst();
        helper.assertTrue(mainPage.getSizeWidth() == 190 && mainPage.getSizeHeight() == 125,
                "Active Transformer main page did not preserve its 190x125 body");
        helper.assertTrue(mainPage.getChildren().size() == 1 &&
                mainPage.getChildren().getFirst() instanceof GTScrollerViewElement,
                "Active Transformer main page did not create one display scroller");
        UIElement scroller = mainPage.getChildren().getFirst();
        helper.assertTrue(scroller.getSizeWidth() == 182 && scroller.getSizeHeight() == 117,
                "Active Transformer display scroller did not preserve its 182x117 bounds");
        List<GTComponentPanelElement> panels = descendants(mainPage).stream()
                .filter(GTComponentPanelElement.class::isInstance)
                .map(GTComponentPanelElement.class::cast)
                .toList();
        helper.assertTrue(panels.size() == 1 && panels.getFirst().getMaxWidthLimit() == 150,
                "Active Transformer display panel did not preserve its maximum text width");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ActiveTransformerMachineLDLib2UI")
    public static void unsupportedPartsFailFastAndDisplaySnapshotUsesServerStatistics(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ItemBusPartMachine unsupportedPart = createItemBus(GTMachines.ITEM_IMPORT_BUS[LV]);
        TestActiveTransformerMachine invalidTransformer = new TestActiveTransformerMachine(List.of(unsupportedPart));

        boolean unsupportedPartRejected = false;
        try {
            invalidTransformer.createLDLib2UI(player, new TestMachineUIHolder(invalidTransformer));
        } catch (IllegalStateException expected) {
            unsupportedPartRejected = true;
        }
        helper.assertTrue(unsupportedPartRejected,
                "Active Transformer silently omitted a part without an LDLib2 contextual page");

        EnergyContainerList input = new EnergyContainerList(List.of(
                new TestEnergyContainer(256, 2, 0, 0, 1_200, 0)));
        EnergyContainerList output = new EnergyContainerList(List.of(
                new TestEnergyContainer(0, 0, 128, 4, 0, 2_400)));
        ActiveTransformerMachine.DisplayState state = ActiveTransformerMachine.captureDisplayState(
                true, true, true, input, output, true);
        List<Component> actual = ActiveTransformerMachine.createDisplaySnapshot(state);
        List<Component> expected = List.of(
                Component.translatable("gtpm.multiblock.running"),
                Component.translatable("gtpm.multiblock.active_transformer.max_input",
                        FormattingUtil.formatNumbers(512)),
                Component.translatable("gtpm.multiblock.active_transformer.max_output",
                        FormattingUtil.formatNumbers(512)),
                Component.translatable("gtpm.multiblock.active_transformer.average_in",
                        FormattingUtil.formatNumbers(60)),
                Component.translatable("gtpm.multiblock.active_transformer.average_out",
                        FormattingUtil.formatNumbers(120)),
                Component.translatable("gtpm.multiblock.active_transformer.danger_enabled"));
        helper.assertTrue(actual.equals(expected),
                "Active Transformer display snapshot did not preserve server energy statistics and status text");
        helper.assertTrue(ActiveTransformerMachine.createDisplaySnapshot(
                new ActiveTransformerMachine.DisplayState(false, true, true, 1, 2, 3, 4, true)).isEmpty(),
                "unformed Active Transformer retained stale display text");
        helper.succeed();
    }

    private static EnergyHatchPartMachine placeEnergyHatch(GameTestHelper helper, BlockPos pos,
                                                           MachineDefinition definition) {
        helper.setBlock(pos, definition.getBlock());
        if (!(helper.getBlockEntity(pos) instanceof EnergyHatchPartMachine energyHatch)) {
            throw new IllegalStateException("Placed energy hatch block did not create an energy hatch machine.");
        }
        return energyHatch;
    }

    private static LaserHatchPartMachine placeLaserHatch(GameTestHelper helper, BlockPos pos,
                                                         MachineDefinition definition) {
        helper.setBlock(pos, definition.getBlock());
        if (!(helper.getBlockEntity(pos) instanceof LaserHatchPartMachine laserHatch)) {
            throw new IllegalStateException("Placed laser hatch block did not create a laser hatch machine.");
        }
        return laserHatch;
    }

    private static ItemBusPartMachine createItemBus(MachineDefinition definition) {
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (!(machine instanceof ItemBusPartMachine itemBus)) {
            throw new IllegalStateException("Item Bus definition did not create an Item Bus machine.");
        }
        return itemBus;
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

    private record TestMachineUIHolder(MetaMachine machine) implements MachineUIHolder {

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

    private static final class TestActiveTransformerMachine extends ActiveTransformerMachine {

        private final List<IMultiPart> parts;

        private TestActiveTransformerMachine(List<IMultiPart> parts) {
            super(info(GTMultiMachines.ACTIVE_TRANSFORMER));
            this.parts = List.copyOf(parts);
        }

        @Override
        public List<IMultiPart> getParts() {
            return parts;
        }
    }

    private static final class TestEnergyContainer extends NotifiableEnergyContainer {

        private final long inputPerSec;
        private final long outputPerSec;

        private TestEnergyContainer(long inputVoltage, long inputAmperage,
                                    long outputVoltage, long outputAmperage,
                                    long inputPerSec, long outputPerSec) {
            super(10_000, inputVoltage, inputAmperage, outputVoltage, outputAmperage);
            this.inputPerSec = inputPerSec;
            this.outputPerSec = outputPerSec;
        }

        @Override
        public long getInputPerSec() {
            return inputPerSec;
        }

        @Override
        public long getOutputPerSec() {
            return outputPerSec;
        }
    }

    private static BlockEntityCreationInfo info(MachineDefinition definition) {
        return new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.defaultBlockState());
    }
}

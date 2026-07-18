package com.gregtechceu.gtceu.common.machine.multiblock.electric.research;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.machines.GTResearchMachines;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.ArrayList;
import java.util.List;

import static com.gregtechceu.gtceu.api.GTValues.LV;
import static com.gregtechceu.gtceu.api.GTValues.LuV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class NetworkSwitchMachineLDLib2UITest {

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "NetworkSwitchMachineLDLib2UI")
    public static void controllerShellPreservesLegacyLayoutAndOpeningScopedParts(GameTestHelper helper) {
        boolean maintenanceEnabled = ConfigHolder.INSTANCE.machines.enableMaintenance;
        ConfigHolder.INSTANCE.machines.enableMaintenance = true;
        try {
            ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            List<IMultiPart> parts = List.of(
                    requirePart(placeMachine(helper, new BlockPos(0, 1, 0),
                            GTResearchMachines.COMPUTATION_HATCH_RECEIVER)),
                    requirePart(placeMachine(helper, new BlockPos(1, 1, 0),
                            GTResearchMachines.COMPUTATION_HATCH_TRANSMITTER)),
                    requirePart(placeMachine(helper, new BlockPos(2, 1, 0), GTMachines.ENERGY_INPUT_HATCH[LuV])),
                    requirePart(placeMachine(helper, new BlockPos(3, 1, 0), GTMachines.MAINTENANCE_HATCH)));
            TestNetworkSwitchMachine networkSwitch = new TestNetworkSwitchMachine(parts);
            networkSwitch.setLevel(helper.getLevel());
            networkSwitch.setFormedForTest(true);
            networkSwitch.refreshDisplaySnapshot();
            MutableMachineUIHolder holder = new MutableMachineUIHolder(networkSwitch);
            MutableMachineUIHolder replacementHolder = new MutableMachineUIHolder(
                    new TestNetworkSwitchMachine(List.of()));

            helper.assertTrue(networkSwitch.canCreateLDLib2UI(player, holder),
                    "Network Switch rejected its matching controller holder");
            helper.assertTrue(!networkSwitch.canCreateLDLib2UI(player, replacementHolder),
                    "Network Switch accepted another controller instance with the same definition");
            boolean replacementRejected = false;
            try {
                networkSwitch.createLDLib2UI(player, replacementHolder);
            } catch (IllegalArgumentException expected) {
                replacementRejected = expected.getMessage().contains("holder");
            }
            helper.assertTrue(replacementRejected,
                    "Network Switch created an LDLib2 UI for another controller instance");

            LDLib2FancyMachineUIElement shell = requireFancyShell(
                    networkSwitch.createLDLib2UI(player, holder).getRootElement(), "Network Switch");
            helper.assertTrue(shell.getHolder() == holder,
                    "Network Switch Fancy shell did not retain its controller holder");
            int expectedConfiguratorCount = networkSwitch.supportsBatchMode() ? 3 : 2;
            helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == expectedConfiguratorCount,
                    "Network Switch did not preserve voiding, conditional batch, and working configurators");
            helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                    "Network Switch did not expose its controller cover-direction page");
            helper.assertTrue(shell.getTooltipsPanel().getChildren().size() == 1,
                    "Network Switch did not expose its maintenance warning");

            UIElement pageContainer = shell.getChildren().getFirst();
            helper.assertTrue(pageContainer.getChildren().size() == parts.size() + 1,
                    "Network Switch did not create one contextual page for every actual part");
            UIElement mainPage = pageContainer.getChildren().getFirst();
            UITemplate.LDLib2Bounds mainPageBounds = UITemplate.getLDLib2Bounds(mainPage);
            helper.assertTrue(mainPageBounds.width() == 190 && mainPageBounds.height() == 125,
                    "Network Switch main page did not preserve its 190x125 body");
            helper.assertTrue(mainPage.getChildren().size() == 1 &&
                    mainPage.getChildren().getFirst() instanceof GTScrollerViewElement,
                    "Network Switch main page did not create one display scroller");
            UIElement scroller = mainPage.getChildren().getFirst();
            UITemplate.LDLib2Bounds scrollerBounds = UITemplate.getLDLib2Bounds(scroller);
            helper.assertTrue(scrollerBounds.x() == 4 && scrollerBounds.y() == 4 &&
                    scrollerBounds.width() == 182 && scrollerBounds.height() == 117,
                    "Network Switch display scroller did not preserve its (4,4) 182x117 bounds");

            List<GTLabelElement> labels = descendants(mainPage).stream()
                    .filter(GTLabelElement.class::isInstance)
                    .map(GTLabelElement.class::cast)
                    .toList();
            UITemplate.LDLib2Bounds labelBounds = UITemplate.getLDLib2Bounds(labels.getFirst());
            helper.assertTrue(labels.size() == 1 && labelBounds.x() == 4 &&
                    labelBounds.y() == 5,
                    "Network Switch title did not preserve its (4,5) position");
            List<GTComponentPanelElement> panels = descendants(mainPage).stream()
                    .filter(GTComponentPanelElement.class::isInstance)
                    .map(GTComponentPanelElement.class::cast)
                    .toList();
            UITemplate.LDLib2Bounds panelBounds = UITemplate.getLDLib2Bounds(panels.getFirst());
            helper.assertTrue(panels.size() == 1 && panelBounds.x() == 4 &&
                    panelBounds.y() == 17 && panels.getFirst().getMaxWidthLimit() == 200,
                    "Network Switch display panel did not preserve its position and maximum text width");
            helper.assertTrue(panels.getFirst().getLastText().equals(networkSwitch.getDisplaySnapshot()),
                    "Network Switch display panel did not consume the synchronized snapshot");

            LDLib2FancyMachineUIElement secondShell = requireFancyShell(
                    networkSwitch.createLDLib2UI(player, holder).getRootElement(), "second Network Switch opening");
            List<UIElement> firstPages = pageContainer.getChildren();
            List<UIElement> secondPages = secondShell.getChildren().getFirst().getChildren();
            for (int index = 0; index < firstPages.size(); index++) {
                helper.assertTrue(firstPages.get(index) != secondPages.get(index),
                        "Network Switch reused a cached page across menu openings");
            }

            for (IMultiPart part : parts) {
                if (!(part instanceof LDLib2FancyPartUIProvider pageProvider)) {
                    throw new IllegalStateException("Supported Network Switch part has no LDLib2 Fancy page.");
                }
                MutableMachineUIHolder partHolder = new MutableMachineUIHolder(part.self());
                LDLib2FancyUIProvider firstPage = pageProvider.createLDLib2FancyPage(player, partHolder);
                LDLib2FancyUIProvider secondPage = pageProvider.createLDLib2FancyPage(player, partHolder);
                helper.assertTrue(firstPage != secondPage,
                        "Network Switch part reused a page provider across openings");
                LDLib2FancyMachineUIElement partShell = new LDLib2FancyMachineUIElement(firstPage,
                        player.getInventory(), partHolder,
                        firstPage.getLDLib2PageWidth(), firstPage.getLDLib2PageHeight());
                helper.assertTrue(partShell.getHolder() == partHolder,
                        "Network Switch part page did not retain its dedicated holder");
            }
        } finally {
            ConfigHolder.INSTANCE.machines.enableMaintenance = maintenanceEnabled;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "NetworkSwitchMachineLDLib2UI")
    public static void holderStalenessAndUnsupportedPartsFailFast(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        TestNetworkSwitchMachine networkSwitch = new TestNetworkSwitchMachine(List.of());
        TestNetworkSwitchMachine replacement = new TestNetworkSwitchMachine(List.of());
        SequencedMachineUIHolder staleHolder = new SequencedMachineUIHolder(networkSwitch, replacement, 3);

        boolean staleOpeningRejected = false;
        try {
            networkSwitch.createLDLib2UI(player, staleHolder);
        } catch (IllegalStateException expected) {
            staleOpeningRejected = expected.getMessage().contains("page holder");
        }
        helper.assertTrue(staleOpeningRejected,
                "Network Switch page accepted a holder replaced after opening validation");

        IMultiPart unsupportedPart = new UnsupportedPart();
        TestNetworkSwitchMachine invalidSwitch = new TestNetworkSwitchMachine(List.of(unsupportedPart));
        boolean unsupportedPartRejected = false;
        try {
            invalidSwitch.createLDLib2UI(player, new MutableMachineUIHolder(invalidSwitch));
        } catch (IllegalStateException expected) {
            unsupportedPartRejected = expected.getMessage().contains("part");
        }
        helper.assertTrue(unsupportedPartRejected,
                "Network Switch silently omitted a part without an LDLib2 contextual page");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "NetworkSwitchMachineLDLib2UI")
    public static void displayStatesPreserveLegacyOrderAndImmutableSnapshots(GameTestHelper helper) {
        int energyUsage = NetworkSwitchMachine.EUT_PER_HATCH * 2;
        int maxCWUt = 256;
        int usedCWUt = 96;
        NetworkSwitchMachine.DisplayState providingState = NetworkSwitchMachine.captureDisplayState(
                true, true, true, true, energyUsage, maxCWUt, usedCWUt);
        List<Component> providing = NetworkSwitchMachine.createDisplaySnapshot(providingState);
        List<Component> expectedProviding = List.of(
                Component.translatable("gtpm.multiblock.energy_consumption",
                        FormattingUtil.formatNumbers(energyUsage),
                        Component.literal(GTValues.VNF[GTUtil.getTierByVoltage(energyUsage)]))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("gtpm.multiblock.computation.max",
                        Component.literal(FormattingUtil.formatNumbers(maxCWUt)).withStyle(ChatFormatting.AQUA))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("gtpm.multiblock.computation.usage",
                        Component.literal(FormattingUtil.formatNumbers(usedCWUt) + " CWU/t")
                                .withStyle(ChatFormatting.AQUA))
                        .withStyle(ChatFormatting.GRAY),
                Component.translatable("gtpm.multiblock.data_bank.providing").withStyle(ChatFormatting.GREEN));
        helper.assertTrue(providing.equals(expectedProviding),
                "Network Switch providing snapshot lost its legacy display order or text");

        List<NetworkSwitchMachine.DisplayState> gatedStates = List.of(
                NetworkSwitchMachine.captureDisplayState(
                        false, true, true, true, energyUsage, maxCWUt, usedCWUt),
                NetworkSwitchMachine.captureDisplayState(
                        true, false, true, true, energyUsage, maxCWUt, usedCWUt),
                NetworkSwitchMachine.captureDisplayState(
                        true, true, true, false, energyUsage, maxCWUt, usedCWUt));
        helper.assertTrue(gatedStates.stream().allMatch(state -> state.maxCWUt() == 0 && state.usedCWUt() == 0),
                "Network Switch exposed stale computation values while unformed, disabled, or bridge-inactive");

        Component idling = Component.translatable("gtpm.multiblock.idling").withStyle(ChatFormatting.GRAY);
        List<Component> idle = NetworkSwitchMachine.createDisplaySnapshot(
                NetworkSwitchMachine.captureDisplayState(true, true, false, false, 0, maxCWUt, usedCWUt));
        List<Component> disabled = NetworkSwitchMachine.createDisplaySnapshot(
                gatedStates.get(1));
        helper.assertTrue(idle.equals(List.of(idling)) &&
                disabled.equals(List.of(expectedProviding.getFirst(), idling)),
                "Network Switch idle or disabled snapshot lost its legacy energy and idling branches");

        Component invalidStructure = Component.translatable("gtpm.multiblock.invalid_structure")
                .withStyle(ChatFormatting.RED)
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("gtpm.multiblock.invalid_structure.tooltip")
                                .withStyle(ChatFormatting.GRAY))));
        List<Component> unformed = NetworkSwitchMachine.createDisplaySnapshot(gatedStates.getFirst());
        helper.assertTrue(unformed.equals(List.of(invalidStructure)),
                "Unformed Network Switch did not expose only the legacy invalid-structure line");

        boolean immutable = false;
        try {
            providing.add(Component.literal("mutation"));
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        helper.assertTrue(immutable, "Network Switch display snapshot was mutable");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "NetworkSwitchMachineLDLib2UI")
    public static void snapshotLifecycleInitializesAndClearsRuntimeState(GameTestHelper helper) {
        TestNetworkSwitchMachine networkSwitch = new TestNetworkSwitchMachine(List.of());
        networkSwitch.setLevel(helper.getLevel());
        networkSwitch.onLoad();
        helper.assertTrue(networkSwitch.getDisplaySnapshot().equals(NetworkSwitchMachine.createDisplaySnapshot(
                new NetworkSwitchMachine.DisplayState(false, true, false, 0, 0, 0))),
                "Network Switch onLoad did not initialize its server-owned invalid-structure snapshot");
        networkSwitch.onUnload();
        helper.assertTrue(networkSwitch.getDisplaySnapshot().isEmpty(),
                "Network Switch retained its display snapshot after unload");

        TestNetworkSwitchMachine partUnloadSwitch = new TestNetworkSwitchMachine(List.of());
        partUnloadSwitch.setLevel(helper.getLevel());
        partUnloadSwitch.setFormedForTest(true);
        partUnloadSwitch.refreshDisplaySnapshot();
        helper.assertTrue(partUnloadSwitch.getDisplaySnapshot().equals(
                List.of(Component.translatable("gtpm.multiblock.idling").withStyle(ChatFormatting.GRAY))),
                "Network Switch exposed stale computation values while its bridge was inactive");
        partUnloadSwitch.onPartUnload();
        helper.assertTrue(partUnloadSwitch.getDisplaySnapshot().isEmpty(),
                "Network Switch retained its display snapshot after a part unloaded");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "NetworkSwitchMachineLDLib2UI", timeoutTicks = 20)
    public static void displaySubscriptionRunsIndependentlyUntilUnload(GameTestHelper helper) {
        TestNetworkSwitchMachine networkSwitch = new TestNetworkSwitchMachine(List.of());
        networkSwitch.setLevel(helper.getLevel());
        networkSwitch.setFormedForTest(true);
        networkSwitch.setWorkingEnabled(false);
        networkSwitch.onLoad();
        int refreshesAfterLoad = networkSwitch.getDisplayRefreshCount();

        helper.runAfterDelay(3, () -> {
            networkSwitch.serverTick();
            int refreshesWhileDisabled = networkSwitch.getDisplayRefreshCount();
            helper.assertTrue(refreshesWhileDisabled > refreshesAfterLoad,
                    "Network Switch display subscription stopped with its disabled business tick");
            networkSwitch.onUnload();
            helper.runAfterDelay(3, () -> {
                networkSwitch.serverTick();
                helper.assertTrue(networkSwitch.getDisplayRefreshCount() == refreshesWhileDisabled,
                        "Network Switch display subscription kept running after unload");
                helper.succeed();
            });
        });
    }

    private static LDLib2FancyMachineUIElement requireFancyShell(UIElement root, String description) {
        if (!(root instanceof LDLib2FancyMachineUIElement shell)) {
            throw new IllegalStateException(description + " did not create an LDLib2 Fancy shell.");
        }
        return shell;
    }

    private static MetaMachine placeMachine(GameTestHelper helper, BlockPos pos, MachineDefinition definition) {
        helper.setBlock(pos, definition.getBlock());
        if (!(helper.getBlockEntity(pos) instanceof MetaMachine machine)) {
            throw new IllegalStateException("Placed block did not create its expected machine.");
        }
        return machine;
    }

    private static MetaMachine createMachine(MachineDefinition definition) {
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (machine == null) {
            throw new IllegalStateException("Machine definition did not create a machine.");
        }
        return machine;
    }

    private static IMultiPart requirePart(MetaMachine machine) {
        if (!(machine instanceof IMultiPart part)) {
            throw new IllegalStateException("Expected a multiblock part machine.");
        }
        return part;
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

    private static BlockEntityCreationInfo info(MachineDefinition definition) {
        return new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.defaultBlockState());
    }

    private static class MutableMachineUIHolder implements MachineUIHolder {

        protected MetaMachine machine;

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
    }

    private static final class SequencedMachineUIHolder extends MutableMachineUIHolder {

        private final MetaMachine replacement;
        private final int matchingReads;
        private int reads;

        private SequencedMachineUIHolder(MetaMachine machine, MetaMachine replacement, int matchingReads) {
            super(machine);
            this.replacement = replacement;
            this.matchingReads = matchingReads;
        }

        @Override
        public MetaMachine getMachine() {
            return reads++ < matchingReads ? machine : replacement;
        }
    }

    private static final class UnsupportedPart extends MultiblockPartMachine {

        private UnsupportedPart() {
            super(info(GTMachines.ITEM_IMPORT_BUS[LV]));
        }
    }

    private static final class TestNetworkSwitchMachine extends NetworkSwitchMachine {

        private final List<IMultiPart> parts;
        private int displayRefreshCount;

        private TestNetworkSwitchMachine(List<IMultiPart> parts) {
            super(info(GTResearchMachines.NETWORK_SWITCH));
            this.parts = List.copyOf(parts);
        }

        private void setFormedForTest(boolean formed) {
            this.isFormed = formed;
        }

        private int getDisplayRefreshCount() {
            return displayRefreshCount;
        }

        @Override
        void refreshDisplaySnapshot() {
            displayRefreshCount++;
            super.refreshDisplaySnapshot();
        }

        @Override
        public List<IMultiPart> getParts() {
            return parts;
        }
    }
}

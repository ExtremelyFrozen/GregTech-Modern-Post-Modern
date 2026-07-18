package com.gregtechceu.gtceu.common.machine.multiblock.electric.gcym;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.machines.GCYMMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.CokeOvenHatch;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ParallelHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.StandardFluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.StandardItemBusPartMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;

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

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.gregtechceu.gtceu.api.GTValues.IV;
import static com.gregtechceu.gtceu.api.GTValues.LV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LargeMacerationTowerMachineLDLib2UITest {

    private static final Component FIRST_DISPLAY_LINE = Component.literal("first server display line");
    private static final Component SECOND_DISPLAY_LINE = Component.literal("second server display line");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LargeMacerationTowerMachineLDLib2UI")
    public static void registeredDefinitionUsesConcreteLDLib2ControllerAndRejectsStaleHolders(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        MetaMachine registeredMachine = createMachine(GCYMMachines.LARGE_MACERATION_TOWER);
        helper.assertTrue(registeredMachine.getClass() == LargeMacerationTowerMachine.class,
                "Large Maceration Tower definition did not create its concrete controller type");
        helper.assertTrue(registeredMachine instanceof LDLib2MachineUIProvider,
                "Large Maceration Tower definition did not expose the LDLib2 machine UI route");
        helper.assertTrue(registeredMachine instanceof LDLib2FancyActionMachine,
                "Large Maceration Tower definition did not expose Fancy action validation");

        LargeMacerationTowerMachine tower = (LargeMacerationTowerMachine) registeredMachine;
        MutableMachineUIHolder holder = new MutableMachineUIHolder(tower);
        helper.assertTrue(tower.canCreateLDLib2UI(player, holder),
                "Large Maceration Tower rejected its matching controller holder");
        helper.assertTrue(tower.createLDLib2UI(player, holder).getRootElement() instanceof LDLib2FancyMachineUIElement,
                "Large Maceration Tower matching holder did not open an LDLib2 Fancy shell");

        MetaMachine wrongMachine = createMachine(GTMachines.MACERATOR[LV]);
        MutableMachineUIHolder wrongHolder = new MutableMachineUIHolder(wrongMachine);
        helper.assertTrue(!tower.canCreateLDLib2UI(player, wrongHolder),
                "Large Maceration Tower accepted a holder for a different machine definition");
        helper.assertTrue(createUIFails(tower, player, wrongHolder),
                "Large Maceration Tower created an LDLib2 UI for a different machine definition");

        MetaMachine replacement = createMachine(GCYMMachines.LARGE_MACERATION_TOWER);
        helper.assertTrue(replacement.getBlockPos().equals(tower.getBlockPos()) &&
                replacement.getDefinition() == tower.getDefinition(),
                "Large Maceration Tower stale-holder fixture did not preserve definition and position");
        holder.setMachine(replacement);
        helper.assertTrue(!tower.canCreateLDLib2UI(player, holder),
                "Large Maceration Tower accepted a replacement controller after capability probing");
        helper.assertTrue(createUIFails(tower, player, holder),
                "Large Maceration Tower created an LDLib2 UI for a stale replacement controller");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = "LargeMacerationTowerMachineLDLib2UI")
    public static void shellPreservesDisplayConfiguratorsTooltipsAndOpeningScopedPartPages(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        StandardItemBusPartMachine itemInput = requireItemBus(
                placeMachine(helper, new BlockPos(0, 1, 0), GTMachines.ITEM_IMPORT_BUS[LV]));
        StandardFluidHatchPartMachine fluidInput = requireFluidHatch(
                placeMachine(helper, new BlockPos(1, 1, 0), GTMachines.FLUID_IMPORT_HATCH[LV]));
        MaintenanceHatchPartMachine maintenance = requireMaintenanceHatch(
                placeMachine(helper, new BlockPos(2, 1, 0), GTMachines.MAINTENANCE_HATCH));
        ParallelHatchPartMachine parallel = requireParallelHatch(
                placeMachine(helper, new BlockPos(3, 1, 0), GCYMMachines.PARALLEL_HATCH[IV]));
        List<IMultiPart> parts = List.of(itemInput, fluidInput, maintenance, parallel);
        TestLargeMacerationTowerMachine tower = new TestLargeMacerationTowerMachine(parts);
        MachineUIHolder holder = new MutableMachineUIHolder(tower);

        boolean maintenanceEnabled = ConfigHolder.INSTANCE.machines.enableMaintenance;
        ConfigHolder.INSTANCE.machines.enableMaintenance = true;
        try {
            LDLib2FancyMachineUIElement firstShell = requireFancyShell(
                    tower.createLDLib2UI(player, holder).getRootElement());
            LDLib2FancyMachineUIElement secondShell = requireFancyShell(
                    tower.createLDLib2UI(player, holder).getRootElement());

            helper.assertTrue(firstShell.getHolder() == holder,
                    "Large Maceration Tower Fancy shell did not retain its controller holder");
            helper.assertTrue(firstShell.getConfiguratorPanel().getChildren().size() == 3,
                    "Large Maceration Tower did not retain voiding, batch, and working configurators");
            helper.assertTrue(firstShell.getSideTabsElement().getChildren().size() == 2,
                    "Large Maceration Tower did not retain its contextual direction page");
            helper.assertTrue(firstShell.getTooltipsPanel().getChildren().size() == 1,
                    "Large Maceration Tower did not attach the maintenance warning tooltip");

            UIElement firstPageContainer = firstShell.getChildren().getFirst();
            UIElement secondPageContainer = secondShell.getChildren().getFirst();
            helper.assertTrue(firstPageContainer.getChildren().size() == parts.size() + 1,
                    "Large Maceration Tower silently dropped an actual multiblock part page");
            helper.assertTrue(secondPageContainer.getChildren().size() == parts.size() + 1,
                    "Large Maceration Tower reused an incomplete part-page set on a later opening");
            for (int index = 0; index < firstPageContainer.getChildren().size(); index++) {
                helper.assertTrue(firstPageContainer.getChildren().get(index) !=
                        secondPageContainer.getChildren().get(index),
                        "Large Maceration Tower reused a page element across UI openings");
            }

            UIElement mainPage = firstPageContainer.getChildren().getFirst();
            UITemplate.LDLib2Bounds mainPageBounds = UITemplate.getLDLib2Bounds(mainPage);
            helper.assertTrue(mainPageBounds.width() == 190 && mainPageBounds.height() == 125,
                    "Large Maceration Tower main page did not preserve its 190x125 body");
            helper.assertTrue(mainPage.getChildren().size() == 1 &&
                    mainPage.getChildren().getFirst() instanceof GTScrollerViewElement,
                    "Large Maceration Tower main page did not create one display scroller");
            UIElement scroller = mainPage.getChildren().getFirst();
            UITemplate.LDLib2Bounds scrollerBounds = UITemplate.getLDLib2Bounds(scroller);
            helper.assertTrue(scrollerBounds.x() == 4 && scrollerBounds.y() == 4 &&
                    scrollerBounds.width() == 182 && scrollerBounds.height() == 117,
                    "Large Maceration Tower display scroller did not preserve its 4,4 182x117 bounds");
            List<UIElement> descendants = descendants(mainPage);
            helper.assertTrue(descendants.stream().filter(GTLabelElement.class::isInstance).count() == 1,
                    "Large Maceration Tower display did not preserve its title label");
            List<GTComponentPanelElement> panels = descendants.stream()
                    .filter(GTComponentPanelElement.class::isInstance)
                    .map(GTComponentPanelElement.class::cast)
                    .toList();
            helper.assertTrue(panels.size() == 1 && panels.getFirst().getMaxWidthLimit() == 200,
                    "Large Maceration Tower display panel did not preserve its maximum text width");
        } finally {
            ConfigHolder.INSTANCE.machines.enableMaintenance = maintenanceEnabled;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LargeMacerationTowerMachineLDLib2UI")
    public static void unsupportedPartsFailFastAndDisplaySnapshotFollowsServerLifecycle(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        CokeOvenHatch unsupportedPart = requireCokeOvenHatch(createMachine(GTMachines.COKE_OVEN_HATCH));
        TestLargeMacerationTowerMachine invalidTower = new TestLargeMacerationTowerMachine(
                List.of(unsupportedPart));
        helper.assertTrue(createUIFails(invalidTower, player, new MutableMachineUIHolder(invalidTower)),
                "Large Maceration Tower silently omitted a part without an LDLib2 contextual page");

        TestLargeMacerationTowerMachine tower = new TestLargeMacerationTowerMachine(List.of());
        tower.setFormedForTest(true);
        tower.setServerDisplay(List.of(FIRST_DISPLAY_LINE));
        tower.refreshDisplaySnapshot();
        List<Component> firstSnapshot = tower.getDisplaySnapshot();
        helper.assertTrue(firstSnapshot.equals(List.of(FIRST_DISPLAY_LINE)),
                "Large Maceration Tower snapshot did not collect server display text");
        tower.refreshDisplaySnapshot();
        helper.assertTrue(tower.getDisplaySnapshot() == firstSnapshot,
                "Large Maceration Tower replaced an unchanged display snapshot instance");
        List<Component> publishedDisplay = new ArrayList<>();
        tower.addDisplayText(publishedDisplay);
        helper.assertTrue(publishedDisplay.equals(firstSnapshot),
                "Large Maceration Tower client display did not read only the synced snapshot");

        tower.setFormedForTest(true);
        tower.getDisplaySnapshotSubscription().updateSubscription();
        TickableSubscription snapshotTick = tower.getCapturedSubscription();
        helper.assertTrue(snapshotTick != null && snapshotTick.isStillSubscribed(),
                "Large Maceration Tower did not subscribe its formed server display refresh");
        tower.setServerDisplay(List.of(SECOND_DISPLAY_LINE));
        snapshotTick.run();
        helper.assertTrue(tower.getDisplaySnapshot().equals(List.of(SECOND_DISPLAY_LINE)),
                "Large Maceration Tower display subscription did not refresh server text");
        tower.setFormedForTest(false);
        tower.getDisplaySnapshotSubscription().updateSubscription();
        helper.assertTrue(!snapshotTick.isStillSubscribed(),
                "Large Maceration Tower retained its display subscription after becoming unformed");

        assertInvalidationPreservesUnformedSnapshot(helper);
        assertPartUnloadClearsSnapshot(helper);
        assertControllerUnloadClearsSnapshot(helper);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LargeMacerationTowerMachineLDLib2UI", timeoutTicks = 20)
    public static void onLoadInitializesDisplayRefreshUntilControllerUnload(GameTestHelper helper) {
        TestLargeMacerationTowerMachine unformedTower = new TestLargeMacerationTowerMachine(List.of());
        unformedTower.setLevel(helper.getLevel());
        unformedTower.useLiveDisplayContract();
        unformedTower.onLoad();
        helper.assertTrue(unformedTower.getDisplaySnapshot().equals(List.of(invalidStructureLine())),
                "Large Maceration Tower onLoad did not publish the legacy invalid-structure display");
        unformedTower.onUnload();

        TestLargeMacerationTowerMachine formedTower = new TestLargeMacerationTowerMachine(List.of());
        formedTower.setLevel(helper.getLevel());
        formedTower.setFormedForTest(true);
        formedTower.setServerDisplay(List.of(FIRST_DISPLAY_LINE));
        formedTower.onLoad();
        int refreshesAfterLoad = formedTower.getDisplayRefreshCount();

        helper.runAfterDelay(3, () -> {
            formedTower.serverTick();
            int refreshesWhileLoaded = formedTower.getDisplayRefreshCount();
            helper.assertTrue(refreshesWhileLoaded > refreshesAfterLoad,
                    "Large Maceration Tower onLoad did not initialize its display subscription");
            formedTower.onUnload();
            helper.assertTrue(formedTower.getDisplaySnapshot().isEmpty(),
                    "Large Maceration Tower retained its display snapshot after controller unload");
            helper.runAfterDelay(3, () -> {
                formedTower.serverTick();
                helper.assertTrue(formedTower.getDisplayRefreshCount() == refreshesWhileLoaded,
                        "Large Maceration Tower display subscription kept running after controller unload");
                helper.succeed();
            });
        });
    }

    private static boolean createUIFails(LargeMacerationTowerMachine tower, ServerPlayer player,
                                         MachineUIHolder holder) {
        try {
            tower.createLDLib2UI(player, holder);
            return false;
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return true;
        }
    }

    private static void assertInvalidationPreservesUnformedSnapshot(GameTestHelper helper) {
        TestLargeMacerationTowerMachine tower = subscribedTower();
        TickableSubscription subscription = tower.getCapturedSubscription();
        tower.useLiveDisplayContract();
        tower.invalidateStructure(LargeMacerationTowerMachine.DEFAULT_STRUCTURE);
        helper.assertTrue(tower.getDisplaySnapshot().equals(List.of(invalidStructureLine())),
                "Large Maceration Tower lost the legacy invalid-structure display after invalidation");
        helper.assertTrue(subscription != null && !subscription.isStillSubscribed(),
                "Large Maceration Tower retained its display subscription after structure invalidation");
    }

    private static void assertPartUnloadClearsSnapshot(GameTestHelper helper) {
        TestLargeMacerationTowerMachine tower = subscribedTower();
        TickableSubscription subscription = tower.getCapturedSubscription();
        tower.onPartUnload();
        assertRuntimeDisplayCleared(helper, tower, subscription, "part unload");
    }

    private static void assertControllerUnloadClearsSnapshot(GameTestHelper helper) {
        TestLargeMacerationTowerMachine tower = subscribedTower();
        TickableSubscription subscription = tower.getCapturedSubscription();
        tower.onUnload();
        assertRuntimeDisplayCleared(helper, tower, subscription, "controller unload");
    }

    private static TestLargeMacerationTowerMachine subscribedTower() {
        TestLargeMacerationTowerMachine tower = new TestLargeMacerationTowerMachine(List.of());
        tower.setFormedForTest(true);
        tower.setServerDisplay(List.of(FIRST_DISPLAY_LINE));
        tower.refreshDisplaySnapshot();
        tower.getDisplaySnapshotSubscription().updateSubscription();
        return tower;
    }

    private static Component invalidStructureLine() {
        Component hover = Component.translatable("gtpm.multiblock.invalid_structure.tooltip")
                .withStyle(ChatFormatting.GRAY);
        return Component.translatable("gtpm.multiblock.invalid_structure")
                .withStyle(ChatFormatting.RED)
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static void assertRuntimeDisplayCleared(GameTestHelper helper,
                                                    TestLargeMacerationTowerMachine tower,
                                                    @Nullable TickableSubscription subscription,
                                                    String lifecycleEvent) {
        helper.assertTrue(tower.getDisplaySnapshot().isEmpty(),
                "Large Maceration Tower retained display text after " + lifecycleEvent);
        helper.assertTrue(subscription != null && !subscription.isStillSubscribed(),
                "Large Maceration Tower retained its display subscription after " + lifecycleEvent);
    }

    private static LDLib2FancyMachineUIElement requireFancyShell(UIElement root) {
        if (!(root instanceof LDLib2FancyMachineUIElement shell)) {
            throw new IllegalStateException("Large Maceration Tower did not create an LDLib2 Fancy shell.");
        }
        return shell;
    }

    private static MetaMachine placeMachine(GameTestHelper helper, BlockPos pos, MachineDefinition definition) {
        helper.setBlock(pos, definition.getBlock());
        if (!(helper.getBlockEntity(pos) instanceof MetaMachine machine)) {
            throw new IllegalStateException("Placed machine block did not create a MetaMachine: " +
                    definition.getId());
        }
        return machine;
    }

    private static MetaMachine createMachine(MachineDefinition definition) {
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (machine == null) {
            throw new IllegalStateException("Machine definition did not create a MetaMachine: " + definition.getId());
        }
        return machine;
    }

    private static StandardItemBusPartMachine requireItemBus(MetaMachine machine) {
        if (!(machine instanceof StandardItemBusPartMachine itemBus)) {
            throw new IllegalStateException("Item Bus definition did not create its standard concrete type.");
        }
        return itemBus;
    }

    private static StandardFluidHatchPartMachine requireFluidHatch(MetaMachine machine) {
        if (!(machine instanceof StandardFluidHatchPartMachine fluidHatch)) {
            throw new IllegalStateException("Fluid Hatch definition did not create its standard concrete type.");
        }
        return fluidHatch;
    }

    private static MaintenanceHatchPartMachine requireMaintenanceHatch(MetaMachine machine) {
        if (!(machine instanceof MaintenanceHatchPartMachine maintenanceHatch)) {
            throw new IllegalStateException("Maintenance Hatch definition did not create its expected type.");
        }
        return maintenanceHatch;
    }

    private static ParallelHatchPartMachine requireParallelHatch(MetaMachine machine) {
        if (!(machine instanceof ParallelHatchPartMachine parallelHatch)) {
            throw new IllegalStateException("Parallel Hatch definition did not create its expected type.");
        }
        return parallelHatch;
    }

    private static CokeOvenHatch requireCokeOvenHatch(MetaMachine machine) {
        if (!(machine instanceof CokeOvenHatch cokeOvenHatch)) {
            throw new IllegalStateException("Coke Oven Hatch definition did not create its expected type.");
        }
        return cokeOvenHatch;
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

    private static final class TestLargeMacerationTowerMachine extends LargeMacerationTowerMachine {

        private final List<IMultiPart> parts;
        private List<Component> serverDisplay = List.of();
        private @Nullable TickableSubscription capturedSubscription;
        private boolean useLiveDisplayContract;
        private int displayRefreshCount;

        private TestLargeMacerationTowerMachine(List<IMultiPart> parts) {
            super(info(GCYMMachines.LARGE_MACERATION_TOWER));
            this.parts = List.copyOf(parts);
        }

        @Override
        public List<IMultiPart> getParts() {
            return parts;
        }

        @Override
        protected void collectServerDisplayText(List<Component> textList) {
            if (useLiveDisplayContract) {
                super.collectServerDisplayText(textList);
            } else {
                textList.addAll(serverDisplay);
            }
        }

        @Override
        void refreshDisplaySnapshot() {
            displayRefreshCount++;
            super.refreshDisplaySnapshot();
        }

        @Override
        public TickableSubscription subscribeServerTick(Runnable runnable) {
            capturedSubscription = super.subscribeServerTick(runnable);
            if (capturedSubscription == null) {
                throw new IllegalStateException("Server-side display test did not create a tick subscription.");
            }
            return capturedSubscription;
        }

        @Override
        public TickableSubscription subscribeServerTick(@Nullable TickableSubscription last, Runnable runnable) {
            capturedSubscription = super.subscribeServerTick(last, runnable);
            if (capturedSubscription == null) {
                throw new IllegalStateException("Server-side display test did not create a tick subscription.");
            }
            return capturedSubscription;
        }

        private void setServerDisplay(List<Component> serverDisplay) {
            this.serverDisplay = List.copyOf(serverDisplay);
        }

        private void useLiveDisplayContract() {
            this.useLiveDisplayContract = true;
        }

        private void setFormedForTest(boolean formed) {
            this.isFormed = formed;
        }

        private @Nullable TickableSubscription getCapturedSubscription() {
            return capturedSubscription;
        }

        private int getDisplayRefreshCount() {
            return displayRefreshCount;
        }
    }

    private static BlockEntityCreationInfo info(MachineDefinition definition) {
        return new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.defaultBlockState());
    }
}

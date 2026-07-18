package com.gregtechceu.gtceu.common.machine.multiblock.generator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
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
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.CokeOvenHatch;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.StandardFluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.StandardItemBusPartMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LargeCombustionEngineMachineLDLib2UITest {

    private static final String BATCH = "LargeCombustionEngineMachineLDLib2UI";
    private static final ResourceLocation DISPLAY_SNAPSHOT_FIELD = SyncFieldData.key("displaySnapshot");
    private static final ResourceLocation INTAKE_OBSTRUCTION_FIELD = SyncFieldData.key("intakeObstructionSnapshot");
    private static final LargeCombustionEngineMachine.DisplayState REGULAR_DISPLAY = new LargeCombustionEngineMachine.DisplayState(
            true, true, true, GTValues.EV, 512, "1,000mB", 40, true);
    private static final LargeCombustionEngineMachine.DisplayState EXTREME_DISPLAY = new LargeCombustionEngineMachine.DisplayState(
            true, true, true, GTValues.IV, 2_048, "2,000mB", 80, true);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void registeredDefinitionsUseConcreteLDLib2ControllersAndValidateHolders(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        LargeCombustionEngineMachine large = requireEngine(createMachine(GTMultiMachines.LARGE_COMBUSTION_ENGINE));
        LargeCombustionEngineMachine extreme = requireEngine(createMachine(GTMultiMachines.EXTREME_COMBUSTION_ENGINE));

        helper.assertTrue(large.getClass() == LargeCombustionEngineMachine.class &&
                extreme.getClass() == LargeCombustionEngineMachine.class,
                "Large Combustion Engine definitions did not create their concrete controller type");
        helper.assertTrue(large instanceof LDLib2MachineUIProvider && large instanceof LDLib2FancyActionMachine &&
                extreme instanceof LDLib2MachineUIProvider && extreme instanceof LDLib2FancyActionMachine,
                "Large Combustion Engine definitions did not expose the LDLib2 UI and Fancy action contracts");

        MutableMachineUIHolder holder = new MutableMachineUIHolder(large);
        helper.assertTrue(large.canCreateLDLib2UI(player, holder) &&
                large.createLDLib2UI(player, holder).getRootElement() instanceof LDLib2FancyMachineUIElement,
                "Large Combustion Engine matching holder did not open an LDLib2 Fancy shell");

        MutableMachineUIHolder wrongHolder = new MutableMachineUIHolder(
                createMachine(GTMachines.MACERATOR[GTValues.LV]));
        helper.assertTrue(!large.canCreateLDLib2UI(player, wrongHolder) && createUIFails(large, player, wrongHolder),
                "Large Combustion Engine accepted a holder for another machine definition");

        LargeCombustionEngineMachine replacement = requireEngine(
                createMachine(GTMultiMachines.LARGE_COMBUSTION_ENGINE));
        helper.assertTrue(replacement.getBlockPos().equals(large.getBlockPos()) &&
                replacement.getDefinition() == large.getDefinition(),
                "Large Combustion Engine stale-holder fixture changed definition or position");
        holder.setMachine(replacement);
        helper.assertTrue(!large.canCreateLDLib2UI(player, holder) && createUIFails(large, player, holder),
                "Large Combustion Engine accepted a replacement controller instance");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = BATCH)
    public static void shellPreservesLayoutConfiguratorsTooltipsAndOpeningScopedParts(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        StandardItemBusPartMachine itemInput = requireItemBus(
                placeMachine(helper, new BlockPos(0, 1, 0), GTMachines.ITEM_IMPORT_BUS[GTValues.LV]));
        StandardFluidHatchPartMachine fluidInput = requireFluidHatch(
                placeMachine(helper, new BlockPos(1, 1, 0), GTMachines.FLUID_IMPORT_HATCH[GTValues.LV]));
        MaintenanceHatchPartMachine maintenance = requireMaintenanceHatch(
                placeMachine(helper, new BlockPos(2, 1, 0), GTMachines.MAINTENANCE_HATCH));
        List<IMultiPart> parts = List.of(itemInput, fluidInput, maintenance);
        TestLargeCombustionEngineMachine engine = new TestLargeCombustionEngineMachine(parts, GTValues.EV);
        engine.setFormedForTest(true);
        engine.setDisplayState(REGULAR_DISPLAY);
        engine.refreshDisplaySnapshot();
        MachineUIHolder holder = new MutableMachineUIHolder(engine);

        boolean maintenanceEnabled = ConfigHolder.INSTANCE.machines.enableMaintenance;
        ConfigHolder.INSTANCE.machines.enableMaintenance = true;
        try {
            LDLib2FancyMachineUIElement firstShell = requireFancyShell(
                    engine.createLDLib2UI(player, holder).getRootElement());
            LDLib2FancyMachineUIElement secondShell = requireFancyShell(
                    engine.createLDLib2UI(player, holder).getRootElement());

            helper.assertTrue(firstShell.getHolder() == holder,
                    "Large Combustion Engine Fancy shell did not retain its controller holder");
            helper.assertTrue(firstShell.getConfiguratorPanel().getChildren().size() == 2,
                    "Large Combustion Engine did not retain voiding and working configurators");
            helper.assertTrue(firstShell.getSideTabsElement().getChildren().size() == 2,
                    "Large Combustion Engine did not retain its contextual direction page");
            helper.assertTrue(firstShell.getTooltipsPanel().getChildren().size() == 1,
                    "Large Combustion Engine did not attach the maintenance warning tooltip");

            UIElement firstPageContainer = firstShell.getChildren().getFirst();
            UIElement secondPageContainer = secondShell.getChildren().getFirst();
            helper.assertTrue(firstPageContainer.getChildren().size() == parts.size() + 1 &&
                    secondPageContainer.getChildren().size() == parts.size() + 1,
                    "Large Combustion Engine silently dropped an actual multiblock part page");
            for (int index = 0; index < firstPageContainer.getChildren().size(); index++) {
                helper.assertTrue(firstPageContainer.getChildren().get(index) !=
                        secondPageContainer.getChildren().get(index),
                        "Large Combustion Engine reused a page element across UI openings");
            }

            clickButton(firstShell.getSideTabsElement().getChildren().get(1));
            clickButton(secondShell.getSideTabsElement().getChildren().get(1));
            helper.assertTrue(firstPageContainer.getChildren().size() == parts.size() + 2 &&
                    secondPageContainer.getChildren().size() == parts.size() + 2 &&
                    firstPageContainer.getChildren().getLast() != secondPageContainer.getChildren().getLast(),
                    "Large Combustion Engine reused or omitted its directional page across openings");
            clickButton(firstShell.getSideTabsElement().getChildren().getFirst());
            clickButton(secondShell.getSideTabsElement().getChildren().getFirst());

            UIElement mainPage = firstPageContainer.getChildren().getFirst();
            UITemplate.LDLib2Bounds mainPageBounds = UITemplate.getLDLib2Bounds(mainPage);
            helper.assertTrue(mainPageBounds.width() == 190 && mainPageBounds.height() == 125 &&
                    mainPage.getChildren().size() == 1 &&
                    mainPage.getChildren().getFirst() instanceof GTScrollerViewElement,
                    "Large Combustion Engine main page did not preserve its 190x125 display body");
            UIElement scroller = mainPage.getChildren().getFirst();
            UITemplate.LDLib2Bounds scrollerBounds = UITemplate.getLDLib2Bounds(scroller);
            helper.assertTrue(scrollerBounds.x() == 4 && scrollerBounds.y() == 4 &&
                    scrollerBounds.width() == 182 && scrollerBounds.height() == 117,
                    "Large Combustion Engine display scroller did not preserve its bounds");
            List<UIElement> descendants = descendants(mainPage);
            helper.assertTrue(descendants.stream().filter(GTLabelElement.class::isInstance).count() == 1,
                    "Large Combustion Engine display did not preserve its title label");
            List<GTComponentPanelElement> panels = descendants.stream()
                    .filter(GTComponentPanelElement.class::isInstance)
                    .map(GTComponentPanelElement.class::cast)
                    .toList();
            helper.assertTrue(panels.size() == 1 && panels.getFirst().getMaxWidthLimit() == 200 &&
                    panels.getFirst().getLastText().equals(engine.getDisplaySnapshot()),
                    "Large Combustion Engine display panel did not consume its synchronized snapshot");

            engine.setIntakesObstructed(true);
            engine.refreshDisplaySnapshot();
            firstShell.getTooltipsPanel().screenTick();
            helper.assertTrue(firstShell.getTooltipsPanel().getChildren().size() == 2,
                    "Large Combustion Engine did not expose its obstructed-intake tooltip");
            List<Component> obstructionTooltip = hoverTooltips(
                    firstShell.getTooltipsPanel().getChildren().getLast());
            helper.assertTrue(obstructionTooltip.size() == 1 &&
                    hasTranslation(obstructionTooltip,
                            "gtpm.multiblock.large_combustion_engine.obstructed") &&
                    obstructionTooltip.getFirst().getStyle().getColor() != null &&
                    obstructionTooltip.getFirst().getStyle().getColor().getValue() == ChatFormatting.RED.getColor(),
                    "Large Combustion Engine obstructed-intake tooltip lost its text or red style");

            engine.setIntakesObstructed(false);
            engine.refreshDisplaySnapshot();
            firstShell.getTooltipsPanel().screenTick();
            helper.assertTrue(firstShell.getTooltipsPanel().getChildren().size() == 1,
                    "Large Combustion Engine retained its obstructed-intake tooltip after clearing the intake");
        } finally {
            ConfigHolder.INSTANCE.machines.enableMaintenance = maintenanceEnabled;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void snapshotIsImmutableSynchronizedAndFollowsServerLifecycle(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        CokeOvenHatch unsupportedPart = requireCokeOvenHatch(createMachine(GTMachines.COKE_OVEN_HATCH));
        TestLargeCombustionEngineMachine invalidEngine = new TestLargeCombustionEngineMachine(List.of(unsupportedPart),
                GTValues.EV);
        helper.assertTrue(createUIFails(invalidEngine, player, new MutableMachineUIHolder(invalidEngine)),
                "Large Combustion Engine silently omitted a part without an LDLib2 contextual page");

        TestLargeCombustionEngineMachine server = new TestLargeCombustionEngineMachine(List.of(), GTValues.EV);
        server.setFormedForTest(true);
        server.setDisplayState(REGULAR_DISPLAY);
        server.setIntakesObstructed(true);
        server.refreshDisplaySnapshot();
        List<Component> firstSnapshot = server.getDisplaySnapshot();
        helper.assertTrue(firstSnapshot.equals(LargeCombustionEngineMachine.createDisplaySnapshot(REGULAR_DISPLAY)) &&
                server.isIntakeObstructionSnapshot(),
                "Large Combustion Engine did not publish its server display and intake snapshots");
        server.refreshDisplaySnapshot();
        helper.assertTrue(server.getDisplaySnapshot() == firstSnapshot,
                "Large Combustion Engine replaced an unchanged display snapshot instance");

        boolean immutable = false;
        try {
            firstSnapshot.add(Component.literal("mutation"));
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        helper.assertTrue(immutable, "Large Combustion Engine published a mutable display snapshot");
        List<Component> publishedDisplay = new ArrayList<>();
        server.addDisplayText(publishedDisplay);
        helper.assertTrue(publishedDisplay.equals(firstSnapshot),
                "Large Combustion Engine client display did not read only the synchronized snapshot");

        TestLargeCombustionEngineMachine client = new TestLargeCombustionEngineMachine(List.of(), GTValues.EV);
        DataComponentMap fullSync = server.getSyncDataHolder()
                .serializeFullClientSyncComponents(helper.getLevel().registryAccess());
        SyncFieldData saved = server.getSyncDataHolder()
                .serializeToFieldData(helper.getLevel().registryAccess(), false, false);
        helper.assertTrue(saved.get(DISPLAY_SNAPSHOT_FIELD) == null &&
                saved.get(INTAKE_OBSTRUCTION_FIELD) == null,
                "Large Combustion Engine persisted transient display or intake UI state");
        client.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), fullSync);
        DataComponentMap clientFullSync = client.getSyncDataHolder()
                .serializeFullClientSyncComponents(helper.getLevel().registryAccess());
        SyncFieldData serverFields = fullSync.get(GTDataComponents.SYNC_FIELD_DATA.get());
        SyncFieldData clientFields = clientFullSync.get(GTDataComponents.SYNC_FIELD_DATA.get());
        helper.assertTrue(serverFields != null && clientFields != null &&
                serverFields.get(DISPLAY_SNAPSHOT_FIELD) != null &&
                serverFields.get(DISPLAY_SNAPSHOT_FIELD).equals(clientFields.get(DISPLAY_SNAPSHOT_FIELD)) &&
                client.isIntakeObstructionSnapshot(),
                "Large Combustion Engine client did not apply its display or intake snapshot wire data");

        server.getDisplaySnapshotSubscription().updateSubscription();
        TickableSubscription snapshotTick = server.requireCapturedSubscription();
        helper.assertTrue(snapshotTick.isStillSubscribed(),
                "Large Combustion Engine did not subscribe its formed display refresh");
        server.setDisplayState(EXTREME_DISPLAY);
        server.setIntakesObstructed(false);
        snapshotTick.run();
        helper.assertTrue(server.getDisplaySnapshot().equals(
                LargeCombustionEngineMachine.createDisplaySnapshot(EXTREME_DISPLAY)) &&
                !server.isIntakeObstructionSnapshot(),
                "Large Combustion Engine display subscription did not refresh authoritative state");

        server.setFormedForTest(false);
        server.getDisplaySnapshotSubscription().updateSubscription();
        helper.assertTrue(!snapshotTick.isStillSubscribed(),
                "Large Combustion Engine retained its display subscription after becoming unformed");

        TestLargeCombustionEngineMachine unloading = subscribedEngine();
        TickableSubscription unloadingTick = unloading.requireCapturedSubscription();
        unloading.onPartUnload();
        helper.assertTrue(unloading.getDisplaySnapshot().isEmpty() &&
                !unloading.isIntakeObstructionSnapshot() && !unloadingTick.isStillSubscribed(),
                "Large Combustion Engine retained runtime UI state after part unload");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 20)
    public static void onLoadInitializesDisplayRefreshUntilControllerUnload(GameTestHelper helper) {
        TestLargeCombustionEngineMachine engine = new TestLargeCombustionEngineMachine(List.of(), GTValues.EV);
        engine.setLevel(helper.getLevel());
        engine.setFormedForTest(true);
        engine.setDisplayState(REGULAR_DISPLAY);
        engine.onLoad();
        int refreshesAfterLoad = engine.getDisplayRefreshCount();

        helper.runAfterDelay(3, () -> {
            engine.serverTick();
            int refreshesWhileLoaded = engine.getDisplayRefreshCount();
            helper.assertTrue(refreshesWhileLoaded > refreshesAfterLoad,
                    "Large Combustion Engine onLoad did not initialize its display subscription");
            engine.onUnload();
            helper.assertTrue(engine.getDisplaySnapshot().isEmpty(),
                    "Large Combustion Engine retained its display snapshot after controller unload");
            helper.runAfterDelay(3, () -> {
                engine.serverTick();
                helper.assertTrue(engine.getDisplayRefreshCount() == refreshesWhileLoaded,
                        "Large Combustion Engine display subscription kept running after controller unload");
                helper.succeed();
            });
        });
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void regularAndExtremeSnapshotsPreserveTierSpecificDisplaySemantics(GameTestHelper helper) {
        List<Component> regular = LargeCombustionEngineMachine.createDisplaySnapshot(REGULAR_DISPLAY);
        List<Component> extreme = LargeCombustionEngineMachine.createDisplaySnapshot(EXTREME_DISPLAY);
        LargeCombustionEngineMachine.DisplayState invalidState = new LargeCombustionEngineMachine.DisplayState(
                false, true, false, GTValues.EV, 0, null, 0, true);
        List<Component> invalid = LargeCombustionEngineMachine.createDisplaySnapshot(invalidState);

        helper.assertTrue(hasTranslation(regular, "gtpm.multiblock.max_energy_per_tick_amps") &&
                !hasTranslation(regular, "gtpm.multiblock.max_energy_per_tick") &&
                hasTranslation(regular, "gtpm.multiblock.large_combustion_engine.oxygen_boosted"),
                "regular Large Combustion Engine snapshot lost its three-amp or oxygen display");
        helper.assertTrue(hasTranslation(extreme, "gtpm.multiblock.max_energy_per_tick") &&
                !hasTranslation(extreme, "gtpm.multiblock.max_energy_per_tick_amps") &&
                hasTranslation(extreme, "gtpm.multiblock.large_combustion_engine.liquid_oxygen_boosted"),
                "Extreme Combustion Engine snapshot lost its voltage or liquid-oxygen display");
        helper.assertTrue(hasTranslation(regular, "gtpm.multiblock.turbine.energy_per_tick_maxed") &&
                hasTranslation(regular, "gtpm.multiblock.turbine.fuel_needed") &&
                hasTranslation(regular, "gtpm.multiblock.running"),
                "Large Combustion Engine active snapshot lost current output, fuel, or working status");
        helper.assertTrue(hasTranslation(invalid, "gtpm.multiblock.invalid_structure") &&
                !hasTranslation(invalid, "gtpm.multiblock.large_combustion_engine.oxygen_boosted"),
                "unformed Large Combustion Engine snapshot exposed formed-only display state");
        helper.succeed();
    }

    private static boolean createUIFails(LargeCombustionEngineMachine engine, ServerPlayer player,
                                         MachineUIHolder holder) {
        try {
            engine.createLDLib2UI(player, holder);
            return false;
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return true;
        }
    }

    private static boolean hasTranslation(List<Component> lines, String translationKey) {
        return lines.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents contents &&
                contents.getKey().equals(translationKey));
    }

    private static List<Component> hoverTooltips(UIElement target) {
        UIEvent event = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
        event.target = target;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
        if (event.hoverTooltips == null) {
            throw new IllegalStateException("Large Combustion Engine tooltip icon did not expose hover text");
        }
        return event.hoverTooltips.tooltipTexts();
    }

    private static TestLargeCombustionEngineMachine subscribedEngine() {
        TestLargeCombustionEngineMachine engine = new TestLargeCombustionEngineMachine(List.of(), GTValues.EV);
        engine.setFormedForTest(true);
        engine.setDisplayState(REGULAR_DISPLAY);
        engine.setIntakesObstructed(true);
        engine.refreshDisplaySnapshot();
        engine.getDisplaySnapshotSubscription().updateSubscription();
        return engine;
    }

    private static LDLib2FancyMachineUIElement requireFancyShell(UIElement root) {
        if (!(root instanceof LDLib2FancyMachineUIElement shell)) {
            throw new IllegalStateException("Large Combustion Engine did not create an LDLib2 Fancy shell");
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

    private static LargeCombustionEngineMachine requireEngine(MetaMachine machine) {
        if (!(machine instanceof LargeCombustionEngineMachine engine)) {
            throw new IllegalStateException("Combustion Engine definition did not create its expected type");
        }
        return engine;
    }

    private static StandardItemBusPartMachine requireItemBus(MetaMachine machine) {
        if (!(machine instanceof StandardItemBusPartMachine itemBus)) {
            throw new IllegalStateException("Item Bus definition did not create its standard concrete type");
        }
        return itemBus;
    }

    private static StandardFluidHatchPartMachine requireFluidHatch(MetaMachine machine) {
        if (!(machine instanceof StandardFluidHatchPartMachine fluidHatch)) {
            throw new IllegalStateException("Fluid Hatch definition did not create its standard concrete type");
        }
        return fluidHatch;
    }

    private static MaintenanceHatchPartMachine requireMaintenanceHatch(MetaMachine machine) {
        if (!(machine instanceof MaintenanceHatchPartMachine maintenanceHatch)) {
            throw new IllegalStateException("Maintenance Hatch definition did not create its expected type");
        }
        return maintenanceHatch;
    }

    private static CokeOvenHatch requireCokeOvenHatch(MetaMachine machine) {
        if (!(machine instanceof CokeOvenHatch cokeOvenHatch)) {
            throw new IllegalStateException("Coke Oven Hatch definition did not create its expected type");
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

    private static void clickButton(UIElement button) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = button;
        event.button = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
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

    private static final class TestLargeCombustionEngineMachine extends LargeCombustionEngineMachine {

        private final List<IMultiPart> parts;
        private @Nullable DisplayState displayState;
        private @Nullable TickableSubscription capturedSubscription;
        private boolean intakesObstructed;
        private int displayRefreshCount;

        private TestLargeCombustionEngineMachine(List<IMultiPart> parts, int tier) {
            super(info(tier > GTValues.EV ? GTMultiMachines.EXTREME_COMBUSTION_ENGINE :
                    GTMultiMachines.LARGE_COMBUSTION_ENGINE), tier);
            this.parts = List.copyOf(parts);
        }

        @Override
        public List<IMultiPart> getParts() {
            return parts;
        }

        @Override
        protected DisplayState captureDisplayState() {
            return displayState == null ? super.captureDisplayState() : displayState;
        }

        @Override
        protected boolean isIntakesObstructed() {
            return intakesObstructed;
        }

        @Override
        void refreshDisplaySnapshot() {
            displayRefreshCount++;
            super.refreshDisplaySnapshot();
        }

        @Override
        public @Nullable TickableSubscription subscribeServerTick(Runnable runnable) {
            capturedSubscription = super.subscribeServerTick(runnable);
            return capturedSubscription;
        }

        @Override
        public @Nullable TickableSubscription subscribeServerTick(@Nullable TickableSubscription last,
                                                                  Runnable runnable) {
            capturedSubscription = super.subscribeServerTick(last, runnable);
            return capturedSubscription;
        }

        private void setDisplayState(DisplayState displayState) {
            this.displayState = displayState;
        }

        private void setIntakesObstructed(boolean intakesObstructed) {
            this.intakesObstructed = intakesObstructed;
        }

        private void setFormedForTest(boolean formed) {
            isFormed = formed;
        }

        private TickableSubscription requireCapturedSubscription() {
            if (capturedSubscription == null) {
                throw new IllegalStateException("Server-side display test did not create a tick subscription");
            }
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

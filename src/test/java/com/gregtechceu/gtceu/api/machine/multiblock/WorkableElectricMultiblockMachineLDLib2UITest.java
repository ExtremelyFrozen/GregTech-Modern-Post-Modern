package com.gregtechceu.gtceu.api.machine.multiblock;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.CokeOvenHatch;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.RotorHolderPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.StandardItemBusPartMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.mojang.authlib.GameProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class WorkableElectricMultiblockMachineLDLib2UITest {

    private static final String BATCH = "WorkableElectricMultiblockMachineLDLib2UI";
    private static final ResourceLocation DISPLAY_SNAPSHOT_FIELD = SyncFieldData.key("ldlib2DisplaySnapshot");
    private static final List<Component> FIRST_DISPLAY = List.of(Component.literal("first workable display"));
    private static final List<Component> SECOND_DISPLAY = List.of(Component.literal("second workable display"));

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void registeredDefaultControllerUsesValidatedLDLib2Opening(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        WorkableElectricMultiblockMachine machine = requireWorkable(
                createMachine(GTMultiMachines.LARGE_CHEMICAL_REACTOR));

        helper.assertTrue(machine.getClass() == WorkableElectricMultiblockMachine.class,
                "Large Chemical Reactor definition did not create the default workable controller");
        MutableMachineUIHolder holder = new MutableMachineUIHolder(machine);
        helper.assertTrue(machine.canCreateLDLib2UI(player, holder) &&
                machine.createLDLib2UI(player, holder).getRootElement() instanceof LDLib2FancyMachineUIElement &&
                machine.getLdlib2DisplaySnapshot().isEmpty(),
                "default workable controller did not create an unattached LDLib2 Fancy shell");

        MutableMachineUIHolder wrongHolder = new MutableMachineUIHolder(
                createMachine(GTMachines.MACERATOR[GTValues.LV]));
        helper.assertTrue(!machine.canCreateLDLib2UI(player, wrongHolder) &&
                createUIFails(machine, player, wrongHolder),
                "default workable controller accepted a holder for another machine");

        WorkableElectricMultiblockMachine replacement = requireWorkable(
                createMachine(GTMultiMachines.LARGE_CHEMICAL_REACTOR));
        helper.assertTrue(replacement.getBlockPos().equals(machine.getBlockPos()) &&
                replacement.getDefinition() == machine.getDefinition(),
                "default workable stale-holder fixture changed definition or position");
        holder.setMachine(replacement);
        helper.assertTrue(!machine.canCreateLDLib2UI(player, holder) && createUIFails(machine, player, holder),
                "default workable controller accepted a replacement instance");

        CokeOvenHatch unsupportedPart = requireCokeOvenHatch(createMachine(GTMachines.COKE_OVEN_HATCH));
        TestWorkableMachine unsupported = new TestWorkableMachine(List.of(unsupportedPart));
        MachineUIHolder unsupportedHolder = new MutableMachineUIHolder(unsupported);
        helper.assertTrue(!unsupported.canCreateLDLib2UI(player, unsupportedHolder) &&
                createUIFails(unsupported, player, unsupportedHolder),
                "default workable controller silently omitted an unsupported contextual part");
        helper.assertTrue(unsupported.getLdlib2DisplaySnapshot().isEmpty() &&
                unsupported.getCapturedSubscription() == null,
                "rejected default workable opening started display snapshot tracking");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", batch = BATCH)
    public static void shellPreservesLayoutControlsOpeningScopedPartsAndWarnings(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        StandardItemBusPartMachine itemInput = requireItemBus(
                placeMachine(helper, new BlockPos(1, 1, 2), GTMachines.ITEM_IMPORT_BUS[GTValues.LV]));
        MaintenanceHatchPartMachine maintenance = requireMaintenanceHatch(
                placeMachine(helper, new BlockPos(2, 1, 2), GTMachines.MAINTENANCE_HATCH));
        RotorHolderPartMachine rotorHolder = requireRotorHolder(
                placeMachine(helper, new BlockPos(3, 1, 2), GTMachines.ROTOR_HOLDER[GTValues.HV]));
        rotorHolder.setFrontFacing(Direction.NORTH);
        helper.getLevel().setBlockAndUpdate(rotorHolder.getBlockPos().relative(Direction.NORTH),
                Blocks.AIR.defaultBlockState());

        List<IMultiPart> parts = List.of(itemInput, maintenance, rotorHolder);
        TestWorkableMachine machine = new TestWorkableMachine(parts);
        machine.setFormedForTest();
        machine.setServerDisplay(FIRST_DISPLAY);
        MachineUIHolder holder = new MutableMachineUIHolder(machine);

        boolean maintenanceEnabled = ConfigHolder.INSTANCE.machines.enableMaintenance;
        ConfigHolder.INSTANCE.machines.enableMaintenance = true;
        try {
            LDLib2FancyMachineUIElement firstShell = requireFancyShell(
                    machine.createLDLib2UI(player, holder).getRootElement());
            LDLib2FancyMachineUIElement secondShell = requireFancyShell(
                    machine.createLDLib2UI(player, holder).getRootElement());

            helper.assertTrue(firstShell.getHolder() == holder,
                    "default workable Fancy shell did not retain its controller holder");
            helper.assertTrue(machine.getLdlib2DisplaySnapshot().isEmpty() &&
                    machine.getCapturedSubscription() == null,
                    "creating unattached default workable shells started display tracking");
            List<UIElement> configuratorTabs = firstShell.getConfiguratorPanel().getChildren();
            helper.assertTrue(configuratorTabs.size() == 3,
                    "default workable shell did not retain voiding, batch, and working configurators");
            List<List<Component>> configuratorTooltips = configuratorTabs.stream()
                    .map(tab -> hoverTooltips(tab.getChildren().getFirst()))
                    .toList();
            helper.assertTrue(configuratorTooltips.stream()
                    .anyMatch(lines -> hasTranslation(lines, "gtpm.gui.multiblock.voiding_mode")) &&
                    configuratorTooltips.stream()
                            .anyMatch(lines -> hasTranslation(lines, "gtpm.machine.batch_enabled") ||
                                    hasTranslation(lines, "gtpm.machine.batch_disabled")) &&
                    configuratorTooltips.stream()
                            .anyMatch(lines -> hasTranslation(lines, "behaviour.soft_hammer.enabled") ||
                                    hasTranslation(lines, "behaviour.soft_hammer.disabled")),
                    "default workable shell configurators did not represent voiding, batch, and working controls");
            helper.assertTrue(machine.getRecipeTypes().length == 1 &&
                    firstShell.getSideTabsElement().getChildren().size() == 2,
                    "default workable shell did not retain its contextual direction page");
            helper.assertTrue(firstShell.getTooltipsPanel().getChildren().size() == 1,
                    "default workable shell did not attach its maintenance warning");
            List<Component> maintenanceWarning = hoverTooltips(
                    firstShell.getTooltipsPanel().getChildren().getFirst());
            Component maintenanceHeader = Component
                    .translatable("gtpm.multiblock.universal.has_problems_header")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.RED));
            helper.assertTrue(maintenanceWarning.size() == 7 &&
                    maintenanceWarning.getFirst().equals(maintenanceHeader),
                    "default workable maintenance warning lost its problem header or details");

            UIElement firstPageContainer = firstShell.getChildren().getFirst();
            UIElement secondPageContainer = secondShell.getChildren().getFirst();
            helper.assertTrue(firstPageContainer.getChildren().size() == parts.size() + 1 &&
                    secondPageContainer.getChildren().size() == parts.size() + 1,
                    "default workable shell silently dropped a contextual part page");
            for (int index = 0; index < firstPageContainer.getChildren().size(); index++) {
                helper.assertTrue(firstPageContainer.getChildren().get(index) !=
                        secondPageContainer.getChildren().get(index),
                        "default workable shell reused a page element across openings");
            }

            clickButton(firstShell.getSideTabsElement().getChildren().get(1));
            clickButton(secondShell.getSideTabsElement().getChildren().get(1));
            helper.assertTrue(firstPageContainer.getChildren().size() == parts.size() + 2 &&
                    secondPageContainer.getChildren().size() == parts.size() + 2 &&
                    firstPageContainer.getChildren().getLast() != secondPageContainer.getChildren().getLast(),
                    "default workable shell reused or omitted its directional page across openings");
            clickButton(firstShell.getSideTabsElement().getChildren().getFirst());
            clickButton(secondShell.getSideTabsElement().getChildren().getFirst());

            UIElement mainPage = firstPageContainer.getChildren().getFirst();
            UITemplate.LDLib2Bounds mainPageBounds = UITemplate.getLDLib2Bounds(mainPage);
            helper.assertTrue(mainPageBounds.width() == 190 && mainPageBounds.height() == 125 &&
                    mainPage.getChildren().size() == 1 &&
                    mainPage.getChildren().getFirst() instanceof GTScrollerViewElement,
                    "default workable main page did not preserve its 190x125 display body");
            UIElement scroller = mainPage.getChildren().getFirst();
            UITemplate.LDLib2Bounds scrollerBounds = UITemplate.getLDLib2Bounds(scroller);
            helper.assertTrue(scrollerBounds.x() == 4 && scrollerBounds.y() == 4 &&
                    scrollerBounds.width() == 182 && scrollerBounds.height() == 117,
                    "default workable display scroller did not preserve its bounds");
            List<UIElement> descendants = descendants(mainPage);
            helper.assertTrue(descendants.stream().filter(GTLabelElement.class::isInstance).count() == 1,
                    "default workable display did not preserve its title label");
            List<GTComponentPanelElement> panels = descendants.stream()
                    .filter(GTComponentPanelElement.class::isInstance)
                    .map(GTComponentPanelElement.class::cast)
                    .toList();
            helper.assertTrue(panels.size() == 1 && panels.getFirst().getMaxWidthLimit() == 200 &&
                    panels.getFirst().getLastText().equals(machine.getLdlib2DisplaySnapshot()),
                    "default workable panel did not consume its synchronized display snapshot");

            helper.getLevel().setBlockAndUpdate(rotorHolder.getBlockPos().relative(Direction.NORTH),
                    Blocks.STONE.defaultBlockState());
            firstShell.getTooltipsPanel().screenTick();
            helper.assertTrue(firstShell.getTooltipsPanel().getChildren().size() == 2,
                    "default workable shell did not expose its rotor obstruction warning");
            List<Component> rotorWarning = hoverTooltips(firstShell.getTooltipsPanel().getChildren().getLast());
            Component expectedRotorWarning = Component
                    .translatable("gtpm.multiblock.universal.rotor_obstructed")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.RED));
            helper.assertTrue(rotorWarning.equals(List.of(expectedRotorWarning)),
                    "default workable rotor warning lost its text or red style");

            helper.getLevel().setBlockAndUpdate(rotorHolder.getBlockPos().relative(Direction.NORTH),
                    Blocks.AIR.defaultBlockState());
            firstShell.getTooltipsPanel().screenTick();
            helper.assertTrue(firstShell.getTooltipsPanel().getChildren().size() == 1,
                    "default workable shell retained the rotor warning after clearing the rotor plane");
        } finally {
            ConfigHolder.INSTANCE.machines.enableMaintenance = maintenanceEnabled;
        }
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void displaySnapshotUsesVirtualServerTextAndGtmSyncLifecycle(GameTestHelper helper) {
        ServerPlayer firstPlayer = testPlayer(helper, "workable-first");
        ServerPlayer secondPlayer = testPlayer(helper, "workable-second");
        helper.assertTrue(firstPlayer != secondPlayer &&
                !firstPlayer.getGameProfile().getId().equals(secondPlayer.getGameProfile().getId()),
                "concurrent default workable openings did not use distinct players");

        TestWorkableMachine server = new TestWorkableMachine(List.of());
        server.setFormedForTest();
        server.setServerDisplay(FIRST_DISPLAY);
        UI firstUI = server.createLDLib2UI(firstPlayer, new MutableMachineUIHolder(server));
        helper.assertTrue(server.getLdlib2DisplaySnapshot().isEmpty() &&
                server.getCapturedSubscription() == null,
                "creating a default workable UI started tracking before menu attachment");

        InventoryMenu firstMenu = new InventoryMenu(firstPlayer.getInventory(), false, firstPlayer);
        helper.assertTrue(firstPlayer.containerMenu == firstPlayer.inventoryMenu,
                "first default workable player did not begin on its previous menu");
        ModularUI.of(firstUI, firstPlayer).setMenu(firstMenu);
        List<Component> firstSnapshot = server.getLdlib2DisplaySnapshot();
        TickableSubscription displayTick = server.requireCapturedSubscription();
        helper.assertTrue(firstPlayer.containerMenu == firstPlayer.inventoryMenu &&
                firstSnapshot.equals(FIRST_DISPLAY) &&
                displayTick.isStillSubscribed(),
                "LDLib2 menu attachment did not start the default workable display snapshot");
        firstPlayer.containerMenu = firstMenu;

        server.refreshLDLib2DisplaySnapshot();
        helper.assertTrue(server.getLdlib2DisplaySnapshot() == firstSnapshot,
                "default workable controller replaced an unchanged display snapshot instance");

        boolean immutable = false;
        try {
            firstSnapshot.add(Component.literal("mutation"));
        } catch (UnsupportedOperationException expected) {
            immutable = true;
        }
        helper.assertTrue(immutable, "default workable controller published a mutable display snapshot");

        TestWorkableMachine client = new TestWorkableMachine(List.of());
        DataComponentMap fullSync = server.getSyncDataHolder()
                .serializeFullClientSyncComponents(helper.getLevel().registryAccess());
        SyncFieldData saved = server.getSyncDataHolder()
                .serializeToFieldData(helper.getLevel().registryAccess(), false, false);
        helper.assertTrue(saved.get(DISPLAY_SNAPSHOT_FIELD) == null,
                "default workable controller persisted its transient LDLib2 display snapshot");
        client.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), fullSync);
        helper.assertTrue(client.getLdlib2DisplaySnapshot().equals(firstSnapshot),
                "default workable client did not apply the GTM display snapshot");

        server.setServerDisplay(SECOND_DISPLAY);
        UI secondUI = server.createLDLib2UI(secondPlayer, new MutableMachineUIHolder(server));
        helper.assertTrue(server.getLdlib2DisplaySnapshot() == firstSnapshot &&
                server.requireCapturedSubscription() == displayTick,
                "creating a second unattached default workable UI changed tracking state");

        InventoryMenu secondMenu = new InventoryMenu(secondPlayer.getInventory(), false, secondPlayer);
        helper.assertTrue(secondPlayer.containerMenu == secondPlayer.inventoryMenu,
                "second default workable player did not begin on its previous menu");
        ModularUI.of(secondUI, secondPlayer).setMenu(secondMenu);
        secondPlayer.containerMenu = secondMenu;
        helper.assertTrue(server.getLdlib2DisplaySnapshot().equals(SECOND_DISPLAY),
                "second attached default workable opening did not refresh virtual server display text");

        firstPlayer.closeContainer();
        server.setServerDisplay(FIRST_DISPLAY);
        displayTick.run();
        helper.assertTrue(displayTick.isStillSubscribed() &&
                server.getLdlib2DisplaySnapshot().equals(FIRST_DISPLAY),
                "closing one concurrent default workable opening stopped the remaining opening");

        secondPlayer.closeContainer();
        server.setServerDisplay(SECOND_DISPLAY);
        displayTick.run();
        helper.assertTrue(server.getLdlib2DisplaySnapshot().isEmpty() && !displayTick.isStillSubscribed(),
                "closing the final default workable opening retained display runtime state");

        TestWorkableMachine partUnloading = trackingMachine(firstPlayer);
        TickableSubscription partUnloadTick = partUnloading.requireCapturedSubscription();
        partUnloading.onPartUnload();
        helper.assertTrue(partUnloading.getLdlib2DisplaySnapshot().isEmpty() &&
                !partUnloadTick.isStillSubscribed(),
                "default workable controller retained display runtime state after part unload");

        TestWorkableMachine unloading = trackingMachine(firstPlayer);
        TickableSubscription unloadTick = unloading.requireCapturedSubscription();
        unloading.onUnload();
        helper.assertTrue(unloading.getLdlib2DisplaySnapshot().isEmpty() && !unloadTick.isStillSubscribed(),
                "default workable controller retained display runtime state after controller unload");
        helper.succeed();
    }

    private static TestWorkableMachine trackingMachine(ServerPlayer player) {
        TestWorkableMachine machine = new TestWorkableMachine(List.of());
        machine.setFormedForTest();
        machine.setServerDisplay(FIRST_DISPLAY);
        UI ui = machine.createLDLib2UI(player, new MutableMachineUIHolder(machine));
        InventoryMenu menu = new InventoryMenu(player.getInventory(), false, player);
        ModularUI.of(ui, player).setMenu(menu);
        player.containerMenu = menu;
        return machine;
    }

    private static ServerPlayer testPlayer(GameTestHelper helper, String name) {
        UUID profileId = UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(profileId, name));
        player.closeContainer();
        return player;
    }

    private static boolean createUIFails(WorkableElectricMultiblockMachine machine, ServerPlayer player,
                                         MachineUIHolder holder) {
        try {
            machine.createLDLib2UI(player, holder);
            return false;
        } catch (IllegalArgumentException | IllegalStateException expected) {
            return true;
        }
    }

    private static LDLib2FancyMachineUIElement requireFancyShell(UIElement root) {
        if (!(root instanceof LDLib2FancyMachineUIElement shell)) {
            throw new IllegalStateException("default workable controller did not create an LDLib2 Fancy shell");
        }
        return shell;
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
            throw new IllegalStateException("default workable tooltip icon did not expose hover text");
        }
        return event.hoverTooltips.tooltipTexts();
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

    private static WorkableElectricMultiblockMachine requireWorkable(MetaMachine machine) {
        if (!(machine instanceof WorkableElectricMultiblockMachine workable)) {
            throw new IllegalStateException("Definition did not create a workable electric multiblock controller");
        }
        return workable;
    }

    private static CokeOvenHatch requireCokeOvenHatch(MetaMachine machine) {
        if (!(machine instanceof CokeOvenHatch cokeOvenHatch)) {
            throw new IllegalStateException("Coke Oven Hatch definition did not create its expected type");
        }
        return cokeOvenHatch;
    }

    private static StandardItemBusPartMachine requireItemBus(MetaMachine machine) {
        if (!(machine instanceof StandardItemBusPartMachine itemBus)) {
            throw new IllegalStateException("Item Bus definition did not create its standard concrete type");
        }
        return itemBus;
    }

    private static MaintenanceHatchPartMachine requireMaintenanceHatch(MetaMachine machine) {
        if (!(machine instanceof MaintenanceHatchPartMachine maintenanceHatch)) {
            throw new IllegalStateException("Maintenance Hatch definition did not create its expected type");
        }
        return maintenanceHatch;
    }

    private static RotorHolderPartMachine requireRotorHolder(MetaMachine machine) {
        if (!(machine instanceof RotorHolderPartMachine rotorHolder)) {
            throw new IllegalStateException("Rotor Holder definition did not create its expected type");
        }
        return rotorHolder;
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

    private static final class TestWorkableMachine extends WorkableElectricMultiblockMachine {

        private final List<IMultiPart> parts;
        private List<Component> serverDisplay = List.of();
        private @Nullable TickableSubscription capturedSubscription;

        private TestWorkableMachine(List<IMultiPart> parts) {
            super(info());
            this.parts = List.copyOf(parts);
        }

        @Override
        public @NotNull List<IMultiPart> getParts() {
            return parts;
        }

        @Override
        public boolean supportsBatchMode() {
            return true;
        }

        @Override
        public void addDisplayText(List<Component> textList) {
            textList.addAll(serverDisplay);
        }

        @Override
        public @Nullable TickableSubscription subscribeServerTick(@Nullable TickableSubscription last,
                                                                  @NotNull Runnable runnable) {
            capturedSubscription = super.subscribeServerTick(last, runnable);
            return capturedSubscription;
        }

        private void setServerDisplay(List<Component> serverDisplay) {
            this.serverDisplay = List.copyOf(serverDisplay);
        }

        private void setFormedForTest() {
            isFormed = true;
        }

        private @Nullable TickableSubscription getCapturedSubscription() {
            return capturedSubscription;
        }

        private TickableSubscription requireCapturedSubscription() {
            if (capturedSubscription == null) {
                throw new IllegalStateException("default workable display did not create a server tick subscription");
            }
            return capturedSubscription;
        }
    }

    private static BlockEntityCreationInfo info() {
        MachineDefinition definition = GTMultiMachines.LARGE_CHEMICAL_REACTOR;
        return new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.defaultBlockState());
    }
}

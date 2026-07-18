package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTPhantomFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class SteamHatchPartMachineLDLib2UITest {

    private static final String BATCH = "SteamHatchPartMachineLDLib2UI";
    private static final ResourceLocation CLICK_FLUID_SLOT_ACTION = GTCEu.id("click_fluid_hatch_fluid_slot");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void standaloneScreenRemainsSteamThemedAndHolderScoped(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SteamHatchPartMachine steamHatch = createSteamHatch();
        SteamHatchPartMachine anotherSteamHatch = createSteamHatch();
        MachineUIHolder holder = new MutableMachineUIHolder(steamHatch);
        MachineUIHolder wrongHolder = new MutableMachineUIHolder(anotherSteamHatch);

        helper.assertTrue(steamHatch.canCreateLDLib2UI(player, holder),
                "Steam Hatch rejected its matching standalone holder");
        helper.assertTrue(!steamHatch.canCreateLDLib2UI(player, wrongHolder),
                "Steam Hatch accepted another machine's standalone holder");

        UI standalone = steamHatch.createLDLib2UI(player, holder);
        UIElement root = standalone.getRootElement();
        boolean steelSteamMultiblocks = ConfigHolder.INSTANCE.machines.steelSteamMultiblocks;
        helper.assertTrue(!(root instanceof LDLib2FancyMachineUIElement) &&
                UITemplate.getLDLib2Bounds(root).width() == 176 && UITemplate.getLDLib2Bounds(root).height() == 166,
                "Steam Hatch standalone screen lost its dedicated 176x166 layout");
        helper.assertTrue(root.getStyle().getInline(PropertyRegistry.BACKGROUND) ==
                GuiTextures.BACKGROUND_STEAM.get(steelSteamMultiblocks),
                "Steam Hatch standalone screen lost its configured Steam background");

        List<GTFluidSlotElement> fluidSlots = normalFluidSlots(root);
        helper.assertTrue(fluidSlots.size() == 1,
                "Steam Hatch standalone screen did not retain one real fluid slot");
        UITemplate.LDLib2Bounds fluidSlotBounds = UITemplate.getLDLib2Bounds(fluidSlots.getFirst());
        helper.assertTrue(fluidSlotBounds.x() == 90 && fluidSlotBounds.y() == 35,
                "Steam Hatch standalone fluid slot moved from 90,35");
        helper.assertTrue(fluidSlots.getFirst().getCapacity() == steamHatch.tank.getTankCapacity(0),
                "Steam Hatch standalone fluid slot lost its real tank capacity");
        helper.assertTrue(fluidSlots.getFirst().isAllowClickFilled() &&
                fluidSlots.getFirst().isAllowClickDrained(),
                "Steam Hatch standalone slot lost one of its bucket interaction directions");
        helper.assertTrue(fluidSlots.getFirst().getBackgroundTexture() == GuiTextures.FLUID_SLOT,
                "Steam Hatch standalone fluid slot lost its dedicated slot background");

        List<UIElement> playerInventories = root.getChildren().stream()
                .filter(child -> child.getChildren().size() == 36)
                .filter(child -> child.getChildren().stream().allMatch(GTItemSlotElement.class::isInstance))
                .toList();
        helper.assertTrue(playerInventories.size() == 1 &&
                UITemplate.getLDLib2Bounds(playerInventories.getFirst()).x() == 7 &&
                UITemplate.getLDLib2Bounds(playerInventories.getFirst()).y() == 84,
                "Steam Hatch standalone screen lost its 36-slot player inventory at 7,84");
        helper.assertTrue(playerInventories.getFirst().getChildren().stream()
                .allMatch(child -> child.getStyle().getInline(PropertyRegistry.BACKGROUND) ==
                        GuiTextures.SLOT_STEAM.get(steelSteamMultiblocks)),
                "Steam Hatch standalone player inventory lost its Steam slot theme");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void contextualPagePreservesInputHatchSemanticsAndActionProtocol(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SteamHatchPartMachine steamHatch = createSteamHatch();
        MachineUIHolder holder = new MutableMachineUIHolder(steamHatch);
        LDLib2FancyUIProvider page = steamHatch.createLDLib2FancyPage(player, holder);

        helper.assertTrue(page.getLDLib2PageWidth() == 89 && page.getLDLib2PageHeight() == 63,
                "Steam Hatch contextual page lost its 89x63 single-tank body");
        helper.assertTrue(page.hasPlayerInventory(),
                "Steam Hatch contextual page stopped exposing the player inventory");
        Component expectedTitle = Component.translatable(steamHatch.getDefinition().getDescriptionId());
        helper.assertTrue(page.getTitle().equals(expectedTitle) &&
                page.getTabTooltips().equals(List.of(expectedTitle)),
                "Steam Hatch contextual page did not use its definition title");
        assertImportGrouping(helper, page);

        LDLib2FancyMachineUIElement shell = createShell(player, holder, page);
        helper.assertTrue(shell.getHolder() == holder,
                "Steam Hatch contextual shell lost its opening holder");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == 2,
                "Steam Hatch contextual page did not expose working and circuit configurators");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                "Steam Hatch contextual page did not expose one directional side page");

        UIElement root = pageRoot(shell);
        helper.assertTrue(
                UITemplate.getLDLib2Bounds(root).width() == 89 && UITemplate.getLDLib2Bounds(root).height() == 63,
                "Steam Hatch contextual page lost its single-tank bounds");
        helper.assertTrue(root.getStyle().getInline(PropertyRegistry.BACKGROUND) == GuiTextures.BACKGROUND_INVERSE,
                "Steam Hatch contextual page lost its single-tank inverse background");
        List<GTFluidSlotElement> fluidSlots = normalFluidSlots(root);
        helper.assertTrue(fluidSlots.size() == 1 &&
                UITemplate.getLDLib2Bounds(fluidSlots.getFirst()).x() == 67 &&
                UITemplate.getLDLib2Bounds(fluidSlots.getFirst()).y() == 22 &&
                fluidSlots.getFirst().getCapacity() == steamHatch.tank.getTankCapacity(0),
                "Steam Hatch contextual page did not bind its real tank at 67,22");
        helper.assertTrue(fluidSlots.getFirst().isAllowClickFilled() &&
                fluidSlots.getFirst().isAllowClickDrained(),
                "Steam Hatch contextual slot did not preserve dual bucket permissions");
        helper.assertTrue(phantomFluidSlots(root).isEmpty() && toggleButtons(root).isEmpty(),
                "Steam Hatch contextual page unexpectedly exposed fluid locking controls");
        helper.assertTrue(steamHatch.supportsFluidHatchActions() &&
                !steamHatch.supportsFluidHatchLocking(),
                "Steam Hatch exposed the wrong contextual fluid hatch action capabilities");

        int expectedTooltips = steamHatch.showFancyTooltip() ? 1 : 0;
        expectedTooltips += (int) steamHatch.getTraitHolder().getAllTraits().stream()
                .filter(IFancyTooltip.class::isInstance)
                .map(IFancyTooltip.class::cast)
                .filter(IFancyTooltip::showFancyTooltip)
                .count();
        helper.assertTrue(expectedTooltips > 0 &&
                shell.getTooltipsPanel().getChildren().size() == expectedTooltips,
                "Steam Hatch contextual page lost its visible machine and trait tooltips");

        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        List<CapturedAction> actions = new ArrayList<>();
        UIElement actionPage = steamHatch.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)),
                () -> true,
                event -> Boolean.TRUE.equals(event.customData));
        UIEvent click = click(normalFluidSlots(actionPage).getFirst(), true);
        helper.assertTrue(actions.size() == 1 && actions.getFirst().holder() == holder,
                "Steam Hatch contextual tank click did not send exactly one holder-scoped action");
        helper.assertTrue(actions.getFirst().action().actionId().equals(CLICK_FLUID_SLOT_ACTION) &&
                actions.getFirst().action().sequence() == 1,
                "Steam Hatch contextual tank click did not use the shifted Fluid Hatch action protocol");
        helper.assertTrue(click.hasHandler,
                "Steam Hatch contextual tank click was not marked handled");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void contextualProviderIsFreshAndRejectsWrongOrStaleHolders(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SteamHatchPartMachine steamHatch = createSteamHatch();
        SteamHatchPartMachine replacement = createSteamHatch();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(steamHatch);
        MutableMachineUIHolder wrongHolder = new MutableMachineUIHolder(replacement);

        helper.assertTrue(steamHatch instanceof LDLib2FancyPartUIProvider,
                "Steam Hatch did not explicitly expose a contextual LDLib2 provider");
        LDLib2FancyUIProvider firstPage = steamHatch.createLDLib2FancyPage(player, holder);
        LDLib2FancyUIProvider secondPage = steamHatch.createLDLib2FancyPage(player, holder);
        helper.assertTrue(firstPage != secondPage,
                "Steam Hatch reused a contextual page provider across openings");

        IllegalArgumentException wrongHolderRejection = null;
        try {
            steamHatch.createLDLib2FancyPage(player, wrongHolder);
        } catch (IllegalArgumentException exception) {
            wrongHolderRejection = exception;
        }
        helper.assertTrue(wrongHolderRejection != null && wrongHolderRejection.getMessage().contains("holder"),
                "Steam Hatch contextual provider accepted another machine's holder");

        helper.assertTrue(replacement.getDefinition() == steamHatch.getDefinition() &&
                replacement.getBlockPos().equals(steamHatch.getBlockPos()),
                "Steam Hatch replacement test changed definition or opening position");
        holder.setMachine(replacement);
        IllegalStateException staleHolderRejection = null;
        try {
            createShell(player, holder, firstPage);
        } catch (IllegalStateException exception) {
            staleHolderRejection = exception;
        }
        helper.assertTrue(staleHolderRejection != null && staleHolderRejection.getMessage().contains("no longer"),
                "Steam Hatch contextual provider accepted a same-definition holder replacement");
        helper.succeed();
    }

    private static void assertImportGrouping(GameTestHelper helper, LDLib2FancyUIProvider page) {
        LDLib2FancyUIProvider.PageGroupingData grouping = page.getPageGroupingData();
        if (grouping == null) {
            throw new IllegalStateException("Steam Hatch contextual page omitted import grouping metadata.");
        }
        helper.assertTrue("gtpm.multiblock.page_switcher.io.import".equals(grouping.groupKey()) &&
                grouping.groupPositionWeight() == 1,
                "Steam Hatch contextual page exposed incorrect import grouping metadata");
    }

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player, MachineUIHolder holder,
                                                           LDLib2FancyUIProvider page) {
        return new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
    }

    private static UIElement pageRoot(LDLib2FancyMachineUIElement shell) {
        return shell.getChildren().getFirst().getChildren().getFirst();
    }

    private static List<GTFluidSlotElement> normalFluidSlots(UIElement root) {
        return descendants(root).stream()
                .filter(GTFluidSlotElement.class::isInstance)
                .filter(element -> !(element instanceof GTPhantomFluidSlotElement))
                .map(GTFluidSlotElement.class::cast)
                .toList();
    }

    private static List<GTPhantomFluidSlotElement> phantomFluidSlots(UIElement root) {
        return descendants(root).stream()
                .filter(GTPhantomFluidSlotElement.class::isInstance)
                .map(GTPhantomFluidSlotElement.class::cast)
                .toList();
    }

    private static List<GTToggleButtonElement> toggleButtons(UIElement root) {
        return descendants(root).stream()
                .filter(GTToggleButtonElement.class::isInstance)
                .map(GTToggleButtonElement.class::cast)
                .toList();
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

    private static UIEvent click(UIElement element, boolean shiftDown) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = element;
        event.button = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        event.customData = shiftDown;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
        return event;
    }

    private static SteamHatchPartMachine createSteamHatch() {
        MetaMachine machine = GTMachines.STEAM_HATCH.getBlockEntityType()
                .create(BlockPos.ZERO, GTMachines.STEAM_HATCH.defaultBlockState());
        if (machine instanceof SteamHatchPartMachine steamHatch) {
            return steamHatch;
        }
        throw new IllegalStateException("Steam Hatch definition did not create a Steam Hatch machine.");
    }

    private record CapturedAction(MachineUIHolder holder, SyncActionData action) {}

    private static final class MutableMachineUIHolder implements MachineUIHolder {

        private final BlockPos pos;
        private final ResourceLocation definitionId;
        private MetaMachine machine;

        private MutableMachineUIHolder(MetaMachine machine) {
            this.pos = machine.getBlockPos();
            this.definitionId = machine.getDefinition().getId();
            this.machine = machine;
        }

        private void setMachine(MetaMachine machine) {
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
    }
}

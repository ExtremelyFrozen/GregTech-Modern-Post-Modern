package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTPhantomFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
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
public class PumpHatchPartMachineLDLib2UITest {

    private static final String BATCH = "PumpHatchPartMachineLDLib2UI";
    private static final ResourceLocation CLICK_FLUID_SLOT_ACTION = GTCEu.id("click_fluid_hatch_fluid_slot");
    private static final ResourceLocation SET_LOCKED_FLUID_ACTION = GTCEu.id("set_fluid_hatch_locked_fluid");
    private static final ResourceLocation SET_LOCKED_ACTION = GTCEu.id("set_fluid_hatch_locked");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void standaloneAndContextualPagesPreservePumpSemantics(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        PumpHatchPartMachine pump = createPumpHatch();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(pump);

        UI standalone = pump.createLDLib2UI(player, holder);
        UIElement standaloneRoot = standalone.getRootElement();
        helper.assertTrue(
                UITemplate.getLDLib2Bounds(standaloneRoot).width() == 176 &&
                        UITemplate.getLDLib2Bounds(standaloneRoot).height() == 166,
                "Pump Hatch standalone UI lost its 176x166 bounds");
        helper.assertTrue(standaloneRoot.getStyle().getInline(PropertyRegistry.BACKGROUND) == GuiTextures.BACKGROUND,
                "Pump Hatch standalone UI lost its GT background");

        List<GTFluidSlotElement> standaloneSlots = normalFluidSlots(standaloneRoot);
        List<GTToggleButtonElement> standaloneToggles = toggleButtons(standaloneRoot);
        helper.assertTrue(standaloneSlots.size() == 1 &&
                UITemplate.getLDLib2Bounds(standaloneSlots.getFirst()).x() == 90 &&
                UITemplate.getLDLib2Bounds(standaloneSlots.getFirst()).y() == 35,
                "Pump Hatch standalone fluid slot moved from 90,35");
        helper.assertTrue(standaloneSlots.getFirst().isAllowClickFilled() &&
                !standaloneSlots.getFirst().isAllowClickDrained(),
                "Pump Hatch standalone slot changed its output-only container permissions");
        helper.assertTrue(phantomFluidSlots(standaloneRoot).isEmpty(),
                "Pump Hatch standalone UI unexpectedly gained a phantom lock slot");
        helper.assertTrue(standaloneToggles.size() == 1 &&
                UITemplate.getLDLib2Bounds(standaloneToggles.getFirst()).x() == 7 &&
                UITemplate.getLDLib2Bounds(standaloneToggles.getFirst()).y() == 53,
                "Pump Hatch standalone working toggle moved from 7,53");
        helper.assertTrue(standaloneRoot.getChildren().stream()
                .filter(child -> child.getChildren().size() == 36)
                .filter(child -> child.getChildren().stream().allMatch(GTItemSlotElement.class::isInstance))
                .anyMatch(child -> UITemplate.getLDLib2Bounds(child).x() == 7 &&
                        UITemplate.getLDLib2Bounds(child).y() == 84),
                "Pump Hatch standalone UI lost its player inventory at 7,84");

        LDLib2FancyUIProvider page = pump.createLDLib2FancyPage(player, holder);
        LDLib2FancyMachineUIElement shell = createShell(player, holder, page);
        UIElement contextualRoot = pageRoot(shell);
        helper.assertTrue(page.getLDLib2PageWidth() == 89 && page.getLDLib2PageHeight() == 63 &&
                UITemplate.getLDLib2Bounds(contextualRoot).width() == 89 &&
                UITemplate.getLDLib2Bounds(contextualRoot).height() == 63,
                "Pump Hatch contextual page lost its 89x63 single-slot body");
        helper.assertTrue(contextualRoot.getStyle().getInline(PropertyRegistry.BACKGROUND) ==
                GuiTextures.BACKGROUND_INVERSE,
                "Pump Hatch contextual page lost its inverse background");

        List<GTFluidSlotElement> contextualSlots = normalFluidSlots(contextualRoot);
        List<GTPhantomFluidSlotElement> phantomSlots = phantomFluidSlots(contextualRoot);
        List<GTToggleButtonElement> lockToggles = toggleButtons(contextualRoot);
        helper.assertTrue(contextualSlots.size() == 1 &&
                UITemplate.getLDLib2Bounds(contextualSlots.getFirst()).x() == 67 &&
                UITemplate.getLDLib2Bounds(contextualSlots.getFirst()).y() == 22 &&
                contextualSlots.getFirst().getCapacity() == pump.tank.getTankCapacity(0),
                "Pump Hatch contextual page did not bind its real tank at 67,22");
        helper.assertTrue(contextualSlots.getFirst().isAllowClickFilled() &&
                !contextualSlots.getFirst().isAllowClickDrained(),
                "Pump Hatch contextual slot changed its output-only container permissions");
        helper.assertTrue(phantomSlots.size() == 1 &&
                UITemplate.getLDLib2Bounds(phantomSlots.getFirst()).x() == 67 &&
                UITemplate.getLDLib2Bounds(phantomSlots.getFirst()).y() == 40,
                "Pump Hatch contextual page did not preserve its phantom lock slot at 67,40");
        helper.assertTrue(lockToggles.size() == 1 &&
                UITemplate.getLDLib2Bounds(lockToggles.getFirst()).x() == 7 &&
                UITemplate.getLDLib2Bounds(lockToggles.getFirst()).y() == 40,
                "Pump Hatch contextual page did not preserve its lock toggle at 7,40");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == 1,
                "Pump Hatch contextual page should expose only its working configurator");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                "Pump Hatch contextual page did not expose one directional side tab");
        helper.assertTrue(!shell.getTooltipsPanel().getChildren().isEmpty(),
                "Pump Hatch contextual page did not attach machine tooltip metadata");
        assertExportGrouping(helper, page);
        helper.assertTrue(pump.supportsFluidHatchActions() && pump.supportsFluidHatchLocking(),
                "Pump Hatch did not opt into its contextual tank and lock action protocol");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void contextualControlsSendAndExecuteThreeHolderScopedActions(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.containerMenu.setCarried(new ItemStack(Items.WATER_BUCKET));
        PumpHatchPartMachine pump = createPumpHatch();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(pump);
        List<CapturedAction> actions = new ArrayList<>();
        UIElement page = pump.createLDLib2MainElement(player, holder,
                (sentHolder, action) -> actions.add(new CapturedAction(sentHolder, action)),
                () -> holder.getMachine() == pump,
                event -> Boolean.TRUE.equals(event.customData));

        UIEvent tankClick = click(normalFluidSlots(page).getFirst(), true);
        UIEvent phantomClick = click(phantomFluidSlots(page).getFirst(), false);
        UIEvent lockClick = click(toggleButtons(page).getFirst(), false);

        helper.assertTrue(actions.size() == 3,
                "Pump Hatch contextual controls did not send exactly three actions");
        assertAction(helper, actions.get(0), holder, CLICK_FLUID_SLOT_ACTION, "real tank");
        assertAction(helper, actions.get(1), holder, SET_LOCKED_FLUID_ACTION, "phantom lock slot");
        assertAction(helper, actions.get(2), holder, SET_LOCKED_ACTION, "lock toggle");
        helper.assertTrue(actions.getFirst().action().sequence() == 1,
                "Pump Hatch contextual tank action lost its shifted state");
        FluidStack lockedFluid = actions.get(1).action().payload()
                .getOrDefault(GTDataComponents.FLUID_CONTENT.get(),
                        SimpleFluidContent.EMPTY)
                .copy();
        helper.assertTrue(lockedFluid.is(Fluids.WATER) && lockedFluid.getAmount() == 1,
                "Pump Hatch phantom slot did not encode one unit of selected water");
        helper.assertTrue(tankClick.hasHandler && phantomClick.hasHandler && lockClick.hasHandler,
                "Pump Hatch contextual controls did not mark their sent actions handled");

        for (CapturedAction captured : actions) {
            helper.assertTrue(dispatch(player, pump, captured.action()),
                    "Pump Hatch contextual action was rejected by the server dispatcher");
        }
        helper.assertTrue(pump.tank.isLocked() &&
                pump.tank.getLockedFluid().getFluid().is(Fluids.WATER),
                "Pump Hatch contextual lock actions did not apply their selected fluid");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void contextualProviderIsOpeningScopedAndRejectsWrongOrStaleHolders(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        PumpHatchPartMachine pump = createPumpHatch();
        PumpHatchPartMachine replacement = createPumpHatch();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(pump);
        MutableMachineUIHolder wrongHolder = new MutableMachineUIHolder(replacement);

        helper.assertTrue(pump.canCreateLDLib2UI(player, holder),
                "Pump Hatch standalone UI rejected its matching holder");
        helper.assertFalse(pump.canCreateLDLib2UI(player, wrongHolder),
                "Pump Hatch standalone UI accepted a different machine holder");

        LDLib2FancyUIProvider firstPage = pump.createLDLib2FancyPage(player, holder);
        LDLib2FancyUIProvider secondPage = pump.createLDLib2FancyPage(player, holder);
        helper.assertTrue(firstPage != secondPage,
                "Pump Hatch reused a contextual provider across openings");

        IllegalArgumentException wrongHolderRejection = null;
        try {
            pump.createLDLib2FancyPage(player, wrongHolder);
        } catch (IllegalArgumentException exception) {
            wrongHolderRejection = exception;
        }
        helper.assertTrue(wrongHolderRejection != null && wrongHolderRejection.getMessage().contains("holder"),
                "Pump Hatch contextual provider accepted another machine's holder");

        holder.setMachine(replacement);
        IllegalStateException staleHolderRejection = null;
        try {
            createShell(player, holder, firstPage);
        } catch (IllegalStateException exception) {
            staleHolderRejection = exception;
        }
        helper.assertTrue(staleHolderRejection != null && staleHolderRejection.getMessage().contains("no longer"),
                "Pump Hatch contextual provider accepted a same-definition replacement");
        helper.succeed();
    }

    private static void assertExportGrouping(GameTestHelper helper, LDLib2FancyUIProvider page) {
        LDLib2FancyUIProvider.PageGroupingData grouping = page.getPageGroupingData();
        if (grouping == null) {
            throw new IllegalStateException("Pump Hatch contextual page omitted export grouping metadata.");
        }
        helper.assertTrue("gtpm.multiblock.page_switcher.io.export".equals(grouping.groupKey()) &&
                grouping.groupPositionWeight() == 2,
                "Pump Hatch contextual page exposed incorrect export grouping metadata");
    }

    private static void assertAction(GameTestHelper helper, CapturedAction captured,
                                     MachineUIHolder expectedHolder, ResourceLocation expectedAction,
                                     String description) {
        helper.assertTrue(captured.holder() == expectedHolder,
                "Pump Hatch " + description + " action used the wrong opening holder");
        helper.assertTrue(captured.action().actionId().equals(expectedAction),
                "Pump Hatch " + description + " action used the wrong action id");
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO,
                null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
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

    private static PumpHatchPartMachine createPumpHatch() {
        MetaMachine machine = GTMachines.PUMP_HATCH.getBlockEntityType()
                .create(BlockPos.ZERO, GTMachines.PUMP_HATCH.defaultBlockState());
        if (machine instanceof PumpHatchPartMachine pumpHatch) {
            return pumpHatch;
        }
        throw new IllegalStateException("Pump Hatch definition did not create a Pump Hatch machine.");
    }

    private record CapturedAction(MachineUIHolder holder, SyncActionData action) {}

    private static final class MutableMachineUIHolder implements MachineUIHolder {

        private final BlockPos pos;
        private final ResourceLocation definitionId;
        @Nullable
        private MetaMachine machine;

        private MutableMachineUIHolder(MetaMachine machine) {
            this.pos = machine.getBlockPos();
            this.definitionId = machine.getDefinition().getId();
            this.machine = machine;
        }

        private void setMachine(@Nullable MetaMachine machine) {
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
        @Nullable
        public MetaMachine getMachine() {
            return machine;
        }
    }
}

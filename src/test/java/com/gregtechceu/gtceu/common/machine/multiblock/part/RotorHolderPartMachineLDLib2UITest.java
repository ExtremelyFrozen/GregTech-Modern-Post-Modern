package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTooltipsPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.ItemStackTexture;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;

import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.ArrayList;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class RotorHolderPartMachineLDLib2UITest {

    private static final String BATCH = "RotorHolderPartMachineLDLib2UI";

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void standaloneAndContextualPagesPreserveRotorSlotSemantics(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        RotorHolderPartMachine machine = createMachine(helper.absolutePos(new BlockPos(2, 3, 2)));
        machine.setLevel(helper.getLevel());
        MachineUIHolder holder = new MutableMachineUIHolder(machine);

        helper.assertTrue(machine.canCreateLDLib2UI(player, holder),
                "Rotor Holder rejected its matching standalone holder");
        UI standalone = machine.createLDLib2UI(player, holder);
        UIElement standaloneRoot = standalone.getRootElement();
        assertRotorPage(helper, player, machine, standaloneRoot, "standalone");
        helper.assertTrue(descendants(standaloneRoot).stream()
                .filter(GTItemSlotElement.class::isInstance)
                .count() == 1,
                "standalone Rotor Holder page gained an unexpected player inventory");

        LDLib2FancyUIProvider contextualPage = machine.createLDLib2FancyPage(player, holder);
        LDLib2FancyMachineUIElement shell = createShell(player, holder, contextualPage);
        UIElement contextualRoot = shell.getChildren().getFirst().getChildren().getFirst();
        helper.assertTrue(contextualPage.getLDLib2PageWidth() == 34 &&
                contextualPage.getLDLib2PageHeight() == 34,
                "contextual Rotor Holder page lost its 34x34 bounds");
        assertRotorPage(helper, player, machine, contextualRoot, "contextual");

        List<UIElement> shellDescendants = descendants(shell);
        helper.assertTrue(shellDescendants.stream()
                .filter(GTItemSlotElement.class::isInstance)
                .count() == 37,
                "contextual Rotor Holder shell did not contain one rotor and 36 player slots");
        helper.assertTrue(shellDescendants.stream()
                .filter(child -> child.getChildren().size() == 36)
                .filter(child -> child.getChildren().stream().allMatch(GTItemSlotElement.class::isInstance))
                .count() == 1,
                "contextual Rotor Holder shell did not contain exactly one player inventory");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void controllerHookReusesOnlyRotorObstructionWarning(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos machinePos = helper.absolutePos(new BlockPos(2, 3, 2));
        RotorHolderPartMachine machine = createMachine(machinePos);
        machine.setLevel(level);
        machine.setFrontFacing(Direction.NORTH);
        clearRotorPlane(level, machinePos);
        MultiblockControllerMachine controller = createController(helper.absolutePos(new BlockPos(1, 3, 2)));
        controller.setLevel(level);
        LDLib2FancyTooltipsPanelElement controllerTooltips = new LDLib2FancyTooltipsPanelElement(0, 0);

        machine.attachLDLib2FancyTooltipsToController(controller, controllerTooltips);
        helper.assertTrue(controllerTooltips.getChildren().isEmpty(),
                "unobstructed Rotor Holder controller hook added a generic machine tooltip");

        level.setBlockAndUpdate(machinePos.relative(Direction.NORTH), Blocks.STONE.defaultBlockState());
        controllerTooltips.screenTick();
        helper.assertTrue(controllerTooltips.getChildren().size() == 1,
                "obstructed Rotor Holder controller hook did not expose exactly one warning tooltip");
        Component expectedWarning = Component.translatable("gtpm.multiblock.universal.rotor_obstructed")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        helper.assertTrue(tooltips(controllerTooltips.getChildren().getFirst()).equals(List.of(expectedWarning)),
                "Rotor Holder controller hook did not reuse its obstruction warning");

        clearRotorPlane(level, machinePos);
        controllerTooltips.screenTick();
        helper.assertTrue(controllerTooltips.getChildren().isEmpty(),
                "Rotor Holder controller hook retained its warning after the rotor plane was cleared");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void contextualPageLifecycleMetadataPanelsAndTooltipAreHolderScoped(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        BlockPos machinePos = helper.absolutePos(new BlockPos(2, 4, 2));
        RotorHolderPartMachine machine = createMachine(machinePos);
        machine.setLevel(level);
        machine.setFrontFacing(Direction.NORTH);
        clearRotorPlane(level, machinePos);
        MutableMachineUIHolder holder = new MutableMachineUIHolder(machine);

        LDLib2FancyUIProvider firstPage = machine.createLDLib2FancyPage(player, holder);
        LDLib2FancyUIProvider secondPage = machine.createLDLib2FancyPage(player, holder);
        helper.assertTrue(firstPage != secondPage,
                "Rotor Holder reused a contextual page provider across openings");
        Component expectedTitle = Component.translatable(machine.getDefinition().getDescriptionId());
        helper.assertTrue(firstPage.getTitle().equals(expectedTitle) &&
                firstPage.getTabTooltips().equals(List.of(expectedTitle)),
                "Rotor Holder contextual page did not use its definition title");
        helper.assertTrue(firstPage.getTabIcon() instanceof ItemStackTexture icon &&
                icon.items.length == 1 && icon.items[0].is(machine.getDefinition().getItem()),
                "Rotor Holder contextual page did not use its definition icon");
        helper.assertTrue(firstPage.getPageGroupingData() == null,
                "Rotor Holder contextual page invented grouping metadata");

        LDLib2FancyMachineUIElement shell = createShell(player, holder, firstPage);
        helper.assertTrue(shell.getHolder() == holder,
                "Rotor Holder contextual shell lost its dedicated holder");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().isEmpty(),
                "Rotor Holder exposed an unsupported working configurator");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                "Rotor Holder did not expose exactly one contextual directional side page");
        helper.assertTrue(machine.showFancyTooltip() && shell.getTooltipsPanel().getChildren().isEmpty(),
                "unobstructed Rotor Holder attached default machine tooltips instead of only its obstruction warning");

        level.setBlockAndUpdate(machinePos.relative(Direction.NORTH), Blocks.STONE.defaultBlockState());
        shell.getTooltipsPanel().screenTick();
        helper.assertTrue(shell.getTooltipsPanel().getChildren().size() == 1,
                "obstructed Rotor Holder did not expose exactly one warning tooltip");
        Component expectedWarning = Component.translatable("gtpm.multiblock.universal.rotor_obstructed")
                .setStyle(Style.EMPTY.withColor(ChatFormatting.RED));
        helper.assertTrue(tooltips(shell.getTooltipsPanel().getChildren().getFirst()).equals(List.of(expectedWarning)),
                "Rotor Holder obstruction warning changed its text or style");

        clearRotorPlane(level, machinePos);
        shell.getTooltipsPanel().screenTick();
        helper.assertTrue(shell.getTooltipsPanel().getChildren().isEmpty(),
                "Rotor Holder retained its obstruction warning after the rotor plane was cleared");

        RotorHolderPartMachine replacement = createMachine(machinePos);
        replacement.setLevel(level);
        MachineUIHolder replacementHolder = new MutableMachineUIHolder(replacement);
        helper.assertFalse(machine.canCreateLDLib2UI(player, replacementHolder),
                "Rotor Holder accepted another machine's standalone holder");

        boolean mismatchedStandaloneHolderRejected = false;
        try {
            machine.createLDLib2UI(player, replacementHolder);
        } catch (IllegalArgumentException expected) {
            mismatchedStandaloneHolderRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(mismatchedStandaloneHolderRejected,
                "Rotor Holder standalone page creation accepted another machine's holder");

        boolean mismatchedContextualHolderRejected = false;
        try {
            machine.createLDLib2FancyPage(player, replacementHolder);
        } catch (IllegalArgumentException expected) {
            mismatchedContextualHolderRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(mismatchedContextualHolderRejected,
                "Rotor Holder contextual page creation accepted another machine's holder");

        MutableMachineUIHolder staleHolder = new MutableMachineUIHolder(machine);
        LDLib2FancyUIProvider stalePage = machine.createLDLib2FancyPage(player, staleHolder);
        staleHolder.setMachine(replacement);
        boolean staleHolderRejected = false;
        try {
            createShell(player, staleHolder, stalePage);
        } catch (IllegalStateException expected) {
            staleHolderRejected = expected.getMessage().contains("no longer");
        }
        helper.assertTrue(staleHolderRejected,
                "Rotor Holder contextual page accepted a same-definition holder replacement");
        helper.succeed();
    }

    private static void assertRotorPage(GameTestHelper helper, ServerPlayer player,
                                        RotorHolderPartMachine machine, UIElement root, String surface) {
        helper.assertTrue(root.getSizeWidth() == 34 && root.getSizeHeight() == 34,
                surface + " Rotor Holder page lost its 34x34 bounds");
        helper.assertTrue(root.getChildren().size() == 3,
                surface + " Rotor Holder page changed its three-element body");

        UIElement container = root.getChildren().stream()
                .filter(child -> child.getLayoutX() == 4 && child.getLayoutY() == 4)
                .filter(child -> child.getSizeWidth() == 26 && child.getSizeHeight() == 26)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Rotor Holder inverse background moved."));
        helper.assertTrue(container.getStyle().getInline(PropertyRegistry.BACKGROUND) == GuiTextures.BACKGROUND_INVERSE,
                surface + " Rotor Holder page lost its inverse background");

        List<GTItemSlotElement> slots = root.getChildren().stream()
                .filter(GTItemSlotElement.class::isInstance)
                .map(GTItemSlotElement.class::cast)
                .toList();
        helper.assertTrue(slots.size() == 1,
                surface + " Rotor Holder page did not expose exactly one rotor slot");
        GTItemSlotElement slot = slots.getFirst();
        ItemHandlerSlot handlerSlot = requireHandlerSlot(slot);
        helper.assertTrue(slot.getLayoutX() == 8 && slot.getLayoutY() == 8 &&
                slot.getSizeWidth() == 18 && slot.getSizeHeight() == 18,
                surface + " Rotor Holder slot moved from its original bounds");
        helper.assertTrue(handlerSlot.getItemHandler() == machine.inventory.storage &&
                handlerSlot.getSlotIndex() == 0,
                surface + " Rotor Holder slot did not bind its real slot-zero handler");
        helper.assertTrue(slot.getIngredientIO() == IngredientIO.NONE,
                surface + " Rotor Holder slot gained an unintended XEI role");
        helper.assertTrue(slot.getStyle().getInline(PropertyRegistry.BACKGROUND) == GuiTextures.SLOT,
                surface + " Rotor Holder slot lost its GT slot background");

        List<GTImageElement> overlays = root.getChildren().stream()
                .filter(GTImageElement.class::isInstance)
                .map(GTImageElement.class::cast)
                .toList();
        helper.assertTrue(overlays.size() == 1 && overlays.getFirst().getLayoutX() == 9 &&
                overlays.getFirst().getLayoutY() == 9 && overlays.getFirst().getSizeWidth() == 16 &&
                overlays.getFirst().getSizeHeight() == 16 && !overlays.getFirst().isVisible(),
                surface + " Rotor Holder lock overlay changed its unlocked layout or visibility");

        ItemStack probe = new ItemStack(Items.STONE);
        helper.assertTrue(handlerSlot.getCanPlace().test(probe) && handlerSlot.getCanTake().test(player),
                surface + " stopped Rotor Holder rejected slot insertion or extraction");
        machine.setRotorSpeed(1);
        overlays.getFirst().screenTick();
        helper.assertTrue(!handlerSlot.getCanPlace().test(probe) && !handlerSlot.getCanTake().test(player) &&
                overlays.getFirst().isVisible(),
                surface + " spinning Rotor Holder did not lock its slot and show the overlay");
        machine.setRotorSpeed(0);
        overlays.getFirst().screenTick();
        helper.assertTrue(handlerSlot.getCanPlace().test(probe) && handlerSlot.getCanTake().test(player) &&
                !overlays.getFirst().isVisible(),
                surface + " stopped Rotor Holder did not unlock its slot and hide the overlay");
    }

    private static ItemHandlerSlot requireHandlerSlot(GTItemSlotElement slot) {
        if (slot.getSlot() instanceof ItemHandlerSlot handlerSlot) {
            return handlerSlot;
        }
        throw new IllegalStateException("Rotor Holder slot was not bound through an item handler.");
    }

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player, MachineUIHolder holder,
                                                           LDLib2FancyUIProvider page) {
        return new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
    }

    private static List<Component> tooltips(UIElement target) {
        UIEvent event = UIEvent.create(UIEvents.HOVER_TOOLTIPS);
        event.target = target;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
        if (event.hoverTooltips == null) {
            throw new IllegalStateException("Rotor Holder warning icon omitted its hover tooltip.");
        }
        return event.hoverTooltips.tooltipTexts();
    }

    private static void clearRotorPlane(ServerLevel level, BlockPos machinePos) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                level.setBlockAndUpdate(machinePos.offset(x, y, -1), Blocks.AIR.defaultBlockState());
            }
        }
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

    private static RotorHolderPartMachine createMachine(BlockPos pos) {
        MachineDefinition definition = GTMachines.ROTOR_HOLDER[GTValues.HV];
        MetaMachine machine = definition.getBlockEntityType().create(pos, definition.defaultBlockState());
        if (machine instanceof RotorHolderPartMachine rotorHolder) {
            return rotorHolder;
        }
        throw new IllegalStateException("Rotor Holder definition did not create its expected machine.");
    }

    private static MultiblockControllerMachine createController(BlockPos pos) {
        MachineDefinition definition = GTMultiMachines.LARGE_CHEMICAL_REACTOR;
        MetaMachine machine = definition.getBlockEntityType().create(pos, definition.defaultBlockState());
        if (machine instanceof MultiblockControllerMachine controller) {
            return controller;
        }
        throw new IllegalStateException("Large Chemical Reactor definition did not create a multiblock controller.");
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

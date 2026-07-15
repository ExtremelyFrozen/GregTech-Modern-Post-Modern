package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;

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

import java.util.ArrayList;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class SteamItemBusPartMachineLDLib2UITest {

    private static final String BATCH = "SteamItemBusPartMachineLDLib2UI";

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void standaloneSteamScreensRemainThemedAndHolderScoped(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SteamItemBusPartMachine input = createBus(GTMachines.STEAM_IMPORT_BUS);
        SteamItemBusPartMachine output = createBus(GTMachines.STEAM_EXPORT_BUS);
        MachineUIHolder inputHolder = new MutableMachineUIHolder(input);
        MachineUIHolder outputHolder = new MutableMachineUIHolder(output);

        helper.assertTrue(input.canCreateLDLib2UI(player, inputHolder),
                "Steam Import Bus rejected its matching standalone holder");
        helper.assertFalse(input.canCreateLDLib2UI(player, outputHolder),
                "Steam Import Bus accepted another machine's standalone holder");
        helper.assertTrue(output.canCreateLDLib2UI(player, outputHolder),
                "Steam Export Bus rejected its matching standalone holder");

        assertStandalonePage(helper, player, input, inputHolder, true, "Steam Import Bus");
        assertStandalonePage(helper, player, output, outputHolder, false, "Steam Export Bus");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void contextualPagesPreserveItemBusSemanticsAndOpeningIdentity(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SteamItemBusPartMachine input = createBus(GTMachines.STEAM_IMPORT_BUS);
        SteamItemBusPartMachine output = createBus(GTMachines.STEAM_EXPORT_BUS);
        MutableMachineUIHolder inputHolder = new MutableMachineUIHolder(input);
        MutableMachineUIHolder outputHolder = new MutableMachineUIHolder(output);
        LDLib2FancyPartUIProvider inputProvider = requireContextualProvider(input);
        LDLib2FancyPartUIProvider outputProvider = requireContextualProvider(output);

        LDLib2FancyUIProvider firstInputPage = inputProvider.createLDLib2FancyPage(player, inputHolder);
        LDLib2FancyUIProvider secondInputPage = inputProvider.createLDLib2FancyPage(player, inputHolder);
        helper.assertTrue(firstInputPage != secondInputPage,
                "Steam Import Bus reused a contextual page provider across openings");

        boolean wrongHolderRejected = false;
        try {
            inputProvider.createLDLib2FancyPage(player, outputHolder);
        } catch (IllegalArgumentException expected) {
            wrongHolderRejected = expected.getMessage().contains("holder");
        }
        helper.assertTrue(wrongHolderRejected,
                "Steam Import Bus contextual page accepted another machine's holder");

        assertContextualPage(helper, player, input, inputHolder, firstInputPage,
                IngredientIO.INPUT, true, false, 3,
                "gtpm.multiblock.page_switcher.io.import", 1, "Steam Import Bus");
        LDLib2FancyUIProvider outputPage = outputProvider.createLDLib2FancyPage(player, outputHolder);
        assertContextualPage(helper, player, output, outputHolder, outputPage,
                IngredientIO.OUTPUT, false, true, 1,
                "gtpm.multiblock.page_switcher.io.export", 2, "Steam Export Bus");

        SteamItemBusPartMachine replacement = createBus(GTMachines.STEAM_IMPORT_BUS);
        helper.assertTrue(replacement.getDefinition() == input.getDefinition() &&
                replacement.getBlockPos().equals(input.getBlockPos()),
                "Steam Item Bus replacement test changed definition or opening position");
        MutableMachineUIHolder staleHolder = new MutableMachineUIHolder(input);
        LDLib2FancyUIProvider stalePage = inputProvider.createLDLib2FancyPage(player, staleHolder);
        staleHolder.setMachine(replacement);
        boolean staleHolderRejected = false;
        try {
            createShell(player, staleHolder, stalePage);
        } catch (IllegalStateException expected) {
            staleHolderRejected = expected.getMessage().contains("no longer");
        }
        helper.assertTrue(staleHolderRejected,
                "Steam Item Bus contextual page accepted a same-definition holder replacement");
        helper.succeed();
    }

    private static void assertStandalonePage(GameTestHelper helper, ServerPlayer player,
                                             SteamItemBusPartMachine machine, MachineUIHolder holder,
                                             boolean canPut, String owner) {
        UIElement root = machine.createLDLib2UI(player, holder).getRootElement();
        boolean steelSteamMultiblocks = ConfigHolder.INSTANCE.machines.steelSteamMultiblocks;
        helper.assertTrue(!(root instanceof LDLib2FancyMachineUIElement) &&
                root.getSizeWidth() == 176 && root.getSizeHeight() == 159,
                owner + " standalone screen lost its dedicated 176x159 layout");
        helper.assertTrue(root.getStyle().getInline(PropertyRegistry.BACKGROUND) ==
                GuiTextures.BACKGROUND_STEAM.get(steelSteamMultiblocks),
                owner + " standalone screen lost its configured Steam background");

        List<GTItemSlotElement> machineSlots = root.getChildren().stream()
                .filter(GTItemSlotElement.class::isInstance)
                .map(GTItemSlotElement.class::cast)
                .toList();
        helper.assertTrue(machineSlots.size() == 4,
                owner + " standalone screen did not retain its 2x2 machine inventory");
        assertInventorySlots(helper, player, machine, machineSlots, IngredientIO.NONE, canPut,
                GuiTextures.SLOT_STEAM.get(steelSteamMultiblocks), owner + " standalone");

        helper.assertTrue(root.getChildren().stream()
                .filter(child -> child.getChildren().size() == 36)
                .filter(child -> child.getChildren().stream().allMatch(GTItemSlotElement.class::isInstance))
                .count() == 1,
                owner + " standalone screen did not retain exactly one player inventory");
        helper.assertTrue(descendants(root).stream()
                .filter(GTItemSlotElement.class::isInstance)
                .count() == 40,
                owner + " standalone screen did not contain four machine and 36 player slots");
        helper.assertTrue(root.getChildren().stream()
                .filter(GTToggleButtonElement.class::isInstance)
                .count() == 1,
                owner + " standalone screen lost its automatic-transfer toggle");
    }

    private static void assertContextualPage(GameTestHelper helper, ServerPlayer player,
                                             SteamItemBusPartMachine machine, MachineUIHolder holder,
                                             LDLib2FancyUIProvider page, IngredientIO expectedRole,
                                             boolean canPut, boolean hasFilter, int configuratorCount,
                                             String groupingKey, int groupingWeight, String owner) {
        helper.assertTrue(page.getLDLib2PageWidth() == 52 && page.getLDLib2PageHeight() == 52,
                owner + " contextual page lost its 2x2 Item Bus bounds");
        helper.assertTrue(page.hasPlayerInventory(),
                owner + " contextual page stopped exposing the player inventory");
        Component expectedTitle = Component.translatable(machine.getDefinition().getDescriptionId());
        helper.assertTrue(page.getTitle().equals(expectedTitle) &&
                page.getTabTooltips().equals(List.of(expectedTitle)),
                owner + " contextual page did not use its definition title");
        LDLib2FancyUIProvider.PageGroupingData grouping = page.getPageGroupingData();
        helper.assertTrue(grouping != null && grouping.groupKey().equals(groupingKey) &&
                grouping.groupPositionWeight() == groupingWeight,
                owner + " contextual page used the wrong IO grouping metadata");

        LDLib2FancyMachineUIElement shell = createShell(player, holder, page);
        helper.assertTrue(shell.getHolder() == holder,
                owner + " contextual shell lost its opening holder");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == configuratorCount,
                owner + " contextual page exposed the wrong working/distinct/circuit configurators");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                owner + " contextual page did not expose exactly one directional side page");

        UIElement root = shell.getChildren().getFirst().getChildren().getFirst();
        List<UIElement> inventoryContainers = root.getChildren().stream()
                .filter(child -> child.getChildren().size() == 4)
                .filter(child -> child.getChildren().stream().allMatch(GTItemSlotElement.class::isInstance))
                .toList();
        helper.assertTrue(inventoryContainers.size() == 1,
                owner + " contextual page did not contain one 2x2 inventory grid");
        List<GTItemSlotElement> machineSlots = inventoryContainers.getFirst().getChildren().stream()
                .map(GTItemSlotElement.class::cast)
                .toList();
        assertInventorySlots(helper, player, machine, machineSlots, expectedRole, canPut,
                GuiTextures.SLOT, owner + " contextual");

        List<GTItemSlotElement> filterSlots = root.getChildren().stream()
                .filter(GTItemSlotElement.class::isInstance)
                .map(GTItemSlotElement.class::cast)
                .toList();
        helper.assertTrue(filterSlots.size() == (hasFilter ? 1 : 0),
                owner + " contextual page exposed the wrong number of filter slots");
        if (hasFilter) {
            ItemHandlerSlot filterSlot = requireHandlerSlot(filterSlots.getFirst(), owner + " filter");
            helper.assertTrue(filterSlot.getItemHandler() != machine.getInventory().storage,
                    owner + " contextual filter slot was bound to the machine inventory");
        }

        int expectedTooltips = machine.showFancyTooltip() ? 1 : 0;
        expectedTooltips += (int) machine.getTraitHolder().getAllTraits().stream()
                .filter(IFancyTooltip.class::isInstance)
                .map(IFancyTooltip.class::cast)
                .filter(IFancyTooltip::showFancyTooltip)
                .count();
        helper.assertTrue(expectedTooltips > 0 &&
                shell.getTooltipsPanel().getChildren().size() == expectedTooltips,
                owner + " contextual page lost its visible machine and trait tooltips");
    }

    private static void assertInventorySlots(GameTestHelper helper, ServerPlayer player,
                                             SteamItemBusPartMachine machine,
                                             List<GTItemSlotElement> slots, IngredientIO expectedRole,
                                             boolean canPut, IGuiTexture expectedBackground, String owner) {
        for (int index = 0; index < slots.size(); index++) {
            GTItemSlotElement slot = slots.get(index);
            ItemHandlerSlot handlerSlot = requireHandlerSlot(slot, owner + " inventory");
            helper.assertTrue(handlerSlot.getItemHandler() == machine.getInventory().storage &&
                    handlerSlot.getSlotIndex() == index,
                    owner + " slot was bound to the wrong handler index");
            helper.assertTrue(handlerSlot.getCanPlace().test(new ItemStack(Items.STONE)) == canPut,
                    owner + " slot used the wrong insertion permission");
            helper.assertTrue(handlerSlot.getCanTake().test(player),
                    owner + " slot did not allow extraction");
            helper.assertTrue(slot.getIngredientIO() == expectedRole,
                    owner + " slot used the wrong XEI role");
            helper.assertTrue(slot.getStyle().getInline(PropertyRegistry.BACKGROUND) == expectedBackground,
                    owner + " slot used the wrong background texture");
        }
    }

    private static ItemHandlerSlot requireHandlerSlot(GTItemSlotElement slot, String owner) {
        if (slot.getSlot() instanceof ItemHandlerSlot handlerSlot) {
            return handlerSlot;
        }
        throw new IllegalStateException(owner + " slot was not bound through an item handler.");
    }

    private static LDLib2FancyPartUIProvider requireContextualProvider(SteamItemBusPartMachine machine) {
        if (machine instanceof LDLib2FancyPartUIProvider provider) {
            return provider;
        }
        throw new IllegalStateException("Steam Item Bus has no contextual LDLib2 page provider.");
    }

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player, MachineUIHolder holder,
                                                           LDLib2FancyUIProvider page) {
        return new LDLib2FancyMachineUIElement(page, player.getInventory(), holder,
                page.getLDLib2PageWidth(), page.getLDLib2PageHeight());
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

    private static SteamItemBusPartMachine createBus(MachineDefinition definition) {
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (machine instanceof SteamItemBusPartMachine steamItemBus) {
            return steamItemBus;
        }
        throw new IllegalStateException("Steam Item Bus definition did not create its expected machine.");
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

package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.data.GTMachines;

import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.PropertyRegistry;
import com.lowdragmc.lowdraglib2.integration.xei.IngredientIO;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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

import java.util.List;

import static com.gregtechceu.gtceu.api.GTValues.LV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class ItemBusPartMachineLDLib2UITest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "ItemBusPartMachineLDLib2UI")
    public static void standalonePagePreservesInventoryAndFancySemantics(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ItemBusPartMachine input = createStandardBus(GTMachines.ITEM_IMPORT_BUS[LV]);
        ItemBusPartMachine output = createStandardBus(GTMachines.ITEM_EXPORT_BUS[LV]);
        ItemBusPartMachine passthrough = createStandardBus(GTMachines.ITEM_PASSTHROUGH_HATCH[LV]);
        EightSlotItemBusPartMachine eightSlot = createEightSlotBus();
        MachineUIHolder inputHolder = new TestMachineUIHolder(input);
        MachineUIHolder outputHolder = new TestMachineUIHolder(output);
        MachineUIHolder passthroughHolder = new TestMachineUIHolder(passthrough);
        MachineUIHolder eightSlotHolder = new TestMachineUIHolder(eightSlot);

        helper.assertTrue(input.canCreateLDLib2UI(player, inputHolder),
                "standard input bus rejected its matching holder");
        helper.assertFalse(input.canCreateLDLib2UI(player, outputHolder),
                "standard input bus accepted another machine's holder");
        helper.assertTrue(output.canCreateLDLib2UI(player, outputHolder),
                "standard output bus rejected its matching holder");
        helper.assertTrue(passthrough.canCreateLDLib2UI(player, passthroughHolder),
                "standard passthrough bus rejected its matching holder");
        helper.assertFalse(eightSlot.canCreateLDLib2UI(player, eightSlotHolder),
                "specialized Item Bus subclass used the standard page without explicitly opting in");

        assertPage(helper, createShell(player, input, inputHolder), input, inputHolder,
                52, 52, IngredientIO.INPUT, true, false, 3);
        assertPage(helper, createShell(player, output, outputHolder), output, outputHolder,
                52, 52, IngredientIO.OUTPUT, false, true, 1);
        assertPage(helper, createShell(player, passthrough, passthroughHolder), passthrough, passthroughHolder,
                52, 52, IngredientIO.INPUT, true, false, 1);
        assertPage(helper, createShell(player, eightSlot, eightSlotHolder), eightSlot, eightSlotHolder,
                88, 52, IngredientIO.INPUT, true, false, 3);

        assertGrouping(helper, input, "gtpm.multiblock.page_switcher.io.import", 1);
        assertGrouping(helper, output, "gtpm.multiblock.page_switcher.io.export", 2);
        assertGrouping(helper, passthrough, "gtpm.multiblock.page_switcher.io.both", 3);

        input.setCircuitSlotEnabled(false);
        LDLib2FancyMachineUIElement inputWithoutCircuit = createShell(player, input, inputHolder);
        helper.assertTrue(inputWithoutCircuit.getConfiguratorPanel().getChildren().size() == 2,
                "input bus with a disabled circuit slot should keep only working and distinct configurators");
        helper.succeed();
    }

    private static void assertPage(GameTestHelper helper, LDLib2FancyMachineUIElement shell,
                                   ItemBusPartMachine machine, MachineUIHolder holder,
                                   int expectedWidth, int expectedHeight, IngredientIO expectedRole,
                                   boolean canPut, boolean hasFilter, int configuratorCount) {
        helper.assertTrue(shell.getHolder() == holder, "Fancy shell did not retain the opening Item Bus holder");
        helper.assertTrue(machine.getLDLib2PageWidth() == expectedWidth &&
                machine.getLDLib2PageHeight() == expectedHeight,
                "Item Bus page dimensions did not match the legacy inventory grid");
        helper.assertTrue(shell.getConfiguratorPanel().getChildren().size() == configuratorCount,
                "Item Bus configurator count did not preserve its IO and circuit semantics");
        helper.assertTrue(shell.getSideTabsElement().getChildren().size() == 2,
                "Item Bus did not expose exactly one holder-scoped directional side tab");

        UIElement pageContainer = shell.getChildren().getFirst();
        UIElement page = pageContainer.getChildren().getFirst();
        List<UIElement> slotContainers = page.getChildren().stream()
                .filter(child -> child.getChildren().stream().allMatch(GTItemSlotElement.class::isInstance))
                .filter(child -> child.getChildren().size() == machine.getInventorySize())
                .toList();
        helper.assertTrue(slotContainers.size() == 1,
                "Item Bus page did not create one inventory-grid container");

        List<GTItemSlotElement> inventorySlots = slotContainers.getFirst().getChildren().stream()
                .map(GTItemSlotElement.class::cast)
                .toList();
        for (int index = 0; index < inventorySlots.size(); index++) {
            GTItemSlotElement slot = inventorySlots.get(index);
            helper.assertTrue(slot.getSlot() instanceof ItemHandlerSlot,
                    "Item Bus inventory element was not bound through an item-handler slot");
            ItemHandlerSlot handlerSlot = (ItemHandlerSlot) slot.getSlot();
            helper.assertTrue(handlerSlot.getItemHandler() == machine.getInventory().storage &&
                    handlerSlot.getSlotIndex() == index,
                    "Item Bus inventory element was bound to the wrong handler slot");
            helper.assertTrue(handlerSlot.getCanPlace().test(new ItemStack(Items.STONE)) == canPut,
                    "Item Bus inventory slot used the wrong insertion permission");
            helper.assertTrue(handlerSlot.getCanTake().test(FakePlayerFactory.getMinecraft(helper.getLevel())),
                    "Item Bus inventory slot did not allow extraction");
            helper.assertTrue(slot.getIngredientIO() == expectedRole,
                    "Item Bus inventory slot used the wrong XEI role");
            helper.assertTrue(slot.getStyle().getInline(PropertyRegistry.BACKGROUND) == GuiTextures.SLOT,
                    "Item Bus inventory slot did not retain the GT slot background");
        }

        List<GTItemSlotElement> filterSlots = page.getChildren().stream()
                .filter(GTItemSlotElement.class::isInstance)
                .map(GTItemSlotElement.class::cast)
                .toList();
        helper.assertTrue(filterSlots.size() == (hasFilter ? 1 : 0),
                "Item Bus page exposed the wrong number of filter slots");
        if (hasFilter) {
            helper.assertTrue(filterSlots.getFirst().getSlot() instanceof ItemHandlerSlot,
                    "output Item Bus filter was not bound to an LDLib2 item-handler slot");
            ItemHandlerSlot filterSlot = (ItemHandlerSlot) filterSlots.getFirst().getSlot();
            helper.assertTrue(filterSlot.getItemHandler() != machine.getInventory().storage,
                    "output Item Bus filter slot was incorrectly bound to its inventory");
        }

        int expectedTooltips = machine.showFancyTooltip() ? 1 : 0;
        expectedTooltips += (int) machine.getTraitHolder().getAllTraits().stream()
                .filter(IFancyTooltip.class::isInstance)
                .map(IFancyTooltip.class::cast)
                .filter(IFancyTooltip::showFancyTooltip)
                .count();
        helper.assertTrue(expectedTooltips > 0 &&
                shell.getTooltipsPanel().getChildren().size() == expectedTooltips,
                "Item Bus Fancy page did not attach its visible machine and trait tooltips");

        click(shell.getSideTabsElement().getChildren().get(1));
        helper.assertTrue(pageContainer.getChildren().size() == 2 &&
                pageContainer.getChildren().get(1).isVisible() && pageContainer.getChildren().get(1).isActive(),
                "Item Bus directional side tab did not navigate to its holder-scoped page");
    }

    private static void assertGrouping(GameTestHelper helper, ItemBusPartMachine machine,
                                       String expectedKey, int expectedWeight) {
        LDLib2FancyUIProvider.PageGroupingData grouping = machine.getLDLib2PageGroupingData();
        helper.assertTrue(grouping != null && grouping.groupKey().equals(expectedKey) &&
                grouping.groupPositionWeight() == expectedWeight,
                "Item Bus LDLib2 page used the wrong IO grouping metadata");
    }

    private static LDLib2FancyMachineUIElement createShell(ServerPlayer player, ItemBusPartMachine machine,
                                                           MachineUIHolder holder) {
        UIElement root = machine.createLDLib2UI(player, holder).getRootElement();
        if (!(root instanceof LDLib2FancyMachineUIElement shell)) {
            throw new IllegalStateException("Item Bus LDLib2 UI did not create a Fancy shell.");
        }
        return shell;
    }

    private static ItemBusPartMachine createStandardBus(MachineDefinition definition) {
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (!(machine instanceof ItemBusPartMachine itemBus) || machine.getClass() != ItemBusPartMachine.class) {
            throw new IllegalStateException("Standard Item Bus definition did not create a standard Item Bus.");
        }
        return itemBus;
    }

    private static EightSlotItemBusPartMachine createEightSlotBus() {
        MachineDefinition definition = GTMachines.ITEM_IMPORT_BUS[LV];
        return new EightSlotItemBusPartMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()));
    }

    private static void click(UIElement element) {
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = element;
        event.button = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
    }

    private static final class EightSlotItemBusPartMachine extends ItemBusPartMachine {

        private EightSlotItemBusPartMachine(BlockEntityCreationInfo info) {
            super(info, LV, IO.IN);
        }

        @Override
        protected int getInventorySize() {
            return 8;
        }
    }

    private record TestMachineUIHolder(ItemBusPartMachine machine) implements MachineUIHolder {

        @Override
        public BlockPos getPos() {
            return machine.getBlockPos();
        }

        @Override
        public ResourceLocation getMachineDefinitionId() {
            return machine.getDefinition().getId();
        }

        @Override
        public ItemBusPartMachine getMachine() {
            return machine;
        }
    }
}

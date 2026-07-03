package com.gregtechceu.gtceu.api.multiblock.autobuild;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.common.data.GTItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.HashMap;
import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder("gtpm_multiblock_autobuild")
public class AutoBuildBackendTest {

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", templateNamespace = GTCEu.MOD_ID, batch = "AutoBuildBackend")
    public static void blockMapSupportsCategoriesAliasesAndReverseLookup(GameTestHelper helper) {
        Block[] batteries = AutoBuildBlockMap.categoryBlocks(AutoBuildBlockMap.POWER_SUBSTATION_BATTERIES);
        Block[] batteryAlias = AutoBuildBlockMap.categoryBlocks(AutoBuildBlockMap.BATTERIES);
        helper.assertTrue(batteries != null && batteryAlias != null, "PSS battery categories missing");
        helper.assertTrue(batteries.length == batteryAlias.length, "PSS battery alias size mismatch");
        helper.assertTrue(batteries[0] == batteryAlias[0], "PSS battery alias does not target same blocks");
        helper.assertTrue(AutoBuildBlockMap.POWER_SUBSTATION_BATTERIES.equals(
                AutoBuildBlockMap.category(batteries[0])), "PSS battery reverse lookup returned wrong category");
        helper.assertTrue(AutoBuildBlockMap.POWER_SUBSTATION_BATTERIES.equals(
                AutoBuildBlockMap.canonicalCategory(AutoBuildBlockMap.BATTERIES)),
                "PSS battery alias did not canonicalize");

        Block[] rotorHolders = AutoBuildBlockMap.categoryBlocks(AutoBuildBlockMap.ROTOR_HOLDER);
        Block[] rotorAlias = AutoBuildBlockMap.categoryBlocks(AutoBuildBlockMap.ROTOR_HOLDERS);
        helper.assertTrue(rotorHolders != null && rotorAlias != null, "Rotor holder categories missing");
        helper.assertTrue(rotorHolders.length == rotorAlias.length, "Rotor holder alias size mismatch");
        helper.assertTrue(rotorHolders[0] == rotorAlias[0], "Rotor holder alias does not target same blocks");
        helper.assertTrue(AutoBuildBlockMap.ROTOR_HOLDER.equals(AutoBuildBlockMap.category(rotorHolders[0])),
                "Rotor holder reverse lookup returned wrong category");

        Block[] coils = AutoBuildBlockMap.categoryBlocks(AutoBuildBlockMap.HEATING_COILS);
        helper.assertTrue(coils != null && coils.length > 1, "Heating coil category missing");
        Block firstCoil = coils[0];
        coils[0] = Blocks.AIR;
        helper.assertTrue(AutoBuildBlockMap.categoryBlocks(AutoBuildBlockMap.HEATING_COILS)[0] == firstCoil,
                "BlockMap exposed mutable category storage");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", templateNamespace = GTCEu.MOD_ID, batch = "AutoBuildBackend")
    public static void itemHandlerSourceReservesNestedContainersAndRefundsInOrder(GameTestHelper helper) {
        ItemStackHandler root = new ItemStackHandler(3);
        ItemStack nestedContainer = GTItems.TERMINAL.asStack();
        IItemHandler nested = nestedContainer.getCapability(Capabilities.ItemHandler.ITEM);
        helper.assertTrue(nested != null, "Terminal item handler missing");
        nested.insertItem(0, new ItemStack(Blocks.STONE, 2), false);
        root.setStackInSlot(0, nestedContainer);

        AutoBuildMaterialSource.Session session = AutoBuildMaterialSources.itemHandler(root).openSession();
        AutoBuildMaterialSource.Reservation first = session.reserve(List.of(new ItemStack(Blocks.STONE)));
        AutoBuildMaterialSource.Reservation second = session.reserve(List.of(new ItemStack(Blocks.STONE)));
        AutoBuildMaterialSource.Reservation third = session.reserve(List.of(new ItemStack(Blocks.STONE)));
        helper.assertTrue(first != null, "First nested reservation failed");
        helper.assertTrue(second != null, "Second nested reservation failed");
        helper.assertTrue(third == null, "Source reserved more nested items than available");
        helper.assertTrue(first.commit(), "First nested commit failed");
        helper.assertTrue(second.commit(), "Second nested commit failed");
        nested = root.getStackInSlot(0).getCapability(Capabilities.ItemHandler.ITEM);
        helper.assertTrue(nested != null, "Nested Terminal item handler missing after commit");
        helper.assertTrue(nested.getStackInSlot(0).isEmpty(), "Nested source did not extract committed items");

        ItemStack remainder = session.insert(new ItemStack(Blocks.COBBLESTONE, 1), false);
        helper.assertTrue(remainder.isEmpty(), "Nested insert returned remainder");
        nested = root.getStackInSlot(0).getCapability(Capabilities.ItemHandler.ITEM);
        helper.assertTrue(nested != null, "Nested Terminal item handler missing after insert");
        helper.assertTrue(ItemStack.isSameItemSameComponents(nested.getStackInSlot(0),
                new ItemStack(Blocks.COBBLESTONE)),
                "Nested insert did not prefer child container");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", templateNamespace = GTCEu.MOD_ID, batch = "AutoBuildBackend")
    public static void itemHandlerSourceExcludesCurrentTerminalContainer(GameTestHelper helper) {
        ItemStackHandler root = new ItemStackHandler(3);
        ItemStack terminal = GTItems.TERMINAL.asStack();
        IItemHandler terminalInventory = terminal.getCapability(Capabilities.ItemHandler.ITEM);
        helper.assertTrue(terminalInventory != null, "Terminal item handler missing");
        terminalInventory.insertItem(0, new ItemStack(Blocks.STONE, 1), false);
        root.setStackInSlot(0, terminal);
        root.setStackInSlot(1, new ItemStack(Blocks.COBBLESTONE, 1));

        AutoBuildMaterialSource.Session session = AutoBuildMaterialSources.itemHandler(root, terminal).openSession();
        AutoBuildMaterialSource.Reservation terminalReservation = session.reserve(List.of(new ItemStack(Blocks.STONE)));
        AutoBuildMaterialSource.Reservation backpackReservation = session.reserve(
                List.of(new ItemStack(Blocks.COBBLESTONE)));
        helper.assertTrue(terminalReservation == null, "Player inventory source reserved excluded Terminal inventory");
        helper.assertTrue(backpackReservation != null, "Player inventory source skipped normal backpack item");

        ItemStack remainder = session.insert(new ItemStack(Blocks.DIRT, 1), false);
        helper.assertTrue(remainder.isEmpty(), "Insert into player inventory returned remainder");
        helper.assertTrue(terminalInventory.getStackInSlot(1).isEmpty(),
                "Player inventory source inserted refund into excluded Terminal inventory");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", templateNamespace = GTCEu.MOD_ID, batch = "AutoBuildBackend")
    public static void itemHandlerSourceDoesNotReserveBeyondSimulatedExtraction(GameTestHelper helper) {
        ItemStackHandler root = new LimitedSimulatedExtractItemHandler();
        root.setStackInSlot(0, new ItemStack(Blocks.STONE, 2));

        AutoBuildMaterialSource.Session session = AutoBuildMaterialSources.itemHandler(root).openSession();
        AutoBuildMaterialSource.Reservation first = session.reserve(List.of(new ItemStack(Blocks.STONE)));
        AutoBuildMaterialSource.Reservation second = session.reserve(List.of(new ItemStack(Blocks.STONE)));
        helper.assertTrue(first != null, "First throttled reservation failed");
        helper.assertTrue(second == null, "Source reserved more items than simulated extraction exposed");
        helper.assertTrue(first.commit(), "First throttled commit failed");
        helper.assertTrue(root.getStackInSlot(0).getCount() == 1, "Throttled commit extracted wrong amount");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", templateNamespace = GTCEu.MOD_ID, batch = "AutoBuildBackend")
    public static void terminalItemHandlerPersistsTwentySevenSlots(GameTestHelper helper) {
        ItemStack terminal = GTItems.TERMINAL.asStack();
        IItemHandler handler = terminal.getCapability(Capabilities.ItemHandler.ITEM);
        helper.assertTrue(handler != null, "Terminal item handler missing");
        helper.assertTrue(handler.getSlots() == 27, "Terminal item handler size changed");
        ItemStack remainder = handler.insertItem(26, new ItemStack(Items.DIAMOND, 3), false);
        helper.assertTrue(remainder.isEmpty(), "Terminal item handler rejected valid insert");

        IItemHandler reloadedHandler = terminal.getCapability(Capabilities.ItemHandler.ITEM);
        helper.assertTrue(reloadedHandler != null, "Reloaded Terminal item handler missing");
        helper.assertTrue(ItemStack.isSameItemSameComponents(reloadedHandler.getStackInSlot(26),
                new ItemStack(Items.DIAMOND)), "Terminal item handler did not persist item identity");
        helper.assertTrue(reloadedHandler.getStackInSlot(26).getCount() == 3,
                "Terminal item handler did not persist item count");
        ItemContainerContents contents = terminal.get(DataComponents.CONTAINER);
        helper.assertTrue(contents != null, "Terminal item handler did not write container data component");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate("5")
    @GameTest(template = "empty_5x5", templateNamespace = GTCEu.MOD_ID, batch = "AutoBuildBackend")
    public static void autoBuildOptionsCopiesTierSelectionMap(GameTestHelper helper) {
        HashMap<String, Integer> selections = new HashMap<>();
        selections.put(AutoBuildBlockMap.HEATING_COILS, 1);
        AutoBuildOptions options = new AutoBuildOptions(0, false, false, false, false, true, selections);
        selections.put(AutoBuildBlockMap.HEATING_COILS, 2);
        helper.assertTrue(options.tierSelections().get(AutoBuildBlockMap.HEATING_COILS) == 1,
                "AutoBuildOptions kept mutable tier selection map");
        try {
            options.tierSelections().put(AutoBuildBlockMap.LAMPS, 1);
            helper.fail("AutoBuildOptions tier selection map is mutable");
        } catch (UnsupportedOperationException ignored) {
            helper.succeed();
        }
    }

    private static final class LimitedSimulatedExtractItemHandler extends ItemStackHandler {

        private LimitedSimulatedExtractItemHandler() {
            super(1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return super.extractItem(slot, simulate ? 1 : amount, simulate);
        }
    }
}

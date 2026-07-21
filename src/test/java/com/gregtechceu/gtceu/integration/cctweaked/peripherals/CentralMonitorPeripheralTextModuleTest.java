package com.gregtechceu.gtceu.integration.cctweaked.peripherals;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IMonitorComponent;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.item.datacomponents.TextLineList;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorTextModuleActions;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import dan200.computercraft.api.lua.LuaException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CentralMonitorPeripheralTextModuleTest {

    private static final String BATCH = "CentralMonitorPeripheralTextModule";
    private static final BlockPos MONITOR_POSITION = new BlockPos(1, 0, 0);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void staleLuaModuleCannotWriteIdenticalPhysicalReplacement(GameTestHelper helper) {
        TestCentralMonitorMachine machine = preparedMachine(helper);
        MonitorGroup group = machine.getMonitorGroups().getFirst();
        ItemStack openingModule = textModule("opening");
        group.getItemStackHandler().setStackInSlot(0, openingModule);
        UUID openingSlotIncarnation = group.getModuleSlotIncarnation();
        CentralMonitorPeripheral.LuaMonitorModule staleModule = new CentralMonitorPeripheral.LuaMonitorGroup(machine,
                group).getModule();

        ItemStack replacement = textModule("opening");
        group.getItemStackHandler().setStackInSlot(0, replacement);
        helper.assertTrue(!group.getModuleSlotIncarnation().equals(openingSlotIncarnation) &&
                group.getItemStackHandler().getStackInSlot(0) == replacement,
                "Central Monitor test did not install a distinct physical text module replacement");

        boolean rejected = false;
        try {
            staleModule.setPlaceholderText("stale write");
        } catch (LuaException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected,
                "stale ComputerCraft text module wrapper changed an identical physical replacement");
        helper.assertTrue(group.getItemStackHandler().getStackInSlot(0) == replacement &&
                configuration("opening").equals(
                        replacement.get(GTDataComponents.FORMAT_STRING_LIST.get())) &&
                group.getTextConfigurationRevision() == 0,
                "rejected stale ComputerCraft write changed the replacement module or revision");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void luaModuleAdvancesItsRevisionAfterEachSuccessfulWrite(GameTestHelper helper) {
        TestCentralMonitorMachine machine = preparedMachine(helper);
        MonitorGroup group = machine.getMonitorGroups().getFirst();
        ItemStack module = textModule("opening");
        group.getItemStackHandler().setStackInSlot(0, module);
        CentralMonitorPeripheral.LuaMonitorModule luaModule = new CentralMonitorPeripheral.LuaMonitorGroup(machine,
                group).getModule();

        try {
            luaModule.setPlaceholderText("first");
            luaModule.setPlaceholderText("second");
        } catch (LuaException exception) {
            helper.fail("current ComputerCraft text module wrapper rejected a sequential write: " +
                    exception.getMessage());
            return;
        }

        helper.assertTrue(group.getItemStackHandler().getStackInSlot(0) == module &&
                configuration("second").equals(module.get(GTDataComponents.FORMAT_STRING_LIST.get())) &&
                group.getTextConfigurationRevision() == 2,
                "ComputerCraft text module wrapper did not advance two in-place configuration writes");
        helper.succeed();
    }

    private static TestCentralMonitorMachine preparedMachine(GameTestHelper helper) {
        TestCentralMonitorMachine machine = new TestCentralMonitorMachine();
        machine.setLevel(helper.getLevel());
        machine.addMonitor(MONITOR_POSITION);
        helper.assertTrue(machine.createCentralMonitorGroup(
                0, UUID.randomUUID(), Set.of(MONITOR_POSITION)),
                "server rejected ComputerCraft text module test group creation");
        return machine;
    }

    private static TextLineList configuration(String text) {
        return CentralMonitorTextModuleActions.createConfiguration(List.of(text), 1.0f);
    }

    private static ItemStack textModule(String text) {
        ItemStack module = GTItems.TEXT_MODULE.get().getDefaultInstance();
        module.set(GTDataComponents.FORMAT_STRING_LIST.get(), configuration(text));
        return module;
    }

    private static BlockEntityCreationInfo centralMonitorInfo() {
        return new BlockEntityCreationInfo(GTMultiMachines.CENTRAL_MONITOR.getBlockEntityType(), BlockPos.ZERO,
                GTMultiMachines.CENTRAL_MONITOR.defaultBlockState());
    }

    private static final class TestCentralMonitorMachine extends CentralMonitorMachine {

        private final Map<BlockPos, IMonitorComponent> components = new HashMap<>();

        private TestCentralMonitorMachine() {
            super(centralMonitorInfo());
        }

        private void addMonitor(BlockPos position) {
            components.put(position, new TestMonitorComponent(position));
        }

        @Override
        public boolean isRemote() {
            return false;
        }

        @Override
        protected boolean isMembershipStructureAvailable() {
            return true;
        }

        @Override
        public int getCentralMonitorMembershipCapacity() {
            return components.size();
        }

        @Override
        protected Map<BlockPos, IMonitorComponent> resolveMembershipComponents() {
            return new HashMap<>(components);
        }
    }

    private record TestMonitorComponent(BlockPos position) implements IMonitorComponent {

        @Override
        public boolean isMonitor() {
            return true;
        }

        @Override
        public IGuiTexture getComponentIcon() {
            return GuiTextures.BLANK_TRANSPARENT;
        }

        @Override
        public BlockPos getBlockPos() {
            return position;
        }
    }
}

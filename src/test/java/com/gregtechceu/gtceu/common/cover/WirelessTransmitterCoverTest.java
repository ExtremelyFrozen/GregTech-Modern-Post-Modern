package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.item.datacomponents.SimpleEnergyContent;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.datacomponents.TextLineList;
import com.gregtechceu.gtceu.common.item.modules.TextModuleBehaviour;
import com.gregtechceu.gtceu.common.machine.electric.BatteryBufferMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;
import com.gregtechceu.gtceu.common.machine.multiblock.part.DataAccessHatchMachine;
import com.gregtechceu.gtceu.gametest.util.TestUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.ForEachTest;
import net.neoforged.testframework.annotation.TestHolder;

import java.util.List;
import java.util.function.Supplier;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
@ForEachTest(groups = "coverTests")
public class WirelessTransmitterCoverTest {

    private static final long HV_BATTERY_CAPACITY = 1_800_000L;
    private static final String ENERGY_FORMAT = "Energy: {formatInt {energy}}/{formatInt {energyCapacity}} EU";

    private static ItemStack hvLithiumBattery(long charge) {
        ItemStack stack = GTItems.BATTERY_HV_LITHIUM.asStack();
        stack.set(GTDataComponents.ENERGY_CONTENT, new SimpleEnergyContent(HV_BATTERY_CAPACITY, charge));
        return stack;
    }

    private static ItemStack energyTextModule() {
        ItemStack module = GTItems.TEXT_MODULE.asStack();
        module.set(GTDataComponents.FORMAT_STRING_LIST,
                new TextLineList(List.of(Component.literal(ENERGY_FORMAT)), 1.0f));
        return module;
    }

    @TestHolder()
    @GameTest(template = "central_monitor", batch = "coverTests")
    public static void wirelessTransmitterCoverTest(GameTestHelper helper) {
        CentralMonitorMachine machine = helper.getBlockEntity(new BlockPos(1, 3, 2));
        DataAccessHatchMachine dataHatch = helper.getBlockEntity(new BlockPos(1, 2, 2));
        BatteryBufferMachine batteryBuffer = helper.getBlockEntity(new BlockPos(2, 2, 3));
        helper.assertTrue(machine != null, "Central monitor controller missing");
        TestUtils.formMultiblock(machine);
        batteryBuffer.getBatteryInventory().setStackInSlot(0, hvLithiumBattery(HV_BATTERY_CAPACITY));
        batteryBuffer.getBatteryInventory().setStackInSlot(1, hvLithiumBattery(HV_BATTERY_CAPACITY));
        batteryBuffer.getBatteryInventory().setStackInSlot(2, hvLithiumBattery(HV_BATTERY_CAPACITY));
        batteryBuffer.getBatteryInventory().setStackInSlot(3, hvLithiumBattery(0));
        WirelessTransmitterCover cover = (WirelessTransmitterCover) TestUtils.placeCover(helper, batteryBuffer,
                GTItems.COVER_WIRELESS_TRANSMITTER.asStack(), Direction.UP);
        MonitorGroup group = new MonitorGroup("test");
        group.add(helper.absolutePos(new BlockPos(2, 1, 2)));
        group.getItemStackHandler().setStackInSlot(0, energyTextModule());
        machine.getMonitorGroups().add(group);
        group.setTarget(dataHatch.getBlockPos());
        group.setDataSlot(0);
        Supplier<ItemStack> module = () -> group.getItemStackHandler().getStackInSlot(0);
        ItemStack stack = GTItems.TOOL_DATA_STICK.asStack();
        // noinspection DataFlowIssue
        cover.onDataStickUse(helper.makeMockPlayer(GameType.CREATIVE), stack);
        dataHatch.importItems.setStackInSlot(0, stack);
        TestUtils.assertEqual(helper, module.get(), GTItems.TEXT_MODULE.asStack());
        helper.runAtTickTime(80, () -> {
            TestUtils.assertEqual(helper, group.getTarget(helper.getLevel()),
                    helper.absolutePos(new BlockPos(2, 2, 3)));
            TestUtils.assertEqual(helper, new TextModuleBehaviour().getText(module.get()), "Energy: 5.40M/7.20M EU\n");
            helper.succeed();
        });
    }
}

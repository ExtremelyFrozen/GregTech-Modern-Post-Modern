package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyUIMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.multiblock.AutoStockingPart;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LDLib2AutoStockingFancyConfiguratorActionTest {

    private static final ResourceLocation SET_AUTO_STOCKING_MIN_STACK_SIZE_ACTION = GTCEu
            .id("set_auto_stocking_min_stack_size");
    private static final ResourceLocation SET_AUTO_STOCKING_TICKS_PER_CYCLE_ACTION = GTCEu
            .id("set_auto_stocking_ticks_per_cycle");
    private static final ResourceLocation MIN_STACK_SIZE_FIELD = SyncFieldData.key("minStackSize");
    private static final ResourceLocation TICKS_PER_CYCLE_FIELD = SyncFieldData.key("ticksPerCycle");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("otherField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2AutoStockingFancyConfiguratorAction")
    public static void dispatcherExecutesMinStackSizeActionForFancyAutoStockingHolder(GameTestHelper helper) {
        TestFancyAutoStockingHolder holder = new TestFancyAutoStockingHolder(1,
                ConfigHolder.INSTANCE.compat.ae2.updateIntervals);
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder, SET_AUTO_STOCKING_MIN_STACK_SIZE_ACTION,
                payload(MIN_STACK_SIZE_FIELD, new JsonPrimitive(16)));

        helper.assertTrue(result, "valid auto-stocking min stack size action was rejected");
        helper.assertTrue(holder.getMinStackSize() == 16,
                "valid auto-stocking min stack size action did not update holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2AutoStockingFancyConfiguratorAction")
    public static void dispatcherExecutesAutoStockingActionsForFancyActionMarkerHolder(GameTestHelper helper) {
        int updateIntervals = ConfigHolder.INSTANCE.compat.ae2.updateIntervals;
        TestFancyActionAutoStockingHolder holder = new TestFancyActionAutoStockingHolder(1, updateIntervals);
        triggerActionRegistration(holder);

        boolean minStackSizeResult = dispatch(helper, holder, SET_AUTO_STOCKING_MIN_STACK_SIZE_ACTION,
                payload(MIN_STACK_SIZE_FIELD, new JsonPrimitive(16)));
        boolean ticksPerCycleResult = dispatch(helper, holder, SET_AUTO_STOCKING_TICKS_PER_CYCLE_ACTION,
                payload(TICKS_PER_CYCLE_FIELD, new JsonPrimitive(updateIntervals + 5)));

        helper.assertTrue(minStackSizeResult, "min stack size action rejected marker-only holder");
        helper.assertTrue(ticksPerCycleResult, "ticks per cycle action rejected marker-only holder");
        helper.assertTrue(holder.getMinStackSize() == 16, "marker-only holder min stack size was not updated");
        helper.assertTrue(holder.getTicksPerCycle() == updateIntervals + 5,
                "marker-only holder ticks per cycle was not updated");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2AutoStockingFancyConfiguratorAction")
    public static void dispatcherExecutesTicksPerCycleActionForFancyAutoStockingHolder(GameTestHelper helper) {
        int updateIntervals = ConfigHolder.INSTANCE.compat.ae2.updateIntervals;
        TestFancyAutoStockingHolder holder = new TestFancyAutoStockingHolder(1, updateIntervals);
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder, SET_AUTO_STOCKING_TICKS_PER_CYCLE_ACTION,
                payload(TICKS_PER_CYCLE_FIELD, new JsonPrimitive(updateIntervals + 5)));

        helper.assertTrue(result, "valid auto-stocking ticks per cycle action was rejected");
        helper.assertTrue(holder.getTicksPerCycle() == updateIntervals + 5,
                "valid auto-stocking ticks per cycle action did not update holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2AutoStockingFancyConfiguratorAction")
    public static void dispatcherRejectsAutoStockingHolderWithoutFancyMachineContract(GameTestHelper helper) {
        int updateIntervals = ConfigHolder.INSTANCE.compat.ae2.updateIntervals;
        TestAutoStockingHolder holder = new TestAutoStockingHolder(1, updateIntervals);
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder, SET_AUTO_STOCKING_MIN_STACK_SIZE_ACTION,
                payload(MIN_STACK_SIZE_FIELD, new JsonPrimitive(16)));

        helper.assertTrue(!result, "non-fancy auto-stocking holder was accepted");
        helper.assertTrue(holder.getMinStackSize() == 1, "rejected holder action changed min stack size");
        helper.assertTrue(holder.getTicksPerCycle() == updateIntervals,
                "rejected holder action changed ticks per cycle");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2AutoStockingFancyConfiguratorAction")
    public static void dispatcherRejectsInvalidAutoStockingPayload(GameTestHelper helper) {
        int updateIntervals = ConfigHolder.INSTANCE.compat.ae2.updateIntervals;
        TestFancyAutoStockingHolder holder = new TestFancyAutoStockingHolder(1, updateIntervals);
        triggerActionRegistration(holder);

        boolean minStringResult = dispatch(helper, holder, SET_AUTO_STOCKING_MIN_STACK_SIZE_ACTION,
                payload(MIN_STACK_SIZE_FIELD, new JsonPrimitive("16")));
        boolean minZeroResult = dispatch(helper, holder, SET_AUTO_STOCKING_MIN_STACK_SIZE_ACTION,
                payload(MIN_STACK_SIZE_FIELD, new JsonPrimitive(0)));
        boolean ticksStringResult = dispatch(helper, holder, SET_AUTO_STOCKING_TICKS_PER_CYCLE_ACTION,
                payload(TICKS_PER_CYCLE_FIELD, new JsonPrimitive(String.valueOf(updateIntervals + 5))));
        boolean ticksBelowMinimumResult = dispatch(helper, holder, SET_AUTO_STOCKING_TICKS_PER_CYCLE_ACTION,
                payload(TICKS_PER_CYCLE_FIELD, new JsonPrimitive(updateIntervals - 1)));
        boolean missingFieldResult = dispatch(helper, holder, SET_AUTO_STOCKING_MIN_STACK_SIZE_ACTION,
                payload(OTHER_FIELD, new JsonPrimitive(16)));

        helper.assertTrue(!minStringResult, "string min stack size payload was accepted");
        helper.assertTrue(!minZeroResult, "zero min stack size payload was accepted");
        helper.assertTrue(!ticksStringResult, "string ticks per cycle payload was accepted");
        helper.assertTrue(!ticksBelowMinimumResult, "ticks per cycle below update interval was accepted");
        helper.assertTrue(!missingFieldResult, "payload without auto-stocking field was accepted");
        helper.assertTrue(holder.getMinStackSize() == 1, "invalid payload changed min stack size");
        helper.assertTrue(holder.getTicksPerCycle() == updateIntervals, "invalid payload changed ticks per cycle");
        helper.succeed();
    }

    private static void triggerActionRegistration(AutoStockingPart holder) {
        new LDLib2AutoStockingFancyConfigurator(holder, new TestMachineUIHolder());
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, ResourceLocation actionId,
                                    DataComponentMap payload) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SyncActionData action = new SyncActionData(actionId, 1, payload);
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static class TestAutoStockingHolder implements AutoStockingPart {

        private int minStackSize;
        private int ticksPerCycle;

        private TestAutoStockingHolder(int minStackSize, int ticksPerCycle) {
            this.minStackSize = minStackSize;
            this.ticksPerCycle = ticksPerCycle;
        }

        @Override
        public int getMinStackSize() {
            return minStackSize;
        }

        @Override
        public void setMinStackSize(int minStackSize) {
            this.minStackSize = minStackSize;
        }

        @Override
        public int getTicksPerCycle() {
            return ticksPerCycle;
        }

        @Override
        public void setTicksPerCycle(int ticksPerCycle) {
            this.ticksPerCycle = ticksPerCycle;
        }

        @Override
        public StockingTarget getStockingTarget() {
            return StockingTarget.ITEM;
        }
    }

    private static final class TestFancyAutoStockingHolder extends TestAutoStockingHolder
                                                           implements LDLib2FancyUIMachine {

        private TestFancyAutoStockingHolder(int minStackSize, int ticksPerCycle) {
            super(minStackSize, ticksPerCycle);
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            return new UIElement();
        }

        @Override
        public IGuiTexture getTabIcon() {
            return IGuiTexture.EMPTY;
        }

        @Override
        public Component getTitle() {
            return Component.literal("test fancy auto-stocking holder");
        }
    }

    private static final class TestFancyActionAutoStockingHolder extends TestAutoStockingHolder
                                                                 implements LDLib2FancyActionMachine {

        private TestFancyActionAutoStockingHolder(int minStackSize, int ticksPerCycle) {
            super(minStackSize, ticksPerCycle);
        }
    }

    private static final class TestMachineUIHolder implements MachineUIHolder {

        @Override
        public BlockPos getPos() {
            return BlockPos.ZERO;
        }

        @Override
        public ResourceLocation getMachineDefinitionId() {
            return GTCEu.id("test_machine");
        }

        @Override
        public @Nullable MetaMachine getMachine() {
            return null;
        }
    }
}

package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.SteamItemBus;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class SteamItemBusPartMachineActionTest {

    private static final ResourceLocation SET_STEAM_ITEM_BUS_CONFIG_ACTION = GTCEu
            .id("set_steam_item_bus_config");
    private static final ResourceLocation WORKING_ENABLED_FIELD = SyncFieldData.key("workingEnabled");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("otherField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SteamItemBusPartMachineAction")
    public static void dispatcherExecutesWorkingEnabledActionForSteamItemBusHolder(GameTestHelper helper) {
        TestSteamItemBus holder = new TestSteamItemBus(false);
        triggerActionRegistration();

        boolean result = dispatch(helper, holder, payload(WORKING_ENABLED_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(result, "valid steam item bus action was rejected");
        helper.assertTrue(holder.isWorkingEnabled(), "valid steam item bus action did not update holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SteamItemBusPartMachineAction")
    public static void dispatcherRejectsHolderWithoutSteamItemBusContract(GameTestHelper helper) {
        TestControllableOnlyHolder holder = new TestControllableOnlyHolder(false);
        triggerActionRegistration();

        boolean result = dispatch(helper, holder, payload(WORKING_ENABLED_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(!result, "non-steam-item-bus holder was accepted");
        helper.assertTrue(!holder.isWorkingEnabled(), "rejected holder action changed holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "SteamItemBusPartMachineAction")
    public static void dispatcherRejectsInvalidWorkingEnabledPayload(GameTestHelper helper) {
        TestSteamItemBus holder = new TestSteamItemBus(false);
        triggerActionRegistration();

        boolean stringResult = dispatch(helper, holder,
                payload(WORKING_ENABLED_FIELD, new JsonPrimitive("true")));
        boolean missingFieldResult = dispatch(helper, holder,
                payload(OTHER_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(!stringResult, "string working-enabled payload was accepted");
        helper.assertTrue(!missingFieldResult, "payload without working-enabled field was accepted");
        helper.assertTrue(!holder.isWorkingEnabled(), "invalid payload changed holder state");
        helper.succeed();
    }

    private static void triggerActionRegistration() {
        var definition = GTMachines.STEAM_IMPORT_BUS;
        if (definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState()) == null) {
            throw new IllegalStateException("Steam item bus test failed to create a machine block entity.");
        }
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, DataComponentMap payload) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SyncActionData action = new SyncActionData(SET_STEAM_ITEM_BUS_CONFIG_ACTION, 1, payload);
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

    private static final class TestSteamItemBus implements SteamItemBus {

        private boolean workingEnabled;

        private TestSteamItemBus(boolean workingEnabled) {
            this.workingEnabled = workingEnabled;
        }

        @Override
        public boolean isWorkingEnabled() {
            return workingEnabled;
        }

        @Override
        public void setWorkingEnabled(boolean isWorkingAllowed) {
            workingEnabled = isWorkingAllowed;
        }
    }

    private static final class TestControllableOnlyHolder implements IControllable {

        private boolean workingEnabled;

        private TestControllableOnlyHolder(boolean workingEnabled) {
            this.workingEnabled = workingEnabled;
        }

        @Override
        public boolean isWorkingEnabled() {
            return workingEnabled;
        }

        @Override
        public void setWorkingEnabled(boolean isWorkingAllowed) {
            workingEnabled = isWorkingAllowed;
        }
    }
}

package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyUIMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LDLib2WorkingEnabledFancyConfiguratorActionTest {

    private static final ResourceLocation SET_WORKING_ENABLED_ACTION = GTCEu.id("set_working_enabled");
    private static final ResourceLocation WORKING_ENABLED_FIELD = SyncFieldData.key("workingEnabled");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("otherField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2WorkingEnabledFancyConfiguratorAction")
    public static void factoryEncodesAndDispatcherExecutesBothStatesOnce(GameTestHelper helper) {
        TestFancyControllableHolder holder = new TestFancyControllableHolder(false);
        SyncActionData enable = LDLib2WorkingEnabledFancyConfiguratorActions.createSetWorkingEnabledAction(true);

        helper.assertTrue(enable.actionId().equals(SET_WORKING_ENABLED_ACTION) && enable.sequence() == 1 &&
                requireWorkingEnabledPayload(enable), "enabled action factory encoded the wrong action");
        boolean enableResult = dispatch(helper, holder, enable);

        helper.assertTrue(enableResult, "valid enabled action was rejected");
        helper.assertTrue(holder.isWorkingEnabled() && holder.getSetWorkingEnabledCalls() == 1,
                "enabled action did not execute exactly once");

        SyncActionData disable = LDLib2WorkingEnabledFancyConfiguratorActions.createSetWorkingEnabledAction(false);
        helper.assertTrue(disable.actionId().equals(SET_WORKING_ENABLED_ACTION) && disable.sequence() == 0 &&
                !requireWorkingEnabledPayload(disable), "disabled action factory encoded the wrong action");
        boolean disableResult = dispatch(helper, holder, disable);

        helper.assertTrue(disableResult, "valid disabled action was rejected");
        helper.assertTrue(!holder.isWorkingEnabled() && holder.getSetWorkingEnabledCalls() == 2,
                "disabled action did not execute exactly once");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2WorkingEnabledFancyConfiguratorAction")
    public static void dispatcherExecutesWorkingEnabledActionForFancyActionMarkerHolder(GameTestHelper helper) {
        TestFancyActionControllableHolder holder = new TestFancyActionControllableHolder(false);

        boolean result = dispatch(helper, holder,
                LDLib2WorkingEnabledFancyConfiguratorActions.createSetWorkingEnabledAction(true));

        helper.assertTrue(result, "working-enabled action rejected marker-only holder");
        helper.assertTrue(holder.isWorkingEnabled() && holder.getSetWorkingEnabledCalls() == 1,
                "marker-only holder action did not execute exactly once");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2WorkingEnabledFancyConfiguratorAction")
    public static void dispatcherRejectsHolderWithoutFancyMachineContract(GameTestHelper helper) {
        TestControllableOnlyHolder holder = new TestControllableOnlyHolder(false);
        TestFancyActionOnlyHolder markerOnlyHolder = new TestFancyActionOnlyHolder();

        boolean result = dispatch(helper, holder,
                LDLib2WorkingEnabledFancyConfiguratorActions.createSetWorkingEnabledAction(true));
        boolean markerOnlyResult = dispatch(helper, markerOnlyHolder,
                LDLib2WorkingEnabledFancyConfiguratorActions.createSetWorkingEnabledAction(true));

        helper.assertTrue(!result, "non-fancy controllable holder was accepted");
        helper.assertTrue(!markerOnlyResult, "non-controllable Fancy action holder was accepted");
        helper.assertTrue(!holder.isWorkingEnabled() && holder.getSetWorkingEnabledCalls() == 0,
                "rejected holder action changed holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2WorkingEnabledFancyConfiguratorAction")
    public static void dispatcherRejectsInvalidWorkingEnabledPayload(GameTestHelper helper) {
        TestFancyControllableHolder holder = new TestFancyControllableHolder(false);

        boolean stringResult = dispatch(helper, holder, action(payload(new JsonPrimitive("true"))));
        boolean missingFieldResult = dispatch(helper, holder,
                action(payload(OTHER_FIELD, new JsonPrimitive(true))));
        boolean emptyPayloadResult = dispatch(helper, holder, action(DataComponentMap.EMPTY));

        helper.assertTrue(!stringResult, "string working-enabled payload was accepted");
        helper.assertTrue(!missingFieldResult, "payload without working-enabled field was accepted");
        helper.assertTrue(!emptyPayloadResult, "empty working-enabled payload was accepted");
        helper.assertTrue(!holder.isWorkingEnabled() && holder.getSetWorkingEnabledCalls() == 0,
                "invalid payload changed holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2WorkingEnabledFancyConfiguratorAction")
    public static void dispatcherRejectsSpectator(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        TestFancyControllableHolder holder = new TestFancyControllableHolder(false);
        player.setGameMode(GameType.SPECTATOR);
        boolean result;
        try {
            result = dispatch(player, holder,
                    LDLib2WorkingEnabledFancyConfiguratorActions.createSetWorkingEnabledAction(true));
        } finally {
            player.setGameMode(GameType.SURVIVAL);
        }

        helper.assertTrue(!result, "working-enabled action accepted a spectator");
        helper.assertTrue(!holder.isWorkingEnabled() && holder.getSetWorkingEnabledCalls() == 0,
                "spectator action changed holder state");
        helper.succeed();
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, SyncActionData action) {
        return dispatch(FakePlayerFactory.getMinecraft(helper.getLevel()), holder, action);
    }

    private static boolean dispatch(ServerPlayer player, Object holder, SyncActionData action) {
        LDLib2WorkingEnabledFancyConfiguratorActions.initialize();
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static SyncActionData action(DataComponentMap payload) {
        return new SyncActionData(SET_WORKING_ENABLED_ACTION, 1, payload);
    }

    private static DataComponentMap payload(JsonElement workingEnabled) {
        return payload(WORKING_ENABLED_FIELD, workingEnabled);
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static boolean requireWorkingEnabledPayload(SyncActionData action) {
        SyncFieldData fields = action.payload().get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Working-enabled action factory omitted field data.");
        }
        JsonElement element = fields.get(WORKING_ENABLED_FIELD);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
            throw new IllegalStateException("Working-enabled action factory omitted its boolean state.");
        }
        return primitive.getAsBoolean();
    }

    private static final class TestFancyControllableHolder extends TestControllableOnlyHolder
                                                           implements LDLib2FancyUIMachine {

        private TestFancyControllableHolder(boolean workingEnabled) {
            super(workingEnabled);
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
            return Component.literal("test fancy holder");
        }
    }

    private static class TestControllableOnlyHolder implements IControllable {

        private boolean workingEnabled;
        private int setWorkingEnabledCalls;

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
            setWorkingEnabledCalls++;
        }

        int getSetWorkingEnabledCalls() {
            return setWorkingEnabledCalls;
        }
    }

    private static final class TestFancyActionControllableHolder extends TestControllableOnlyHolder
                                                                 implements LDLib2FancyActionMachine {

        private TestFancyActionControllableHolder(boolean workingEnabled) {
            super(workingEnabled);
        }
    }

    private static final class TestFancyActionOnlyHolder implements LDLib2FancyActionMachine {}
}

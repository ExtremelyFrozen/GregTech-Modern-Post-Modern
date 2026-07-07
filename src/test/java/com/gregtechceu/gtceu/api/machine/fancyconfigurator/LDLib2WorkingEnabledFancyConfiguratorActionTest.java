package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
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
public class LDLib2WorkingEnabledFancyConfiguratorActionTest {

    private static final ResourceLocation SET_WORKING_ENABLED_ACTION = GTCEu.id("set_working_enabled");
    private static final ResourceLocation WORKING_ENABLED_FIELD = SyncFieldData.key("workingEnabled");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("otherField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2WorkingEnabledFancyConfiguratorAction")
    public static void dispatcherExecutesWorkingEnabledActionForFancyControllableHolder(GameTestHelper helper) {
        TestFancyControllableHolder holder = new TestFancyControllableHolder(false);
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder, payload(new JsonPrimitive(true)));

        helper.assertTrue(result, "valid working-enabled action was rejected");
        helper.assertTrue(holder.isWorkingEnabled(), "valid working-enabled action did not update holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2WorkingEnabledFancyConfiguratorAction")
    public static void dispatcherRejectsHolderWithoutFancyMachineContract(GameTestHelper helper) {
        TestControllableOnlyHolder holder = new TestControllableOnlyHolder(false);
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder, payload(new JsonPrimitive(true)));

        helper.assertTrue(!result, "non-fancy controllable holder was accepted");
        helper.assertTrue(!holder.isWorkingEnabled(), "rejected holder action changed holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2WorkingEnabledFancyConfiguratorAction")
    public static void dispatcherRejectsInvalidWorkingEnabledPayload(GameTestHelper helper) {
        TestFancyControllableHolder holder = new TestFancyControllableHolder(false);
        triggerActionRegistration(holder);

        boolean stringResult = dispatch(helper, holder, payload(new JsonPrimitive("true")));
        boolean missingFieldResult = dispatch(helper, holder, payload(OTHER_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(!stringResult, "string working-enabled payload was accepted");
        helper.assertTrue(!missingFieldResult, "payload without working-enabled field was accepted");
        helper.assertTrue(!holder.isWorkingEnabled(), "invalid payload changed holder state");
        helper.succeed();
    }

    private static void triggerActionRegistration(IControllable holder) {
        new LDLib2WorkingEnabledFancyConfigurator(holder, new TestMachineUIHolder());
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, DataComponentMap payload) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SyncActionData action = new SyncActionData(SET_WORKING_ENABLED_ACTION, 1, payload);
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
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

    private static final class TestFancyControllableHolder implements IControllable, LDLib2FancyUIMachine {

        private boolean workingEnabled;

        private TestFancyControllableHolder(boolean workingEnabled) {
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

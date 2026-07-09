package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.BatchModeMachine;
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
public class LDLib2BatchModeFancyConfiguratorActionTest {

    private static final ResourceLocation SET_BATCH_ENABLED_ACTION = GTCEu.id("set_batch_enabled");
    private static final ResourceLocation BATCH_ENABLED_FIELD = SyncFieldData.key("batchEnabled");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("otherField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2BatchModeFancyConfiguratorAction")
    public static void dispatcherExecutesBatchEnabledActionForFancyBatchHolder(GameTestHelper helper) {
        TestFancyBatchModeHolder holder = new TestFancyBatchModeHolder(true, false);
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder, payload(new JsonPrimitive(true)));

        helper.assertTrue(result, "valid batch-enabled action was rejected");
        helper.assertTrue(holder.isBatchEnabled(), "valid batch-enabled action did not update holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2BatchModeFancyConfiguratorAction")
    public static void dispatcherExecutesBatchEnabledActionForFancyActionMarkerHolder(GameTestHelper helper) {
        TestFancyActionBatchModeHolder holder = new TestFancyActionBatchModeHolder(true, false);
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder, payload(new JsonPrimitive(true)));

        helper.assertTrue(result, "batch-enabled action rejected marker-only holder");
        helper.assertTrue(holder.isBatchEnabled(), "marker-only holder state was not updated");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2BatchModeFancyConfiguratorAction")
    public static void dispatcherRejectsBatchHolderWithoutFancyMachineContract(GameTestHelper helper) {
        TestBatchModeHolder holder = new TestBatchModeHolder(true, false);
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder, payload(new JsonPrimitive(true)));

        helper.assertTrue(!result, "non-fancy batch holder was accepted");
        helper.assertTrue(!holder.isBatchEnabled(), "rejected holder action changed holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2BatchModeFancyConfiguratorAction")
    public static void dispatcherRejectsUnsupportedFancyBatchHolder(GameTestHelper helper) {
        TestFancyBatchModeHolder holder = new TestFancyBatchModeHolder(false, false);
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder, payload(new JsonPrimitive(true)));

        helper.assertTrue(!result, "unsupported fancy batch holder was accepted");
        helper.assertTrue(!holder.isBatchEnabled(), "unsupported holder action changed holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2BatchModeFancyConfiguratorAction")
    public static void dispatcherRejectsInvalidBatchEnabledPayload(GameTestHelper helper) {
        TestFancyBatchModeHolder holder = new TestFancyBatchModeHolder(true, false);
        triggerActionRegistration(holder);

        boolean stringResult = dispatch(helper, holder, payload(new JsonPrimitive("true")));
        boolean missingFieldResult = dispatch(helper, holder, payload(OTHER_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(!stringResult, "string batch-enabled payload was accepted");
        helper.assertTrue(!missingFieldResult, "payload without batch-enabled field was accepted");
        helper.assertTrue(!holder.isBatchEnabled(), "invalid payload changed holder state");
        helper.succeed();
    }

    private static void triggerActionRegistration(BatchModeMachine holder) {
        LDLib2BatchModeFancyConfigurator.attachConfigurators(
                new LDLib2ConfiguratorPanelElement(new TestMachineUIHolder(), 0, 0), holder);
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, DataComponentMap payload) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SyncActionData action = new SyncActionData(SET_BATCH_ENABLED_ACTION, 1, payload);
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static DataComponentMap payload(JsonElement batchEnabled) {
        return payload(BATCH_ENABLED_FIELD, batchEnabled);
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static class TestBatchModeHolder implements BatchModeMachine {

        private final boolean supportsBatchMode;
        private boolean batchEnabled;

        private TestBatchModeHolder(boolean supportsBatchMode, boolean batchEnabled) {
            this.supportsBatchMode = supportsBatchMode;
            this.batchEnabled = batchEnabled;
        }

        @Override
        public boolean supportsBatchMode() {
            return supportsBatchMode;
        }

        @Override
        public boolean isBatchEnabled() {
            return batchEnabled;
        }

        @Override
        public void setBatchEnabled(boolean batchEnabled) {
            this.batchEnabled = batchEnabled;
        }
    }

    private static final class TestFancyBatchModeHolder extends TestBatchModeHolder implements LDLib2FancyUIMachine {

        private TestFancyBatchModeHolder(boolean supportsBatchMode, boolean batchEnabled) {
            super(supportsBatchMode, batchEnabled);
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
            return Component.literal("test fancy batch holder");
        }
    }

    private static final class TestFancyActionBatchModeHolder extends TestBatchModeHolder
                                                              implements LDLib2FancyActionMachine {

        private TestFancyActionBatchModeHolder(boolean supportsBatchMode, boolean batchEnabled) {
            super(supportsBatchMode, batchEnabled);
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

package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IVoidable;
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
public class LDLib2VoidingModeFancyConfiguratorActionTest {

    private static final ResourceLocation SET_VOIDING_MODE_ACTION = GTCEu.id("set_voiding_mode");
    private static final ResourceLocation VOIDING_MODE_FIELD = SyncFieldData.key("voidingMode");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("otherField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2VoidingModeFancyConfiguratorAction")
    public static void dispatcherExecutesVoidingModeActionForFancyVoidableHolder(GameTestHelper helper) {
        TestFancyVoidableHolder holder = new TestFancyVoidableHolder(IVoidable.VoidingMode.VOID_NONE);
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder,
                payload(new JsonPrimitive(IVoidable.VoidingMode.VOID_ITEMS.ordinal())));

        helper.assertTrue(result, "valid voiding-mode action was rejected");
        helper.assertTrue(holder.getVoidingMode() == IVoidable.VoidingMode.VOID_ITEMS,
                "valid voiding-mode action did not update holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2VoidingModeFancyConfiguratorAction")
    public static void dispatcherExecutesVoidingModeActionForFancyActionMarkerHolder(GameTestHelper helper) {
        TestFancyActionVoidableHolder holder = new TestFancyActionVoidableHolder(IVoidable.VoidingMode.VOID_NONE);
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder,
                payload(new JsonPrimitive(IVoidable.VoidingMode.VOID_ITEMS.ordinal())));

        helper.assertTrue(result, "voiding-mode action rejected marker-only holder");
        helper.assertTrue(holder.getVoidingMode() == IVoidable.VoidingMode.VOID_ITEMS,
                "marker-only holder state was not updated");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2VoidingModeFancyConfiguratorAction")
    public static void dispatcherRejectsVoidableHolderWithoutFancyMachineContract(GameTestHelper helper) {
        TestVoidableOnlyHolder holder = new TestVoidableOnlyHolder(IVoidable.VoidingMode.VOID_NONE);
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder,
                payload(new JsonPrimitive(IVoidable.VoidingMode.VOID_ITEMS.ordinal())));

        helper.assertTrue(!result, "non-fancy voidable holder was accepted");
        helper.assertTrue(holder.getVoidingMode() == IVoidable.VoidingMode.VOID_NONE,
                "rejected holder action changed holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2VoidingModeFancyConfiguratorAction")
    public static void dispatcherRejectsInvalidVoidingModePayload(GameTestHelper helper) {
        TestFancyVoidableHolder holder = new TestFancyVoidableHolder(IVoidable.VoidingMode.VOID_NONE);
        triggerActionRegistration(holder);

        boolean stringResult = dispatch(helper, holder, payload(new JsonPrimitive("VOID_ITEMS")));
        boolean outOfRangeResult = dispatch(helper, holder,
                payload(new JsonPrimitive(IVoidable.VoidingMode.VALUES.length)));
        boolean missingFieldResult = dispatch(helper, holder, payload(OTHER_FIELD,
                new JsonPrimitive(IVoidable.VoidingMode.VOID_ITEMS.ordinal())));

        helper.assertTrue(!stringResult, "string voiding-mode payload was accepted");
        helper.assertTrue(!outOfRangeResult, "out-of-range voiding-mode payload was accepted");
        helper.assertTrue(!missingFieldResult, "payload without voiding-mode field was accepted");
        helper.assertTrue(holder.getVoidingMode() == IVoidable.VoidingMode.VOID_NONE,
                "invalid payload changed holder state");
        helper.succeed();
    }

    private static void triggerActionRegistration(IVoidable holder) {
        LDLib2VoidingModeFancyConfigurator.attachConfigurators(
                new LDLib2ConfiguratorPanelElement(new TestMachineUIHolder(), 0, 0), holder);
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, DataComponentMap payload) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SyncActionData action = new SyncActionData(SET_VOIDING_MODE_ACTION, 1, payload);
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static DataComponentMap payload(JsonElement voidingMode) {
        return payload(VOIDING_MODE_FIELD, voidingMode);
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static final class TestFancyVoidableHolder implements IVoidable, LDLib2FancyUIMachine {

        private IVoidable.VoidingMode voidingMode;

        private TestFancyVoidableHolder(IVoidable.VoidingMode voidingMode) {
            this.voidingMode = voidingMode;
        }

        @Override
        public IVoidable.VoidingMode getVoidingMode() {
            return voidingMode;
        }

        @Override
        public void setVoidingMode(IVoidable.VoidingMode mode) {
            voidingMode = mode;
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
            return Component.literal("test fancy voidable holder");
        }
    }

    private static class TestVoidableOnlyHolder implements IVoidable {

        private IVoidable.VoidingMode voidingMode;

        private TestVoidableOnlyHolder(IVoidable.VoidingMode voidingMode) {
            this.voidingMode = voidingMode;
        }

        @Override
        public IVoidable.VoidingMode getVoidingMode() {
            return voidingMode;
        }

        @Override
        public void setVoidingMode(IVoidable.VoidingMode mode) {
            voidingMode = mode;
        }
    }

    private static final class TestFancyActionVoidableHolder extends TestVoidableOnlyHolder
                                                             implements LDLib2FancyActionMachine {

        private TestFancyActionVoidableHolder(IVoidable.VoidingMode voidingMode) {
            super(voidingMode);
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

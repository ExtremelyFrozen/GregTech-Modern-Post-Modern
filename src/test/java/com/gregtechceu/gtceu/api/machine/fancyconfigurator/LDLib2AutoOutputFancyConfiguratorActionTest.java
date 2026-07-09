package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.AutoOutputMachine;
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
public class LDLib2AutoOutputFancyConfiguratorActionTest {

    private static final ResourceLocation SET_AUTO_OUTPUT_ITEMS_ACTION = GTCEu.id("set_auto_output_items");
    private static final ResourceLocation SET_AUTO_OUTPUT_FLUIDS_ACTION = GTCEu.id("set_auto_output_fluids");
    private static final ResourceLocation AUTO_OUTPUT_ITEMS_FIELD = SyncFieldData.key("autoOutputItems");
    private static final ResourceLocation AUTO_OUTPUT_FLUIDS_FIELD = SyncFieldData.key("autoOutputFluids");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("otherField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2AutoOutputFancyConfiguratorAction")
    public static void dispatcherExecutesItemAutoOutputActionForFancyAutoOutputHolder(GameTestHelper helper) {
        TestFancyAutoOutputHolder holder = new TestFancyAutoOutputHolder(true, true, false, false);
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder, SET_AUTO_OUTPUT_ITEMS_ACTION,
                payload(AUTO_OUTPUT_ITEMS_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(result, "valid item auto-output action was rejected");
        helper.assertTrue(holder.isAutoOutputItems(), "valid item auto-output action did not update item state");
        helper.assertTrue(!holder.isAutoOutputFluids(), "item auto-output action changed fluid state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2AutoOutputFancyConfiguratorAction")
    public static void dispatcherExecutesAutoOutputActionsForFancyActionMarkerHolder(GameTestHelper helper) {
        TestFancyActionAutoOutputHolder holder = new TestFancyActionAutoOutputHolder(true, true, false, false);
        triggerActionRegistration(holder);

        boolean itemResult = dispatch(helper, holder, SET_AUTO_OUTPUT_ITEMS_ACTION,
                payload(AUTO_OUTPUT_ITEMS_FIELD, new JsonPrimitive(true)));
        boolean fluidResult = dispatch(helper, holder, SET_AUTO_OUTPUT_FLUIDS_ACTION,
                payload(AUTO_OUTPUT_FLUIDS_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(itemResult, "item auto-output action rejected marker-only holder");
        helper.assertTrue(fluidResult, "fluid auto-output action rejected marker-only holder");
        helper.assertTrue(holder.isAutoOutputItems(), "marker-only holder item state was not updated");
        helper.assertTrue(holder.isAutoOutputFluids(), "marker-only holder fluid state was not updated");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2AutoOutputFancyConfiguratorAction")
    public static void dispatcherExecutesFluidAutoOutputActionForFancyAutoOutputHolder(GameTestHelper helper) {
        TestFancyAutoOutputHolder holder = new TestFancyAutoOutputHolder(true, true, false, false);
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder, SET_AUTO_OUTPUT_FLUIDS_ACTION,
                payload(AUTO_OUTPUT_FLUIDS_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(result, "valid fluid auto-output action was rejected");
        helper.assertTrue(holder.isAutoOutputFluids(), "valid fluid auto-output action did not update fluid state");
        helper.assertTrue(!holder.isAutoOutputItems(), "fluid auto-output action changed item state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2AutoOutputFancyConfiguratorAction")
    public static void dispatcherRejectsAutoOutputHolderWithoutFancyMachineContract(GameTestHelper helper) {
        TestAutoOutputHolder holder = new TestAutoOutputHolder(true, true, false, false);
        triggerActionRegistration(holder);

        boolean itemResult = dispatch(helper, holder, SET_AUTO_OUTPUT_ITEMS_ACTION,
                payload(AUTO_OUTPUT_ITEMS_FIELD, new JsonPrimitive(true)));
        boolean fluidResult = dispatch(helper, holder, SET_AUTO_OUTPUT_FLUIDS_ACTION,
                payload(AUTO_OUTPUT_FLUIDS_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(!itemResult, "non-fancy item auto-output holder was accepted");
        helper.assertTrue(!fluidResult, "non-fancy fluid auto-output holder was accepted");
        helper.assertTrue(!holder.isAutoOutputItems(), "rejected holder action changed item state");
        helper.assertTrue(!holder.isAutoOutputFluids(), "rejected holder action changed fluid state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2AutoOutputFancyConfiguratorAction")
    public static void dispatcherRejectsUnsupportedFancyAutoOutputHolder(GameTestHelper helper) {
        TestFancyAutoOutputHolder holder = new TestFancyAutoOutputHolder(false, false, false, false);
        triggerActionRegistration(holder);

        boolean itemResult = dispatch(helper, holder, SET_AUTO_OUTPUT_ITEMS_ACTION,
                payload(AUTO_OUTPUT_ITEMS_FIELD, new JsonPrimitive(true)));
        boolean fluidResult = dispatch(helper, holder, SET_AUTO_OUTPUT_FLUIDS_ACTION,
                payload(AUTO_OUTPUT_FLUIDS_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(!itemResult, "unsupported item auto-output holder was accepted");
        helper.assertTrue(!fluidResult, "unsupported fluid auto-output holder was accepted");
        helper.assertTrue(!holder.isAutoOutputItems(), "unsupported holder action changed item state");
        helper.assertTrue(!holder.isAutoOutputFluids(), "unsupported holder action changed fluid state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2AutoOutputFancyConfiguratorAction")
    public static void dispatcherRejectsInvalidAutoOutputPayload(GameTestHelper helper) {
        TestFancyAutoOutputHolder holder = new TestFancyAutoOutputHolder(true, true, false, false);
        triggerActionRegistration(holder);

        boolean itemStringResult = dispatch(helper, holder, SET_AUTO_OUTPUT_ITEMS_ACTION,
                payload(AUTO_OUTPUT_ITEMS_FIELD, new JsonPrimitive("true")));
        boolean itemMissingResult = dispatch(helper, holder, SET_AUTO_OUTPUT_ITEMS_ACTION,
                payload(OTHER_FIELD, new JsonPrimitive(true)));
        boolean fluidStringResult = dispatch(helper, holder, SET_AUTO_OUTPUT_FLUIDS_ACTION,
                payload(AUTO_OUTPUT_FLUIDS_FIELD, new JsonPrimitive("true")));
        boolean fluidMissingResult = dispatch(helper, holder, SET_AUTO_OUTPUT_FLUIDS_ACTION,
                payload(OTHER_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(!itemStringResult, "string item auto-output payload was accepted");
        helper.assertTrue(!itemMissingResult, "payload without item auto-output field was accepted");
        helper.assertTrue(!fluidStringResult, "string fluid auto-output payload was accepted");
        helper.assertTrue(!fluidMissingResult, "payload without fluid auto-output field was accepted");
        helper.assertTrue(!holder.isAutoOutputItems(), "invalid payload changed item state");
        helper.assertTrue(!holder.isAutoOutputFluids(), "invalid payload changed fluid state");
        helper.succeed();
    }

    private static void triggerActionRegistration(AutoOutputMachine holder) {
        LDLib2AutoOutputFancyConfigurator.attachConfigurators(
                new LDLib2ConfiguratorPanelElement(new TestMachineUIHolder(), 0, 0), holder);
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

    private static class TestAutoOutputHolder implements AutoOutputMachine {

        private final boolean supportsItems;
        private final boolean supportsFluids;
        private boolean autoOutputItems;
        private boolean autoOutputFluids;

        private TestAutoOutputHolder(boolean supportsItems, boolean supportsFluids, boolean autoOutputItems,
                                     boolean autoOutputFluids) {
            this.supportsItems = supportsItems;
            this.supportsFluids = supportsFluids;
            this.autoOutputItems = autoOutputItems;
            this.autoOutputFluids = autoOutputFluids;
        }

        @Override
        public boolean supportsAutoOutputItems() {
            return supportsItems;
        }

        @Override
        public boolean supportsAutoOutputFluids() {
            return supportsFluids;
        }

        @Override
        public boolean isAutoOutputItems() {
            return autoOutputItems;
        }

        @Override
        public boolean isAutoOutputFluids() {
            return autoOutputFluids;
        }

        @Override
        public void setAllowAutoOutputItems(boolean allow) {
            autoOutputItems = allow;
        }

        @Override
        public void setAllowAutoOutputFluids(boolean allow) {
            autoOutputFluids = allow;
        }
    }

    private static final class TestFancyAutoOutputHolder extends TestAutoOutputHolder implements LDLib2FancyUIMachine {

        private TestFancyAutoOutputHolder(boolean supportsItems, boolean supportsFluids, boolean autoOutputItems,
                                          boolean autoOutputFluids) {
            super(supportsItems, supportsFluids, autoOutputItems, autoOutputFluids);
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
            return Component.literal("test fancy auto-output holder");
        }
    }

    private static final class TestFancyActionAutoOutputHolder extends TestAutoOutputHolder
                                                               implements LDLib2FancyActionMachine {

        private TestFancyActionAutoOutputHolder(boolean supportsItems, boolean supportsFluids, boolean autoOutputItems,
                                                boolean autoOutputFluids) {
            super(supportsItems, supportsFluids, autoOutputItems, autoOutputFluids);
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

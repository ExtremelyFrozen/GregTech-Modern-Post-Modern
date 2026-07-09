package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyUIMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;

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
public class LDLib2CircuitFancyConfiguratorActionTest {

    private static final ResourceLocation SET_MACHINE_CIRCUIT_CONFIGURATION_ACTION = GTCEu.id(
            "set_machine_circuit_configuration");
    private static final ResourceLocation CIRCUIT_CONFIGURATION_FIELD = SyncFieldData.key("circuitConfig");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("otherField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2CircuitFancyConfiguratorAction")
    public static void dispatcherExecutesCircuitActionForFancyCircuitHolder(GameTestHelper helper) {
        TestFancyCircuitHolder holder = new TestFancyCircuitHolder();
        triggerActionRegistration(holder);

        helper.assertTrue(circuitConfiguration(holder) == 0, "test circuit holder did not start at configuration 0");
        boolean result = dispatch(helper, holder, payload(new JsonPrimitive(7)));

        helper.assertTrue(result, "valid circuit configuration action was rejected");
        helper.assertTrue(circuitConfiguration(holder) == 7,
                "valid circuit configuration action did not update holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2CircuitFancyConfiguratorAction")
    public static void dispatcherExecutesCircuitActionForFancyActionMarkerHolder(GameTestHelper helper) {
        TestFancyActionCircuitHolder holder = new TestFancyActionCircuitHolder();
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder, payload(new JsonPrimitive(7)));

        helper.assertTrue(result, "circuit action rejected marker-only holder");
        helper.assertTrue(circuitConfiguration(holder) == 7, "marker-only holder state was not updated");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2CircuitFancyConfiguratorAction")
    public static void dispatcherRejectsCircuitHolderWithoutFancyMachineContract(GameTestHelper helper) {
        TestCircuitHolder holder = new TestCircuitHolder();
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder, payload(new JsonPrimitive(7)));

        helper.assertTrue(!result, "non-fancy circuit holder was accepted");
        helper.assertTrue(circuitConfiguration(holder) == 0, "rejected holder action changed holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2CircuitFancyConfiguratorAction")
    public static void dispatcherRejectsInvalidCircuitPayload(GameTestHelper helper) {
        TestFancyCircuitHolder holder = new TestFancyCircuitHolder();
        triggerActionRegistration(holder);

        boolean stringResult = dispatch(helper, holder, payload(new JsonPrimitive("7")));
        helper.assertTrue(circuitConfiguration(holder) == 0, "string payload changed holder state");
        boolean outOfRangeResult = dispatch(helper, holder,
                payload(new JsonPrimitive(IntCircuitBehaviour.CIRCUIT_MAX + 1)));
        helper.assertTrue(circuitConfiguration(holder) == 0, "out-of-range payload changed holder state");
        boolean negativeResult = dispatch(helper, holder, payload(new JsonPrimitive(-2)));
        helper.assertTrue(circuitConfiguration(holder) == 0, "negative payload changed holder state");
        boolean missingFieldResult = dispatch(helper, holder, payload(OTHER_FIELD, new JsonPrimitive(7)));
        helper.assertTrue(circuitConfiguration(holder) == 0, "missing-field payload changed holder state");

        helper.assertTrue(!stringResult, "string circuit configuration payload was accepted");
        helper.assertTrue(!outOfRangeResult, "out-of-range circuit configuration payload was accepted");
        helper.assertTrue(!negativeResult, "negative circuit configuration payload was accepted");
        helper.assertTrue(!missingFieldResult, "payload without circuit configuration field was accepted");
        helper.assertTrue(circuitConfiguration(holder) == 0, "invalid payload changed holder state");
        helper.succeed();
    }

    private static void triggerActionRegistration(IHasCircuitSlot holder) {
        new LDLib2CircuitFancyConfigurator(holder, new TestMachineUIHolder());
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, DataComponentMap payload) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SyncActionData action = new SyncActionData(SET_MACHINE_CIRCUIT_CONFIGURATION_ACTION, 1, payload);
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static DataComponentMap payload(JsonElement circuitConfiguration) {
        return payload(CIRCUIT_CONFIGURATION_FIELD, circuitConfiguration);
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static int circuitConfiguration(IHasCircuitSlot holder) {
        return IntCircuitBehaviour.getCircuitConfiguration(holder.getCircuitInventory().getStackInSlot(0));
    }

    private static class TestCircuitHolder implements IHasCircuitSlot {

        private final NotifiableItemStackHandler inventory = new NotifiableItemStackHandler(1, IO.IN, IO.NONE);

        private TestCircuitHolder() {
            inventory.storage.setStackInSlot(0, IntCircuitBehaviour.stack(0));
        }

        @Override
        public NotifiableItemStackHandler getCircuitInventory() {
            return inventory;
        }
    }

    private static final class TestFancyCircuitHolder extends TestCircuitHolder implements LDLib2FancyUIMachine {

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
            return Component.literal("test fancy circuit holder");
        }
    }

    private static final class TestFancyActionCircuitHolder extends TestCircuitHolder
                                                            implements LDLib2FancyActionMachine {}

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

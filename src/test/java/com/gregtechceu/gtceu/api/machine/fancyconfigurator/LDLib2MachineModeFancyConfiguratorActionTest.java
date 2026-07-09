package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyUIMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.handler.RecipeHandlerList;
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
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LDLib2MachineModeFancyConfiguratorActionTest {

    private static final ResourceLocation SET_MACHINE_MODE_ACTION = GTCEu.id("set_machine_mode");
    private static final ResourceLocation ACTIVE_RECIPE_TYPE_FIELD = SyncFieldData.key("activeRecipeType");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("otherField");
    private static final GTRecipeType[] RECIPE_TYPES = {
            new GTRecipeType(GTCEu.id("test_machine_mode_a"), "test"),
            new GTRecipeType(GTCEu.id("test_machine_mode_b"), "test")
    };

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2MachineModeFancyConfiguratorAction")
    public static void dispatcherExecutesMachineModeActionForFancyRecipeLogicHolder(GameTestHelper helper) {
        TestFancyRecipeLogicHolder holder = new TestFancyRecipeLogicHolder(0);
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder, payload(new JsonPrimitive(1)));

        helper.assertTrue(result, "valid machine-mode action was rejected");
        helper.assertTrue(holder.getActiveRecipeType() == 1, "valid machine-mode action did not update holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2MachineModeFancyConfiguratorAction")
    public static void dispatcherExecutesMachineModeActionForFancyActionMarkerHolder(GameTestHelper helper) {
        TestFancyActionRecipeLogicHolder holder = new TestFancyActionRecipeLogicHolder(0);
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder, payload(new JsonPrimitive(1)));

        helper.assertTrue(result, "machine-mode action rejected marker-only holder");
        helper.assertTrue(holder.getActiveRecipeType() == 1, "marker-only holder state was not updated");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2MachineModeFancyConfiguratorAction")
    public static void dispatcherRejectsRecipeLogicHolderWithoutFancyMachineContract(GameTestHelper helper) {
        TestRecipeLogicHolder holder = new TestRecipeLogicHolder(0);
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder, payload(new JsonPrimitive(1)));

        helper.assertTrue(!result, "non-fancy recipe logic holder was accepted");
        helper.assertTrue(holder.getActiveRecipeType() == 0, "rejected holder action changed holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2MachineModeFancyConfiguratorAction")
    public static void dispatcherRejectsInvalidMachineModePayload(GameTestHelper helper) {
        TestFancyRecipeLogicHolder holder = new TestFancyRecipeLogicHolder(0);
        triggerActionRegistration(holder);

        boolean stringResult = dispatch(helper, holder, payload(new JsonPrimitive("1")));
        boolean negativeResult = dispatch(helper, holder, payload(new JsonPrimitive(-1)));
        boolean outOfRangeResult = dispatch(helper, holder, payload(new JsonPrimitive(RECIPE_TYPES.length)));
        boolean missingFieldResult = dispatch(helper, holder, payload(OTHER_FIELD, new JsonPrimitive(1)));

        helper.assertTrue(!stringResult, "string machine-mode payload was accepted");
        helper.assertTrue(!negativeResult, "negative machine-mode payload was accepted");
        helper.assertTrue(!outOfRangeResult, "out-of-range machine-mode payload was accepted");
        helper.assertTrue(!missingFieldResult, "payload without machine-mode field was accepted");
        helper.assertTrue(holder.getActiveRecipeType() == 0, "invalid payload changed holder state");
        helper.succeed();
    }

    private static void triggerActionRegistration(IRecipeLogicMachine holder) {
        new LDLib2MachineModeFancyConfigurator(holder);
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, DataComponentMap payload) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SyncActionData action = new SyncActionData(SET_MACHINE_MODE_ACTION, 1, payload);
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static DataComponentMap payload(JsonElement activeRecipeType) {
        return payload(ACTIVE_RECIPE_TYPE_FIELD, activeRecipeType);
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static class TestRecipeLogicHolder implements IRecipeLogicMachine {

        private int activeRecipeType;

        private TestRecipeLogicHolder(int activeRecipeType) {
            this.activeRecipeType = activeRecipeType;
        }

        @Override
        public @NotNull GTRecipeType[] getRecipeTypes() {
            return RECIPE_TYPES;
        }

        @Override
        public @NotNull GTRecipeType getRecipeType() {
            return RECIPE_TYPES[activeRecipeType];
        }

        @Override
        public int getActiveRecipeType() {
            return activeRecipeType;
        }

        @Override
        public void setActiveRecipeType(int type) {
            activeRecipeType = type;
        }

        @Override
        public @NotNull RecipeLogic getRecipeLogic() {
            throw new UnsupportedOperationException("recipe logic should not be requested by machine-mode action test");
        }

        @Override
        public boolean keepSubscribing() {
            return true;
        }

        @Override
        public @NotNull Map<IO, List<RecipeHandlerList>> getCapabilitiesProxy() {
            return Map.of();
        }

        @Override
        public @NotNull Map<IO, Map<RecipeCapability<?>, List<IRecipeHandler<?>>>> getCapabilitiesFlat() {
            return Map.of();
        }
    }

    private static final class TestFancyRecipeLogicHolder extends TestRecipeLogicHolder
                                                          implements LDLib2FancyUIMachine {

        private TestFancyRecipeLogicHolder(int activeRecipeType) {
            super(activeRecipeType);
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
            return Component.literal("test fancy recipe logic holder");
        }
    }

    private static final class TestFancyActionRecipeLogicHolder extends TestRecipeLogicHolder
                                                                implements LDLib2FancyActionMachine {

        private TestFancyActionRecipeLogicHolder(int activeRecipeType) {
            super(activeRecipeType);
        }
    }
}

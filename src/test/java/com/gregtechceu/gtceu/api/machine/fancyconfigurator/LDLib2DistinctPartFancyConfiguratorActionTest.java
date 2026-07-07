package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.DistinctPart;
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
public class LDLib2DistinctPartFancyConfiguratorActionTest {

    private static final ResourceLocation SET_DISTINCT_PART_ACTION = GTCEu.id("set_distinct_part");
    private static final ResourceLocation DISTINCT_FIELD = SyncFieldData.key("isDistinct");
    private static final ResourceLocation OTHER_FIELD = SyncFieldData.key("otherField");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DistinctPartFancyConfiguratorAction")
    public static void dispatcherExecutesDistinctActionForFancyDistinctHolder(GameTestHelper helper) {
        TestFancyDistinctHolder holder = new TestFancyDistinctHolder(false);
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder, payload(new JsonPrimitive(true)));

        helper.assertTrue(result, "valid distinct action was rejected");
        helper.assertTrue(holder.isDistinct(), "valid distinct action did not update holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DistinctPartFancyConfiguratorAction")
    public static void dispatcherRejectsDistinctHolderWithoutFancyMachineContract(GameTestHelper helper) {
        TestDistinctOnlyHolder holder = new TestDistinctOnlyHolder(false);
        triggerActionRegistration(holder);

        boolean result = dispatch(helper, holder, payload(new JsonPrimitive(true)));

        helper.assertTrue(!result, "non-fancy distinct holder was accepted");
        helper.assertTrue(!holder.isDistinct(), "rejected holder action changed holder state");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2DistinctPartFancyConfiguratorAction")
    public static void dispatcherRejectsInvalidDistinctPayload(GameTestHelper helper) {
        TestFancyDistinctHolder holder = new TestFancyDistinctHolder(false);
        triggerActionRegistration(holder);

        boolean stringResult = dispatch(helper, holder, payload(new JsonPrimitive("true")));
        boolean missingFieldResult = dispatch(helper, holder, payload(OTHER_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(!stringResult, "string distinct payload was accepted");
        helper.assertTrue(!missingFieldResult, "payload without distinct field was accepted");
        helper.assertTrue(!holder.isDistinct(), "invalid payload changed holder state");
        helper.succeed();
    }

    private static void triggerActionRegistration(DistinctPart holder) {
        LDLib2DistinctPartFancyConfigurator.attachConfigurators(
                new LDLib2ConfiguratorPanelElement(new TestMachineUIHolder(), 0, 0), holder);
    }

    private static boolean dispatch(GameTestHelper helper, Object holder, DataComponentMap payload) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        SyncActionData action = new SyncActionData(SET_DISTINCT_PART_ACTION, 1, payload);
        SyncActionContext context = new SyncActionContext(player, holder, action, BlockPos.ZERO, null, null, null);
        return SyncActionDispatchers.server().dispatch(context);
    }

    private static DataComponentMap payload(JsonElement distinct) {
        return payload(DISTINCT_FIELD, distinct);
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
    }

    private static final class TestFancyDistinctHolder implements DistinctPart, LDLib2FancyUIMachine {

        private boolean distinct;

        private TestFancyDistinctHolder(boolean distinct) {
            this.distinct = distinct;
        }

        @Override
        public boolean isDistinct() {
            return distinct;
        }

        @Override
        public void setDistinct(boolean isDistinct) {
            distinct = isDistinct;
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
            return Component.literal("test fancy distinct holder");
        }
    }

    private static final class TestDistinctOnlyHolder implements DistinctPart {

        private boolean distinct;

        private TestDistinctOnlyHolder(boolean distinct) {
            this.distinct = distinct;
        }

        @Override
        public boolean isDistinct() {
            return distinct;
        }

        @Override
        public void setDistinct(boolean isDistinct) {
            distinct = isDistinct;
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

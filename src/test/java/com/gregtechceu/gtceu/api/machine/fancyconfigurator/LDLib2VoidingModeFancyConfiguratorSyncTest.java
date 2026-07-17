package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IVoidable;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import org.lwjgl.glfw.GLFW;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LDLib2VoidingModeFancyConfiguratorSyncTest {

    private static final String BATCH = "LDLib2VoidingModeFancyConfiguratorSync";
    private static final ResourceLocation VOIDING_MODE_FIELD = SyncFieldData.key("voidingMode");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void rootFieldAcceptsChangedAndNoOpCandidatesWithAcknowledgements(GameTestHelper helper) {
        TestVoidingMachine machine = createMachine(false);
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.getSyncDataHolder().serializeFullClientSyncData(registries);
        int baselineRefreshes = machine.recipeLogic.subscriptionRefreshes;

        ServerFieldUpdateResult changed = apply(machine, registries, payload(IVoidable.VoidingMode.VOID_ITEMS));

        helper.assertTrue(changed.getAccepted() && changed.getChanged(),
                "changed voiding-mode candidate was not accepted");
        helper.assertTrue(machine.getVoidingMode() == IVoidable.VoidingMode.VOID_ITEMS,
                "changed voiding-mode candidate did not update the field");
        helper.assertTrue(machine.recipeLogic.subscriptionRefreshes == baselineRefreshes + 1,
                "changed voiding-mode candidate did not refresh the recipe subscription exactly once");
        assertAcknowledgement(helper, machine, registries, IVoidable.VoidingMode.VOID_ITEMS,
                "changed voiding-mode acknowledgement");

        int changedRefreshes = machine.recipeLogic.subscriptionRefreshes;
        ServerFieldUpdateResult noOp = apply(machine, registries, payload(IVoidable.VoidingMode.VOID_ITEMS));

        helper.assertTrue(noOp.getAccepted() && !noOp.getChanged(),
                "identical voiding-mode candidate was not accepted as a no-op");
        helper.assertTrue(machine.recipeLogic.subscriptionRefreshes == changedRefreshes,
                "no-op voiding-mode candidate invoked the server listener");
        assertAcknowledgement(helper, machine, registries, IVoidable.VoidingMode.VOID_ITEMS,
                "no-op voiding-mode acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void malformedEnumCandidatesAreRejectedWithoutMutationOrListener(GameTestHelper helper) {
        TestVoidingMachine machine = createMachine(false);
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.getSyncDataHolder().serializeFullClientSyncData(registries);
        int baselineRefreshes = machine.recipeLogic.subscriptionRefreshes;

        assertRejected(helper, machine, registries, new JsonPrimitive("not-a-voiding-mode"),
                "unknown enum name");
        assertRejected(helper, machine, registries,
                new JsonPrimitive(IVoidable.VoidingMode.VOID_ITEMS.ordinal()), "numeric ordinal");
        assertRejected(helper, machine, registries, JsonNull.INSTANCE, "null candidate");

        helper.assertTrue(machine.getVoidingMode() == IVoidable.VoidingMode.VOID_NONE,
                "malformed voiding-mode candidates changed the field");
        helper.assertTrue(machine.recipeLogic.subscriptionRefreshes == baselineRefreshes,
                "malformed voiding-mode candidates invoked the server listener");
        assertAcknowledgement(helper, machine, registries, IVoidable.VoidingMode.VOID_NONE,
                "rejected voiding-mode acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void selectorClickUpdatesClientFieldFlushesOnceAndConsumesEvent(GameTestHelper helper) {
        TestVoidingMachine machine = createMachine(true);
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.getSyncDataHolder().applyClientNetworkUpdate(registries,
                machine.getSyncDataHolder().serializeFullClientSyncComponents(registries));
        LDLib2ConfiguratorPanelElement panel = new LDLib2ConfiguratorPanelElement(new TestMachineUIHolder(machine),
                0, 0);
        LDLib2VoidingModeFancyConfigurator.attachConfigurators(panel, machine);
        UIElement tab = panel.getChildren().getFirst();
        GTButtonElement button = (GTButtonElement) tab.getChildren().getFirst();
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);

        dispatchClick(button, event);

        helper.assertTrue(machine.getVoidingMode() == IVoidable.VoidingMode.VOID_ITEMS,
                "LDLib2 selector did not update the client voiding-mode field");
        helper.assertTrue(machine.syncRequests == 1,
                "LDLib2 selector did not flush machine field sync exactly once");
        helper.assertTrue(event.propagationStopped, "LDLib2 selector did not consume the click event");
        assertRequest(helper, machine.getSyncDataHolder().collectServerNetworkChanges(registries),
                IVoidable.VoidingMode.VOID_ITEMS, "LDLib2 selector request");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void selectorRejectsHolderForDifferentMachine(GameTestHelper helper) {
        TestVoidingMachine machine = createMachine(true);
        TestVoidingMachine otherMachine = createMachine(true);
        LDLib2ConfiguratorPanelElement panel = new LDLib2ConfiguratorPanelElement(
                new TestMachineUIHolder(otherMachine), 0, 0);
        LDLib2VoidingModeFancyConfigurator.attachConfigurators(panel, machine);
        UIElement tab = panel.getChildren().getFirst();
        GTButtonElement button = (GTButtonElement) tab.getChildren().getFirst();
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);

        dispatchClick(button, event);

        helper.assertTrue(machine.getVoidingMode() == IVoidable.VoidingMode.VOID_NONE,
                "voiding selector changed a controller that did not match its opened holder");
        helper.assertTrue(machine.syncRequests == 0,
                "voiding selector flushed a controller that did not match its opened holder");
        helper.assertFalse(event.propagationStopped,
                "voiding selector consumed a click for a mismatched opened holder");
        helper.succeed();
    }

    private static void dispatchClick(GTButtonElement button, UIEvent event) {
        event.target = button;
        event.button = GLFW.GLFW_MOUSE_BUTTON_LEFT;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
    }

    private static TestVoidingMachine createMachine(boolean clientSide) {
        var definition = GTMultiMachines.ELECTRIC_BLAST_FURNACE;
        TestRecipeLogic recipeLogic = new TestRecipeLogic();
        return new TestVoidingMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()), recipeLogic,
                clientSide);
    }

    private static ServerFieldUpdateResult apply(TestVoidingMachine machine, RegistryAccess registries,
                                                 DataComponentMap components) {
        return machine.getSyncDataHolder().tryApplyServerNetworkUpdate(registries, components);
    }

    private static void assertRejected(GameTestHelper helper, TestVoidingMachine machine, RegistryAccess registries,
                                       JsonElement candidate, String description) {
        ServerFieldUpdateResult result = apply(machine, registries, payload(candidate));
        helper.assertTrue(!result.getAccepted(), description + " was accepted");
    }

    private static void assertAcknowledgement(GameTestHelper helper, TestVoidingMachine machine,
                                              RegistryAccess registries, IVoidable.VoidingMode expected,
                                              String description) {
        SyncFieldData fields = machine.getSyncDataHolder().serializeToFieldData(registries, true, false);
        assertField(helper, fields, expected,
                description + " did not contain only the authoritative voiding-mode field");
    }

    private static void assertRequest(GameTestHelper helper, DataComponentMap components,
                                      IVoidable.VoidingMode expected, String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        helper.assertTrue(fields != null, description + " did not contain sync field data");
        assertField(helper, fields, expected,
                description + " did not contain only the expected voiding-mode field");
    }

    private static void assertField(GameTestHelper helper, SyncFieldData fields, IVoidable.VoidingMode expected,
                                    String message) {
        JsonElement value = fields.get(VOIDING_MODE_FIELD);
        helper.assertTrue(fields.fields().size() == 1 && value instanceof JsonPrimitive primitive &&
                primitive.isString() && primitive.getAsString().equals(expected.getSerializedName()), message);
    }

    private static DataComponentMap payload(IVoidable.VoidingMode mode) {
        return payload(new JsonPrimitive(mode.getSerializedName()));
    }

    private static DataComponentMap payload(JsonElement voidingMode) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(VOIDING_MODE_FIELD, voidingMode)
                        .build())
                .build();
    }

    private static final class TestRecipeLogic extends RecipeLogic {

        private int subscriptionRefreshes;

        @Override
        public void updateTickSubscription() {
            subscriptionRefreshes++;
        }
    }

    private static final class TestVoidingMachine extends WorkableElectricMultiblockMachine {

        private final TestRecipeLogic recipeLogic;
        private final boolean clientSide;
        private int syncRequests;

        private TestVoidingMachine(BlockEntityCreationInfo info, TestRecipeLogic recipeLogic, boolean clientSide) {
            super(info, recipeLogic);
            this.recipeLogic = recipeLogic;
            this.clientSide = clientSide;
        }

        @Override
        public boolean isRemote() {
            return clientSide;
        }

        @Override
        public void sendServerSyncChanges() {
            syncRequests++;
        }
    }

    private record TestMachineUIHolder(TestVoidingMachine machine) implements MachineUIHolder {

        @Override
        public BlockPos getPos() {
            return machine.getBlockPos();
        }

        @Override
        public ResourceLocation getMachineDefinitionId() {
            return machine.getDefinition().getId();
        }

        @Override
        public MetaMachine getMachine() {
            return machine;
        }
    }
}

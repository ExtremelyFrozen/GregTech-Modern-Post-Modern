package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.WorkableTieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LDLib2MachineModeFancyConfiguratorSyncTest {

    private static final String BATCH = "LDLib2MachineModeFancyConfiguratorSync";
    private static final ResourceLocation ACTIVE_RECIPE_TYPE_FIELD = SyncFieldData.key("activeRecipeType");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void tieredOwnerAcceptsChangedAndNoOpCandidatesWithAcknowledgements(GameTestHelper helper) {
        TestTieredMachine machine = createTieredMachine(false);

        assertChangedAndNoOp(helper, machine, machine, machine.testRecipeLogic, "tiered owner");
        assertTieredSetRecipeTypeRefreshesOnce(helper, machine);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void multiblockOwnerAcceptsChangedAndNoOpCandidatesWithAcknowledgements(GameTestHelper helper) {
        TestMultiblockMachine machine = createMultiblockMachine(false);

        assertChangedAndNoOp(helper, machine, machine, machine.testRecipeLogic, "multiblock owner");
        assertMultiblockSetRecipeTypeRefreshesOnce(helper, machine);
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void bothOwnersRejectMalformedAndOutOfRangeCandidates(GameTestHelper helper) {
        TestTieredMachine tiered = createTieredMachine(false);
        TestMultiblockMachine multiblock = createMultiblockMachine(false);

        assertInvalidCandidatesRejected(helper, tiered, tiered, tiered.testRecipeLogic, "tiered owner");
        assertInvalidCandidatesRejected(helper, multiblock, multiblock, multiblock.testRecipeLogic,
                "multiblock owner");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void selectorClickUpdatesMatchingClientMachineAndFlushesOnce(GameTestHelper helper) {
        TestTieredMachine machine = createTieredMachine(true);
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.getSyncDataHolder().applyClientNetworkUpdate(registries,
                machine.getSyncDataHolder().serializeFullClientSyncComponents(registries));
        LDLib2FancyMachineUIElement shell = createShell(helper, machine);
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);

        clickMode(machine, shell, 1, event);

        helper.assertTrue(machine.getActiveRecipeType() == 1,
                "machine-mode selector did not update the matching client field");
        helper.assertTrue(machine.syncRequests == 1,
                "machine-mode selector did not flush the matching client machine exactly once");
        helper.assertTrue(event.hasHandler, "machine-mode selector did not consume the accepted click");
        assertRequest(helper, machine.getSyncDataHolder().collectServerNetworkChanges(registries), 1,
                "machine-mode selector request");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void selectorIgnoresHolderThatNoLongerResolvesTargetMachine(GameTestHelper helper) {
        TestTieredMachine target = createTieredMachine(true);
        TestTieredMachine resolved = createTieredMachine(true);
        LDLib2FancyMachineUIElement shell = createShell(helper, resolved);
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);

        clickMode(target, shell, 1, event);

        helper.assertTrue(target.getActiveRecipeType() == 0 && resolved.getActiveRecipeType() == 0,
                "mismatched machine-mode holder changed machine state");
        helper.assertTrue(target.syncRequests == 0 && resolved.syncRequests == 0,
                "mismatched machine-mode holder flushed field sync");
        helper.assertTrue(!event.hasHandler, "mismatched machine-mode holder consumed the click");
        helper.succeed();
    }

    private static void assertChangedAndNoOp(GameTestHelper helper, MetaMachine syncOwner,
                                             IRecipeLogicMachine modeOwner, TestRecipeLogic recipeLogic,
                                             String description) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        helper.assertTrue(modeOwner.getRecipeTypes().length >= 2,
                description + " test definition does not expose two recipe types");
        syncOwner.getSyncDataHolder().serializeFullClientSyncData(registries);
        int baselineRefreshes = recipeLogic.subscriptionRefreshes;

        ServerFieldUpdateResult changed = apply(syncOwner, registries, new JsonPrimitive(1));

        helper.assertTrue(changed.getAccepted() && changed.getChanged(),
                description + " rejected a changed machine-mode candidate");
        helper.assertTrue(modeOwner.getActiveRecipeType() == 1,
                description + " did not commit the changed machine-mode candidate");
        helper.assertTrue(recipeLogic.subscriptionRefreshes == baselineRefreshes + 1,
                description + " did not refresh recipe subscription exactly once after a changed candidate");
        assertAcknowledgement(helper, syncOwner, registries, 1,
                description + " changed acknowledgement");

        int changedRefreshes = recipeLogic.subscriptionRefreshes;
        ServerFieldUpdateResult noOp = apply(syncOwner, registries, new JsonPrimitive(1));

        helper.assertTrue(noOp.getAccepted() && !noOp.getChanged(),
                description + " did not accept an identical machine-mode candidate as a no-op");
        helper.assertTrue(recipeLogic.subscriptionRefreshes == changedRefreshes,
                description + " invoked the server listener for a no-op candidate");
        assertAcknowledgement(helper, syncOwner, registries, 1,
                description + " no-op acknowledgement");
    }

    private static void assertInvalidCandidatesRejected(GameTestHelper helper, MetaMachine syncOwner,
                                                        IRecipeLogicMachine modeOwner, TestRecipeLogic recipeLogic,
                                                        String description) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        syncOwner.getSyncDataHolder().serializeFullClientSyncData(registries);
        int baselineRefreshes = recipeLogic.subscriptionRefreshes;

        assertRejected(helper, syncOwner, registries, new JsonPrimitive(-1), description + " negative index");
        assertRejected(helper, syncOwner, registries, new JsonPrimitive(modeOwner.getRecipeTypes().length),
                description + " out-of-range index");
        assertRejected(helper, syncOwner, registries, new JsonPrimitive(1.5D),
                description + " fractional index");
        assertRejected(helper, syncOwner, registries, new JsonPrimitive("1"),
                description + " string index");

        helper.assertTrue(modeOwner.getActiveRecipeType() == 0,
                description + " malformed machine-mode candidates changed the field");
        helper.assertTrue(recipeLogic.subscriptionRefreshes == baselineRefreshes,
                description + " malformed machine-mode candidates invoked the server listener");
        assertAcknowledgement(helper, syncOwner, registries, 0,
                description + " rejected acknowledgement");
    }

    private static void assertTieredSetRecipeTypeRefreshesOnce(GameTestHelper helper, TestTieredMachine machine) {
        int baselineRefreshes = machine.testRecipeLogic.subscriptionRefreshes;

        machine.setRecipeType(machine.getRecipeTypes()[0]);

        helper.assertTrue(machine.getActiveRecipeType() == 0,
                "tiered setRecipeType did not update the active recipe type");
        helper.assertTrue(machine.testRecipeLogic.subscriptionRefreshes == baselineRefreshes + 1,
                "tiered setRecipeType did not refresh recipe subscription exactly once");
    }

    private static void assertMultiblockSetRecipeTypeRefreshesOnce(GameTestHelper helper,
                                                                   TestMultiblockMachine machine) {
        int baselineRefreshes = machine.testRecipeLogic.subscriptionRefreshes;

        machine.setRecipeType(machine.getRecipeTypes()[0]);

        helper.assertTrue(machine.getActiveRecipeType() == 0,
                "multiblock setRecipeType did not update the active recipe type");
        helper.assertTrue(machine.testRecipeLogic.subscriptionRefreshes == baselineRefreshes + 1,
                "multiblock setRecipeType did not refresh recipe subscription exactly once");
    }

    private static ServerFieldUpdateResult apply(MetaMachine machine, RegistryAccess registries,
                                                 JsonElement candidate) {
        return machine.getSyncDataHolder().tryApplyServerNetworkUpdate(registries, payload(candidate));
    }

    private static void assertRejected(GameTestHelper helper, MetaMachine machine, RegistryAccess registries,
                                       JsonElement candidate, String description) {
        ServerFieldUpdateResult result = apply(machine, registries, candidate);
        helper.assertTrue(!result.getAccepted(), description + " was accepted");
    }

    private static void assertAcknowledgement(GameTestHelper helper, MetaMachine machine,
                                              RegistryAccess registries, int expected, String description) {
        SyncFieldData fields = machine.getSyncDataHolder().serializeToFieldData(registries, true, false);
        assertField(helper, fields, expected,
                description + " did not contain only the authoritative machine-mode field");
    }

    private static void assertRequest(GameTestHelper helper, DataComponentMap components, int expected,
                                      String description) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        helper.assertTrue(fields != null, description + " did not contain sync field data");
        assertField(helper, fields, expected,
                description + " did not contain only the expected machine-mode field");
    }

    private static void assertField(GameTestHelper helper, SyncFieldData fields, int expected, String message) {
        JsonElement value = fields.get(ACTIVE_RECIPE_TYPE_FIELD);
        helper.assertTrue(fields.fields().size() == 1 && value instanceof JsonPrimitive primitive &&
                primitive.isNumber() && primitive.getAsInt() == expected, message);
    }

    private static DataComponentMap payload(JsonElement activeRecipeType) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(ACTIVE_RECIPE_TYPE_FIELD, activeRecipeType)
                        .build())
                .build();
    }

    private static void clickMode(IRecipeLogicMachine machine, LDLib2FancyMachineUIElement shell, int mode,
                                  UIEvent event) {
        UIElement root = new LDLib2MachineModeFancyConfigurator(machine).createLDLib2MainPage(shell);
        GTButtonElement button = (GTButtonElement) root.getChildren().get(mode * 2);
        button.onClick(event);
    }

    private static LDLib2FancyMachineUIElement createShell(GameTestHelper helper, MetaMachine resolvedMachine) {
        var inventory = FakePlayerFactory.getMinecraft(helper.getLevel()).getInventory();
        return new LDLib2FancyMachineUIElement(new TestFancyPage(), inventory,
                new TestMachineUIHolder(resolvedMachine), 176, 166);
    }

    private static BlockEntityCreationInfo createInfo() {
        var definition = GTMultiMachines.MULTI_SMELTER;
        return new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState());
    }

    private static TestTieredMachine createTieredMachine(boolean clientSide) {
        TestRecipeLogic recipeLogic = new TestRecipeLogic();
        return new TestTieredMachine(createInfo(), recipeLogic, clientSide);
    }

    private static TestMultiblockMachine createMultiblockMachine(boolean clientSide) {
        TestRecipeLogic recipeLogic = new TestRecipeLogic();
        return new TestMultiblockMachine(createInfo(), recipeLogic, clientSide);
    }

    private static final class TestRecipeLogic extends RecipeLogic {

        private int subscriptionRefreshes;

        @Override
        public void updateTickSubscription() {
            subscriptionRefreshes++;
        }
    }

    private static final class TestTieredMachine extends WorkableTieredMachine {

        private final TestRecipeLogic testRecipeLogic;
        private final boolean clientSide;
        private int syncRequests;

        private TestTieredMachine(BlockEntityCreationInfo info, TestRecipeLogic recipeLogic, boolean clientSide) {
            super(info, GTValues.LV, recipeLogic, 1, 1, 1, 1, tier -> 1_000);
            this.testRecipeLogic = recipeLogic;
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

    private static final class TestMultiblockMachine extends WorkableElectricMultiblockMachine {

        private final TestRecipeLogic testRecipeLogic;
        private final boolean clientSide;

        private TestMultiblockMachine(BlockEntityCreationInfo info, TestRecipeLogic recipeLogic, boolean clientSide) {
            super(info, recipeLogic);
            this.testRecipeLogic = recipeLogic;
            this.clientSide = clientSide;
        }

        @Override
        public boolean isRemote() {
            return clientSide;
        }
    }

    private record TestMachineUIHolder(MetaMachine machine) implements MachineUIHolder {

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

    private static final class TestFancyPage implements LDLib2FancyUIProvider {

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
            return Component.literal("machine mode test");
        }

        @Override
        public boolean hasPlayerInventory() {
            return false;
        }
    }
}

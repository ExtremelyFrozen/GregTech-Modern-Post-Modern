package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import com.google.gson.JsonPrimitive;

import java.util.List;

import static com.gregtechceu.gtceu.api.GTValues.LV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class AutoOutputTraitSyncTest {

    private static final String BATCH = "AutoOutputTraitSync";
    private static final ResourceLocation AUTO_OUTPUT_ITEMS_FIELD = SyncFieldData.key("autoOutputItems");
    private static final ResourceLocation AUTO_OUTPUT_FLUIDS_FIELD = SyncFieldData.key("autoOutputFluids");
    private static final ResourceLocation ALLOW_ITEM_INPUT_FIELD = SyncFieldData.key("allowItemInputFromOutputSide");
    private static final ResourceLocation ALLOW_FLUID_INPUT_FIELD = SyncFieldData
            .key("allowFluidInputFromOutputSide");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void supportedFieldsNormalizeCommitNotifyAndAcknowledge(GameTestHelper helper) {
        TrackingAutoOutputTrait trait = new TrackingAutoOutputTrait(true, true);
        RegistryAccess registries = helper.getLevel().registryAccess();
        trait.getSyncDataHolder().serializeFullClientSyncComponents(registries);

        ServerFieldUpdateResult result = trait.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                payload(SyncFieldData.builder()
                        .put(AUTO_OUTPUT_ITEMS_FIELD, new JsonPrimitive(true))
                        .put(AUTO_OUTPUT_FLUIDS_FIELD, new JsonPrimitive(true))
                        .put(ALLOW_ITEM_INPUT_FIELD, new JsonPrimitive(true))
                        .put(ALLOW_FLUID_INPUT_FIELD, new JsonPrimitive(true))
                        .build()));

        helper.assertTrue(result.getAccepted() && result.getChanged(),
                "supported auto-output field batch was not committed");
        helper.assertTrue(trait.isAutoOutputItems() && trait.isAutoOutputFluids(),
                "supported auto-output field batch did not update both states");
        helper.assertTrue(trait.allowsItemInputFromOutputSide() && trait.allowsFluidInputFromOutputSide(),
                "supported output-side input batch did not update both states");
        helper.assertTrue(trait.itemSubscriptionUpdates == 1 && trait.fluidSubscriptionUpdates == 1,
                "server change listeners did not update both output subscriptions exactly once");

        SyncFieldData acknowledgement = trait.getSyncDataHolder().serializeToFieldData(registries, true, false);
        helper.assertTrue(acknowledgement.get(AUTO_OUTPUT_ITEMS_FIELD).getAsBoolean() &&
                acknowledgement.get(AUTO_OUTPUT_FLUIDS_FIELD).getAsBoolean() &&
                acknowledgement.get(ALLOW_ITEM_INPUT_FIELD).getAsBoolean() &&
                acknowledgement.get(ALLOW_FLUID_INPUT_FIELD).getAsBoolean(),
                "SyncBoth auto-output fields did not request authoritative acknowledgements");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void unsupportedAutoOutputCandidatesAreRejected(GameTestHelper helper) {
        TrackingAutoOutputTrait trait = new TrackingAutoOutputTrait(false, false);
        RegistryAccess registries = helper.getLevel().registryAccess();

        ServerFieldUpdateResult itemResult = trait.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                payload(AUTO_OUTPUT_ITEMS_FIELD, new JsonPrimitive(true)));
        ServerFieldUpdateResult fluidResult = trait.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                payload(AUTO_OUTPUT_FLUIDS_FIELD, new JsonPrimitive(true)));
        ServerFieldUpdateResult itemInputResult = trait.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                payload(ALLOW_ITEM_INPUT_FIELD, new JsonPrimitive(true)));
        ServerFieldUpdateResult fluidInputResult = trait.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                payload(ALLOW_FLUID_INPUT_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(!itemResult.getAccepted(), "unsupported item auto-output candidate was accepted");
        helper.assertTrue(!fluidResult.getAccepted(), "unsupported fluid auto-output candidate was accepted");
        helper.assertTrue(!itemInputResult.getAccepted(), "unsupported item input-policy candidate was accepted");
        helper.assertTrue(!fluidInputResult.getAccepted(), "unsupported fluid input-policy candidate was accepted");
        helper.assertTrue(!trait.isAutoOutputItems() && !trait.isAutoOutputFluids(),
                "unsupported candidate changed an auto-output field");
        helper.assertTrue(!trait.allowsItemInputFromOutputSide() && !trait.allowsFluidInputFromOutputSide(),
                "unsupported candidate changed an output-side input field");
        helper.assertTrue(trait.itemSubscriptionUpdates == 0 && trait.fluidSubscriptionUpdates == 0,
                "unsupported candidate invoked a subscription listener");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void outputSideInputNormalizersUseTheirMatchingCapability(GameTestHelper helper) {
        TrackingAutoOutputTrait itemOnly = new TrackingAutoOutputTrait(true, false);
        TrackingAutoOutputTrait fluidOnly = new TrackingAutoOutputTrait(false, true);
        RegistryAccess registries = helper.getLevel().registryAccess();

        ServerFieldUpdateResult itemAccepted = itemOnly.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                payload(ALLOW_ITEM_INPUT_FIELD, new JsonPrimitive(true)));
        ServerFieldUpdateResult itemRejected = itemOnly.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                payload(ALLOW_FLUID_INPUT_FIELD, new JsonPrimitive(true)));
        ServerFieldUpdateResult fluidRejected = fluidOnly.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                payload(ALLOW_ITEM_INPUT_FIELD, new JsonPrimitive(true)));
        ServerFieldUpdateResult fluidAccepted = fluidOnly.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                payload(ALLOW_FLUID_INPUT_FIELD, new JsonPrimitive(true)));

        helper.assertTrue(itemAccepted.getAccepted() && !itemRejected.getAccepted() &&
                !fluidRejected.getAccepted() && fluidAccepted.getAccepted(),
                "output-side input normalizers used the wrong capability");
        helper.assertTrue(itemOnly.allowsItemInputFromOutputSide() &&
                !itemOnly.allowsFluidInputFromOutputSide() &&
                !fluidOnly.allowsItemInputFromOutputSide() && fluidOnly.allowsFluidInputFromOutputSide(),
                "capability-specific input-policy updates changed the wrong field");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void invalidBatchIsRejectedWithoutPartialStateOrListeners(GameTestHelper helper) {
        TrackingAutoOutputTrait trait = new TrackingAutoOutputTrait(true, true);
        RegistryAccess registries = helper.getLevel().registryAccess();
        trait.getSyncDataHolder().serializeFullClientSyncComponents(registries);

        ServerFieldUpdateResult result = trait.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                payload(SyncFieldData.builder()
                        .put(AUTO_OUTPUT_ITEMS_FIELD, new JsonPrimitive(true))
                        .put(AUTO_OUTPUT_FLUIDS_FIELD, new JsonPrimitive("not a boolean"))
                        .put(ALLOW_ITEM_INPUT_FIELD, new JsonPrimitive(true))
                        .put(ALLOW_FLUID_INPUT_FIELD, new JsonPrimitive(true))
                        .build()));

        helper.assertTrue(!result.getAccepted(), "invalid auto-output field batch was accepted");
        helper.assertTrue(!trait.isAutoOutputItems() && !trait.isAutoOutputFluids(),
                "invalid field batch partially changed auto-output state");
        helper.assertTrue(!trait.allowsItemInputFromOutputSide() && !trait.allowsFluidInputFromOutputSide(),
                "invalid field batch partially changed output-side input state");
        helper.assertTrue(trait.itemSubscriptionUpdates == 0 && trait.fluidSubscriptionUpdates == 0,
                "invalid field batch invoked a server change listener");

        SyncFieldData acknowledgement = trait.getSyncDataHolder().serializeToFieldData(registries, true, false);
        helper.assertTrue(!acknowledgement.get(AUTO_OUTPUT_ITEMS_FIELD).getAsBoolean() &&
                !acknowledgement.get(AUTO_OUTPUT_FLUIDS_FIELD).getAsBoolean() &&
                !acknowledgement.get(ALLOW_ITEM_INPUT_FIELD).getAsBoolean() &&
                !acknowledgement.get(ALLOW_FLUID_INPUT_FIELD).getAsBoolean(),
                "rejected batch did not request canonical auto-output acknowledgements");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void ldlib2TogglesChangeFieldsAndFlushMachineSync(GameTestHelper helper) {
        TestClientMachine machine = createClientMachine();
        TrackingAutoOutputTrait trait = machine.attachTrait(new TrackingAutoOutputTrait(true, true));
        TestMachineUIHolder holder = new TestMachineUIHolder(machine);
        LDLib2ConfiguratorPanelElement panel = new LDLib2ConfiguratorPanelElement(holder, 0, 0);
        LDLib2FancyConfiguratorButton.Toggle itemToggle = LDLib2AutoOutputFancyConfigurator
                .createAutoOutputItemConfigurator(panel, trait);
        LDLib2FancyConfiguratorButton.Toggle fluidToggle = LDLib2AutoOutputFancyConfigurator
                .createAutoOutputFluidConfigurator(panel, trait);
        UIEvent itemEvent = UIEvent.create(UIEvents.MOUSE_DOWN);
        UIEvent fluidEvent = UIEvent.create(UIEvents.MOUSE_DOWN);

        itemToggle.onClick(itemEvent);
        fluidToggle.onClick(fluidEvent);

        helper.assertTrue(trait.isAutoOutputItems() && trait.isAutoOutputFluids(),
                "LDLib2 toggles did not change their client-visible fields");
        helper.assertTrue(machine.syncRequests == 2,
                "LDLib2 toggles did not flush machine field sync after each field change");
        helper.assertTrue(itemEvent.hasHandler && fluidEvent.hasHandler,
                "LDLib2 toggle events were not consumed after sending field sync");

        DataComponentMap candidates = trait.getSyncDataHolder()
                .collectServerNetworkChanges(helper.getLevel().registryAccess());
        SyncFieldData fields = candidates.get(GTDataComponents.SYNC_FIELD_DATA.get());
        helper.assertTrue(fields != null && fields.get(AUTO_OUTPUT_ITEMS_FIELD).getAsBoolean() &&
                fields.get(AUTO_OUTPUT_FLUIDS_FIELD).getAsBoolean(),
                "LDLib2 toggle changes were not available to the trait C2S field collector");
        helper.succeed();
    }

    private static TestClientMachine createClientMachine() {
        var definition = GTMachines.BUFFER[LV];
        return new TestClientMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()));
    }

    private static DataComponentMap payload(ResourceLocation field, JsonPrimitive value) {
        return payload(SyncFieldData.builder().put(field, value).build());
    }

    private static DataComponentMap payload(SyncFieldData fields) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
                .build();
    }

    private static final class TrackingAutoOutputTrait extends AutoOutputTrait {

        private int itemSubscriptionUpdates;
        private int fluidSubscriptionUpdates;

        private TrackingAutoOutputTrait(boolean supportsItems, boolean supportsFluids) {
            super(supportsItems ? List.<IItemHandler>of(new ItemStackHandler(1)) : List.of(),
                    supportsFluids ? List.<IFluidHandler>of(new FluidTank(1_000)) : List.of(), false);
        }

        @Override
        protected void updateItemOutputSubscription() {
            itemSubscriptionUpdates++;
        }

        @Override
        protected void updateFluidOutputSubscription() {
            fluidSubscriptionUpdates++;
        }
    }

    private static final class TestClientMachine extends MetaMachine {

        private int syncRequests;

        private TestClientMachine(BlockEntityCreationInfo info) {
            super(info);
        }

        @Override
        public boolean isRemote() {
            return true;
        }

        @Override
        public void sendServerSyncChanges() {
            syncRequests++;
        }
    }

    private record TestMachineUIHolder(TestClientMachine machine) implements MachineUIHolder {

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

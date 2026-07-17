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
import net.minecraft.core.Direction;
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

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

import static com.gregtechceu.gtceu.api.GTValues.LV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class AutoOutputTraitSyncTest {

    private static final String BATCH = "AutoOutputTraitSync";
    private static final BlockPos MACHINE_POS = new BlockPos(1, 1, 1);
    private static final ResourceLocation AUTO_OUTPUT_ITEMS_FIELD = SyncFieldData.key("autoOutputItems");
    private static final ResourceLocation AUTO_OUTPUT_FLUIDS_FIELD = SyncFieldData.key("autoOutputFluids");
    private static final ResourceLocation ITEM_OUTPUT_DIRECTION_FIELD = SyncFieldData.key("itemOutputDirection");
    private static final ResourceLocation FLUID_OUTPUT_DIRECTION_FIELD = SyncFieldData.key("fluidOutputDirection");
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
    public static void directionFieldsUseEnumStringsForFullAndDeltaSync(GameTestHelper helper) {
        TestClientMachine machine = createClientMachine(helper);
        machine.setFrontFacing(Direction.NORTH);
        TrackingAutoOutputTrait trait = machine.attachTrait(new TrackingAutoOutputTrait(true, true));
        RegistryAccess registries = helper.getLevel().registryAccess();
        trait.setItemOutputDirection(Direction.SOUTH);
        trait.setFluidOutputDirection(Direction.DOWN);

        SyncFieldData full = trait.getSyncDataHolder().serializeFullClientSyncData(registries);

        assertDirectionField(helper, full, ITEM_OUTPUT_DIRECTION_FIELD, Direction.SOUTH,
                "full sync did not encode the item direction as its enum string");
        assertDirectionField(helper, full, FLUID_OUTPUT_DIRECTION_FIELD, Direction.DOWN,
                "full sync did not encode the fluid direction as its enum string");

        trait.getSyncDataHolder().collectServerNetworkChanges(registries);
        trait.setItemOutputDirection(Direction.EAST);
        SyncFieldData delta = requireFields(trait.getSyncDataHolder().collectServerNetworkChanges(registries));

        helper.assertTrue(delta.fields().size() == 1,
                "item direction delta included an unchanged auto-output field");
        assertDirectionField(helper, delta, ITEM_OUTPUT_DIRECTION_FIELD, Direction.EAST,
                "client delta did not encode the changed item direction as its enum string");
        helper.assertTrue(trait.getSyncDataHolder().collectServerNetworkChanges(registries).isEmpty(),
                "unchanged direction produced a second client delta");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void directionBatchCommitsNotifiesAndAcceptsNoOp(GameTestHelper helper) {
        TestClientMachine machine = createClientMachine(helper);
        machine.setFrontFacing(Direction.NORTH);
        TrackingAutoOutputTrait trait = machine.attachTrait(new TrackingAutoOutputTrait(true, true));
        RegistryAccess registries = helper.getLevel().registryAccess();
        trait.setItemOutputDirection(Direction.SOUTH);
        trait.setFluidOutputDirection(Direction.DOWN);
        trait.setAllowAutoOutputItems(true);
        trait.setAllowAutoOutputFluids(true);
        trait.resetSubscriptionUpdates();
        trait.getSyncDataHolder().serializeFullClientSyncComponents(registries);

        ServerFieldUpdateResult changed = trait.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                payload(SyncFieldData.builder()
                        .put(AUTO_OUTPUT_ITEMS_FIELD, new JsonPrimitive(false))
                        .put(ITEM_OUTPUT_DIRECTION_FIELD, new JsonPrimitive(Direction.EAST.getSerializedName()))
                        .put(AUTO_OUTPUT_FLUIDS_FIELD, new JsonPrimitive(false))
                        .put(FLUID_OUTPUT_DIRECTION_FIELD, new JsonPrimitive(Direction.UP.getSerializedName()))
                        .build()));

        helper.assertTrue(changed.getAccepted() && changed.getChanged(),
                "valid direction batch was not committed");
        helper.assertTrue(trait.getItemOutputDirection() == Direction.EAST &&
                trait.getFluidOutputDirection() == Direction.UP && !trait.isAutoOutputItems() &&
                !trait.isAutoOutputFluids(),
                "valid batch did not update both direction and auto-output fields");
        helper.assertTrue(trait.itemSubscriptionUpdates == 2 && trait.fluidSubscriptionUpdates == 2,
                "combined direction and auto-output changes did not preserve both setter side effects");
        SyncFieldData changedAck = trait.getSyncDataHolder().serializeToFieldData(registries, true, false);
        assertDirectionField(helper, changedAck, ITEM_OUTPUT_DIRECTION_FIELD, Direction.EAST,
                "changed item direction was not acknowledged");
        assertDirectionField(helper, changedAck, FLUID_OUTPUT_DIRECTION_FIELD, Direction.UP,
                "changed fluid direction was not acknowledged");

        trait.setItemOutputDirectionValidator(direction -> false);
        trait.setFluidOutputDirectionValidator(direction -> false);
        ServerFieldUpdateResult noOp = trait.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                payload(SyncFieldData.builder()
                        .put(AUTO_OUTPUT_ITEMS_FIELD, new JsonPrimitive(false))
                        .put(ITEM_OUTPUT_DIRECTION_FIELD, new JsonPrimitive(Direction.EAST.getSerializedName()))
                        .put(AUTO_OUTPUT_FLUIDS_FIELD, new JsonPrimitive(false))
                        .put(FLUID_OUTPUT_DIRECTION_FIELD, new JsonPrimitive(Direction.UP.getSerializedName()))
                        .build()));

        helper.assertTrue(noOp.getAccepted() && !noOp.getChanged(),
                "current directions were not accepted as no-ops after validators changed");
        helper.assertTrue(trait.itemSubscriptionUpdates == 2 && trait.fluidSubscriptionUpdates == 2,
                "no-op direction batch invoked a server change listener");
        SyncFieldData noOpAck = trait.getSyncDataHolder().serializeToFieldData(registries, true, false);
        assertDirectionField(helper, noOpAck, ITEM_OUTPUT_DIRECTION_FIELD, Direction.EAST,
                "no-op item direction was not acknowledged");
        assertDirectionField(helper, noOpAck, FLUID_OUTPUT_DIRECTION_FIELD, Direction.UP,
                "no-op fluid direction was not acknowledged");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void invalidDirectionRejectsWholeAutoOutputBatch(GameTestHelper helper) {
        TestClientMachine machine = createClientMachine(helper);
        machine.setFrontFacing(Direction.NORTH);
        TrackingAutoOutputTrait trait = machine.attachTrait(new TrackingAutoOutputTrait(true, true));
        RegistryAccess registries = helper.getLevel().registryAccess();
        trait.setItemOutputDirection(Direction.SOUTH);
        trait.setFluidOutputDirection(Direction.DOWN);
        trait.setAllowAutoOutputItems(true);
        trait.setAllowAutoOutputFluids(true);
        trait.resetSubscriptionUpdates();
        trait.getSyncDataHolder().serializeFullClientSyncComponents(registries);

        ServerFieldUpdateResult frontFace = trait.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                payload(SyncFieldData.builder()
                        .put(AUTO_OUTPUT_ITEMS_FIELD, new JsonPrimitive(false))
                        .put(ITEM_OUTPUT_DIRECTION_FIELD, new JsonPrimitive(Direction.NORTH.getSerializedName()))
                        .put(AUTO_OUTPUT_FLUIDS_FIELD, new JsonPrimitive(false))
                        .put(FLUID_OUTPUT_DIRECTION_FIELD, new JsonPrimitive(Direction.NORTH.getSerializedName()))
                        .build()));

        helper.assertTrue(!frontFace.getAccepted(), "machine front face was accepted as an output direction");
        assertInitialOutputState(helper, trait,
                "front-face rejection partially committed the auto-output batch");
        helper.assertTrue(trait.itemSubscriptionUpdates == 0 && trait.fluidSubscriptionUpdates == 0,
                "front-face rejection invoked a server change listener");
        assertInitialOutputAcknowledgement(helper,
                trait.getSyncDataHolder().serializeToFieldData(registries, true, false));

        trait.setItemOutputDirectionValidator(direction -> direction != Direction.WEST);
        trait.setFluidOutputDirectionValidator(direction -> direction != Direction.WEST);
        ServerFieldUpdateResult customValidator = trait.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                payload(SyncFieldData.builder()
                        .put(AUTO_OUTPUT_ITEMS_FIELD, new JsonPrimitive(false))
                        .put(ITEM_OUTPUT_DIRECTION_FIELD, new JsonPrimitive(Direction.WEST.getSerializedName()))
                        .put(AUTO_OUTPUT_FLUIDS_FIELD, new JsonPrimitive(false))
                        .put(FLUID_OUTPUT_DIRECTION_FIELD, new JsonPrimitive(Direction.WEST.getSerializedName()))
                        .build()));

        helper.assertTrue(!customValidator.getAccepted(),
                "custom-validator-rejected directions were accepted");
        assertInitialOutputState(helper, trait,
                "custom validator rejection partially committed the auto-output batch");

        ServerFieldUpdateResult ordinal = trait.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                payload(SyncFieldData.builder()
                        .put(AUTO_OUTPUT_ITEMS_FIELD, new JsonPrimitive(false))
                        .put(ITEM_OUTPUT_DIRECTION_FIELD, new JsonPrimitive(Direction.EAST.get3DDataValue()))
                        .build()));

        helper.assertTrue(!ordinal.getAccepted(), "legacy ordinal direction payload was accepted");
        assertInitialOutputState(helper, trait,
                "legacy ordinal rejection partially committed the auto-output batch");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void unsupportedDirectionCandidatesAreRejected(GameTestHelper helper) {
        TestClientMachine machine = createClientMachine(helper);
        TrackingAutoOutputTrait trait = machine.attachTrait(new TrackingAutoOutputTrait(false, false));
        RegistryAccess registries = helper.getLevel().registryAccess();

        ServerFieldUpdateResult item = trait.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                payload(ITEM_OUTPUT_DIRECTION_FIELD, new JsonPrimitive(Direction.EAST.getSerializedName())));
        ServerFieldUpdateResult fluid = trait.getSyncDataHolder().tryApplyServerNetworkUpdate(registries,
                payload(FLUID_OUTPUT_DIRECTION_FIELD, new JsonPrimitive(Direction.UP.getSerializedName())));

        helper.assertTrue(!item.getAccepted() && !fluid.getAccepted(),
                "unsupported output direction candidate was accepted");
        helper.assertTrue(trait.getItemOutputDirection() == null && trait.getFluidOutputDirection() == null,
                "unsupported output direction candidate changed trait state");
        helper.assertTrue(trait.itemSubscriptionUpdates == 0 && trait.fluidSubscriptionUpdates == 0,
                "unsupported output direction candidate invoked a listener");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void ldlib2TogglesChangeFieldsAndFlushMachineSync(GameTestHelper helper) {
        TestClientMachine machine = createClientMachine(helper);
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

    private static TestClientMachine createClientMachine(GameTestHelper helper) {
        var definition = GTMachines.HULL[LV];
        helper.setBlock(MACHINE_POS, definition.getBlock());
        BlockPos absolutePos = helper.absolutePos(MACHINE_POS);
        helper.getLevel().removeBlockEntity(absolutePos);
        TestClientMachine machine = new TestClientMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), absolutePos, helper.getLevel().getBlockState(absolutePos)));
        helper.getLevel().setBlockEntity(machine);
        return machine;
    }

    private static DataComponentMap payload(ResourceLocation field, JsonElement value) {
        return payload(SyncFieldData.builder().put(field, value).build());
    }

    private static DataComponentMap payload(SyncFieldData fields) {
        return DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), fields)
                .build();
    }

    private static SyncFieldData requireFields(DataComponentMap components) {
        SyncFieldData fields = components.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Expected synchronized field data.");
        }
        return fields;
    }

    private static void assertDirectionField(GameTestHelper helper, SyncFieldData fields, ResourceLocation field,
                                             Direction expected, String message) {
        JsonElement value = fields.get(field);
        helper.assertTrue(value instanceof JsonPrimitive primitive && primitive.isString() &&
                primitive.getAsString().equals(expected.getSerializedName()), message);
    }

    private static void assertInitialOutputState(GameTestHelper helper, TrackingAutoOutputTrait trait,
                                                 String message) {
        helper.assertTrue(trait.isAutoOutputItems() && trait.isAutoOutputFluids() &&
                trait.getItemOutputDirection() == Direction.SOUTH &&
                trait.getFluidOutputDirection() == Direction.DOWN, message);
    }

    private static void assertInitialOutputAcknowledgement(GameTestHelper helper, SyncFieldData fields) {
        helper.assertTrue(fields.get(AUTO_OUTPUT_ITEMS_FIELD).getAsBoolean() &&
                fields.get(AUTO_OUTPUT_FLUIDS_FIELD).getAsBoolean(),
                "rejected direction batch did not acknowledge canonical auto-output states");
        assertDirectionField(helper, fields, ITEM_OUTPUT_DIRECTION_FIELD, Direction.SOUTH,
                "rejected direction batch did not acknowledge the canonical item direction");
        assertDirectionField(helper, fields, FLUID_OUTPUT_DIRECTION_FIELD, Direction.DOWN,
                "rejected direction batch did not acknowledge the canonical fluid direction");
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

        private void resetSubscriptionUpdates() {
            itemSubscriptionUpdates = 0;
            fluidSubscriptionUpdates = 0;
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

package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2ConfiguratorPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyConfiguratorButton;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.ServerFieldUpdateResult;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.DualHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
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

import com.google.gson.JsonPrimitive;

import java.util.concurrent.atomic.AtomicInteger;

import static com.gregtechceu.gtceu.api.GTValues.LV;
import static com.gregtechceu.gtceu.api.GTValues.LuV;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LDLib2DistinctPartFancyConfiguratorSyncTest {

    private static final String BATCH = "LDLib2DistinctPartFancyConfiguratorSync";
    private static final ResourceLocation DISTINCT_FIELD = SyncFieldData.key("isDistinct");

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void inputBusServerUpdateChangesHandlerGroupAndNotifiesOnce(GameTestHelper helper) {
        TestItemBusPartMachine machine = createItemBus(IO.IN, false);
        RegistryAccess registries = helper.getLevel().registryAccess();
        AtomicInteger notifications = new AtomicInteger();
        machine.getInventory().addChangedListener(notifications::incrementAndGet);
        machine.getSyncDataHolder().serializeFullClientSyncComponents(registries);

        ServerFieldUpdateResult changed = apply(machine, registries, true);

        helper.assertTrue(changed.getAccepted() && changed.getChanged(),
                "input bus distinct update was not accepted as a change");
        helper.assertTrue(machine.isDistinct() && machine.getInventory().isDistinct(),
                "input bus distinct update did not propagate to its item handler");
        helper.assertTrue(machine.getRecipeHandlers().getFirst().isDistinct(),
                "input bus distinct update did not change its recipe handler group");
        helper.assertTrue(notifications.get() == 1,
                "input bus distinct update did not notify its item handler exactly once");

        ServerFieldUpdateResult unchanged = apply(machine, registries, true);

        helper.assertTrue(unchanged.getAccepted() && !unchanged.getChanged(),
                "repeated input bus distinct update was not accepted as unchanged");
        helper.assertTrue(notifications.get() == 1,
                "repeated input bus distinct update invoked the server change listener");

        SyncFieldData acknowledgement = machine.getSyncDataHolder().serializeToFieldData(registries, true, false);
        helper.assertTrue(acknowledgement.get(DISTINCT_FIELD).getAsBoolean(),
                "input bus distinct update did not request an authoritative acknowledgement");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void outputBusServerUpdateKeepsSetterCanonicalFalse(GameTestHelper helper) {
        TestItemBusPartMachine machine = createItemBus(IO.OUT, false);
        RegistryAccess registries = helper.getLevel().registryAccess();
        AtomicInteger notifications = new AtomicInteger();
        machine.getInventory().addChangedListener(notifications::incrementAndGet);
        machine.getSyncDataHolder().serializeFullClientSyncComponents(registries);

        machine.setDistinct(true);
        helper.assertTrue(!machine.isDistinct() && !machine.getInventory().isDistinct(),
                "output bus public setter did not canonicalize distinct to false");

        ServerFieldUpdateResult normalized = apply(machine, registries, true);

        helper.assertTrue(normalized.getAccepted() && !normalized.getChanged(),
                "output bus server update did not preserve the setter's canonical false state");
        helper.assertTrue(!machine.isDistinct() && !machine.getRecipeHandlers().getFirst().isDistinct(),
                "output bus server update enabled a distinct recipe handler group");
        helper.assertTrue(notifications.get() == 0,
                "canonical-unchanged output bus update invoked the server change listener");

        SyncFieldData acknowledgement = machine.getSyncDataHolder().serializeToFieldData(registries, true, false);
        helper.assertTrue(!acknowledgement.get(DISTINCT_FIELD).getAsBoolean(),
                "output bus update did not acknowledge the canonical false value");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void inheritedDualInputHatchFieldUpdatesAllHandlerGroups(GameTestHelper helper) {
        DualHatchPartMachine machine = createDualHatch(GTMachines.DUAL_IMPORT_HATCH[LuV]);
        RegistryAccess registries = helper.getLevel().registryAccess();
        AtomicInteger itemNotifications = new AtomicInteger();
        AtomicInteger fluidNotifications = new AtomicInteger();
        machine.getInventory().addChangedListener(itemNotifications::incrementAndGet);
        machine.tank.addChangedListener(fluidNotifications::incrementAndGet);

        ServerFieldUpdateResult changed = apply(machine, registries, true);

        helper.assertTrue(changed.getAccepted() && changed.getChanged(),
                "dual input hatch did not inherit the distinct server field update");
        helper.assertTrue(machine.isDistinct() && machine.getInventory().isDistinct() && machine.tank.isDistinct(),
                "dual input hatch distinct update did not propagate to both handler traits");
        helper.assertTrue(machine.getRecipeHandlers().getFirst().isDistinct(),
                "dual input hatch distinct update did not change its inherited handler group");
        helper.assertTrue(itemNotifications.get() == 1 && fluidNotifications.get() == 1,
                "dual input hatch distinct update did not notify both handlers exactly once");

        ServerFieldUpdateResult unchanged = apply(machine, registries, true);
        helper.assertTrue(unchanged.getAccepted() && !unchanged.getChanged(),
                "repeated dual input hatch distinct update was not accepted as unchanged");
        helper.assertTrue(itemNotifications.get() == 1 && fluidNotifications.get() == 1,
                "repeated dual input hatch update notified inherited handlers again");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void ldlib2ToggleChangesRootFieldFlushesSyncAndConsumesEvent(GameTestHelper helper) {
        TestItemBusPartMachine machine = createItemBus(IO.IN, true);
        RegistryAccess registries = helper.getLevel().registryAccess();
        machine.getSyncDataHolder().collectServerNetworkChanges(registries);
        LDLib2ConfiguratorPanelElement panel = new LDLib2ConfiguratorPanelElement(new TestMachineUIHolder(machine), 0,
                0);
        LDLib2FancyConfiguratorButton.Toggle toggle = LDLib2DistinctPartFancyConfigurator
                .createDistinctConfigurator(panel, machine);
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);

        toggle.onClick(event);

        helper.assertTrue(machine.isDistinct() && machine.getInventory().isDistinct(),
                "LDLib2 distinct toggle did not update the client root field and handler group");
        helper.assertTrue(machine.syncRequests == 1,
                "LDLib2 distinct toggle did not flush the owning machine exactly once");
        helper.assertTrue(event.hasHandler,
                "LDLib2 distinct toggle did not consume its handled event");

        DataComponentMap candidates = machine.getSyncDataHolder().collectServerNetworkChanges(registries);
        SyncFieldData fields = candidates.get(GTDataComponents.SYNC_FIELD_DATA.get());
        helper.assertTrue(fields != null && fields.get(DISTINCT_FIELD).getAsBoolean(),
                "LDLib2 distinct toggle did not expose the root field to the C2S collector");
        helper.succeed();
    }

    private static TestItemBusPartMachine createItemBus(IO io, boolean remote) {
        MachineDefinition definition = io == IO.OUT ? GTMachines.ITEM_EXPORT_BUS[LV] : GTMachines.ITEM_IMPORT_BUS[LV];
        return new TestItemBusPartMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()), LV, io, remote);
    }

    private static DualHatchPartMachine createDualHatch(MachineDefinition definition) {
        MetaMachine machine = definition.getBlockEntityType().create(BlockPos.ZERO, definition.defaultBlockState());
        if (!(machine instanceof DualHatchPartMachine dualHatch)) {
            throw new IllegalStateException("Dual hatch definition did not create a dual hatch machine.");
        }
        return dualHatch;
    }

    private static ServerFieldUpdateResult apply(ItemBusPartMachine machine, RegistryAccess registries,
                                                 boolean distinct) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(DISTINCT_FIELD, new JsonPrimitive(distinct))
                        .build())
                .build();
        return machine.getSyncDataHolder().tryApplyServerNetworkUpdate(registries, payload);
    }

    private static final class TestItemBusPartMachine extends ItemBusPartMachine {

        private final boolean remote;
        private int syncRequests;

        private TestItemBusPartMachine(BlockEntityCreationInfo info, int tier, IO io, boolean remote) {
            super(info, tier, io);
            this.remote = remote;
        }

        @Override
        public boolean isRemote() {
            return remote;
        }

        @Override
        public void sendServerSyncChanges() {
            syncRequests++;
        }
    }

    private record TestMachineUIHolder(TestItemBusPartMachine machine) implements MachineUIHolder {

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

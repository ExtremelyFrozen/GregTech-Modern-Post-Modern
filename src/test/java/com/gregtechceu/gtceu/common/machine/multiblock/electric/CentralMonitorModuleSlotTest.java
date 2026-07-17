package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IMonitorComponent;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.CentralMonitorGroupItemHandler;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CentralMonitorModuleSlotTest {

    private static final BlockPos MONITOR_POSITION = new BlockPos(1, 0, 0);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CentralMonitorModuleSlot")
    public static void groupItemHandlerPreservesPlaceholderFirstSlotSemantics(GameTestHelper helper) {
        MonitorGroup group = new MonitorGroup("group-handler-test");
        CentralMonitorGroupItemHandler groupHandler = new CentralMonitorGroupItemHandler(group);
        AtomicInteger placeholderChanges = new AtomicInteger();
        AtomicInteger moduleChanges = new AtomicInteger();
        group.getPlaceholderSlotsHandler().setOnContentsChanged(placeholderChanges::incrementAndGet);
        group.getItemStackHandler().setOnContentsChanged(moduleChanges::incrementAndGet);

        helper.assertTrue(groupHandler.getSlots() == CentralMonitorGroupItemHandler.SLOT_COUNT &&
                CentralMonitorGroupItemHandler.MODULE_SLOT_OFFSET == 8,
                "Central Monitor group handler did not expose the legacy nine-slot shape");
        for (int slot = 0; slot < CentralMonitorGroupItemHandler.PLACEHOLDER_SLOT_COUNT; slot++) {
            ItemStack placeholder = new ItemStack(Items.COBBLESTONE, slot + 1);
            groupHandler.setStackInSlot(slot, placeholder);
            helper.assertTrue(ItemStack.matches(
                    group.getPlaceholderSlotsHandler().getStackInSlot(slot), placeholder) &&
                    ItemStack.matches(groupHandler.getStackInSlot(slot), placeholder),
                    "Central Monitor group handler remapped placeholder slot " + slot);
        }

        ItemStack module = GTItems.IMAGE_MODULE.get().getDefaultInstance();
        groupHandler.setStackInSlot(CentralMonitorGroupItemHandler.MODULE_SLOT_OFFSET, module.copy());
        helper.assertTrue(ItemStack.matches(group.getItemStackHandler().getStackInSlot(0), module) &&
                ItemStack.matches(groupHandler.getStackInSlot(CentralMonitorGroupItemHandler.MODULE_SLOT_OFFSET),
                        module),
                "Central Monitor group handler did not map offset eight to the module slot");
        helper.assertTrue(groupHandler.isItemValid(0, Items.IRON_INGOT.getDefaultInstance()) &&
                groupHandler.isItemValid(CentralMonitorGroupItemHandler.MODULE_SLOT_OFFSET, module) &&
                !groupHandler.isItemValid(CentralMonitorGroupItemHandler.MODULE_SLOT_OFFSET,
                        Items.IRON_INGOT.getDefaultInstance()),
                "Central Monitor group handler did not preserve placeholder and module filters");

        ItemStack extracted = groupHandler.extractItem(7, 8, false);
        helper.assertTrue(extracted.getCount() == 8 && group.getPlaceholderSlotsHandler().getStackInSlot(7).isEmpty(),
                "Central Monitor group handler did not forward placeholder extraction");
        ItemStack placeholderRemainder = groupHandler.insertItem(7, Items.GOLD_INGOT.getDefaultInstance(), false);
        helper.assertTrue(placeholderRemainder.isEmpty() &&
                group.getPlaceholderSlotsHandler().getStackInSlot(7).is(Items.GOLD_INGOT),
                "Central Monitor group handler did not forward placeholder insertion");

        ItemStack extractedModule = groupHandler.extractItem(
                CentralMonitorGroupItemHandler.MODULE_SLOT_OFFSET, 1, false);
        helper.assertTrue(ItemStack.matches(extractedModule, module) &&
                group.getItemStackHandler().getStackInSlot(0).isEmpty(),
                "Central Monitor group handler did not forward module extraction");
        ItemStack rejectedModuleInsert = groupHandler.insertItem(
                CentralMonitorGroupItemHandler.MODULE_SLOT_OFFSET, Items.IRON_INGOT.getDefaultInstance(), false);
        helper.assertTrue(!rejectedModuleInsert.isEmpty() && group.getItemStackHandler().getStackInSlot(0).isEmpty(),
                "Central Monitor group handler bypassed the module filter during insertion");
        ItemStack moduleRemainder = groupHandler.insertItem(
                CentralMonitorGroupItemHandler.MODULE_SLOT_OFFSET, module.copy(), false);
        helper.assertTrue(moduleRemainder.isEmpty() &&
                ItemStack.matches(group.getItemStackHandler().getStackInSlot(0), module),
                "Central Monitor group handler rejected a valid module insertion");

        groupHandler.setStackInSlot(0, ItemStack.EMPTY);
        groupHandler.setStackInSlot(CentralMonitorGroupItemHandler.MODULE_SLOT_OFFSET, ItemStack.EMPTY);
        placeholderChanges.set(0);
        moduleChanges.set(0);
        int placeholderCapacity = groupHandler.getMaxStackSizeForEmptySlot(
                0, Items.COBBLESTONE.getDefaultInstance());
        int moduleCapacity = groupHandler.getMaxStackSizeForEmptySlot(
                CentralMonitorGroupItemHandler.MODULE_SLOT_OFFSET, module);
        helper.assertTrue(groupHandler.isNonMutatingEmptySlotCapacityQueryEnabled() &&
                placeholderCapacity > 0 && moduleCapacity > 0,
                "Central Monitor group handler did not expose both non-mutating capacity paths");
        helper.assertTrue(placeholderChanges.get() == 0 && moduleChanges.get() == 0 &&
                groupHandler.getStackInSlot(0).isEmpty() &&
                groupHandler.getStackInSlot(CentralMonitorGroupItemHandler.MODULE_SLOT_OFFSET).isEmpty(),
                "Central Monitor group capacity query mutated a handler or fired a contents callback");

        CustomItemStackHandler externalModuleHandler = new CustomItemStackHandler(1);
        CustomItemStackHandler externalPlaceholderHandler = new CustomItemStackHandler(
                CentralMonitorGroupItemHandler.PLACEHOLDER_SLOT_COUNT);
        MonitorGroup externalGroup = MonitorGroup.restore(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "external-handler-test",
                externalModuleHandler,
                externalPlaceholderHandler);
        helper.assertTrue(!externalModuleHandler.isNonMutatingEmptySlotCapacityQueryEnabled() &&
                !externalPlaceholderHandler.isNonMutatingEmptySlotCapacityQueryEnabled() &&
                !new CentralMonitorGroupItemHandler(externalGroup)
                        .isNonMutatingEmptySlotCapacityQueryEnabled(),
                "Monitor group incorrectly opted external handlers into non-mutating capacity queries");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CentralMonitorModuleSlot")
    public static void capacityQueriesAreNonMutatingAndPreserveSpecialHandlerSemantics(GameTestHelper helper) {
        CustomItemStackHandler moduleHandler = MonitorGroup.createModuleHandler();
        AtomicInteger moduleChanges = new AtomicInteger();
        moduleHandler.setOnContentsChanged(moduleChanges::incrementAndGet);
        ItemStack module = GTItems.IMAGE_MODULE.get().getDefaultInstance();
        Slot legacyModuleSlot = new SlotWidget().new WidgetSlotItemHandler(moduleHandler, 0, 0, 0);
        Slot ldlib2ModuleSlot = new GTItemSlotElement(moduleHandler, 0).getSlot();

        int expectedCapacity = moduleHandler.getMaxStackSizeForEmptySlot(0, module);
        helper.assertTrue(expectedCapacity > 0,
                "module handler rejected a valid module during its non-mutating capacity query");
        helper.assertTrue(legacyModuleSlot.getMaxStackSize(module) == expectedCapacity &&
                ldlib2ModuleSlot.getMaxStackSize(module) == expectedCapacity,
                "empty module slot capacity differed between legacy and LDLib2 slots");
        helper.assertTrue(moduleHandler.getStackInSlot(0).isEmpty() && moduleChanges.get() == 0,
                "empty module slot capacity query mutated the handler");

        moduleHandler.setStackInSlot(0, module.copy());
        moduleChanges.set(0);
        helper.assertTrue(legacyModuleSlot.getMaxStackSize(module) == expectedCapacity &&
                ldlib2ModuleSlot.getMaxStackSize(module) == expectedCapacity,
                "occupied module slot capacity differed between legacy and LDLib2 slots");
        helper.assertTrue(ItemStack.isSameItemSameComponents(moduleHandler.getStackInSlot(0), module) &&
                moduleChanges.get() == 0,
                "occupied module slot capacity query replaced the current module");
        int emptyCandidateCapacity = ItemStack.EMPTY.getMaxStackSize();
        helper.assertTrue(moduleHandler.getMaxStackSizeForEmptySlot(0, ItemStack.EMPTY) == emptyCandidateCapacity &&
                legacyModuleSlot.getMaxStackSize(ItemStack.EMPTY) == emptyCandidateCapacity &&
                ldlib2ModuleSlot.getMaxStackSize(ItemStack.EMPTY) == emptyCandidateCapacity &&
                moduleChanges.get() == 0,
                "empty candidate capacity query changed slot semantics or mutated the module handler");

        CustomItemStackHandler occupantSensitiveHandler = new CustomItemStackHandler(1);
        ItemStack statefulOccupant = Items.IRON_INGOT.getDefaultInstance();
        occupantSensitiveHandler.setStackInSlot(0, statefulOccupant);
        occupantSensitiveHandler.setFilter(stack -> occupantSensitiveHandler.getStackInSlot(0).isEmpty());
        AtomicInteger fallbackChanges = new AtomicInteger();
        occupantSensitiveHandler.setOnContentsChanged(fallbackChanges::incrementAndGet);
        ItemStack statefulCandidate = Items.COBBLESTONE.getDefaultInstance();
        Slot legacyStatefulSlot = new SlotWidget().new WidgetSlotItemHandler(occupantSensitiveHandler, 0, 0, 0);
        Slot ldlib2StatefulSlot = new GTItemSlotElement(occupantSensitiveHandler, 0).getSlot();

        helper.assertTrue(!occupantSensitiveHandler.isNonMutatingEmptySlotCapacityQueryEnabled(),
                "ordinary handler implicitly opted into non-mutating capacity queries");
        helper.assertTrue(legacyStatefulSlot.getMaxStackSize(statefulCandidate) ==
                statefulCandidate.getMaxStackSize() &&
                ldlib2StatefulSlot.getMaxStackSize(statefulCandidate) == statefulCandidate.getMaxStackSize(),
                "ordinary handler capacity query changed occupant-sensitive filter semantics");
        helper.assertTrue(ItemStack.isSameItemSameComponents(
                occupantSensitiveHandler.getStackInSlot(0), statefulOccupant),
                "ordinary handler capacity query did not restore its occupant");
        helper.assertTrue(fallbackChanges.get() == 4,
                "ordinary handler did not retain the legacy clear-and-restore probe");

        VirtualCapacityHandler virtualHandler = new VirtualCapacityHandler(4, 7);
        ItemStack occupant = Items.IRON_INGOT.getDefaultInstance();
        virtualHandler.setStackInSlot(3, occupant);
        virtualHandler.resetProbeCounters();
        ItemStack candidate = Items.COBBLESTONE.getDefaultInstance();
        Slot legacyVirtualSlot = new SlotWidget().new WidgetSlotItemHandler(virtualHandler, 3, 0, 0);
        Slot ldlib2VirtualSlot = new GTItemSlotElement(virtualHandler, 3).getSlot();

        helper.assertTrue(!virtualHandler.isNonMutatingEmptySlotCapacityQueryEnabled(),
                "special handler implicitly opted into the default capacity query");
        helper.assertTrue(legacyVirtualSlot.getMaxStackSize(candidate) == 7 &&
                ldlib2VirtualSlot.getMaxStackSize(candidate) == 7,
                "slot capacity query bypassed the special handler's insertion semantics");
        helper.assertTrue(ItemStack.isSameItemSameComponents(virtualHandler.getStackInSlot(3), occupant),
                "slot capacity query did not restore the special handler's occupant");
        helper.assertTrue(virtualHandler.getSimulatedInsertions() == 2,
                "slot capacity query did not delegate to both special-handler insertion probes");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "CentralMonitorModuleSlot")
    public static void newAndRestoredGroupsPublishPhysicalModuleReplacement(GameTestHelper helper) {
        TestCentralMonitorMachine server = new TestCentralMonitorMachine(false);
        server.addMonitor(MONITOR_POSITION);
        UUID groupIdentity = UUID.randomUUID();
        helper.assertTrue(server.createCentralMonitorGroup(0, groupIdentity, Set.of(MONITOR_POSITION)),
                "server rejected valid module-slot test group creation");
        MonitorGroup serverGroup = server.getMonitorGroups().getFirst();

        TestCentralMonitorMachine client = new TestCentralMonitorMachine(true);
        DataComponentMap full = server.getSyncDataHolder()
                .serializeFullClientSyncComponents(helper.getLevel().registryAccess());
        client.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), full);
        server.getSyncDataHolder().serializeToComponents(helper.getLevel().registryAccess(), true, false);

        ItemStack module = GTItems.IMAGE_MODULE.get().getDefaultInstance();
        UUID beforeReplacement = serverGroup.getModuleSlotIncarnation();
        serverGroup.getItemStackHandler().setStackInSlot(0, module.copy());
        helper.assertTrue(!serverGroup.getModuleSlotIncarnation().equals(beforeReplacement),
                "new server group did not rotate its module-slot incarnation");

        DataComponentMap delta = server.getSyncDataHolder()
                .serializeToComponents(helper.getLevel().registryAccess(), true, false);
        helper.assertTrue(!delta.isEmpty(), "module replacement did not mark monitorGroups dirty");
        client.getSyncDataHolder().applyClientNetworkUpdate(helper.getLevel().registryAccess(), delta);
        MonitorGroup clientGroup = client.getMonitorGroups().getFirst();
        helper.assertTrue(clientGroup.getModuleSlotIncarnation().equals(serverGroup.getModuleSlotIncarnation()),
                "module replacement delta did not publish the new slot incarnation");
        helper.assertTrue(ItemStack.isSameItemSameComponents(
                clientGroup.getItemStackHandler().getStackInSlot(0), module),
                "module replacement delta did not publish the current slot occupant");

        UUID beforeInPlaceConfiguration = serverGroup.getModuleSlotIncarnation();
        serverGroup.getItemStackHandler().getStackInSlot(0)
                .set(DataComponents.CUSTOM_NAME, Component.literal("configured"));
        helper.assertTrue(serverGroup.getModuleSlotIncarnation().equals(beforeInPlaceConfiguration),
                "in-place module configuration was treated as physical slot replacement");

        SyncFieldData saved = server.getSyncDataHolder()
                .serializeToSaveFieldData(helper.getLevel().registryAccess());
        TestCentralMonitorMachine restored = new TestCentralMonitorMachine(false);
        restored.getSyncDataHolder().deserializeFieldData(helper.getLevel().registryAccess(), saved, false);
        MonitorGroup restoredGroup = restored.getMonitorGroups().getFirst();
        helper.assertTrue(restoredGroup.getModuleSlotIncarnation().equals(serverGroup.getModuleSlotIncarnation()),
                "restoration changed the saved module-slot incarnation before listener binding");
        helper.assertTrue(new CentralMonitorGroupItemHandler(restoredGroup)
                .isNonMutatingEmptySlotCapacityQueryEnabled(),
                "restored monitor group lost its codec-owned non-mutating capacity handlers");

        restored.onLoad();
        restored.getSyncDataHolder().serializeToComponents(helper.getLevel().registryAccess(), true, false);
        UUID beforeRestoredReplacement = restoredGroup.getModuleSlotIncarnation();
        restoredGroup.getItemStackHandler().setStackInSlot(
                0, restoredGroup.getItemStackHandler().getStackInSlot(0).copy());
        helper.assertTrue(!restoredGroup.getModuleSlotIncarnation().equals(beforeRestoredReplacement),
                "loaded server group did not bind its module replacement listener");
        helper.assertTrue(!restored.getSyncDataHolder()
                .serializeToComponents(helper.getLevel().registryAccess(), true, false)
                .isEmpty(), "loaded group replacement did not mark monitorGroups dirty");
        helper.succeed();
    }

    private static BlockEntityCreationInfo centralMonitorInfo() {
        return new BlockEntityCreationInfo(GTMultiMachines.CENTRAL_MONITOR.getBlockEntityType(), BlockPos.ZERO,
                GTMultiMachines.CENTRAL_MONITOR.defaultBlockState());
    }

    private static final class TestCentralMonitorMachine extends CentralMonitorMachine {

        private final Map<BlockPos, IMonitorComponent> components = new HashMap<>();
        private final boolean remote;

        private TestCentralMonitorMachine(boolean remote) {
            super(centralMonitorInfo());
            this.remote = remote;
        }

        private void addMonitor(BlockPos position) {
            components.put(position, new TestMonitorComponent(position));
        }

        @Override
        public boolean isRemote() {
            return remote;
        }

        @Override
        protected boolean isMembershipStructureAvailable() {
            return true;
        }

        @Override
        public int getCentralMonitorMembershipCapacity() {
            return components.size();
        }

        @Override
        protected Map<BlockPos, IMonitorComponent> resolveMembershipComponents() {
            return new HashMap<>(components);
        }
    }

    private static final class VirtualCapacityHandler extends CustomItemStackHandler {

        private final int logicalSlots;
        private final int acceptedCapacity;
        private ItemStack occupant = ItemStack.EMPTY;
        private int simulatedInsertions;

        private VirtualCapacityHandler(int logicalSlots, int acceptedCapacity) {
            this.logicalSlots = logicalSlots;
            this.acceptedCapacity = acceptedCapacity;
        }

        @Override
        public int getSlots() {
            return logicalSlots;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            validateLogicalSlot(slot);
            return occupant;
        }

        @Override
        public void setStackInSlot(int slot, ItemStack stack) {
            validateLogicalSlot(slot);
            occupant = stack;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            validateLogicalSlot(slot);
            if (simulate) {
                simulatedInsertions++;
            }
            int accepted = Math.min(acceptedCapacity, stack.getCount());
            return accepted == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - accepted);
        }

        @Override
        public int getSlotLimit(int slot) {
            validateLogicalSlot(slot);
            return Items.COBBLESTONE.getDefaultMaxStackSize();
        }

        private void resetProbeCounters() {
            simulatedInsertions = 0;
        }

        private int getSimulatedInsertions() {
            return simulatedInsertions;
        }

        private void validateLogicalSlot(int slot) {
            if (slot < 0 || slot >= logicalSlots) {
                throw new IllegalArgumentException("Invalid logical slot: " + slot);
            }
        }
    }

    private record TestMonitorComponent(BlockPos position) implements IMonitorComponent {

        @Override
        public IGuiTexture getComponentIcon() {
            return GuiTextures.BLANK_TRANSPARENT;
        }

        @Override
        public BlockPos getBlockPos() {
            return position;
        }
    }
}

package com.gregtechceu.gtceu.common.machine.multiblock.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IMonitorComponent;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEventDispatcher;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class CentralMonitorMachineLDLib2UITest {

    private static final String BATCH = "CentralMonitorMachineLDLib2UI";
    private static final BlockPos FIRST_MONITOR = new BlockPos(1, 0, 0);
    private static final BlockPos SECOND_MONITOR = new BlockPos(2, 0, 0);
    private static final BlockPos DATA_TARGET = new BlockPos(3, 0, 0);

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void realClicksEmitExactCreateSetClearAndRemoveActions(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine machine = preparedMachine();
        UUID createdGroupIdentity = new UUID(0, 101);
        List<SyncActionData> actions = new ArrayList<>();
        CentralMonitorElement element = new CentralMonitorElement(
                machine, player, new MutableMachineUIHolder(machine), actions::add, () -> createdGroupIdentity);

        List<Component> expectedDisplayText = new ArrayList<>();
        machine.addDisplayText(expectedDisplayText);
        helper.assertTrue(!expectedDisplayText.isEmpty() &&
                element.statusPanel().getParent() == element.overviewElement() &&
                element.statusPanel().getLastText().equals(expectedDisplayText),
                "Central Monitor LDLib2 overview did not preserve the machine display text panel");

        click(element.componentControls().get(FIRST_MONITOR), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        helper.assertTrue(element.createGroupButton().isVisible() && element.createGroupButton().isActive(),
                "selecting one ungrouped monitor did not expose the create action");
        click(element.createGroupButton(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        assertLastAction(helper, actions, 1, CentralMonitorMembershipActions.createGroupAction(
                machine.getCentralMonitorActionIncarnation(), 0, createdGroupIdentity, Set.of(FIRST_MONITOR), 0),
                "create click");
        helper.assertTrue(element.selectedMonitorPositions().isEmpty() &&
                element.selectedTargetPosition().isEmpty(),
                "create click did not clear its opening-local selection draft");

        helper.assertTrue(machine.createCentralMonitorGroup(0, createdGroupIdentity, Set.of(FIRST_MONITOR)),
                "test server rejected the captured create action state");
        element.screenTick();
        MonitorGroup group = machine.resolveCentralMonitorGroup(createdGroupIdentity);
        helper.assertTrue(group != null, "created group did not resolve by its stable identity");

        click(element.componentControls().get(FIRST_MONITOR), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        click(element.componentControls().get(DATA_TARGET), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        element.dataSlotInput().setValue(3);
        helper.assertTrue(element.setTargetButton().isVisible() && element.dataSlotInput().isVisible(),
                "group selection and data target did not expose their target controls");
        CentralMonitorGroupTargetState initialTarget = new CentralMonitorGroupTargetState(null, 0);
        CentralMonitorGroupTargetState requestedTarget = new CentralMonitorGroupTargetState(DATA_TARGET, 2);
        click(element.setTargetButton(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        assertLastAction(helper, actions, 2, CentralMonitorGroupTargetActions.createSetGroupTargetAction(
                machine.getCentralMonitorActionIncarnation(), createdGroupIdentity, initialTarget, requestedTarget, 1),
                "set-target click");

        helper.assertTrue(machine.setCentralMonitorGroupTarget(createdGroupIdentity, initialTarget, requestedTarget),
                "test server rejected the captured target action state");
        element.screenTick();
        click(element.componentControls().get(DATA_TARGET), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        click(element.setTargetButton(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        assertLastAction(helper, actions, 3, CentralMonitorGroupTargetActions.createSetGroupTargetAction(
                machine.getCentralMonitorActionIncarnation(), createdGroupIdentity, requestedTarget,
                new CentralMonitorGroupTargetState(null, 0), 2), "clear-target click");

        click(element.removeMembersButton(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        assertLastAction(helper, actions, 4, CentralMonitorMembershipActions.createRemoveGroupMembersAction(
                machine.getCentralMonitorActionIncarnation(), 1, createdGroupIdentity, Set.of(FIRST_MONITOR), 3),
                "remove click");
        helper.assertTrue(element.selectedMonitorPositions().isEmpty() &&
                element.selectedTargetPosition().isEmpty(),
                "remove click did not clear its opening-local selection draft");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = BATCH)
    public static void openingsKeepDraftsAndActionSequencesIndependent(GameTestHelper helper) {
        ServerPlayer player = preparedPlayer(helper);
        TestCentralMonitorMachine machine = preparedMachine();
        MutableMachineUIHolder holder = new MutableMachineUIHolder(machine);
        UUID firstGroupIdentity = new UUID(0, 201);
        UUID secondGroupIdentity = new UUID(0, 202);
        List<SyncActionData> firstActions = new ArrayList<>();
        List<SyncActionData> secondActions = new ArrayList<>();
        CentralMonitorElement first = new CentralMonitorElement(
                machine, player, holder, firstActions::add, () -> firstGroupIdentity);
        CentralMonitorElement second = new CentralMonitorElement(
                machine, player, holder, secondActions::add, () -> secondGroupIdentity);

        click(first.componentControls().get(FIRST_MONITOR), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        click(first.componentControls().get(DATA_TARGET), GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        first.dataSlotInput().setValue(2);
        click(second.componentControls().get(SECOND_MONITOR), GLFW.GLFW_MOUSE_BUTTON_LEFT);

        helper.assertTrue(first.selectedMonitorPositions().equals(Set.of(FIRST_MONITOR)) &&
                first.selectedTargetPosition().equals(Optional.of(DATA_TARGET)),
                "first opening lost its own monitor or target draft");
        helper.assertTrue(second.selectedMonitorPositions().equals(Set.of(SECOND_MONITOR)) &&
                second.selectedTargetPosition().isEmpty(),
                "first opening leaked monitor or target selection into the second opening");

        click(second.createGroupButton(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        click(first.createGroupButton(), GLFW.GLFW_MOUSE_BUTTON_LEFT);
        assertLastAction(helper, secondActions, 1, CentralMonitorMembershipActions.createGroupAction(
                machine.getCentralMonitorActionIncarnation(), 0, secondGroupIdentity, Set.of(SECOND_MONITOR), 0),
                "second opening create click");
        assertLastAction(helper, firstActions, 1, CentralMonitorMembershipActions.createGroupAction(
                machine.getCentralMonitorActionIncarnation(), 0, firstGroupIdentity, Set.of(FIRST_MONITOR), 0),
                "first opening create click");
        helper.assertTrue(first.selectedMonitorPositions().isEmpty() && second.selectedMonitorPositions().isEmpty(),
                "one opening retained or changed another opening's cleared draft");
        helper.succeed();
    }

    private static void assertLastAction(GameTestHelper helper, List<SyncActionData> actions, int expectedCount,
                                         SyncActionData expected, String description) {
        helper.assertTrue(actions.size() == expectedCount,
                description + " did not send exactly one action at the expected sequence");
        helper.assertTrue(actions.getLast().equals(expected),
                description + " sent the wrong action id, sequence, or structured payload");
    }

    private static void click(UIElement target, int button) {
        if (target == null) {
            throw new IllegalStateException("Central Monitor test could not resolve its click target");
        }
        UIEvent event = UIEvent.create(UIEvents.MOUSE_DOWN);
        event.target = target;
        event.button = button;
        UIEventDispatcher.dispatchEvent(event, false, false, false);
    }

    private static ServerPlayer preparedPlayer(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.closeContainer();
        player.getInventory().clearContent();
        return player;
    }

    private static TestCentralMonitorMachine preparedMachine() {
        TestCentralMonitorMachine machine = new TestCentralMonitorMachine();
        machine.addComponent(FIRST_MONITOR, true, null);
        machine.addComponent(SECOND_MONITOR, true, null);
        machine.addComponent(DATA_TARGET, false, new ItemStackHandler(3));
        return machine;
    }

    private static BlockEntityCreationInfo centralMonitorInfo() {
        return new BlockEntityCreationInfo(GTMultiMachines.CENTRAL_MONITOR.getBlockEntityType(), BlockPos.ZERO,
                GTMultiMachines.CENTRAL_MONITOR.defaultBlockState());
    }

    private static final class TestCentralMonitorMachine extends CentralMonitorMachine {

        private final Map<BlockPos, IMonitorComponent> components = new LinkedHashMap<>();
        private final List<IMonitorComponent> gridComponents = new ArrayList<>();

        private TestCentralMonitorMachine() {
            super(centralMonitorInfo());
            isFormed = true;
        }

        private void addComponent(BlockPos position, boolean monitor, @Nullable IItemHandler dataItems) {
            TestMonitorComponent component = new TestMonitorComponent(position, monitor, dataItems);
            components.put(position, component);
            gridComponents.add(component);
        }

        @Override
        public int getLeftDist() {
            return 0;
        }

        @Override
        public int getRightDist() {
            return Math.max(0, gridComponents.size() - 1);
        }

        @Override
        public int getUpDist() {
            return 0;
        }

        @Override
        public int getDownDist() {
            return 0;
        }

        @Override
        public @Nullable IMonitorComponent getComponent(int row, int column) {
            return row == 0 && column >= 0 && column < gridComponents.size() ? gridComponents.get(column) : null;
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
        protected @NotNull Map<BlockPos, IMonitorComponent> resolveMembershipComponents() {
            return new LinkedHashMap<>(components);
        }

        @Override
        public boolean isRemote() {
            return true;
        }
    }

    private record TestMonitorComponent(BlockPos position, boolean monitor,
                                        @Nullable IItemHandler dataItems)
            implements IMonitorComponent {

        @Override
        public boolean isMonitor() {
            return monitor;
        }

        @Override
        public IGuiTexture getComponentIcon() {
            return GuiTextures.BLANK_TRANSPARENT;
        }

        @Override
        public BlockPos getBlockPos() {
            return position;
        }

        @Override
        public @Nullable IItemHandler getDataItems() {
            return dataItems;
        }
    }

    private static final class MutableMachineUIHolder implements MachineUIHolder {

        private final MetaMachine machine;

        private MutableMachineUIHolder(MetaMachine machine) {
            this.machine = machine;
        }

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

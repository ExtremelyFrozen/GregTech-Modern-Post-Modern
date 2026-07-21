package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.data.GTMachines;

import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.testframework.annotation.TestHolder;
import net.neoforged.testframework.gametest.EmptyTemplate;

import org.jetbrains.annotations.Nullable;

import java.util.List;

@PrefixGameTestTemplate(false)
@GameTestHolder(GTCEu.MOD_ID)
public class LDLib2WorkingEnabledFancyConfiguratorUITest {

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2WorkingEnabledFancyConfiguratorUI")
    public static void matchingHolderTracksOpeningStateAndTooltips(GameTestHelper helper) {
        TestControllableMachine machine = createMachine(false, false);
        MutableMachineUIHolder holder = new MutableMachineUIHolder(machine);
        LDLib2WorkingEnabledFancyConfigurator configurator = new LDLib2WorkingEnabledFancyConfigurator(machine, holder);

        IGuiTexture disabledIcon = configurator.getIcon();
        assertTooltips(helper, configurator, "behaviour.soft_hammer.disabled", "disabled");

        machine.setWorkingEnabled(true);

        helper.assertTrue(configurator.getIcon() != disabledIcon,
                "working configurator did not read the updated opening-machine state");
        assertTooltips(helper, configurator, "behaviour.soft_hammer.enabled", "enabled");

        UIEvent serverClick = UIEvent.create(UIEvents.CLICK);
        configurator.onClick(serverClick);
        helper.assertFalse(serverClick.hasHandler,
                "server-side matching holder click was marked as a sent client action");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2WorkingEnabledFancyConfiguratorUI")
    public static void constructionRejectsDifferentAndMissingMachines(GameTestHelper helper) {
        TestControllableMachine openingMachine = createMachine(false, false);
        TestControllableMachine otherMachine = createMachine(false, false);
        MutableMachineUIHolder holder = new MutableMachineUIHolder(otherMachine);

        assertConstructionRejected(helper, openingMachine, holder, "different machine");

        holder.setMachine(null);
        assertConstructionRejected(helper, openingMachine, holder, "missing machine");
        helper.succeed();
    }

    @TestHolder
    @EmptyTemplate
    @GameTest(template = "empty", batch = "LDLib2WorkingEnabledFancyConfiguratorUI")
    public static void clickRejectsReplacementAndMissingMachines(GameTestHelper helper) {
        TestControllableMachine openingMachine = createMachine(false, false);
        TestControllableMachine replacement = createMachine(false, true);
        MutableMachineUIHolder holder = new MutableMachineUIHolder(openingMachine);
        LDLib2WorkingEnabledFancyConfigurator configurator = new LDLib2WorkingEnabledFancyConfigurator(openingMachine,
                holder);

        helper.assertTrue(openingMachine.getDefinition() == replacement.getDefinition(),
                "replacement test did not use the same machine definition");

        // A missing identity guard would enter the real client send path for this replacement.
        holder.setMachine(replacement);
        UIEvent replacementClick = UIEvent.create(UIEvents.CLICK);
        assertClickRejected(helper, configurator, replacementClick, "replacement machine");

        holder.setMachine(null);
        UIEvent missingClick = UIEvent.create(UIEvents.CLICK);
        assertClickRejected(helper, configurator, missingClick, "missing machine");
        helper.succeed();
    }

    private static void assertConstructionRejected(GameTestHelper helper, IControllable controllable,
                                                   MachineUIHolder holder, String description) {
        IllegalArgumentException rejection = null;
        try {
            new LDLib2WorkingEnabledFancyConfigurator(controllable, holder);
        } catch (IllegalArgumentException exception) {
            rejection = exception;
        }
        helper.assertTrue(rejection != null && rejection.getMessage().contains("holder"),
                "working configurator accepted a holder resolving " + description);
    }

    private static void assertClickRejected(GameTestHelper helper,
                                            LDLib2WorkingEnabledFancyConfigurator configurator,
                                            UIEvent event, String description) {
        IllegalStateException rejection = null;
        try {
            configurator.onClick(event);
        } catch (IllegalStateException exception) {
            rejection = exception;
        }
        helper.assertTrue(rejection != null && rejection.getMessage().contains("no longer"),
                "stale working configurator accepted a holder resolving " + description);
        helper.assertFalse(event.hasHandler,
                "rejected working configurator click was marked as a sent action for " + description);
    }

    private static void assertTooltips(GameTestHelper helper,
                                       LDLib2WorkingEnabledFancyConfigurator configurator,
                                       String translationKey, String description) {
        helper.assertTrue(configurator.getTooltips().equals(List.of(Component.translatable(translationKey))),
                "working configurator did not expose the " + description + " tooltip");
    }

    private static TestControllableMachine createMachine(boolean workingEnabled, boolean remote) {
        var definition = GTMachines.BUFFER[GTValues.LV];
        return new TestControllableMachine(new BlockEntityCreationInfo(
                definition.getBlockEntityType(), BlockPos.ZERO, definition.defaultBlockState()),
                workingEnabled, remote);
    }

    private static final class TestControllableMachine extends MetaMachine implements IControllable {

        private boolean workingEnabled;
        private final boolean remote;

        private TestControllableMachine(BlockEntityCreationInfo info, boolean workingEnabled, boolean remote) {
            super(info);
            this.workingEnabled = workingEnabled;
            this.remote = remote;
        }

        @Override
        public boolean isWorkingEnabled() {
            return workingEnabled;
        }

        @Override
        public void setWorkingEnabled(boolean workingEnabled) {
            this.workingEnabled = workingEnabled;
        }

        @Override
        public boolean isRemote() {
            return remote;
        }
    }

    private static final class MutableMachineUIHolder implements MachineUIHolder {

        private final BlockPos pos;
        private final ResourceLocation definitionId;
        @Nullable
        private MetaMachine machine;

        private MutableMachineUIHolder(MetaMachine machine) {
            this.pos = machine.getBlockPos();
            this.definitionId = machine.getDefinition().getId();
            this.machine = machine;
        }

        private void setMachine(@Nullable MetaMachine machine) {
            this.machine = machine;
        }

        @Override
        public BlockPos getPos() {
            return pos;
        }

        @Override
        public ResourceLocation getMachineDefinitionId() {
            return definitionId;
        }

        @Override
        @Nullable
        public MetaMachine getMachine() {
            return machine;
        }
    }
}

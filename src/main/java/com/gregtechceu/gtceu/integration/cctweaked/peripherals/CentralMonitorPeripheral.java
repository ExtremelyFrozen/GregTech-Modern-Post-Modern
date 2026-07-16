package com.gregtechceu.gtceu.integration.cctweaked.peripherals;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.api.item.component.IItemComponent;
import com.gregtechceu.gtceu.api.item.component.IMonitorModuleItem;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.item.datacomponents.TextLineList;
import com.gregtechceu.gtceu.common.item.modules.ImageModuleBehaviour;
import com.gregtechceu.gtceu.common.item.modules.TextModuleBehaviour;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.CentralMonitorTextModuleActions;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor.MonitorGroup;

import net.minecraft.world.item.ItemStack;

import dan200.computercraft.api.lua.*;
import dan200.computercraft.api.peripheral.GenericPeripheral;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class CentralMonitorPeripheral implements GenericPeripheral {

    @Override
    public String id() {
        return "gtpm:central_monitor";
    }

    @LuaFunction(mainThread = true)
    public static MethodResult getGroups(CentralMonitorMachine centralMonitor) {
        return MethodResult.of(centralMonitor.getMonitorGroups().stream()
                .map(group -> new LuaMonitorGroup(centralMonitor, group))
                .toList());
    }

    public static class LuaMonitorGroup {

        private final CentralMonitorMachine centralMonitor;
        private final MonitorGroup group;

        public LuaMonitorGroup(CentralMonitorMachine centralMonitor, MonitorGroup group) {
            this.centralMonitor = centralMonitor;
            this.group = group;
        }

        @LuaFunction
        public String getName() {
            return group.getName();
        }

        @LuaFunction(mainThread = true)
        public LuaMonitorModule getModule() {
            return new LuaMonitorModule(centralMonitor, group, group.getItemStackHandler().getStackInSlot(0));
        }

        // TODO item transfer (setModule, etc.)
    }

    public static class LuaMonitorModule {

        private final CentralMonitorMachine centralMonitor;
        private final ItemStack stack;
        private final UUID groupIdentity;
        private final UUID moduleSlotIncarnation;
        private long expectedTextConfigurationRevision;

        public LuaMonitorModule(CentralMonitorMachine centralMonitor, MonitorGroup group, ItemStack stack) {
            this.centralMonitor = centralMonitor;
            this.stack = stack;
            this.groupIdentity = group.getIdentity();
            this.moduleSlotIncarnation = group.getModuleSlotIncarnation();
            this.expectedTextConfigurationRevision = group.getTextConfigurationRevision();
        }

        private @Nullable IMonitorModuleItem getModuleItem() {
            if (stack.getItem() instanceof ComponentItem componentItem) {
                for (IItemComponent component : componentItem.getComponents()) {
                    if (component instanceof IMonitorModuleItem moduleItem) {
                        return moduleItem;
                    }
                }
            }
            return null;
        }

        @LuaFunction
        public String getType() {
            if (stack.isEmpty()) return "none";
            IMonitorModuleItem moduleItem = getModuleItem();
            if (moduleItem != null) return moduleItem.getType();
            return "invalid";
        }

        @LuaFunction
        public MethodResult getCurrentText() {
            if (getModuleItem() instanceof TextModuleBehaviour textModule) {
                return MethodResult.of(textModule.getText(stack).toString());
            } else return MethodResult.of();
        }

        @LuaFunction(mainThread = true)
        public void setPlaceholderText(String text) throws LuaException {
            if (getModuleItem() instanceof TextModuleBehaviour textModule) {
                TextLineList requestedConfiguration = textModule.createPlaceholderConfiguration(stack, text);
                if (requestedConfiguration.equals(stack.get(GTDataComponents.FORMAT_STRING_LIST.get()))) {
                    return;
                }
                boolean applied = centralMonitor.setCentralMonitorTextModuleConfiguration(
                        groupIdentity,
                        moduleSlotIncarnation,
                        expectedTextConfigurationRevision,
                        CentralMonitorTextModuleActions.captureExpectedModule(stack),
                        requestedConfiguration);
                if (!applied) {
                    GTCEu.LOGGER.warn(
                            "ComputerCraft rejected stale Central Monitor text module configuration for group {}",
                            groupIdentity);
                    throw new LuaException("Central Monitor text module changed before the configuration was applied");
                }
                expectedTextConfigurationRevision = Math.incrementExact(expectedTextConfigurationRevision);
            }
        }

        @LuaFunction
        public MethodResult getScale() {
            if (getModuleItem() instanceof TextModuleBehaviour textModule) {
                return MethodResult.of(textModule.getScale(stack));
            } else return MethodResult.of();
        }

        @LuaFunction
        public void setScale(float scale) {
            if (getModuleItem() instanceof TextModuleBehaviour textModule) {
                textModule.setScale(stack, scale);
            }
        }

        @LuaFunction
        public MethodResult getImageUrl() {
            if (getModuleItem() instanceof ImageModuleBehaviour imageModule) {
                return MethodResult.of(imageModule.getUrl(stack));
            } else return MethodResult.of();
        }

        @LuaFunction
        public void setImageUrl(String url) {
            if (getModuleItem() instanceof ImageModuleBehaviour imageModule) {
                imageModule.setUrl(stack, url);
            }
        }
    }
}

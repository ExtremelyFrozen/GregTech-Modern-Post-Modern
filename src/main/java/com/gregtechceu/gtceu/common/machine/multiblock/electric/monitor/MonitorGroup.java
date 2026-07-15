package com.gregtechceu.gtceu.common.machine.multiblock.electric.monitor;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.capability.IMonitorComponent;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.item.IComponentItem;
import com.gregtechceu.gtceu.api.item.component.IItemComponent;
import com.gregtechceu.gtceu.api.item.component.IMonitorModuleItem;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.utils.GlobalPosWithRot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.UnaryOperator;

public class MonitorGroup {

    private final UUID identity;
    private UUID moduleSlotIncarnation;
    private final Set<BlockPos> monitorPositions = new HashSet<>();
    private final String name;
    private final CustomItemStackHandler itemStackHandler;
    private final CustomItemStackHandler placeholderSlotsHandler;
    private @Nullable BlockPos target;
    private @Nullable Direction targetCoverSide;
    private int dataSlot = 0;

    public static boolean isModule(ItemStack stack) {
        if (stack.getItem() instanceof IComponentItem componentItem) {
            for (IItemComponent itemComponent : componentItem.getComponents()) {
                if (itemComponent instanceof IMonitorModuleItem) return true;
            }
        }
        return false;
    }

    public static CustomItemStackHandler createModuleHandler() {
        CustomItemStackHandler customItemStackHandler = new CustomItemStackHandler(1);
        customItemStackHandler.setFilter(MonitorGroup::isModule);
        customItemStackHandler.setNonMutatingEmptySlotCapacityQueryEnabled(true);
        return customItemStackHandler;
    }

    public MonitorGroup(String name) {
        this(name, createModuleHandler(), new CustomItemStackHandler(8));
    }

    public MonitorGroup(String name, CustomItemStackHandler handler, CustomItemStackHandler placeholderSlotsHandler) {
        this(UUID.randomUUID(), UUID.randomUUID(), name, handler, placeholderSlotsHandler);
    }

    private MonitorGroup(UUID identity, UUID moduleSlotIncarnation, String name, CustomItemStackHandler handler,
                         CustomItemStackHandler placeholderSlotsHandler) {
        this.identity = identity;
        this.moduleSlotIncarnation = moduleSlotIncarnation;
        this.name = name;
        this.itemStackHandler = handler;
        this.itemStackHandler.setFilter(MonitorGroup::isModule);
        this.placeholderSlotsHandler = placeholderSlotsHandler;
    }

    /**
     * Restores a group with the stable identities carried by saved or synchronized data.
     */
    public static MonitorGroup restore(UUID identity, UUID moduleSlotIncarnation, String name,
                                       CustomItemStackHandler handler,
                                       CustomItemStackHandler placeholderSlotsHandler) {
        return new MonitorGroup(identity, moduleSlotIncarnation, name, handler, placeholderSlotsHandler);
    }

    /**
     * Creates a new empty group with an identity already validated by the owning Central Monitor.
     */
    public static MonitorGroup createWithIdentity(UUID identity, String name) {
        return new MonitorGroup(identity, UUID.randomUUID(), name, createModuleHandler(),
                new CustomItemStackHandler(8));
    }

    public Set<BlockPos> getMonitorPositions() {
        return monitorPositions;
    }

    public UUID getIdentity() {
        return identity;
    }

    public UUID getModuleSlotIncarnation() {
        return moduleSlotIncarnation;
    }

    /**
     * Invalidates actions opened for the previous physical module-slot occupant.
     */
    public void rotateModuleSlotIncarnation() {
        moduleSlotIncarnation = UUID.randomUUID();
    }

    public String getName() {
        return name;
    }

    public CustomItemStackHandler getItemStackHandler() {
        return itemStackHandler;
    }

    public CustomItemStackHandler getPlaceholderSlotsHandler() {
        return placeholderSlotsHandler;
    }

    public void setTarget(@Nullable BlockPos target) {
        this.target = target;
    }

    public @Nullable Direction getTargetCoverSide() {
        return targetCoverSide;
    }

    public void setTargetCoverSide(@Nullable Direction targetCoverSide) {
        this.targetCoverSide = targetCoverSide;
    }

    public int getDataSlot() {
        return dataSlot;
    }

    public void setDataSlot(int dataSlot) {
        this.dataSlot = dataSlot;
    }

    /**
     * Atomically replaces the raw target configuration and invalidates the cover side derived from its old target.
     */
    public void setTargetAndDataSlot(@Nullable BlockPos target, int dataSlot) {
        this.target = target;
        this.dataSlot = dataSlot;
        this.targetCoverSide = null;
    }

    public void add(BlockPos pos) {
        monitorPositions.add(pos);
    }

    public void remove(BlockPos pos) {
        monitorPositions.remove(pos);
    }

    public List<BlockPos> getRow(int row, UnaryOperator<BlockPos> toRelative) throws IndexOutOfBoundsException {
        IntSet yLevelsSet = new IntOpenHashSet();
        for (BlockPos pos : monitorPositions) {
            yLevelsSet.add(toRelative.apply(pos).getY());
        }
        if (row < 0) row += yLevelsSet.size();
        int y = yLevelsSet.intStream().sorted().toArray()[row];
        List<BlockPos> rowPositions = new ArrayList<>();
        for (BlockPos pos : monitorPositions) {
            if (toRelative.apply(pos).getY() == y) {
                rowPositions.add(toRelative.apply(pos));
            }
        }
        rowPositions.sort(Comparator.comparingInt(Vec3i::getX));
        return rowPositions;
    }

    public boolean contains(BlockPos pos) {
        return monitorPositions.contains(pos);
    }

    public boolean isEmpty() {
        return monitorPositions.isEmpty();
    }

    public @Nullable CoverBehavior getTargetCover(Level level) {
        if (getTarget(level) != null && targetCoverSide != null) {
            ICoverable coverable = GTCapabilityHelper.getCoverable(level, getTarget(level), targetCoverSide);
            if (coverable != null) return coverable.getCoverAtSide(targetCoverSide);
        }
        return null;
    }

    public @Nullable BlockPos getTargetRaw() {
        return target;
    }

    public @Nullable BlockPos getTarget(Level level) {
        if (target == null) return null;

        IMonitorComponent component = GTCapabilityHelper.getMonitorComponent(level, target, null);
        if (component != null && component.getDataItems() != null) {
            ItemStack stack = component.getDataItems().getStackInSlot(dataSlot);
            GlobalPosWithRot pos = stack.get(GTDataComponents.MONITOR_TARGET);
            if (pos == null) {
                return null;
            }
            Direction face = pos.side();
            setTargetCoverSide(face);
            return pos.pos();
        }
        return target;
    }

    public Level getTargetLevel(Level level) {
        if (target == null) return level;

        IMonitorComponent component = GTCapabilityHelper.getMonitorComponent(level, target, null);
        if (component != null && component.getDataItems() != null) {
            ItemStack stack = component.getDataItems().getStackInSlot(dataSlot);
            GlobalPosWithRot pos = stack.get(GTDataComponents.MONITOR_TARGET);
            if (pos == null) return level;
            if (level.getServer() == null) return level;
            return level.getServer().getLevel(pos.dimension());
        }
        return level;
    }
}

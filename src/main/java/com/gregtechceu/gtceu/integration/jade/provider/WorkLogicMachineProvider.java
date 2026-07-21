package com.gregtechceu.gtceu.integration.jade.provider;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IWorkLogicMachine;
import com.gregtechceu.gtceu.api.machine.trait.WorkLogic;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.ResearchStationMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.jetbrains.annotations.Nullable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.BoxStyle;
import snownee.jade.api.ui.IElementHelper;

public class WorkLogicMachineProvider extends CapabilityBlockProvider<IWorkLogicMachine> {

    private static final String ACTIVE = "Active";
    private static final String PROGRESS = "Progress";
    private static final String MAX_PROGRESS = "MaxProgress";
    private static final String WORKING_ENABLED = "WorkingEnabled";
    private static final String RESEARCH = "Research";
    private static final String WAITING_REASON = "WaitingReason";

    public WorkLogicMachineProvider() {
        super(GTCEu.id("workable_provider"));
    }

    @Nullable
    @Override
    protected IWorkLogicMachine getCapability(Level level, BlockPos pos, @Nullable Direction side) {
        if (level.getBlockEntity(pos) instanceof MetaMachine machine &&
                machine instanceof IWorkLogicMachine workMachine) {
            return workMachine;
        }
        return null;
    }

    @Override
    protected void write(CompoundTag data, IWorkLogicMachine machine) {
        WorkLogic workLogic = machine.getWorkLogic();
        data.putBoolean(ACTIVE, machine.isActive());
        data.putInt(PROGRESS, machine.getProgress());
        data.putInt(MAX_PROGRESS, machine.getMaxProgress());
        data.putBoolean(WORKING_ENABLED, machine.isWorkingEnabled());
        if (machine instanceof ResearchStationMachine) {
            data.putBoolean(RESEARCH, true);
        }
        if (workLogic.isWaiting() && workLogic.getWaitingReason() != null) {
            data.put(WAITING_REASON, ComponentSerialization.CODEC.encodeStart(NbtOps.INSTANCE,
                    workLogic.getWaitingReason()).getOrThrow());
        }
    }

    @Override
    protected void addTooltip(CompoundTag capData, ITooltip tooltip, Player player, BlockAccessor block,
                              BlockEntity blockEntity, IPluginConfig config) {
        if (capData.contains(WAITING_REASON)) {
            tooltip.add(ComponentSerialization.CODEC.parse(NbtOps.INSTANCE, capData.get(WAITING_REASON))
                    .getOrThrow().copy().withStyle(ChatFormatting.YELLOW));
        }

        if (!capData.getBoolean(ACTIVE)) return;

        int currentProgress = capData.getInt(PROGRESS);
        int maxProgress = capData.getInt(MAX_PROGRESS);
        Component text;

        if (capData.getBoolean(RESEARCH)) {
            String current = FormattingUtil.formatNumberReadable(currentProgress);
            String max = FormattingUtil.formatNumberReadable(maxProgress);
            text = Component.translatable("gtpm.jade.progress_computation", current, max);

            tooltip.add(IElementHelper.get().progress(
                    getProgress(currentProgress, maxProgress),
                    text,
                    IElementHelper.get().progressStyle().color(0xFF006D6A).textColor(-1),
                    Util.make(BoxStyle.GradientBorder.DEFAULT_NESTED_BOX,
                            style -> style.borderColor = new int[] { 0xFF555555, 0xFF555555, 0xFF555555,
                                    0xFF555555 }),
                    true));
            return;
        }

        if (maxProgress < 20) {
            text = Component.translatable("gtpm.jade.progress_tick", currentProgress, maxProgress);
        } else {
            text = Component.translatable("gtpm.jade.progress_sec", Math.round(currentProgress / 20.0F),
                    Math.round(maxProgress / 20.0F));
        }

        if (maxProgress > 0) {
            int color = capData.getBoolean(WORKING_ENABLED) ? 0xFF4CBB17 : 0xFFBB1C28;
            tooltip.add(IElementHelper.get().progress(
                    getProgress(currentProgress, maxProgress),
                    text,
                    IElementHelper.get().progressStyle().color(color).textColor(-1),
                    Util.make(BoxStyle.GradientBorder.DEFAULT_NESTED_BOX,
                            style -> style.borderColor = new int[] { 0xFF555555, 0xFF555555, 0xFF555555,
                                    0xFF555555 }),
                    true));
        }
    }
}

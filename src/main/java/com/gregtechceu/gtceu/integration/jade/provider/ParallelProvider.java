package com.gregtechceu.gtceu.integration.jade.provider;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ParallelHatchPartMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class ParallelProvider extends MachineInfoProvider<MetaMachine, CompoundTag> {

    public ParallelProvider() {
        super(GTCEu.id("parallel_info"), MetaMachine.class);
    }

    @Override
    protected CompoundTag write(MetaMachine machine) {
        var data = new CompoundTag();
        if (machine instanceof ParallelHatchPartMachine parallelHatch) {
            data.putInt("parallel", parallelHatch.getCurrentParallel());
        } else if (machine instanceof MultiblockControllerMachine controller) {
            if (controller instanceof IRecipeLogicMachine rlm &&
                    rlm.getWorkLogic().isActive() &&
                    rlm.getRecipeLogic().getLastRecipe() != null) {
                data.putInt("parallel", rlm.getRecipeLogic().getLastRecipe().parallels);
                data.putInt("batch", rlm.getRecipeLogic().getLastRecipe().batchParallels);
                data.putInt("subtickParallel", rlm.getRecipeLogic().getLastRecipe().subtickParallels);
                data.putBoolean("exact", true);
            } else {
                controller.getParallelHatch()
                        .ifPresent(parallelHatch -> data.putInt("parallel",
                                parallelHatch.getCurrentParallel()));
            }
        }
        return data;
    }

    @Override
    protected void addTooltip(CompoundTag data, ITooltip tooltip, Player player, BlockAccessor block,
                              BlockEntity blockEntity, IPluginConfig config) {
        if (data.contains("parallel")) {
            int parallel = data.getInt("parallel");
            if (!data.getBoolean("exact") && parallel > 1) {
                Component parallels = Component.literal(FormattingUtil.formatNumbers(parallel))
                        .withStyle(ChatFormatting.DARK_PURPLE);
                tooltip.add(Component.translatable("gtpm.multiblock.parallel", parallels));
            } else {
                int batch = data.getInt("batch");
                int subtickParallel = data.getInt("subtickParallel");
                int totalRuns = parallel * batch * subtickParallel;
                if (totalRuns == 1) return;
                Component runs = Component.literal(FormattingUtil.formatNumbers(totalRuns))
                        .withStyle(ChatFormatting.DARK_PURPLE);
                tooltip.add(Component.translatable("gtpm.multiblock.total_runs", runs));

                if (parallel > 1) {
                    Component parallels = Component.literal(FormattingUtil.formatNumbers(parallel))
                            .withStyle(ChatFormatting.DARK_PURPLE);
                    tooltip.add(Component.translatable("gtpm.multiblock.parallel.exact", parallels));
                }
                if (batch > 1) {
                    Component batches = Component.literal(FormattingUtil.formatNumbers(batch))
                            .withStyle(ChatFormatting.DARK_PURPLE);
                    tooltip.add(Component.translatable("gtpm.multiblock.batch_enabled", batches));
                }
                if (subtickParallel > 1) {
                    Component subticks = Component.literal(FormattingUtil.formatNumbers(subtickParallel))
                            .withStyle(ChatFormatting.DARK_PURPLE);
                    tooltip.add(Component.translatable("gtpm.multiblock.subtick_parallels", subticks));
                }
            }
        }
    }
}

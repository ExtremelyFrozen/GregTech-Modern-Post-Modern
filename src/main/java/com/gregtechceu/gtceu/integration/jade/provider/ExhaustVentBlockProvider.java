package com.gregtechceu.gtceu.integration.jade.provider;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.machine.trait.ExhaustVentMachineTrait;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.apache.commons.lang3.StringUtils;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;

public class ExhaustVentBlockProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    @Override
    public ResourceLocation getUid() {
        return GTCEu.id("exhaust_vent_info");
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor block, IPluginConfig config) {
        var blockEntity = block.getBlockEntity();
        if (blockEntity == null || !block.getServerData().contains(getUid().toString(), CompoundTag.TAG_COMPOUND)) {
            return;
        }

        ExhaustVentData data = ExhaustVentData.fromTag(block.getServerData().getCompound(getUid().toString()));
        addTooltip(data, tooltip, block.getPlayer(), block, blockEntity, config);
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor blockAccessor) {
        var blockEntity = blockAccessor.getBlockEntity();
        if (blockEntity instanceof MetaMachine machine) {
            ExhaustVentMachineTrait trait = machine.getTrait(ExhaustVentMachineTrait.TYPE);
            if (trait != null) {
                data.put(getUid().toString(), write(trait).toTag());
            }
        }
    }

    private ExhaustVentData write(ExhaustVentMachineTrait trait) {
        var pos = trait.getBlockPos().relative(trait.getVentingDirection());
        var key = BuiltInRegistries.BLOCK.getKey(trait.getLevel().getBlockState(pos).getBlock());
        return new ExhaustVentData(trait.getVentingDirection(), key, trait.isVentingBlocked(), trait.isNeedsVenting());
    }

    private void addTooltip(ExhaustVentData data, ITooltip iTooltip, Player player, BlockAccessor blockAccessor,
                            BlockEntity blockEntity, IPluginConfig iPluginConfig) {
        var direction = data.direction();
        if (direction != null) {
            iTooltip.add(Component.translatable("gtpm.top.exhaust_vent_direction",
                    StringUtils.capitalize(direction.getName())));
            if (!data.blocked()) return;

            if (blockAccessor.showDetails()) {
                var block = BuiltInRegistries.BLOCK.get(data.block()).asItem().getDefaultInstance();
                iTooltip.append(IElementHelper.get().smallItem(block));
            }

            if (data.needsVenting()) {
                iTooltip.append(Component.literal(" ("));
                iTooltip.append(Component.translatable("gtpm.top.exhaust_vent_blocked").withStyle(ChatFormatting.RED)
                        .append(Component.literal(")").withStyle(ChatFormatting.GRAY)));
            }
        }
    }

    private record ExhaustVentData(Direction direction, ResourceLocation block, boolean blocked, boolean needsVenting) {

        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("ventDirection", direction.getName());
            tag.putString("ventBlock", block.toString());
            tag.putBoolean("ventBlocked", blocked);
            tag.putBoolean("needsVenting", needsVenting);
            return tag;
        }

        private static ExhaustVentData fromTag(CompoundTag tag) {
            return new ExhaustVentData(
                    Direction.byName(tag.getString("ventDirection")),
                    ResourceLocation.parse(tag.getString("ventBlock")),
                    tag.getBoolean("ventBlocked"),
                    tag.getBoolean("needsVenting"));
        }
    }
}

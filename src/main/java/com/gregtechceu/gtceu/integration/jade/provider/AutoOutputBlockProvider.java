package com.gregtechceu.gtceu.integration.jade.provider;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import org.apache.commons.lang3.StringUtils;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.IElementHelper;

public class AutoOutputBlockProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {

    @Override
    public ResourceLocation getUid() {
        return GTCEu.id("auto_output_info");
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor block, IPluginConfig config) {
        var blockEntity = block.getBlockEntity();
        if (blockEntity == null || !block.getServerData().contains(getUid().toString(), CompoundTag.TAG_COMPOUND)) {
            return;
        }

        AutoOutputData data = AutoOutputData.fromTag(block.getServerData().getCompound(getUid().toString()));
        addTooltip(data, tooltip, block.getPlayer(), block, blockEntity, config);
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor blockAccessor) {
        var blockEntity = blockAccessor.getBlockEntity();
        if (blockEntity instanceof MetaMachine machine) {
            AutoOutputTrait trait = machine.getTrait(AutoOutputTrait.TYPE);
            if (trait != null) {
                data.put(getUid().toString(), write(trait).toTag());
            }
        }
    }

    private void addTooltip(AutoOutputData data, ITooltip tooltip, Player player, BlockAccessor block,
                            BlockEntity blockEntity, IPluginConfig config) {
        if (data.item() != null) {
            addAutoOutputInfo(tooltip, block, data.item(), "gtpm.top.item_auto_output");
        }

        if (data.fluid() != null) {
            addAutoOutputInfo(tooltip, block, data.fluid(), "gtpm.top.fluid_auto_output");
        }
    }

    private AutoOutputData write(AutoOutputTrait trait) {
        AutoOutputSideData item = null;
        if (trait.supportsAutoOutputItems()) {
            var direction = trait.getItemOutputDirection();
            if (direction != null) {
                item = writeData(direction, trait.getLevel(), trait.getBlockPos(),
                        trait.allowsItemInputFromOutputSide(), trait.isAutoOutputItems());
            }
        }
        AutoOutputSideData fluid = null;
        if (trait.supportsAutoOutputFluids()) {
            var direction = trait.getFluidOutputDirection();
            if (direction != null) {
                fluid = writeData(direction, trait.getLevel(), trait.getBlockPos(),
                        trait.allowsFluidInputFromOutputSide(), trait.isAutoOutputFluids());
            }
        }
        return new AutoOutputData(item, fluid);
    }

    private AutoOutputSideData writeData(Direction direction, Level lvl, BlockPos pos,
                                         boolean allowInput, boolean auto) {
        var key = BuiltInRegistries.BLOCK.getKey(lvl.getBlockState(pos).getBlock());
        return new AutoOutputSideData(direction, key, allowInput, auto);
    }

    private void addAutoOutputInfo(ITooltip iTooltip, BlockAccessor blockAccessor, AutoOutputSideData data,
                                   String text) {
        var direction = data.direction();
        boolean allowInput = data.allowInput();
        boolean auto = data.auto();
        if (direction != null) {
            iTooltip.add(Component.translatable(text, StringUtils.capitalize(direction.getName())));
            if (blockAccessor.showDetails()) {
                var block = BuiltInRegistries.BLOCK.get(data.block()).asItem().getDefaultInstance();
                if (!block.isEmpty()) {
                    iTooltip.append(IElementHelper.get().smallItem(block));
                }
            }

            if (allowInput || auto) {
                var component = Component.literal(" (");
                if (auto) {
                    component.append(Component.translatable("gtpm.top.auto_output"));
                }

                if (allowInput && auto) {
                    component.append("/");
                }

                if (allowInput) {
                    component.append(Component.translatable("gtpm.top.allow_output_input"));
                }
                component.append(")");
                iTooltip.append(component);
            }
        }
    }

    private record AutoOutputData(AutoOutputSideData item, AutoOutputSideData fluid) {

        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            if (item != null) {
                tag.put("autoOutputItem", item.toTag());
            }
            if (fluid != null) {
                tag.put("autoOutputFluid", fluid.toTag());
            }
            return tag;
        }

        private static AutoOutputData fromTag(CompoundTag tag) {
            AutoOutputSideData item = tag.contains("autoOutputItem", CompoundTag.TAG_COMPOUND) ?
                    AutoOutputSideData.fromTag(tag.getCompound("autoOutputItem")) : null;
            AutoOutputSideData fluid = tag.contains("autoOutputFluid", CompoundTag.TAG_COMPOUND) ?
                    AutoOutputSideData.fromTag(tag.getCompound("autoOutputFluid")) : null;
            return new AutoOutputData(item, fluid);
        }
    }

    private record AutoOutputSideData(Direction direction, ResourceLocation block, boolean allowInput, boolean auto) {

        private CompoundTag toTag() {
            CompoundTag tag = new CompoundTag();
            tag.putString("direction", direction.getName());
            tag.putString("block", block.toString());
            tag.putBoolean("allowInput", allowInput);
            tag.putBoolean("auto", auto);
            return tag;
        }

        private static AutoOutputSideData fromTag(CompoundTag tag) {
            Direction direction = Direction.byName(tag.getString("direction"));
            ResourceLocation block = ResourceLocation.parse(tag.getString("block"));
            return new AutoOutputSideData(direction, block, tag.getBoolean("allowInput"), tag.getBoolean("auto"));
        }
    }
}

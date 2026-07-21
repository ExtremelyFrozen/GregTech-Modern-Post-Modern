package com.gregtechceu.gtceu.common.machine.multiblock.primitive;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTProgressBarElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.fluids.FluidUtil;

import java.util.List;

public class CokeOvenMachine extends PrimitiveWorkableMachine implements LDLib2MachineUIProvider {

    public CokeOvenMachine(BlockEntityCreationInfo info) {
        super(info);
    }

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, 176, 166);
        root.style(style -> style.backgroundTexture(GuiTextures.PRIMITIVE_BACKGROUND));
        root.addChild(createLDLib2TitleLabel());
        root.addChild(createLDLib2InputSlot());
        root.addChild(createLDLib2ProgressBar());
        root.addChild(createLDLib2OutputSlot());
        root.addChild(createLDLib2FluidTank());
        root.addChild(UITemplate.bindPlayerInventoryLDLib2(player.getInventory(), GuiTextures.PRIMITIVE_SLOT, 7, 84,
                true));
        return UI.of(root);
    }

    private GTLabelElement createLDLib2TitleLabel() {
        GTLabelElement label = new GTLabelElement(5, 5, 166, 10,
                getBlockState().getBlock().getDescriptionId(), true);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private GTItemSlotElement createLDLib2InputSlot() {
        GTItemSlotElement slot = new GTItemSlotElement(importItems.storage, 0)
                .setBackgroundTexture(GuiTextures.group(GuiTextures.PRIMITIVE_SLOT,
                        GuiTextures.PRIMITIVE_FURNACE_OVERLAY))
                .setCanTakeItems(true)
                .setCanPutItems(true);
        return UITemplate.setLDLib2Bounds(slot, 52, 30, 18, 18);
    }

    private GTProgressBarElement createLDLib2ProgressBar() {
        GTProgressBarElement progress = new GTProgressBarElement(recipeLogic::getProgressPercent)
                .setProgressTexture(
                        GuiTextures.PRIMITIVE_BLAST_FURNACE_PROGRESS_BAR.getSubTexture(0, 0, 1, 0.5),
                        GuiTextures.PRIMITIVE_BLAST_FURNACE_PROGRESS_BAR.getSubTexture(0, 0.5, 1, 0.5));
        return UITemplate.setLDLib2Bounds(progress, 76, 32, 20, 15);
    }

    private GTItemSlotElement createLDLib2OutputSlot() {
        GTItemSlotElement slot = new GTItemSlotElement(exportItems.storage, 0)
                .setBackgroundTexture(GuiTextures.group(GuiTextures.PRIMITIVE_SLOT,
                        GuiTextures.PRIMITIVE_FURNACE_OVERLAY))
                .setCanTakeItems(true)
                .setCanPutItems(false);
        return UITemplate.setLDLib2Bounds(slot, 103, 30, 18, 18);
    }

    private GTFluidSlotElement createLDLib2FluidTank() {
        GTFluidSlotElement tank = new GTFluidSlotElement()
                .setFluidTank(exportFluids, 0)
                .setBackgroundTexture(GuiTextures.PRIMITIVE_LARGE_FLUID_TANK)
                .setShowAmount(false)
                .setAllowClickFilled(false)
                .setAllowClickDrained(false)
                .setOnAddedTooltips(this::addLDLib2FluidAmountTooltip)
                .setContentOverlay(GuiTextures.PRIMITIVE_LARGE_FLUID_TANK_OVERLAY);
        return UITemplate.setLDLib2Bounds(tank, 134, 13, 20, 58);
    }

    private void addLDLib2FluidAmountTooltip(GTFluidSlotElement tank, List<Component> tooltips) {
        int fluidAmount = tank.getFluid().getAmount();
        int capacity = Math.max(tank.getCapacity(), fluidAmount);
        tooltips.add(Component.translatable("gtpm.fluid.amount",
                FormattingUtil.formatNumbers(fluidAmount),
                FormattingUtil.formatNumbers(capacity)));
    }

    @Override
    public void animateTick(RandomSource random) {
        if (this.isActive()) {
            final BlockPos pos = getBlockPos();
            float x = pos.getX() + 0.5F;
            float z = pos.getZ() + 0.5F;

            final var facing = getFrontFacing();
            final float horizontalOffset = GTValues.RNG.nextFloat() * 0.6F - 0.3F;
            final float y = pos.getY() + GTValues.RNG.nextFloat() * 0.375F + 0.3F;

            if (facing.getAxis() == Direction.Axis.X) {
                if (facing.getAxisDirection() == Direction.AxisDirection.POSITIVE) x += 0.52F;
                else x -= 0.52F;
                z += horizontalOffset;
            } else if (facing.getAxis() == Direction.Axis.Z) {
                if (facing.getAxisDirection() == Direction.AxisDirection.POSITIVE) z += 0.52F;
                else z -= 0.52F;
                x += horizontalOffset;
            }
            if (ConfigHolder.INSTANCE.machines.machineSounds && GTValues.RNG.nextDouble() < 0.1) {
                getLevel().playLocalSound(x, y, z, SoundEvents.FURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.0F, 1.0F,
                        false);
            }
            getLevel().addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0, 0, 0);
            getLevel().addParticle(ParticleTypes.FLAME, x, y, z, 0, 0, 0);
        }
    }

    @Override
    public InteractionResult onUseWithItem(ExtendedUseOnContext context) {
        if (!isRemote()) {
            if (super.onUseWithItem(context) == InteractionResult.SUCCESS) {
                return InteractionResult.SUCCESS;
            }
            if (FluidUtil.interactWithFluidHandler(context.getPlayer(), context.getHand(), exportFluids)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        }
        return super.onUseWithItem(context);
    }
}

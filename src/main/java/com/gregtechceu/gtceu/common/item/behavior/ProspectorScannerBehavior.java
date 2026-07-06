package com.gregtechceu.gtceu.common.item.behavior;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IElectricItem;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.ProspectingMapElement;
import com.gregtechceu.gtceu.api.gui.factory.HeldItemUIHolder;
import com.gregtechceu.gtceu.api.gui.misc.ProspectorMode;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.item.component.IAddInformation;
import com.gregtechceu.gtceu.api.item.component.IInteractionItem;
import com.gregtechceu.gtceu.api.item.component.IItemUIFactory;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ProspectorScannerBehavior implements IItemUIFactory, IInteractionItem, IAddInformation {

    private final int radius;
    private final long cost;
    private final ProspectorMode<?>[] modes;

    public ProspectorScannerBehavior(int radius, long cost, ProspectorMode<?>... modes) {
        this.radius = radius + 1;
        this.modes = modes.clone();
        this.cost = cost;
    }

    @NotNull
    public ProspectorMode<?> getMode(ItemStack stack) {
        if (stack == ItemStack.EMPTY) {
            return modes[0];
        }
        return modes[stack.getOrDefault(GTDataComponents.SCANNER_MODE, (byte) 0) % modes.length];
    }

    public void setNextMode(ItemStack stack) {
        stack.update(GTDataComponents.SCANNER_MODE, (byte) 0, mode -> (byte) ((mode + 1) % modes.length));
    }

    public boolean drainEnergy(@NotNull ItemStack stack, boolean simulate) {
        IElectricItem electricItem = GTCapabilityHelper.getElectricItem(stack);
        if (electricItem == null) return false;

        int amount = Math.round(cost * (ConfigHolder.INSTANCE.machines.prospectorEnergyUseMultiplier / 100F));

        return electricItem.discharge(amount, Integer.MAX_VALUE, true, false, simulate) >= amount;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(ItemStack item, Level level, Player player,
                                                  InteractionHand usedHand) {
        if (player.isShiftKeyDown() && modes.length > 1) {
            if (!level.isClientSide) {
                setNextMode(item);
                var mode = getMode(item);
                player.sendSystemMessage(Component.translatable(mode.unlocalizedName));
            }
            return InteractionResultHolder.success(item);
        }
        if (!player.isCreative() && !drainEnergy(item, true)) {
            player.sendSystemMessage(Component.translatable("behavior.prospector.not_enough_energy"));
            return InteractionResultHolder.success(item);
        }
        return IItemUIFactory.super.use(item, level, player, usedHand);
    }

    @Override
    public boolean canCreateLDLib2UI(HeldItemUIHolder holder, Player entityPlayer) {
        return ItemStack.isSameItem(holder.getHeld(), holder.getOpenedStack());
    }

    @Override
    public boolean isLDLib2UIStillValid(HeldItemUIHolder holder, Player entityPlayer) {
        return ItemStack.isSameItem(holder.getHeld(), holder.getOpenedStack());
    }

    @Override
    public UI createLDLib2UI(HeldItemUIHolder holder, Player entityPlayer) {
        var mode = getMode(holder.getHeld());
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, 332, 200);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND));

        var map = new ProspectingMapElement(4, 4, 332 - 8, 200 - 8, radius, mode, 1, holder);
        root.addChild(map);
        root.addChild(createDarkModeButton(map));
        return UI.of(root);
    }

    private static GTButtonElement createDarkModeButton(ProspectingMapElement map) {
        GTButtonElement button = new GTButtonElement(-20, 4, 18, 18);
        button.noText();
        updateDarkModeButton(button, map.isDarkMode());
        button.setOnClick(event -> {
            map.setDarkMode(!map.isDarkMode());
            updateDarkModeButton(button, map.isDarkMode());
        });
        return button;
    }

    private static void updateDarkModeButton(GTButtonElement button, boolean darkMode) {
        button.setButtonTexture(darkModeTexture(darkMode));
    }

    private static IGuiTexture darkModeTexture(boolean darkMode) {
        return GuiTextures.group(GuiTextures.BUTTON,
                GuiTextures.buttonState(GuiTextures.PROGRESS_BAR_SOLAR_STEAM.get(true), !darkMode).copy().scale(0.8f));
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents,
                                TooltipFlag isAdvanced) {
        tooltipComponents.add(Component.translatable("metaitem.prospector.tooltip.radius", radius));
        tooltipComponents.add(Component.translatable("metaitem.prospector.tooltip.modes"));
        for (ProspectorMode<?> mode : modes) {
            tooltipComponents.add(Component.literal(" -").append(Component.translatable(mode.unlocalizedName))
                    .withStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
        }
    }
}

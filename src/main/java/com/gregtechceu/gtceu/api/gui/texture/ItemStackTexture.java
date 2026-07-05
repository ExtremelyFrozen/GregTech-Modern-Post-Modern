package com.gregtechceu.gtceu.api.gui.texture;

import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Item stack texture that cycles through multiple stacks on client ticks.
 */
public class ItemStackTexture extends TransformTexture {

    public ItemStack[] items;
    private int index;
    private int ticks;
    private int color = -1;
    private long lastTick;

    public ItemStackTexture() {
        this(Items.APPLE);
    }

    public ItemStackTexture(ItemStack... itemStacks) {
        this.items = itemStacks;
    }

    public ItemStackTexture(Item... items) {
        this.items = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) {
            this.items[i] = new ItemStack(items[i]);
        }
    }

    public ItemStackTexture setItems(ItemStack... itemStacks) {
        this.items = itemStacks;
        this.index = 0;
        return this;
    }

    @Override
    public ItemStackTexture setColor(int color) {
        this.color = color;
        return this;
    }

    @Override
    public ItemStackTexture copy() {
        var copy = new ItemStackTexture(items);
        copy.color = color;
        copy.copyTransform(this);
        return copy;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void updateTick() {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        long tick = Minecraft.getInstance().level.getGameTime();
        if (tick == lastTick) {
            return;
        }
        lastTick = tick;
        if (items.length > 1 && ++ticks % 20 == 0 && ++index == items.length) {
            index = 0;
        }
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected void drawInternal(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width,
                                float height, float partialTicks) {
        if (items.length == 0) {
            return;
        }
        updateTick();
        if (index >= items.length) {
            index = 0;
        }
        ItemStack itemStack = items[index];
        if (itemStack.isEmpty()) {
            return;
        }
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().scale(width / 16f, height / 16f, 1);
        graphics.pose().translate(x * 16 / width, y * 16 / height, -200);
        DrawerHelper.drawItemStack(graphics, itemStack, 0, 0, color, null);
        graphics.pose().popPose();
    }
}

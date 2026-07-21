package com.gregtechceu.gtceu.api.cover.filter;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTPhantomFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static com.gregtechceu.gtceu.api.gui.UITemplate.setLDLib2Bounds;

public class SimpleFluidFilter implements FluidFilter {

    public static final Codec<SimpleFluidFilter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.fieldOf("is_blacklist").forGetter(val -> val.isBlackList),
            Codec.BOOL.fieldOf("ignore_components").forGetter(val -> val.ignoreNbt),
            FluidStack.OPTIONAL_CODEC.listOf().fieldOf("matches").forGetter(val -> Arrays.stream(val.matches).toList()))
            .apply(instance, SimpleFluidFilter::new));
    @Getter
    protected boolean isBlackList;
    @Getter
    protected boolean ignoreNbt;
    @Getter
    protected FluidStack[] matches = new FluidStack[9];

    protected Consumer<SimpleFluidFilter> itemWriter = filter -> {};
    protected Consumer<SimpleFluidFilter> onUpdated = filter -> itemWriter.accept(filter);

    @Getter
    protected int maxStackSize = 1;

    private final List<GTPhantomFluidSlotElement> ldLib2FluidSlots = new ArrayList<>();

    protected SimpleFluidFilter() {
        Arrays.fill(matches, FluidStack.EMPTY);
    }

    protected SimpleFluidFilter(boolean isBlackList, boolean ignoreNbt, List<FluidStack> matches) {
        this.isBlackList = isBlackList;
        this.ignoreNbt = ignoreNbt;
        this.matches = matches.toArray(FluidStack[]::new);
    }

    public static SimpleFluidFilter loadFilter(ItemStack itemStack) {
        var handler = itemStack.getOrDefault(GTDataComponents.SIMPLE_FLUID_FILTER, new SimpleFluidFilter());
        handler.itemWriter = filter -> itemStack.set(GTDataComponents.SIMPLE_FLUID_FILTER, filter);
        return handler;
    }

    @Override
    public void setOnUpdated(Consumer<FluidFilter> onUpdated) {
        this.onUpdated = filter -> {
            this.itemWriter.accept(filter);
            onUpdated.accept(filter);
        };
    }

    @Override
    public boolean isBlank() {
        return !isBlackList && !ignoreNbt && Arrays.stream(matches).allMatch(FluidStack::isEmpty);
    }

    public void setBlackList(boolean blackList) {
        isBlackList = blackList;
        onUpdated.accept(this);
    }

    public void setIgnoreNbt(boolean ingoreNbt) {
        this.ignoreNbt = ingoreNbt;
        onUpdated.accept(this);
    }

    @Override
    public UIElement openLDLib2Configurator(int x, int y) {
        UIElement group = new UIElement();
        setLDLib2Bounds(group, x, y, 18 * 3 + 25, 18 * 3);
        ldLib2FluidSlots.clear();
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                int index = i * 3 + j;
                group.addChild(createLDLib2MatchSlot(index, i * 18, j * 18));
            }
        }
        group.addChild(createLDLib2ToggleButton(18 * 3 + 5, 0,
                GuiTextures.BUTTON_BLACKLIST, this::isBlackList, this::setBlackList));
        group.addChild(createLDLib2ToggleButton(18 * 3 + 5, 20,
                GuiTextures.BUTTON_FILTER_NBT, this::isIgnoreNbt, this::setIgnoreNbt));
        return group;
    }

    private GTPhantomFluidSlotElement createLDLib2MatchSlot(int index, int x, int y) {
        GTPhantomFluidSlotElement slot = new GTPhantomFluidSlotElement(
                () -> matches[index],
                fluidStack -> syncLDLib2MatchSlot(index, fluidStack),
                () -> maxStackSize);
        ldLib2FluidSlots.add(slot);
        setLDLib2Bounds(slot, x, y, 18, 18);
        return slot;
    }

    private void syncLDLib2MatchSlot(int index, FluidStack fluidStack) {
        matches[index] = fluidStack;
        onUpdated.accept(this);
    }

    private GTToggleButtonElement createLDLib2ToggleButton(int x, int y, IGuiTexture texture,
                                                           BooleanSupplier isPressed,
                                                           Consumer<Boolean> setPressed) {
        return new GTToggleButtonElement(x, y, 20, 20, texture, isPressed, setPressed);
    }

    @Override
    public boolean test(FluidStack other) {
        return testFluidAmount(other) > 0L;
    }

    @Override
    public int testFluidAmount(FluidStack fluidStack) {
        int totalFluidAmount = getTotalConfiguredFluidAmount(fluidStack);

        if (isBlackList) {
            return (totalFluidAmount > 0) ? 0 : Integer.MAX_VALUE;
        }

        return totalFluidAmount;
    }

    public int getTotalConfiguredFluidAmount(FluidStack fluidStack) {
        int totalAmount = 0;

        for (var candidate : matches) {
            if (ignoreNbt) {
                if (FluidStack.isSameFluid(candidate, fluidStack)) totalAmount += candidate.getAmount();
            } else {
                if (FluidStack.isSameFluidSameComponents(candidate, fluidStack)) totalAmount += candidate.getAmount();
            }
        }

        return totalAmount;
    }

    public void setMaxStackSize(int maxStackSize) {
        this.maxStackSize = maxStackSize;

        for (FluidStack match : matches) {
            if (!match.isEmpty())
                match.setAmount(Math.min(match.getAmount(), maxStackSize));
        }

        for (GTPhantomFluidSlotElement slot : ldLib2FluidSlots) {
            slot.refreshFromSupplier();
        }
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SimpleFluidFilter that)) return false;

        return isBlackList == that.isBlackList && ignoreNbt == that.ignoreNbt && Arrays.equals(matches, that.matches);
    }

    @Override
    public int hashCode() {
        int result = Boolean.hashCode(isBlackList);
        result = 31 * result + Boolean.hashCode(ignoreNbt);
        result = 31 * result + Arrays.hashCode(matches);
        return result;
    }
}

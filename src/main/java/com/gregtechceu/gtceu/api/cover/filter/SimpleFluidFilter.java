package com.gregtechceu.gtceu.api.cover.filter;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTFluidSlotElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.widget.ScrollablePhantomFluidWidget;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

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

    private CustomFluidTank[] fluidStorageSlots = new CustomFluidTank[9];
    private final List<LDLib2PhantomFluidSlot> ldLib2FluidSlots = new ArrayList<>();

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

    public WidgetGroup openConfigurator(int x, int y) {
        WidgetGroup group = new WidgetGroup(x, y, 18 * 3 + 25, 18 * 3); // 80 55
        fluidStorageSlots = new CustomFluidTank[9];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                final int index = i * 3 + j;

                fluidStorageSlots[index] = new CustomFluidTank(maxStackSize);
                fluidStorageSlots[index].setFluid(matches[index]);

                var tank = new ScrollablePhantomFluidWidget(fluidStorageSlots[index], 0, i * 18, j * 18, 18, 18,
                        () -> fluidStorageSlots[index].getFluid(),
                        (fluid) -> fluidStorageSlots[index].setFluid(fluid)) {

                    @Override
                    public void updateScreen() {
                        super.updateScreen();
                        setShowAmount(maxStackSize > 1L);
                    }

                    @Override
                    public void detectAndSendChanges() {
                        super.detectAndSendChanges();
                        setShowAmount(maxStackSize > 1L);
                    }
                };

                tank.setChangeListener(() -> {
                    matches[index] = fluidStorageSlots[index].getFluidInTank(0);
                    onUpdated.accept(this);
                }).setBackground(GuiTextures.SLOT);

                group.addWidget(tank);
            }
        }
        group.addWidget(new ToggleButtonWidget(18 * 3 + 5, 0, 20, 20,
                GuiTextures.BUTTON_BLACKLIST, this::isBlackList, this::setBlackList));
        group.addWidget(new ToggleButtonWidget(18 * 3 + 5, 20, 20, 20,
                GuiTextures.BUTTON_FILTER_NBT, this::isIgnoreNbt, this::setIgnoreNbt));
        return group;
    }

    @Override
    public boolean supportsLDLib2Configurator() {
        return true;
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

    private GTFluidSlotElement createLDLib2MatchSlot(int index, int x, int y) {
        LDLib2PhantomFluidSlot slot = new LDLib2PhantomFluidSlot(index);
        ldLib2FluidSlots.add(slot);
        setLDLib2Bounds(slot, x, y, 18, 18);
        return slot;
    }

    private void syncLDLib2MatchSlot(int index, FluidStack fluidStack) {
        matches[index] = normalizeLDLib2Match(fluidStack);
        onUpdated.accept(this);
    }

    private FluidStack normalizeLDLib2Match(FluidStack fluidStack) {
        if (fluidStack.isEmpty() || maxStackSize <= 0) {
            return FluidStack.EMPTY;
        }
        FluidStack normalized = fluidStack.copy();
        normalized.setAmount(Math.min(normalized.getAmount(), maxStackSize));
        return normalized;
    }

    private void adjustLDLib2FluidAmount(LDLib2PhantomFluidSlot slot, UIEvent event) {
        FluidStack current = slot.getFluid();
        if (current.isEmpty() || event.deltaY == 0) {
            return;
        }
        int delta = getLDLib2ModifiedChangeAmount(event.deltaY > 0 ? 1 : -1, event);
        int amount = Math.min(Math.max(current.getAmount() + delta, 0), maxStackSize);
        if (amount <= 0) {
            slot.setFluid(FluidStack.EMPTY);
        } else {
            FluidStack adjusted = current.copy();
            adjusted.setAmount(amount);
            slot.setFluid(adjusted);
        }
        event.stopPropagation();
    }

    private int getLDLib2ModifiedChangeAmount(int amount, UIEvent event) {
        if (event.isShiftDown()) {
            amount *= 10;
        }
        if (event.isCtrlDown()) {
            amount *= 100;
        }
        if (!event.isAltDown()) {
            amount *= 1000;
        }
        return amount;
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

        for (CustomFluidTank slot : fluidStorageSlots) {
            if (slot != null)
                slot.setCapacity(maxStackSize);
        }

        for (FluidStack match : matches) {
            if (!match.isEmpty())
                match.setAmount(Math.min(match.getAmount(), maxStackSize));
        }

        for (LDLib2PhantomFluidSlot slot : ldLib2FluidSlots) {
            slot.refreshFromMatch();
        }
    }

    private final class LDLib2PhantomFluidSlot extends GTFluidSlotElement {

        private final int index;
        private boolean suppressUpdate = true;

        private LDLib2PhantomFluidSlot(int index) {
            this.index = index;
            setBackgroundTexture(GuiTextures.SLOT);
            refreshFromMatch();
            suppressUpdate = false;
            xeiPhantom();
            addEventListener(UIEvents.MOUSE_WHEEL, event -> adjustLDLib2FluidAmount(this, event));
        }

        @Override
        public GTFluidSlotElement setFluid(FluidStack fluid) {
            FluidStack normalized = normalizeLDLib2Match(fluid);
            super.setFluid(normalized);
            if (!suppressUpdate) {
                syncLDLib2MatchSlot(index, normalized);
            }
            return this;
        }

        private void refreshFromMatch() {
            suppressUpdate = true;
            setCapacity(maxStackSize);
            setShowAmount(maxStackSize > 1);
            setFluid(matches[index]);
            suppressUpdate = false;
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

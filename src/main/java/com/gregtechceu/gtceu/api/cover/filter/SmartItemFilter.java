package com.gregtechceu.gtceu.api.cover.filter;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2IntOpenCustomHashMap;

import java.util.Collections;
import java.util.function.Consumer;

import static com.gregtechceu.gtceu.api.gui.UITemplate.setLDLib2Bounds;

public class SmartItemFilter implements ItemFilter {

    protected Consumer<SmartItemFilter> itemWriter = filter -> {};
    protected Consumer<SmartItemFilter> onUpdated = filter -> itemWriter.accept(filter);

    private SmartFilteringMode filterMode;

    private SmartItemFilter(SmartFilteringMode filterMode) {
        this.filterMode = filterMode;
    }

    public static SmartItemFilter loadFilter(ItemStack itemStack) {
        SmartFilteringMode mode = itemStack.getOrDefault(GTDataComponents.SMART_ITEM_FILTER,
                SmartFilteringMode.ELECTROLYZER);
        SmartItemFilter handler = new SmartItemFilter(mode);
        handler.itemWriter = filter -> itemStack.set(GTDataComponents.SMART_ITEM_FILTER, filter.filterMode);
        return handler;
    }

    @Override
    public void setOnUpdated(Consumer<ItemFilter> onUpdated) {
        this.onUpdated = filter -> {
            this.itemWriter.accept(filter);
            onUpdated.accept(filter);
        };
    }

    private void setFilterMode(SmartFilteringMode filterMode) {
        this.filterMode = filterMode;
        onUpdated.accept(this);
    }

    @Override
    public boolean supportsLDLib2Configurator() {
        return true;
    }

    @Override
    public UIElement openLDLib2Configurator(int x, int y) {
        UIElement group = new UIElement();
        setLDLib2Bounds(group, x, y, 18 * 3 + 25, 18 * 3);
        group.addChild(createLDLib2ModeButton(16, 8));
        return group;
    }

    private GTButtonElement createLDLib2ModeButton(int x, int y) {
        GTButtonElement button = new GTButtonElement();
        button.noText();
        updateLDLib2ModeButtonTexture(button);
        button.setOnClick(event -> {
            setFilterMode(nextMode());
            updateLDLib2ModeButtonTexture(button);
        });
        setLDLib2Bounds(button, x, y, 32, 32);
        return button;
    }

    private void updateLDLib2ModeButtonTexture(GTButtonElement button) {
        IGuiTexture texture = GuiTextures.group(GuiTextures.VANILLA_BUTTON, filterMode.getIcon());
        button.setButtonTexture(texture);
    }

    private SmartFilteringMode nextMode() {
        for (int i = 0; i < SmartFilteringMode.VALUES.length; i++) {
            if (SmartFilteringMode.VALUES[i] == filterMode) {
                return SmartFilteringMode.VALUES[(i + 1) % SmartFilteringMode.VALUES.length];
            }
        }
        throw new IllegalStateException("Unknown smart filtering mode: " + filterMode);
    }

    @Override
    public boolean test(ItemStack itemStack) {
        return testItemCount(itemStack) > 0;
    }

    @Override
    public int testItemCount(ItemStack itemStack) {
        return filterMode.cache.computeIfAbsent(itemStack, this::lookup);
    }

    private int lookup(ItemStack itemStack) {
        ItemStack copy = itemStack.copyWithCount(Integer.MAX_VALUE);
        var recipe = filterMode.recipeType.db()
                .find(Collections.singletonMap(ItemRecipeCapability.CAP, Collections.singletonList(copy)), r -> true);
        if (recipe == null) {
            return 0;
        }
        for (Content content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
            var stacks = ItemRecipeCapability.CAP.of(content.getContent()).getItems();
            for (var stack : stacks) {
                if (ItemStack.isSameItem(stack, itemStack)) return stack.getCount();
            }
        }
        return 0;
    }

    public void setModeFromMachine(String machineName) {
        for (SmartFilteringMode mode : SmartFilteringMode.VALUES) {
            if (machineName.contains(mode.name)) {
                setFilterMode(mode);
                return;
            }
        }
    }

    public enum SmartFilteringMode implements StringRepresentable {

        ELECTROLYZER("electrolyzer", GTRecipeTypes.ELECTROLYZER_RECIPES),
        CENTRIFUGE("centrifuge", GTRecipeTypes.CENTRIFUGE_RECIPES),
        SIFTER("sifter", GTRecipeTypes.SIFTER_RECIPES);

        public static final Codec<SmartFilteringMode> CODEC = StringRepresentable.fromEnum(SmartFilteringMode::values);
        private static final SmartFilteringMode[] VALUES = values();
        private final String name;
        private final GTRecipeType recipeType;
        private final Object2IntOpenCustomHashMap<ItemStack> cache = new Object2IntOpenCustomHashMap<>(
                ItemStackHashStrategy.comparingAllButCount());

        SmartFilteringMode(String name, GTRecipeType type) {
            this.name = name;
            this.recipeType = type;
        }

        public String getTooltip() {
            return "cover.smart_item_filter.filtering_mode." + name;
        }

        public IGuiTexture getIcon() {
            return GuiTextures.resource("gtpm:textures/block/machines/" + name + "/overlay_front.png");
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }
}

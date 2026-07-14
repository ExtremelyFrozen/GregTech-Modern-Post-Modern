package com.gregtechceu.gtceu.integration.ae2.gui.element;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;

import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.pattern.EncodedPatternItem;
import appeng.items.misc.WrappedGenericStack;

/**
 * Item-handler slot that keeps an encoded AE2 pattern as its value while rendering the pattern's primary output.
 */
public final class AEPatternViewSlotElement extends GTItemSlotElement {

    private final Level level;

    /**
     * Binds one encoded-pattern slot and routes authoritative handler changes back to the owning Pattern Buffer.
     */
    public AEPatternViewSlotElement(IItemHandlerModifiable patternInventory, int slotIndex, Level level,
                                    Runnable changeListener) {
        super(patternInventory, slotIndex);
        this.level = level;
        setCanPlace(stack -> stack.getItem() instanceof EncodedPatternItem<?>);
        setCanTakeItems(true);
        setChangeListener(changeListener);
        setBackgroundTexture(GuiTextures.SLOT);
        slotStyle(style -> style
                .slotOverlay(GuiTextures.PATTERN_OVERLAY)
                .showSlotOverlayOnlyEmpty(true));
    }

    /**
     * Returns the stack rendered for the current encoded pattern without replacing the real slot value.
     */
    public ItemStack getDisplayStack() {
        return displayStack(getValue());
    }

    @Override
    protected void drawItemStack(GUIContext guiContext, ItemStack itemStack) {
        super.drawItemStack(guiContext, displayStack(itemStack));
    }

    private ItemStack displayStack(ItemStack pattern) {
        if (!(pattern.getItem() instanceof EncodedPatternItem<?>)) {
            return pattern;
        }
        var details = PatternDetailsHelper.decodePattern(pattern, level);
        if (details == null) {
            return pattern;
        }
        var output = details.getPrimaryOutput();
        return output.what() instanceof AEItemKey itemKey ?
                itemKey.toStack() : WrappedGenericStack.wrap(output.what(), 0);
    }
}

package com.gregtechceu.gtceu.api.gui.element;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemHandlerRoute;
import com.gregtechceu.gtceu.api.gui.slot.DynamicItemSlotBinding;
import com.gregtechceu.gtceu.api.transfer.item.NonMutatingItemCapacity;

import com.lowdragmc.lowdraglib2.gui.slot.ItemHandlerSlot;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Item slot whose container interaction is enabled only after its dynamic binding is acknowledged.
 *
 * <p>
 * The element remains bound after it becomes a tombstone so its vanilla slot id is never reused.
 * </p>
 */
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public final class GTDynamicItemSlotElement extends GTItemSlotElement {

    private final DynamicItemHandlerRoute handlerRoute;
    private final int routeSlotIndex;
    private boolean protocolInteractionEnabled;

    /**
     * Creates a disabled slot for the supplied routed handler position.
     */
    public GTDynamicItemSlotElement(DynamicItemHandlerRoute handlerRoute, int slotIndex) {
        validateSlotIndex(handlerRoute, slotIndex);
        this.handlerRoute = handlerRoute;
        this.routeSlotIndex = slotIndex;
        super.bind(new DynamicItemHandlerSlot(handlerRoute, slotIndex, this::isInteractionEnabled));
    }

    /**
     * Enables or disables every vanilla interaction path for this physical menu slot.
     */
    public GTDynamicItemSlotElement setInteractionEnabled(boolean interactionEnabled) {
        this.protocolInteractionEnabled = interactionEnabled;
        return this;
    }

    /**
     * Returns whether the acknowledged binding may currently participate in container interaction.
     */
    public boolean isInteractionEnabled() {
        if (!protocolInteractionEnabled) {
            return false;
        }
        UIElement element = this;
        while (element != null) {
            if (!element.isActive() || !element.isVisible() || !element.isDisplayed() ||
                    element.getStyle().opacity() <= 0) {
                return false;
            }
            element = element.getParent();
        }
        return true;
    }

    /** Returns whether this element's immutable UUID route matches one exact manifest range position. */
    boolean matchesBindingRoute(DynamicItemSlotBinding binding, int bindingOffset) {
        return handlerRoute.getTargetId().equals(binding.targetId()) &&
                handlerRoute.getSlots() == binding.slotCount() && routeSlotIndex == bindingOffset;
    }

    private static void validateSlotIndex(DynamicItemHandlerRoute handlerRoute, int slotIndex) {
        if (handlerRoute == null) {
            GTCEu.LOGGER.error("Cannot create a dynamic GTM item slot without a UUID handler route");
            throw new IllegalArgumentException("handlerRoute must not be null");
        }
        if (slotIndex < 0 || slotIndex >= handlerRoute.getSlots()) {
            GTCEu.LOGGER.error("Invalid dynamic GTM item slot index {} for handler with {} slots",
                    slotIndex, handlerRoute.getSlots());
            throw new IllegalArgumentException("Invalid dynamic item slot index: " + slotIndex);
        }
    }

    private static final class DynamicItemHandlerSlot extends ItemHandlerSlot {

        private final int slotIndex;
        private final BooleanSupplier interactionEnabled;

        private DynamicItemHandlerSlot(DynamicItemHandlerRoute itemHandler, int slotIndex,
                                       BooleanSupplier interactionEnabled) {
            super(itemHandler, slotIndex);
            this.slotIndex = slotIndex;
            this.interactionEnabled = interactionEnabled;
        }

        @Override
        public boolean isActive() {
            return interactionEnabled.getAsBoolean() && super.isActive();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return interactionEnabled.getAsBoolean() && super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(@Nullable Player player) {
            return interactionEnabled.getAsBoolean() && super.mayPickup(player);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            if (getItemHandler() instanceof NonMutatingItemCapacity itemCapacity &&
                    itemCapacity.isNonMutatingEmptySlotCapacityQueryEnabled()) {
                return itemCapacity.getMaxStackSizeForEmptySlot(slotIndex, stack);
            }
            return super.getMaxStackSize(stack);
        }
    }
}

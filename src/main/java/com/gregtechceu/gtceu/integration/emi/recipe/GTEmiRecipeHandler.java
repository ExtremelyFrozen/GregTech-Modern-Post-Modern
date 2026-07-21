package com.gregtechceu.gtceu.integration.emi.recipe;

import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.integration.xei.GTXEIHelper;
import com.gregtechceu.gtceu.integration.xei.GTXEIIngredientRoleLDLib2Adapter;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;

import net.minecraft.world.inventory.Slot;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.StandardRecipeHandler;

import java.util.List;

public class GTEmiRecipeHandler implements StandardRecipeHandler<ModularUIContainerMenu> {

    @Override
    public List<Slot> getInputSources(ModularUIContainerMenu handler) {
        return handler.slots.stream()
                .filter(slot -> isRecipeInput(handler, slot) || isPlayerSlot(handler, slot))
                .toList();
    }

    @Override
    public List<Slot> getCraftingSlots(ModularUIContainerMenu handler) {
        return handler.slots.stream()
                .filter(slot -> isRecipeInput(handler, slot))
                .toList();
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return recipe instanceof GTLDLib2EmiRecipe;
    }

    private boolean isRecipeInput(ModularUIContainerMenu handler, Slot slot) {
        GTItemSlotElement itemSlot = getGTItemSlot(handler, slot);
        return itemSlot != null &&
                GTXEIHelper.isInput(GTXEIIngredientRoleLDLib2Adapter.fromLDLib2(itemSlot.getIngredientIO()));
    }

    private boolean isPlayerSlot(ModularUIContainerMenu handler, Slot slot) {
        if (slot.container == handler.inventory) {
            return true;
        }
        ItemSlot itemSlot = handler.asModularUIHolderMenu().getItemSlot(slot);
        return itemSlot != null && itemSlot.getSlotStyle().isPlayerSlot();
    }

    private GTItemSlotElement getGTItemSlot(ModularUIContainerMenu handler, Slot slot) {
        ItemSlot itemSlot = handler.asModularUIHolderMenu().getItemSlot(slot);
        return itemSlot instanceof GTItemSlotElement gtItemSlot ? gtItemSlot : null;
    }
}

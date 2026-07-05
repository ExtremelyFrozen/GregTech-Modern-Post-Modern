package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IDataAccessMachine;
import com.gregtechceu.gtceu.api.capability.IMonitorComponent;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.feature.IDataInfoProvider;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipeDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.item.behavior.PortableScannerBehavior;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.DataBankMachine;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;
import com.gregtechceu.gtceu.utils.ResearchManager;

import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.IItemHandler;

import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class DataAccessHatchMachine extends TieredPartMachine
                                    implements IDataAccessMachine, IDataInfoProvider, IMonitorComponent {

    private final Set<GTRecipeDefinition> recipeDefinitions;
    @Getter
    private final boolean isCreative;
    @SaveField
    public final NotifiableItemStackHandler importItems;

    public DataAccessHatchMachine(BlockEntityCreationInfo info, int tier, boolean isCreative) {
        super(info, tier);
        this.isCreative = isCreative;
        this.recipeDefinitions = isCreative ? Collections.emptySet() : new ObjectOpenHashSet<>();
        this.importItems = attachTrait(createImportItemHandler());
    }

    protected NotifiableItemStackHandler createImportItemHandler() {
        if (isCreative) return new NotifiableItemStackHandler(0, IO.BOTH);
        return new NotifiableItemStackHandler(getInventorySize(), IO.BOTH) {

            @Override
            public void onContentsChanged() {
                super.onContentsChanged();
                rebuildData(isFormed() && getControllers().first() instanceof DataBankMachine);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (acceptsDataItem(stack)) {
                    return super.insertItem(slot, stack, simulate);
                }
                return stack;
            }

            @Override
            public void setStackInSlot(int index, ItemStack stack) {
                if (!stack.isEmpty() && !acceptsDataItem(stack)) {
                    throw new IllegalArgumentException("Data access hatch received an invalid data item: " + stack);
                }
                super.setStackInSlot(index, stack);
            }
        };
    }

    private boolean acceptsDataItem(ItemStack stack) {
        boolean isDataBank = isFormed() && getControllers().first() instanceof DataBankMachine;
        return ResearchManager.isStackDataItem(stack, isDataBank) &&
                (stack.has(GTDataComponents.RESEARCH_ITEM) || stack.has(GTDataComponents.MONITOR_TARGET));
    }

    @Override
    public Widget createUIWidget() {
        int rowSize = (int) Math.sqrt(getInventorySize());
        int xOffset = 18 * rowSize / 2;
        WidgetGroup group = new WidgetGroup(0, 0, 18 * rowSize, 18 * rowSize);

        for (int y = 0; y < rowSize; y++) {
            for (int x = 0; x < rowSize; x++) {
                int index = y * rowSize + x;
                group.addWidget(new SlotWidget(importItems, index,
                        rowSize * 9 + x * 18 - xOffset, y * 18, true, true)
                        .setBackgroundTexture(GuiTextures.SLOT));
            }
        }
        return group;
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return !this.isCreative;
    }

    protected int getInventorySize() {
        return switch (getTier()) {
            case GTValues.LuV -> 16;
            case GTValues.EV -> 9;
            case GTValues.HV -> 4;
            default -> 1;
        };
    }

    private void rebuildData(boolean isDataBank) {
        if (isCreative || getLevel() == null || getLevel().isClientSide) return;
        recipeDefinitions.clear();
        for (int i = 0; i < this.importItems.getSlots(); i++) {
            ItemStack stack = this.importItems.getStackInSlot(i);
            ResearchManager.ResearchItem researchData = stack.get(GTDataComponents.RESEARCH_ITEM);
            boolean isValid = ResearchManager.isStackDataItem(stack, isDataBank);
            if (researchData != null && isValid) {
                Collection<GTRecipeDefinition> collection = researchData.recipeType()
                        .getDataStickEntry(researchData.researchId());
                if (collection != null) {
                    recipeDefinitions.addAll(collection);
                }
            }
        }
        notifyDataAccessControllers();
    }

    @Override
    public boolean isRecipeAvailable(@NotNull GTRecipeType recipeType, @NotNull ResourceLocation recipeId) {
        return isCreative ||
                recipeDefinitions.stream().anyMatch(definition -> isSameRecipe(definition, recipeType, recipeId));
    }

    private static boolean isSameRecipe(GTRecipeDefinition definition, GTRecipeType recipeType,
                                        ResourceLocation recipeId) {
        return definition.recipeType == recipeType &&
                definition.getId() != null &&
                definition.getId().equals(recipeId);
    }

    @Override
    public List<Component> getDataInfo(PortableScannerBehavior.DisplayMode mode) {
        if (mode == PortableScannerBehavior.DisplayMode.SHOW_ALL ||
                mode == PortableScannerBehavior.DisplayMode.SHOW_RECIPE_INFO) {
            if (recipeDefinitions.isEmpty())
                return Collections.emptyList();
            List<Component> list = new ArrayList<>();

            list.add(Component.translatable("behavior.data_item.title"));
            list.add(Component.empty());
            Collection<ItemStack> itemsAdded = new ObjectOpenCustomHashSet<>(ItemStackHashStrategy.comparingAll());
            for (GTRecipeDefinition recipe : recipeDefinitions) {
                ItemStack stack = ItemRecipeCapability.CAP
                        .of(recipe.getOutputContents(ItemRecipeCapability.CAP).getFirst().content).getItems()[0];
                if (!itemsAdded.contains(stack)) {
                    itemsAdded.add(stack);
                    list.add(Component.translatable("behavior.data_item.data", stack.getDisplayName()));
                }
            }
            return list;
        }
        return new ArrayList<>();
    }

    @Override
    public boolean canShared(MultiblockControllerMachine controller, String structureName) {
        return isCreative;
    }

    @Override
    public void addedToController(MultiblockControllerMachine controller, String structureName) {
        rebuildData(controller instanceof DataBankMachine);
        super.addedToController(controller, structureName);
    }

    @Override
    public IGuiTexture getComponentIcon() {
        return GuiTextures.resource(GTCEu.id("textures/item/data_module.png")).getSubTexture(0, 0, 1, 1 / 13f);
    }

    @Override
    public IItemHandler getDataItems() {
        return importItems.storage;
    }

    private void notifyDataAccessControllers() {
        if (!isFormed()) return;
        for (MultiblockControllerMachine controller : getControllers()) {
            if (controller instanceof IDataAccessMachine dataAccessMachine) {
                dataAccessMachine.notifyListeners();
            } else if (controller instanceof IRecipeLogicMachine recipeLogicMachine) {
                recipeLogicMachine.getRecipeLogic().onRecipeHandlerChanged();
            }
        }
    }
}

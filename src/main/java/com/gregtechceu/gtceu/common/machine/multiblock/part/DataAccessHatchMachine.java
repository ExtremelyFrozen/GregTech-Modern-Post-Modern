package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IDataAccessMachine;
import com.gregtechceu.gtceu.api.capability.IMonitorComponent;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.IFancyTooltip;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTabsElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyTooltipsPanelElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.fancyconfigurator.LDLib2DirectionalFancyConfigurator;
import com.gregtechceu.gtceu.api.machine.feature.IDataInfoProvider;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
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

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DataAccessHatchMachine extends TieredPartMachine
                                    implements LDLib2MachineUIProvider, LDLib2FancyPartUIProvider,
                                    IDataAccessMachine, IDataInfoProvider, IMonitorComponent {

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
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this && !isCreative;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        int rowSize = (int) Math.sqrt(getInventorySize());
        int rootWidth = 176;
        int rootHeight = 18 + 18 * rowSize + 94;

        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, rootWidth, rootHeight);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND));
        root.addChild(createLDLib2TitleLabel(rootWidth));
        root.addChild(UITemplate.bindPlayerInventoryLDLib2(player.getInventory(), GuiTextures.SLOT, 7,
                18 + 18 * rowSize + 12, true));

        for (int y = 0; y < rowSize; y++) {
            for (int x = 0; x < rowSize; x++) {
                int index = y * rowSize + x;
                root.addChild(createLDLib2DataSlot(index, 88 - rowSize * 9 + x * 18, 18 + y * 18));
            }
        }
        return UI.of(root);
    }

    /** Creates one holder-scoped inventory page for a surrounding multiblock controller. */
    @Override
    public LDLib2FancyUIProvider createLDLib2FancyPage(Player player, MachineUIHolder holder) {
        if (isCreative) {
            throw new IllegalStateException(
                    "Creative Data Access Hatch is excluded from controller contextual pages.");
        }
        requireMatchingLDLib2Holder(holder);
        return new DataAccessHatchLDLib2Page(player, holder);
    }

    private void requireMatchingLDLib2Holder(MachineUIHolder holder) {
        if (holder.getMachine() != this) {
            throw new IllegalArgumentException(
                    "Data Access Hatch contextual page holder must resolve the opened hatch.");
        }
    }

    private UIElement createLDLib2ContextualMainElement() {
        int rowSize = (int) Math.sqrt(getInventorySize());
        int pageSize = rowSize * 18;
        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, pageSize, pageSize);
        for (int y = 0; y < rowSize; y++) {
            for (int x = 0; x < rowSize; x++) {
                int index = y * rowSize + x;
                root.addChild(createLDLib2DataSlot(index, x * 18, y * 18));
            }
        }
        return root;
    }

    private GTLabelElement createLDLib2TitleLabel(int rootWidth) {
        GTLabelElement label = new GTLabelElement(10, 5, rootWidth - 20, 10,
                getBlockState().getBlock().getDescriptionId(), true);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private GTItemSlotElement createLDLib2DataSlot(int index, int x, int y) {
        GTItemSlotElement slot = new GTItemSlotElement(importItems, index)
                .setBackgroundTexture(GuiTextures.SLOT)
                .setCanPutItems(true)
                .setCanTakeItems(true);
        UITemplate.setLDLib2Bounds(slot, x, y, 18, 18);
        return slot;
    }

    /** Keeps contextual inventory state and actions scoped to one validated menu opening. */
    private final class DataAccessHatchLDLib2Page implements LDLib2FancyUIProvider {

        private final MachineUIHolder holder;
        private final LDLib2DirectionalFancyConfigurator directionalPage;

        private DataAccessHatchLDLib2Page(Player player, MachineUIHolder holder) {
            requireMatchingLDLib2Holder(holder);
            this.holder = holder;
            this.directionalPage = new LDLib2DirectionalFancyConfigurator(
                    DataAccessHatchMachine.this, player, holder);
        }

        @Override
        public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
            if (holder.getMachine() != DataAccessHatchMachine.this) {
                throw new IllegalStateException(
                        "Data Access Hatch contextual page holder no longer resolves its opened hatch.");
            }
            return createLDLib2ContextualMainElement();
        }

        @Override
        public IGuiTexture getTabIcon() {
            return GuiTextures.itemStack(getDefinition().getItem());
        }

        @Override
        public Component getTitle() {
            return Component.translatable(getDefinition().getDescriptionId());
        }

        @Override
        public int getLDLib2PageWidth() {
            return (int) Math.sqrt(getInventorySize()) * 18;
        }

        @Override
        public int getLDLib2PageHeight() {
            return getLDLib2PageWidth();
        }

        @Override
        public void attachSideTabs(LDLib2FancyTabsElement tabs) {
            tabs.attachSubTab(directionalPage);
        }

        @Override
        public void attachTooltips(LDLib2FancyTooltipsPanelElement tooltipsPanel) {
            tooltipsPanel.attachTooltips(DataAccessHatchMachine.this);
            getTraitHolder().getAllTraits().stream()
                    .filter(IFancyTooltip.class::isInstance)
                    .map(IFancyTooltip.class::cast)
                    .forEach(tooltipsPanel::attachTooltips);
        }

        @Override
        public List<Component> getTabTooltips() {
            return List.of(Component.translatable(getDefinition().getDescriptionId()));
        }
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

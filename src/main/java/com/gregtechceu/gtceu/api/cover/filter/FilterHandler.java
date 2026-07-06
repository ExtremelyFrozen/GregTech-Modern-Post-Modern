package com.gregtechceu.gtceu.api.cover.filter;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.widget.SlotWidget;
import com.gregtechceu.gtceu.api.machine.MachineCoverContainer;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncDataHolder;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.sync_system.managed.ISyncManaged;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;

import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.world.item.ItemStack;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public abstract class FilterHandler<T, F extends Filter<T, F>> implements ISyncManaged {

    @Getter
    private final SyncDataHolder syncDataHolder = new SyncDataHolder(this);

    private final ISyncManaged container;

    @SaveField
    @SyncToClient
    @Getter
    private ItemStack filterItem = ItemStack.EMPTY;

    private @Nullable F filter;
    private @Nullable CustomItemStackHandler filterSlot;
    private @Nullable WidgetGroup filterGroup;
    private @Nullable UIElement filterLDLib2Group;

    private Consumer<F> onFilterLoaded = (filter) -> {};
    private Consumer<F> onFilterRemoved = (filter) -> {};
    private Consumer<F> onFilterUpdated = (filter) -> {};

    public FilterHandler(ISyncManaged container) {
        this.container = container;
    }

    protected abstract F loadFilter(ItemStack filterItem);

    protected abstract F getEmptyFilter();

    protected abstract boolean canInsertFilterItem(ItemStack itemStack);

    //////////////////////////////////
    // ***** PUBLIC API ******//
    //////////////////////////////////

    public Widget createFilterSlotUI(int xPos, int yPos) {
        return new SlotWidget(getFilterSlot(), 0, xPos, yPos)
                .setChangeListener(this::updateFilter)
                .setBackgroundTexture(GuiTextures.group(GuiTextures.SLOT, GuiTextures.FILTER_SLOT_OVERLAY));
    }

    public Widget createFilterConfigUI(int xPos, int yPos, int width, int height) {
        this.filterGroup = new WidgetGroup(xPos, yPos, width, height);
        if (!this.filterItem.isEmpty()) {
            this.filterGroup.addWidget(getFilter().openConfigurator(0, 0));
        }

        return this.filterGroup;
    }

    public UIElement createFilterSlotLDLib2UI(int xPos, int yPos) {
        GTItemSlotElement slot = new GTItemSlotElement(getFilterSlot(), 0)
                .setChangeListener(this::updateFilter)
                .setBackgroundTexture(GuiTextures.group(GuiTextures.SLOT, GuiTextures.FILTER_SLOT_OVERLAY));
        return UITemplate.setLDLib2Bounds(slot, xPos, yPos, 18, 18);
    }

    public UIElement createFilterConfigLDLib2UI(int xPos, int yPos, int width, int height) {
        F loadedFilter = this.filterItem.isEmpty() ? null : getFilter();
        this.filterLDLib2Group = UITemplate.setLDLib2Bounds(new UIElement(), xPos, yPos, width, height);
        if (loadedFilter != null && loadedFilter.supportsLDLib2Configurator()) {
            this.filterLDLib2Group.addChild(loadedFilter.openLDLib2Configurator(0, 0));
        }

        return this.filterLDLib2Group;
    }

    public boolean isFilterPresent() {
        return filter != null || !filterItem.isEmpty();
    }

    public F getFilter() {
        if (this.filter == null) {
            if (this.filterItem.isEmpty()) {
                return getEmptyFilter();
            } else {
                loadFilterFromItem();
            }
        }

        return this.filter;
    }

    public boolean test(T resource) {
        return getFilter().test(resource);
    }

    public FilterHandler<T, F> onFilterLoaded(Consumer<F> onFilterLoaded) {
        this.onFilterLoaded = onFilterLoaded;
        return this;
    }

    public FilterHandler<T, F> onFilterRemoved(Consumer<F> onFilterRemoved) {
        this.onFilterRemoved = onFilterRemoved;
        return this;
    }

    public FilterHandler<T, F> onFilterUpdated(Consumer<F> onFilterUpdated) {
        this.onFilterUpdated = onFilterUpdated;
        return this;
    }

    ///////////////////////////////////////
    // ***** FILTER HANDLING ******//
    ///////////////////////////////////////

    private CustomItemStackHandler getFilterSlot() {
        if (this.filterSlot == null) {
            this.filterSlot = new CustomItemStackHandler(this.filterItem) {

                @Override
                public int getSlotLimit(int slot) {
                    return 1;
                }
            };

            this.filterSlot.setFilter(this::canInsertFilterItem);
        }

        return this.filterSlot;
    }

    public void setFilterItem(ItemStack item) {
        getFilterSlot().setStackInSlot(0, item);
        updateFilter();
    }

    private void updateFilter() {
        var filterContainer = getFilterSlot();

        if (GTCEu.isClientThread()) {
            if (!filterContainer.getStackInSlot(0).isEmpty() && !this.filterItem.isEmpty()) {
                return;
            }
        }

        this.filterItem = filterContainer.getStackInSlot(0);
        syncDataHolder.markClientSyncFieldDirty("filterItem");

        if (this.filter != null) {
            this.filter = null;
            this.onFilterRemoved.accept(this.filter);
        }

        loadFilterFromItem();
    }

    private void loadFilterFromItem() {
        if (!this.filterItem.isEmpty()) {
            this.filter = loadFilter(this.filterItem);
            filter.setOnUpdated(this.onFilterUpdated);
            if (filter instanceof SmartItemFilter smart &&
                    container instanceof CoverBehavior cover &&
                    cover.coverHolder instanceof MachineCoverContainer mcc) {
                var machine = MetaMachine.getMachine(mcc.getLevel(), mcc.getBlockPos());
                if (machine != null) {
                    smart.setModeFromMachine(machine.getDefinition().getName());
                }
            }
            this.onFilterLoaded.accept(this.filter);
        }
        updateFilterGroupUI();
    }

    private void updateFilterGroupUI() {
        if (this.filterGroup != null) {
            this.filterGroup.clearAllWidgets();

            if (!this.filterItem.isEmpty() && this.filter != null) {
                this.filterGroup.addWidget(this.filter.openConfigurator(0, 0));
            }
        }

        if (this.filterLDLib2Group != null) {
            for (UIElement child : this.filterLDLib2Group.getSafeChildren()) {
                this.filterLDLib2Group.removeChild(child);
            }

            if (!this.filterItem.isEmpty() && this.filter != null && this.filter.supportsLDLib2Configurator()) {
                this.filterLDLib2Group.addChild(this.filter.openLDLib2Configurator(0, 0));
            }
        }
    }

    @Override
    public @Nullable ISyncManaged getParentSyncObject() {
        return container;
    }
}

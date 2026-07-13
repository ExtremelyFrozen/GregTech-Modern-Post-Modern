package com.gregtechceu.gtceu.common.cover.detector;

import com.gregtechceu.gtceu.api.blockentity.ConfigCopyHelper;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandler;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandlers;
import com.gregtechceu.gtceu.api.cover.filter.ItemFilter;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTIntInputElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
import com.gregtechceu.gtceu.api.gui.factory.CoverUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2CoverUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.UICoverHolder;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.utils.RedstoneUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import lombok.Getter;

import java.util.List;

public class AdvancedItemDetectorCover extends ItemDetectorCover implements LDLib2CoverUIProvider {

    private static final int DEFAULT_MIN = 64;
    private static final int DEFAULT_MAX = 512;

    static {
        AdvancedItemDetectorConfigActions.initialize();
    }

    @SaveField
    @SyncToClient
    @Getter
    private int minValue, maxValue;

    @SaveField
    @SyncToClient
    @Getter
    private boolean isLatched;
    @SaveField
    @SyncToClient
    @Getter
    protected final FilterHandler<ItemStack, ItemFilter> filterHandler;

    public AdvancedItemDetectorCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);

        this.minValue = DEFAULT_MIN;
        this.maxValue = DEFAULT_MAX;

        filterHandler = FilterHandlers.item(this);
    }

    @Override
    public List<ItemStack> getAdditionalDrops() {
        var list = super.getAdditionalDrops();
        if (!filterHandler.getFilterItem().isEmpty()) {
            list.add(filterHandler.getFilterItem());
        }
        return list;
    }

    @Override
    protected void update() {
        if (this.coverHolder.getOffsetTimer() % 20 != 0)
            return;

        ItemFilter filter = filterHandler.getFilter();
        IItemHandler handler = getItemHandler();
        if (handler == null)
            return;

        int storedItems = 0;

        for (int i = 0; i < handler.getSlots(); i++) {
            if (filter.test(handler.getStackInSlot(i)))
                storedItems += handler.getStackInSlot(i).getCount();
        }

        if (isLatched) {
            setRedstoneSignalOutput(RedstoneUtil.computeLatchedRedstoneBetweenValues(storedItems, maxValue, minValue,
                    isInverted(), redstoneSignalOutput));
        } else {
            setRedstoneSignalOutput(
                    RedstoneUtil.computeRedstoneBetweenValues(storedItems, maxValue, minValue, isInverted()));
        }
    }

    public void setMinValue(int minValue) {
        int upperBound = Math.max(0, maxValue - 1);
        int clamped = Mth.clamp(minValue, 0, upperBound);
        if (this.minValue != clamped) {
            this.minValue = clamped;
        }
    }

    public void setMaxValue(int maxValue) {
        int clamped = Math.max(maxValue, 0);
        if (this.maxValue != clamped) {
            this.maxValue = clamped;
        }
    }

    public void setLatched(boolean latched) {
        isLatched = latched;
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////

    @Override
    public boolean canCreateLDLib2UI(Player player, UICoverHolder holder) {
        return holder.getCover() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, UICoverHolder holder) {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, 176, 252);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND));

        root.addChild(createLDLib2Label(10, 5, 156, 10, "cover.advanced_item_detector.label"));
        root.addChild(createLDLib2Label(10, 55, 65, 10, "cover.advanced_item_detector.min"));
        root.addChild(createLDLib2Label(10, 80, 65, 10, "cover.advanced_item_detector.max"));
        root.addChild(new GTIntInputElement(80, 50, 86, 20, this::getMinValue,
                value -> setLDLib2MinValue(player, holder, value)));
        root.addChild(new GTIntInputElement(80, 75, 86, 20, this::getMaxValue,
                value -> setLDLib2MaxValue(player, holder, value)));
        root.addChild(new GTToggleButtonElement(9, 20, 20, 20, GuiTextures.INVERT_REDSTONE_BUTTON,
                this::isInverted, inverted -> setLDLib2Inverted(player, holder, inverted))
                .isMultiLang()
                .setTooltipText("cover.advanced_item_detector.invert"));
        root.addChild(new GTToggleButtonElement(31, 21, 18, 18, GuiTextures.BUTTON_LOCK,
                this::isLatched, latched -> setLDLib2Latched(player, holder, latched))
                .setShouldUseBaseBackground()
                .isMultiLang()
                .setTooltipText("cover.advanced_detector.latch"));
        root.addChild(filterHandler.createFilterSlotLDLib2UI(148, 100));
        root.addChild(filterHandler.createFilterConfigLDLib2UI(10, 100, 156, 60));
        root.addChild(UITemplate.bindPlayerInventoryLDLib2(player.getInventory(), GuiTextures.SLOT, 7, 170, true));
        return UI.of(root);
    }

    private GTLabelElement createLDLib2Label(int x, int y, int width, int height, String text) {
        GTLabelElement label = new GTLabelElement(x, y, width, height, text, true);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private void setLDLib2MinValue(Player player, UICoverHolder holder, int value) {
        setMinValue(value);
        sendLDLib2ConfigAction(player, holder);
    }

    private void setLDLib2MaxValue(Player player, UICoverHolder holder, int value) {
        setMaxValue(value);
        sendLDLib2ConfigAction(player, holder);
    }

    private void setLDLib2Latched(Player player, UICoverHolder holder, boolean latched) {
        setLatched(latched);
        sendLDLib2ConfigAction(player, holder);
    }

    private void setLDLib2Inverted(Player player, UICoverHolder holder, boolean inverted) {
        setInverted(inverted);
        sendLDLib2ConfigAction(player, holder);
    }

    private void sendLDLib2ConfigAction(Player player, UICoverHolder holder) {
        if (player.level().isClientSide()) {
            CoverUIHelper.sendAction(holder, AdvancedItemDetectorConfigActions.createSetConfigAction(
                    getMinValue(), getMaxValue(), isLatched(), isInverted()));
        }
    }

    @Override
    public DataComponentMap copyConfig(HolderLookup.Provider registries) {
        return ConfigCopyHelper.withFields(super.copyConfig(registries), fields -> fields
                .put(SyncFieldData.key("min"),
                        ConfigCopyHelper.intValue(minValue))
                .put(SyncFieldData.key("max"),
                        ConfigCopyHelper.intValue(maxValue))
                .put(SyncFieldData.key("latched"),
                        ConfigCopyHelper.booleanValue(isLatched))
                .put(SyncFieldData.key("filter"),
                        ConfigCopyHelper.encodeItem(registries, filterHandler.getFilterItem())));
    }

    @Override
    public void pasteConfig(ServerPlayer player, HolderLookup.Provider registries, DataComponentMap config) {
        setMinValue(ConfigCopyHelper.getInt(config, "min"));
        setMaxValue(ConfigCopyHelper.getInt(config, "max"));
        setLatched(ConfigCopyHelper.getBoolean(config, "latched"));
        filterHandler
                .setFilterItem(ConfigCopyHelper.decodeItem(registries, ConfigCopyHelper.getField(config, "filter")));
        super.pasteConfig(player, registries, config);
    }
}

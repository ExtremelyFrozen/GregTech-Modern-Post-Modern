package com.gregtechceu.gtceu.common.cover.detector;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.ConfigCopyHelper;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IUICover;
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
import com.gregtechceu.gtceu.api.gui.widget.IntInputWidget;
import com.gregtechceu.gtceu.api.gui.widget.ToggleButtonWidget;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.utils.RedstoneUtil;

import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextBoxWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.utils.LocalizationUtils;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AdvancedItemDetectorCover extends ItemDetectorCover implements IUICover, LDLib2CoverUIProvider {

    private static final int DEFAULT_MIN = 64;
    private static final int DEFAULT_MAX = 512;
    private static final ResourceLocation SET_ADVANCED_ITEM_DETECTOR_CONFIG_ACTION = GTCEu
            .id("set_advanced_item_detector_config");
    private static final ResourceLocation MIN_FIELD = SyncFieldData.key("min");
    private static final ResourceLocation MAX_FIELD = SyncFieldData.key("max");
    private static final ResourceLocation LATCHED_FIELD = SyncFieldData.key("latched");
    private static final ResourceLocation INVERTED_FIELD = SyncFieldData.key("inverted");

    static {
        SyncActionDispatchers.server().register(new AdvancedItemDetectorConfigActionHandler());
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
            syncDataHolder.markClientSyncFieldDirty("minValue");
        }
    }

    public void setMaxValue(int maxValue) {
        int clamped = Math.max(maxValue, 0);
        if (this.maxValue != clamped) {
            this.maxValue = clamped;
            syncDataHolder.markClientSyncFieldDirty("maxValue");
        }
    }

    public void setLatched(boolean latched) {
        isLatched = latched;
        syncDataHolder.markClientSyncFieldDirty("isLatched");
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 176, 170);
        group.addWidget(new LabelWidget(10, 5, "cover.advanced_item_detector.label"));

        group.addWidget(new TextBoxWidget(10, 55, 65,
                List.of(LocalizationUtils.format("cover.advanced_item_detector.min"))));

        group.addWidget(new TextBoxWidget(10, 80, 65,
                List.of(LocalizationUtils.format("cover.advanced_item_detector.max"))));

        group.addWidget(new IntInputWidget(80, 50, 176 - 80 - 10, 20, this::getMinValue, this::setMinValue));
        group.addWidget(new IntInputWidget(80, 75, 176 - 80 - 10, 20, this::getMaxValue, this::setMaxValue));

        // Invert Redstone Output Toggle:
        group.addWidget(new ToggleButtonWidget(
                9, 20, 20, 20,
                GuiTextures.INVERT_REDSTONE_BUTTON, this::isInverted, this::setInverted)
                .isMultiLang()
                .setTooltipText("cover.advanced_item_detector.invert"));

        group.addWidget(new ToggleButtonWidget(31, 21, 18, 18,
                GuiTextures.BUTTON_LOCK, this::isLatched, this::setLatched)
                .setShouldUseBaseBackground()
                .isMultiLang()
                .setTooltipText("cover.advanced_detector.latch"));

        // Item Filter UI:
        group.addWidget(filterHandler.createFilterSlotUI(148, 100));
        group.addWidget(filterHandler.createFilterConfigUI(10, 100, 156, 60));

        return group;
    }

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
            CoverUIHelper.sendAction(holder, createSetAdvancedItemDetectorConfigAction(
                    getMinValue(), getMaxValue(), isLatched(), isInverted()));
        }
    }

    private static SyncActionData createSetAdvancedItemDetectorConfigAction(int min, int max, boolean latched,
                                                                            boolean inverted) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(MIN_FIELD, new JsonPrimitive(min))
                        .put(MAX_FIELD, new JsonPrimitive(max))
                        .put(LATCHED_FIELD, new JsonPrimitive(latched))
                        .put(INVERTED_FIELD, new JsonPrimitive(inverted))
                        .build())
                .build();
        return new SyncActionData(SET_ADVANCED_ITEM_DETECTOR_CONFIG_ACTION, 0, payload);
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

    private static final class AdvancedItemDetectorConfigActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_ADVANCED_ITEM_DETECTOR_CONFIG_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof AdvancedItemDetectorCover;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null &&
                    isValidNonNegativeInt(fields, MIN_FIELD) &&
                    isValidNonNegativeInt(fields, MAX_FIELD) &&
                    readBoolean(fields, LATCHED_FIELD) != null &&
                    readBoolean(fields, INVERTED_FIELD) != null;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof AdvancedItemDetectorCover cover)) {
                throw new IllegalStateException("Advanced item detector config action received a non-item detector.");
            }
            cover.setMinValue(requireNonNegativeInt(context.payload(), MIN_FIELD));
            cover.setMaxValue(requireNonNegativeInt(context.payload(), MAX_FIELD));
            cover.setLatched(requireBoolean(context.payload(), LATCHED_FIELD));
            cover.setInverted(requireBoolean(context.payload(), INVERTED_FIELD));
        }
    }

    private static boolean isValidNonNegativeInt(SyncFieldData fields, ResourceLocation field) {
        Integer value = readInt(fields, field);
        return value != null && value >= 0;
    }

    private static int requireNonNegativeInt(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Advanced item detector config action payload is missing field data.");
        }
        Integer value = readInt(fields, field);
        if (value == null) {
            throw new IllegalStateException("Advanced item detector config action payload is missing " + field + ".");
        }
        if (value < 0) {
            throw new IllegalArgumentException("Advanced item detector config action value is negative: " + value);
        }
        return value;
    }

    private static boolean requireBoolean(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Advanced item detector config action payload is missing field data.");
        }
        Boolean value = readBoolean(fields, field);
        if (value == null) {
            throw new IllegalStateException("Advanced item detector config action payload is missing " + field + ".");
        }
        return value;
    }

    private static @Nullable Integer readInt(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            return primitive.getAsInt();
        }
        return null;
    }

    private static @Nullable Boolean readBoolean(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        return null;
    }
}

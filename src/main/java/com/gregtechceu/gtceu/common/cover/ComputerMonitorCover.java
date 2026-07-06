package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTIntInputElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.element.GTTextFieldElement;
import com.gregtechceu.gtceu.api.gui.factory.CoverUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2CoverUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.UICoverHolder;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.placeholder.IPlaceholderInfoProviderCover;
import com.gregtechceu.gtceu.api.placeholder.MultiLineComponent;
import com.gregtechceu.gtceu.api.placeholder.PlaceholderContext;
import com.gregtechceu.gtceu.api.placeholder.PlaceholderHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.client.renderer.cover.CoverTextRenderer;
import com.gregtechceu.gtceu.client.renderer.cover.IDynamicCoverRenderer;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.item.datacomponents.ComputerMonitorConfig;
import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.integration.create.GTCreateIntegration;
import com.gregtechceu.gtceu.utils.GTStringUtils;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.ParametersAreNonnullByDefault;

@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public class ComputerMonitorCover extends CoverBehavior
        implements LDLib2CoverUIProvider, IDataStickInteractable, IPlaceholderInfoProviderCover {

    private static final int FORMAT_LINE_COUNT = 8;
    private static final int TEXT_FIELD_WIDTH = 160;
    private static final int TEXT_FIELD_HEIGHT = 15;
    private static final int HORIZONTAL_PADDING = 10;
    private static final int VERTICAL_PADDING = 2;
    private static final int UPDATE_INTERVAL_MIN = 1;
    private static final int UPDATE_INTERVAL_MAX = 60 * 20;
    private static final int ROOT_WIDTH = 390;
    private static final int CONTENT_HEIGHT = 195;

    private static final ResourceLocation SET_COMPUTER_MONITOR_COVER_CONFIG_ACTION = GTCEu
            .id("set_computer_monitor_cover_config");
    private static final ResourceLocation FORMAT_LINES_FIELD = SyncFieldData.key("formatLines");
    private static final ResourceLocation FORMAT_ARGS_FIELD = SyncFieldData.key("formatArgs");
    private static final ResourceLocation UPDATE_INTERVAL_FIELD = SyncFieldData.key("updateInterval");

    static {
        SyncActionDispatchers.server().register(new ComputerMonitorCoverConfigActionHandler());
    }

    private TickableSubscription subscription;
    private final CoverTextRenderer renderer;
    @SaveField
    @SyncToClient
    @Getter
    private List<String> formatStringArgs = new ArrayList<>(FORMAT_LINE_COUNT);
    @SaveField
    @SyncToClient
    @Getter
    private List<String> formatStringLines = new ArrayList<>(FORMAT_LINE_COUNT);
    @SaveField
    @SyncToClient
    @Getter
    private List<MutableComponent> text = new ArrayList<>();
    @SaveField
    public CustomItemStackHandler itemStackHandler = new CustomItemStackHandler(FORMAT_LINE_COUNT);
    @Setter
    private String placeholderSearch = "";
    @Setter
    @Getter
    @SaveField
    private int updateInterval = 100;
    @Getter
    @SaveField
    private long ticksSincePlaced = 0;
    @SaveField
    @Getter
    private List<MutableComponent> createDisplayTargetBuffer = new ArrayList<>();
    @SaveField
    @Getter
    private List<MutableComponent> computerCraftTextBuffer = new ArrayList<>();
    @SaveField
    @Getter
    private UUID placeholderUUID;

    public ComputerMonitorCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
        renderer = new CoverTextRenderer(this::getText);
        placeholderUUID = UUID.randomUUID();
        for (int i = 0; i < 100; i++) {
            createDisplayTargetBuffer.add(Component.empty());
            computerCraftTextBuffer.add(Component.empty());
        }
    }

    public List<MutableComponent> getRenderedText() {
        String s = formatStringLines.stream().reduce((a, b) -> a + "\n" + b).orElse("");
        List<String> tmp = new ArrayList<>(formatStringArgs);
        tmp = tmp.stream().map(str -> '{' + str + '}').toList();
        return PlaceholderHandler.processPlaceholders(
                GTStringUtils.replace(s, "\\{}", tmp),
                new PlaceholderContext(coverHolder.getLevel(), coverHolder.getBlockPos(), attachedSide,
                        itemStackHandler,
                        this, new MultiLineComponent(text), placeholderUUID));
    }

    public void setDisplayTargetBufferLine(int line, MutableComponent component) {
        createDisplayTargetBuffer.set(line, component);
    }

    @Override
    public void setComputerCraftTextBufferLine(int line, MutableComponent component) {
        computerCraftTextBuffer.set(line, component);
    }

    @Override
    public boolean canPipePassThrough() {
        return false;
    }

    @Override
    public Supplier<IDynamicCoverRenderer> getDynamicRenderer() {
        return () -> renderer;
    }

    @Override
    public boolean canCreateLDLib2UI(Player player, UICoverHolder holder) {
        return holder.getCover() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, UICoverHolder holder) {
        ensureFormatStringSlots();

        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, ROOT_WIDTH, CONTENT_HEIGHT + 82);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND));

        UIElement mainPage = new UIElement();
        UITemplate.setLDLib2Bounds(mainPage, 0, 0, ROOT_WIDTH, CONTENT_HEIGHT);
        UIElement formatStringArgsPage = new UIElement();
        UITemplate.setLDLib2Bounds(formatStringArgsPage, 0, 0, ROOT_WIDTH, CONTENT_HEIGHT);
        formatStringArgsPage.setVisible(false);

        for (int i = 0; i < FORMAT_LINE_COUNT; i++) {
            mainPage.addChild(createLDLib2FormatStringLineField(player, holder, i));
            mainPage.addChild(createLDLib2ItemSlot(i));
            formatStringArgsPage.addChild(createLDLib2FormatStringArgField(player, holder, i));
        }

        mainPage.addChild(createLDLib2PlaceholderReference(placeholderSearch));
        mainPage.addChild(createLDLib2UpdateIntervalInput(player, holder));

        GTButtonElement switchToFormatStringArgsPageButton = new GTButtonElement(
                HORIZONTAL_PADDING + 50,
                10 * (TEXT_FIELD_HEIGHT + VERTICAL_PADDING) + VERTICAL_PADDING,
                20, 20,
                GuiTextures.group(GuiTextures.VANILLA_BUTTON, GuiTextures.BUTTON_RIGHT),
                event -> {
                    mainPage.setVisible(false);
                    formatStringArgsPage.setVisible(true);
                });
        switchToFormatStringArgsPageButton.noText();
        switchToFormatStringArgsPageButton.style(style -> style.tooltips(
                Component.translatable("gtpm.gui.computer_monitor_cover.edit_blank_placeholders")));

        GTButtonElement switchBack = new GTButtonElement(
                HORIZONTAL_PADDING + 50,
                10 * (TEXT_FIELD_HEIGHT + VERTICAL_PADDING) + VERTICAL_PADDING,
                20, 20,
                GuiTextures.group(GuiTextures.VANILLA_BUTTON, GuiTextures.BUTTON_LEFT),
                event -> {
                    formatStringArgsPage.setVisible(false);
                    mainPage.setVisible(true);
                });
        switchBack.noText();
        switchBack.style(style -> style.tooltips(
                Component.translatable("gtpm.gui.computer_monitor_cover.edit_displayed_text")));

        mainPage.addChild(switchToFormatStringArgsPageButton);
        formatStringArgsPage.addChild(switchBack);
        root.addChild(mainPage);
        root.addChild(formatStringArgsPage);
        root.addChild(UITemplate.bindPlayerInventoryLDLib2(player.getInventory(), GuiTextures.SLOT, 7,
                CONTENT_HEIGHT, true));
        return UI.of(root);
    }

    private GTTextFieldElement createLDLib2FormatStringLineField(Player player, UICoverHolder holder, int index) {
        GTTextFieldElement field = createLDLib2FormatTextField(index);
        field.setText(formatStringLines.get(index), false);
        field.setTextResponder(value -> setLDLib2FormatStringLine(player, holder, index, value));
        field.style(style -> style.tooltips(tooltips(
                LangHandler.getMultiLang("gtpm.gui.computer_monitor_cover.main_textbox_tooltip", index + 1))));
        return field;
    }

    private GTTextFieldElement createLDLib2FormatStringArgField(Player player, UICoverHolder holder, int index) {
        GTTextFieldElement field = createLDLib2FormatTextField(index);
        field.setText(formatStringArgs.get(index), false);
        field.setTextResponder(value -> setLDLib2FormatStringArg(player, holder, index, value));
        field.style(style -> style.tooltips(tooltips(
                LangHandler.getMultiLang("gtpm.gui.computer_monitor_cover.second_page_textbox_tooltip",
                        GTStringUtils.getIntOrderingSuffix(index + 1)))));
        return field;
    }

    private static GTTextFieldElement createLDLib2FormatTextField(int index) {
        GTTextFieldElement field = new GTTextFieldElement(
                HORIZONTAL_PADDING + TEXT_FIELD_WIDTH / 2,
                10 + VERTICAL_PADDING + index * (TEXT_FIELD_HEIGHT + VERTICAL_PADDING),
                TEXT_FIELD_WIDTH,
                TEXT_FIELD_HEIGHT);
        field.setAnyString();
        field.textFieldStyle(style -> style
                .textColor(0x404040)
                .textShadow(false));
        return field;
    }

    private GTItemSlotElement createLDLib2ItemSlot(int index) {
        GTItemSlotElement slot = new GTItemSlotElement(itemStackHandler, index)
                .setBackgroundTexture(GuiTextures.SLOT);
        UITemplate.setLDLib2Bounds(slot, HORIZONTAL_PADDING + 50, 20 * index, 18, 18);
        slot.style(style -> style.tooltips(tooltips(
                LangHandler.getMultiLang("gtpm.gui.computer_monitor_cover.slot_tooltip", index + 1))));
        return slot;
    }

    private UIElement createLDLib2PlaceholderReference(String filter) {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 280, 0, 110, CONTENT_HEIGHT);

        root.addChild(createLDLib2Label(0, 0, 110, 15, Component.literal(GTStringUtils.componentsToString(
                LangHandler.getMultiLang("gtpm.gui.computer_monitor_cover.placeholder_reference")))));

        GTScrollerViewElement placeholderReference = new GTScrollerViewElement(0, 15, 100, CONTENT_HEIGHT - 15);
        int y = 2;
        List<String> placeholders = new ArrayList<>(PlaceholderHandler.getAllPlaceholderNames());
        placeholders.removeIf(placeholder -> placeholder == null || !placeholder.contains(filter));
        placeholders.sort(Comparator.naturalOrder());
        for (String placeholder : placeholders) {
            GTLabelElement placeholderName = createLDLib2Label(0, y, 100, 15, Component.literal(placeholder));
            placeholderName.style(style -> style.tooltips(tooltips(
                    LangHandler.getSingleOrMultiLang("gtpm.placeholder_info." + placeholder))));
            placeholderReference.addChild(placeholderName);
            y += 15;
        }
        root.addChild(placeholderReference);
        return root;
    }

    private GTIntInputElement createLDLib2UpdateIntervalInput(Player player, UICoverHolder holder) {
        GTIntInputElement input = new GTIntInputElement(0, 0, 60, 20, this::getUpdateInterval,
                value -> setLDLib2UpdateInterval(player, holder, value))
                .setMin(UPDATE_INTERVAL_MIN)
                .setMax(UPDATE_INTERVAL_MAX);
        input.style(style -> style.tooltips(
                Component.translatable("gtpm.gui.computer_monitor_cover.update_interval")));
        return input;
    }

    private static GTLabelElement createLDLib2Label(int x, int y, int width, int height, Component text) {
        GTLabelElement label = new GTLabelElement(x, y, width, height, text);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private void setLDLib2FormatStringLine(Player player, UICoverHolder holder, int index, String value) {
        setFormatStringEntry(formatStringLines, index, value);
        sendLDLib2ConfigAction(player, holder);
    }

    private void setLDLib2FormatStringArg(Player player, UICoverHolder holder, int index, String value) {
        setFormatStringEntry(formatStringArgs, index, value);
        sendLDLib2ConfigAction(player, holder);
    }

    private void setLDLib2UpdateInterval(Player player, UICoverHolder holder, int value) {
        setUpdateInterval(value);
        sendLDLib2ConfigAction(player, holder);
    }

    private void sendLDLib2ConfigAction(Player player, UICoverHolder holder) {
        if (player.level().isClientSide()) {
            CoverUIHelper.sendAction(holder, createSetComputerMonitorCoverConfigAction(formatStringLines,
                    formatStringArgs, getUpdateInterval()));
        }
    }

    private void ensureFormatStringSlots() {
        ensureListSize(formatStringLines, FORMAT_LINE_COUNT);
        ensureListSize(formatStringArgs, FORMAT_LINE_COUNT);
    }

    private static void setFormatStringEntry(List<String> values, int index, String value) {
        ensureListSize(values, index + 1);
        values.set(index, value);
    }

    private static void ensureListSize(List<String> values, int size) {
        while (values.size() < size) {
            values.add("");
        }
    }

    private static Component[] tooltips(List<MutableComponent> tooltips) {
        return GTStringUtils.toImmutable(tooltips).toArray(Component[]::new);
    }

    private static SyncActionData createSetComputerMonitorCoverConfigAction(List<String> lines, List<String> args,
                                                                            int updateInterval) {
        if (!isValidUpdateInterval(updateInterval)) {
            throw new IllegalArgumentException(
                    "Computer monitor cover update interval is out of range: " + updateInterval);
        }
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(FORMAT_LINES_FIELD, writeStringList(lines))
                        .put(FORMAT_ARGS_FIELD, writeStringList(args))
                        .put(UPDATE_INTERVAL_FIELD, new JsonPrimitive(updateInterval))
                        .build())
                .build();
        return new SyncActionData(SET_COMPUTER_MONITOR_COVER_CONFIG_ACTION, 0, payload);
    }

    private static JsonArray writeStringList(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    private static boolean isValidUpdateInterval(int value) {
        return value >= UPDATE_INTERVAL_MIN && value <= UPDATE_INTERVAL_MAX;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        subscription = coverHolder.subscribeServerTick(subscription, this::update);
    }

    private void update() {
        ticksSincePlaced++;
        if (coverHolder.getOffsetTimer() % updateInterval == 0) {
            try {
                if (GTCEu.Mods.isCreateLoaded())
                    GTCreateIntegration.TemporaryRedstoneLinkTransmitter.destroyAll();
                setRedstoneSignalOutput(0);
                text = getRenderedText();
            } catch (RuntimeException e) {
                text = GTUtil.list(
                        Component.translatable("gtpm.computer_monitor_cover.error.exception", e.getMessage()));
            }
        }
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        if (subscription != null) {
            subscription.unsubscribe();
        }
    }

    @Override
    public boolean canConnectRedstone() {
        return true;
    }

    @Override
    public List<ItemStack> getAdditionalDrops() {
        List<ItemStack> drops = super.getAdditionalDrops();
        for (int i = 0; i < 8; i++) {
            if (!itemStackHandler.getStackInSlot(i).isEmpty()) {
                drops.add(itemStackHandler.getStackInSlot(i));
            }
        }
        return drops;
    }

    @Override
    public InteractionResult onDataStickUse(Player player, ItemStack dataStick) {
        ComputerMonitorConfig config = dataStick.get(GTDataComponents.COMPUTER_MONITOR_CONFIG);
        if (config == null) return InteractionResult.FAIL;

        formatStringLines.clear();
        formatStringLines.addAll(config.lines());

        formatStringArgs.clear();
        formatStringArgs.addAll(config.args());
        updateInterval = config.updateInterval();
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    @Override
    public InteractionResult onDataStickShiftUse(Player player, ItemStack dataStick) {
        dataStick.set(GTDataComponents.COMPUTER_MONITOR_CONFIG,
                new ComputerMonitorConfig(formatStringLines, formatStringArgs, updateInterval));
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    private static final class ComputerMonitorCoverConfigActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_COMPUTER_MONITOR_COVER_CONFIG_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof ComputerMonitorCover;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null &&
                    readStringList(fields, FORMAT_LINES_FIELD) != null &&
                    readStringList(fields, FORMAT_ARGS_FIELD) != null &&
                    isValidUpdateInterval(fields, UPDATE_INTERVAL_FIELD);
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator() && context.holder() instanceof ComputerMonitorCover;
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof ComputerMonitorCover cover)) {
                throw new IllegalStateException(
                        "Computer monitor cover config action received a non-computer-monitor cover.");
            }
            cover.replaceFormatStringLines(requireStringList(context.payload(), FORMAT_LINES_FIELD));
            cover.replaceFormatStringArgs(requireStringList(context.payload(), FORMAT_ARGS_FIELD));
            cover.setUpdateInterval(requireUpdateInterval(context.payload(), UPDATE_INTERVAL_FIELD));
        }
    }

    private void replaceFormatStringLines(List<String> lines) {
        formatStringLines.clear();
        formatStringLines.addAll(lines);
        ensureListSize(formatStringLines, FORMAT_LINE_COUNT);
    }

    private void replaceFormatStringArgs(List<String> args) {
        formatStringArgs.clear();
        formatStringArgs.addAll(args);
        ensureListSize(formatStringArgs, FORMAT_LINE_COUNT);
    }

    private static boolean isValidUpdateInterval(SyncFieldData fields, ResourceLocation field) {
        Integer value = readInt(fields, field);
        return value != null && isValidUpdateInterval(value);
    }

    private static int requireUpdateInterval(DataComponentMap payload, ResourceLocation field) {
        int value = requireInt(payload, field);
        if (!isValidUpdateInterval(value)) {
            throw new IllegalArgumentException(
                    "Computer monitor cover config action update interval is out of range: " + value);
        }
        return value;
    }

    private static List<String> requireStringList(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Computer monitor cover config action payload is missing field data.");
        }
        List<String> values = readStringList(fields, field);
        if (values == null) {
            throw new IllegalStateException(
                    "Computer monitor cover config action payload is missing " + field + ".");
        }
        return values;
    }

    private static int requireInt(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Computer monitor cover config action payload is missing field data.");
        }
        Integer value = readInt(fields, field);
        if (value == null) {
            throw new IllegalStateException(
                    "Computer monitor cover config action payload is missing " + field + ".");
        }
        return value;
    }

    private static @Nullable List<String> readStringList(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (!(element instanceof JsonArray array)) {
            return null;
        }
        List<String> values = new ArrayList<>(array.size());
        for (JsonElement entry : array) {
            if (!(entry instanceof JsonPrimitive primitive) || !primitive.isString()) {
                return null;
            }
            values.add(primitive.getAsString());
        }
        return values;
    }

    private static @Nullable Integer readInt(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            return primitive.getAsInt();
        }
        return null;
    }
}

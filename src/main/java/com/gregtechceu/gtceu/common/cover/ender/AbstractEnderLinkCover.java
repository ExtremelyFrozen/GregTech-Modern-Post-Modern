package com.gregtechceu.gtceu.common.cover.ender;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.ConfigCopyHelper;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.filter.FilterHandler;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.SelectableEnum;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTEnumSelectorElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.element.GTTextFieldElement;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
import com.gregtechceu.gtceu.api.gui.factory.CoverUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2CoverUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.UICoverHolder;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.machine.MachineCoverContainer;
import com.gregtechceu.gtceu.api.misc.virtualregistry.EntryTypes;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEnderRegistry;
import com.gregtechceu.gtceu.api.misc.virtualregistry.VirtualEntry;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.RerenderOnChanged;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.network.packets.SPacketEnderLinkChannelsToClient;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.regex.Pattern;

@SuppressWarnings("SameParameterValue")
public abstract class AbstractEnderLinkCover<T extends VirtualEntry> extends CoverBehavior
                                            implements LDLib2CoverUIProvider, IControllable {

    public static final Pattern COLOR_INPUT_PATTERN = Pattern.compile("^[0-9a-fA-F]{0,8}$");

    private static final int WIDGET_BOARD = 20;
    private static final int GROUP_WIDTH = 176;
    private static final int TOTAL_WIDTH = 156;
    private static final int BUTTON_SIZE = 16;

    private static final ResourceLocation SET_ENDER_LINK_COVER_CONFIG_ACTION = GTCEu
            .id("set_ender_link_cover_config");
    private static final ResourceLocation SET_ENDER_LINK_CHANNEL_DESCRIPTION_ACTION = GTCEu
            .id("set_ender_link_channel_description");
    private static final ResourceLocation REQUEST_ENDER_LINK_CHANNELS_ACTION = GTCEu
            .id("request_ender_link_channels");
    private static final ResourceLocation CLEAR_ENDER_LINK_CHANNEL_DESCRIPTION_ACTION = GTCEu
            .id("clear_ender_link_channel_description");
    private static final ResourceLocation CHANNEL_COLOR_FIELD = SyncFieldData.key("channelColor");
    private static final ResourceLocation PERMISSION_FIELD = SyncFieldData.key("permission");
    private static final ResourceLocation IO_FIELD = SyncFieldData.key("io");
    private static final ResourceLocation MANUAL_IO_FIELD = SyncFieldData.key("manualIO");
    private static final ResourceLocation WORKING_ENABLED_FIELD = SyncFieldData.key("workingEnabled");
    private static final ResourceLocation DESCRIPTION_FIELD = SyncFieldData.key("description");
    private static final ResourceLocation REQUEST_CHANNELS_FIELD = SyncFieldData.key("requestChannels");

    static {
        SyncActionDispatchers.server().register(new EnderLinkCoverConfigActionHandler());
        SyncActionDispatchers.server().register(new EnderLinkDescriptionActionHandler());
        SyncActionDispatchers.server().register(new EnderLinkChannelListActionHandler());
        SyncActionDispatchers.server().register(new EnderLinkClearDescriptionActionHandler());
    }

    protected final ConditionalSubscriptionHandler subscriptionHandler;

    @SaveField
    @SyncToClient
    protected String colorStr = VirtualEntry.DEFAULT_COLOR;
    @Getter
    @SaveField
    @SyncToClient
    protected Permissions permission = Permissions.PUBLIC;
    @SaveField
    @Getter
    protected boolean isWorkingEnabled = true;
    @Getter
    @SaveField
    @SyncToClient
    protected ManualIOMode manualIOMode = ManualIOMode.DISABLED;
    @Getter
    @SaveField
    @SyncToClient
    @RerenderOnChanged
    protected IO io = IO.OUT;
    @SyncToClient
    boolean isAnyChanged = false;

    public AbstractEnderLinkCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
        subscriptionHandler = new ConditionalSubscriptionHandler(coverHolder, this::update, this::isSubscriptionActive);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        subscriptionHandler.initialize(coverHolder.getLevel());
    }

    @Override
    public abstract boolean canAttach();

    @Override
    public void onAttached(@NotNull ItemStack itemStack, @Nullable ServerPlayer player) {
        super.onAttached(itemStack, player);
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        subscriptionHandler.unsubscribe();
        if (!coverHolder.isRemote()) {
            VirtualEnderRegistry.getInstance()
                    .deleteEntryIf(getOwner(), getEntryType(), getChannelName(), VirtualEntry::canRemove);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        subscriptionHandler.unsubscribe();
        if (!coverHolder.isRemote()) {
            VirtualEnderRegistry.getInstance()
                    .deleteEntryIf(getOwner(), getEntryType(), getChannelName(), VirtualEntry::canRemove);
        }
    }

    @Override
    public void setWorkingEnabled(boolean isWorkingAllowed) {
        if (this.isWorkingEnabled != isWorkingAllowed) {
            this.isWorkingEnabled = isWorkingAllowed;
            subscriptionHandler.updateSubscription();
        }
    }

    @Override
    public boolean canCreateLDLib2UI(Player player, UICoverHolder holder) {
        return holder.getCover() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, UICoverHolder holder) {
        return UI.of(new EnderLinkRootElement(player, holder, this));
    }

    public void setIo(IO io) {
        if (io == IO.IN || io == IO.OUT) {
            this.io = io;
            syncDataHolder.markClientSyncFieldDirty("io");
            subscriptionHandler.updateSubscription();
        }
    }

    public UUID getOwner() {
        if (permission == Permissions.PRIVATE && coverHolder instanceof MachineCoverContainer mcc) {
            var owner = mcc.getMachine().getOwner();
            return owner != null ? owner.getPlayerUUID() : null;
        }
        return null;
    }

    protected boolean isSubscriptionActive() {
        return isWorkingEnabled();
    }

    protected abstract String identifier();

    protected abstract VirtualEntry getEntry();

    protected abstract void setEntry(VirtualEntry entry);

    protected final String getChannelName() {
        return identifier() + this.colorStr;
    }

    protected void setChannelName(String name) {
        if (coverHolder.isRemote()) return;
        VirtualEnderRegistry.getInstance().deleteEntryIf(getOwner(), getEntryType(), getChannelName(),
                VirtualEntry::canRemove);
        this.colorStr = normalizeColorInput(name);
        syncDataHolder.markClientSyncFieldDirty("colorStr");
        setVirtualEntry();
    }

    protected final String getChannelName(VirtualEntry entry) {
        return identifier() + entry.getColorStr();
    }

    protected void setPermission(Permissions permission) {
        if (coverHolder.isRemote()) return;
        VirtualEnderRegistry.getInstance().deleteEntryIf(getOwner(), getEntryType(), getChannelName(),
                VirtualEntry::canRemove);
        this.permission = permission;
        syncDataHolder.markClientSyncFieldDirty("permission");

        setVirtualEntry();
    }

    protected void setVirtualEntry() {
        setEntry(VirtualEnderRegistry.getInstance().getOrCreateEntry(getOwner(), getEntryType(), getChannelName()));
        getEntry().setColor(this.colorStr);
        markEnderLinkUIChanged();
        subscriptionHandler.updateSubscription();
    }

    protected abstract EntryTypes<T> getEntryType();

    protected void update() {
        long timer = coverHolder.getOffsetTimer();
        if (timer % 5 != 0) return;

        if (isWorkingEnabled() && !coverHolder.isRemote()) {
            var entry = VirtualEnderRegistry.getInstance().getOrCreateEntry(getOwner(), getEntryType(),
                    getChannelName());
            if (!entry.getColorStr().equals(this.colorStr)) {
                entry.setColor(this.colorStr);
            }
            if (!getEntry().equals(entry)) {
                setEntry(entry);
            }
            transfer();
        }

        if (isAnyChanged) {
            isAnyChanged = false;
        }
        subscriptionHandler.updateSubscription();
    }

    protected abstract void transfer();

    protected void setManualIOMode(ManualIOMode manualIOMode) {
        this.manualIOMode = manualIOMode;
        syncDataHolder.markClientSyncFieldDirty("manualIOMode");
        subscriptionHandler.updateSubscription();
    }

    @Nullable
    protected FilterHandler<?, ?> getFilterHandler() {
        return null;
    }

    protected abstract UIElement addVirtualEntryLDLib2Element(VirtualEntry entry, int x, int y, int width, int height,
                                                              boolean canClick);

    protected abstract String getUITitle();

    protected int getColor() {
        return VirtualEntry.parseColor(this.colorStr);
    }

    @Override
    public DataComponentMap copyConfig(HolderLookup.Provider registries) {
        return ConfigCopyHelper.withFields(super.copyConfig(registries), fields -> fields
                .put(SyncFieldData.key("colorStr"),
                        ConfigCopyHelper.stringValue(colorStr))
                .put(SyncFieldData.key("permission"),
                        ConfigCopyHelper.intValue(getPermission().ordinal()))
                .put(SyncFieldData.key("io"),
                        ConfigCopyHelper.intValue(getIo().ordinal()))
                .put(SyncFieldData.key("manualIO"),
                        ConfigCopyHelper.intValue(getManualIOMode().ordinal())));
    }

    @Override
    public void pasteConfig(ServerPlayer player, HolderLookup.Provider registries, DataComponentMap config) {
        setChannelName(ConfigCopyHelper.getString(config, "colorStr"));
        setPermission(Permissions.values()[ConfigCopyHelper.getInt(config, "permission")]);
        setIo(IO.values()[ConfigCopyHelper.getInt(config, "io")]);
        setManualIOMode(ManualIOMode.values()[ConfigCopyHelper.getInt(config, "manualIO")]);
        super.pasteConfig(player, registries, config);
    }

    private void setLDLib2ChannelColor(Player player, UICoverHolder holder, String channelColor) {
        String normalized = normalizeColorInput(channelColor);
        if (player.level().isClientSide()) {
            colorStr = normalized;
            CoverUIHelper.sendAction(holder, createSetEnderLinkCoverConfigAction(this));
        } else {
            setChannelName(normalized);
        }
    }

    private void setLDLib2Permission(Player player, UICoverHolder holder, Permissions permission) {
        if (player.level().isClientSide()) {
            this.permission = permission;
            CoverUIHelper.sendAction(holder, createSetEnderLinkCoverConfigAction(this));
        } else {
            setPermission(permission);
        }
    }

    private void setLDLib2Io(Player player, UICoverHolder holder, IO io) {
        if (player.level().isClientSide()) {
            this.io = io;
            CoverUIHelper.sendAction(holder, createSetEnderLinkCoverConfigAction(this));
        } else {
            setIo(io);
        }
    }

    private void setLDLib2ManualIOMode(Player player, UICoverHolder holder, ManualIOMode manualIOMode) {
        if (player.level().isClientSide()) {
            this.manualIOMode = manualIOMode;
            CoverUIHelper.sendAction(holder, createSetEnderLinkCoverConfigAction(this));
        } else {
            setManualIOMode(manualIOMode);
        }
    }

    private void setLDLib2WorkingEnabled(Player player, UICoverHolder holder, boolean workingEnabled) {
        if (player.level().isClientSide()) {
            isWorkingEnabled = workingEnabled;
            CoverUIHelper.sendAction(holder, createSetEnderLinkCoverConfigAction(this));
        } else {
            setWorkingEnabled(workingEnabled);
        }
    }

    private void setLDLib2Description(Player player, UICoverHolder holder, String description) {
        VirtualEntry entry = getEntry();
        if (entry != null) {
            entry.setDescription(description);
        }
        if (player.level().isClientSide()) {
            CoverUIHelper.sendAction(holder, createSetEnderLinkChannelDescriptionAction(getChannelName(), description));
        } else {
            markEnderLinkUIChanged();
        }
    }

    private void requestLDLib2Channels(Player player, UICoverHolder holder) {
        if (player.level().isClientSide()) {
            CoverUIHelper.sendAction(holder, createRequestEnderLinkChannelsAction());
        }
    }

    private void clearLDLib2Description(Player player, UICoverHolder holder, VirtualEntry entry) {
        entry.setDescription("");
        if (player.level().isClientSide()) {
            CoverUIHelper.sendAction(holder, createClearEnderLinkChannelDescriptionAction(getChannelName(entry)));
            requestLDLib2Channels(player, holder);
        }
    }

    private void markEnderLinkUIChanged() {
        syncDataHolder.markClientSyncFieldDirty("isAnyChanged");
        this.isAnyChanged = true;
    }

    private static SyncActionData createSetEnderLinkCoverConfigAction(AbstractEnderLinkCover<?> cover) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(CHANNEL_COLOR_FIELD, new JsonPrimitive(cover.colorStr))
                        .put(PERMISSION_FIELD, new JsonPrimitive(cover.permission.ordinal()))
                        .put(IO_FIELD, new JsonPrimitive(cover.io.ordinal()))
                        .put(MANUAL_IO_FIELD, new JsonPrimitive(cover.manualIOMode.ordinal()))
                        .put(WORKING_ENABLED_FIELD, new JsonPrimitive(cover.isWorkingEnabled))
                        .build())
                .build();
        return new SyncActionData(SET_ENDER_LINK_COVER_CONFIG_ACTION, 0, payload);
    }

    private static SyncActionData createSetEnderLinkChannelDescriptionAction(String channelName, String description) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(CHANNEL_COLOR_FIELD, new JsonPrimitive(channelName))
                        .put(DESCRIPTION_FIELD, new JsonPrimitive(description))
                        .build())
                .build();
        return new SyncActionData(SET_ENDER_LINK_CHANNEL_DESCRIPTION_ACTION, 0, payload);
    }

    private static SyncActionData createRequestEnderLinkChannelsAction() {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(REQUEST_CHANNELS_FIELD, new JsonPrimitive(true))
                        .build())
                .build();
        return new SyncActionData(REQUEST_ENDER_LINK_CHANNELS_ACTION, 0, payload);
    }

    private static SyncActionData createClearEnderLinkChannelDescriptionAction(String channelName) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(CHANNEL_COLOR_FIELD, new JsonPrimitive(channelName))
                        .build())
                .build();
        return new SyncActionData(CLEAR_ENDER_LINK_CHANNEL_DESCRIPTION_ACTION, 0, payload);
    }

    private static void applyEnderLinkCoverConfig(AbstractEnderLinkCover<?> cover, DataComponentMap payload) {
        cover.setChannelName(requireColor(payload, CHANNEL_COLOR_FIELD));
        cover.setPermission(requirePermission(payload, PERMISSION_FIELD));
        cover.setIo(requireIo(payload, IO_FIELD));
        cover.setManualIOMode(requireManualIOMode(payload, MANUAL_IO_FIELD));
        cover.setWorkingEnabled(requireBoolean(payload, WORKING_ENABLED_FIELD));
        cover.markEnderLinkUIChanged();
    }

    private static void sendChannelList(ServerPlayer player, AbstractEnderLinkCover<?> cover, @Nullable BlockPos pos,
                                        @Nullable Direction side) {
        if (pos == null || side == null) {
            throw new IllegalStateException("Ender link channel list action is missing cover position.");
        }
        List<JsonElement> entries = VirtualEnderRegistry.getInstance()
                .getEntryNames(cover.getOwner(), cover.getEntryType())
                .stream()
                .map(name -> requireRegistryEntry(cover, name))
                .sorted(Comparator.comparing(VirtualEntry::getColorStr))
                .map(entry -> serializeEntry(entry, player.registryAccess()))
                .toList();
        PacketDistributor.sendToPlayer(player, new SPacketEnderLinkChannelsToClient(pos, side,
                cover.coverDefinition.getId(), entries));
    }

    private static JsonElement serializeEntry(VirtualEntry entry, HolderLookup.Provider registries) {
        return entry.serializeJson(registries);
    }

    private static VirtualEntry requireRegistryEntry(AbstractEnderLinkCover<?> cover, String channelName) {
        VirtualEntry entry = VirtualEnderRegistry.getInstance()
                .getEntry(cover.getOwner(), cover.getEntryType(), channelName);
        if (entry == null) {
            throw new IllegalStateException("Ender link channel is missing: " + channelName);
        }
        return entry;
    }

    private static void deserializeEntry(VirtualEntry entry, JsonElement data, HolderLookup.Provider registries) {
        entry.deserializeJson(registries, data);
    }

    private static String normalizeColorInput(String color) {
        String normalized = color;
        if (normalized == null || !COLOR_INPUT_PATTERN.matcher(normalized).matches()) {
            normalized = VirtualEntry.DEFAULT_COLOR;
        }
        if (normalized.length() < 8) {
            normalized += "F".repeat(8 - normalized.length());
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private static boolean isValidColorInput(String color) {
        return color != null && COLOR_INPUT_PATTERN.matcher(color).matches();
    }

    private static SyncFieldData requireFields(DataComponentMap payload, String actionName) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException(actionName + " action payload is missing field data.");
        }
        return fields;
    }

    private static String requireColor(DataComponentMap payload, ResourceLocation field) {
        String color = requireString(payload, field);
        if (!isValidColorInput(color)) {
            throw new IllegalArgumentException("Ender link cover action color is invalid: " + color);
        }
        return normalizeColorInput(color);
    }

    private static String requireString(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = requireFields(payload, "Ender link cover");
        String value = readString(fields, field);
        if (value == null) {
            throw new IllegalStateException("Ender link cover action payload is missing " + field + ".");
        }
        return value;
    }

    private static int requireInt(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = requireFields(payload, "Ender link cover");
        Integer value = readInt(fields, field);
        if (value == null) {
            throw new IllegalStateException("Ender link cover action payload is missing " + field + ".");
        }
        return value;
    }

    private static boolean requireBoolean(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = requireFields(payload, "Ender link cover");
        Boolean value = readBoolean(fields, field);
        if (value == null) {
            throw new IllegalStateException("Ender link cover action payload is missing " + field + ".");
        }
        return value;
    }

    private static Permissions requirePermission(DataComponentMap payload, ResourceLocation field) {
        int ordinal = requireInt(payload, field);
        if (ordinal < 0 || ordinal >= Permissions.values().length) {
            throw new IllegalArgumentException("Ender link cover action permission is out of range: " + ordinal);
        }
        return Permissions.values()[ordinal];
    }

    private static IO requireIo(DataComponentMap payload, ResourceLocation field) {
        int ordinal = requireInt(payload, field);
        if (ordinal < 0 || ordinal >= IO.values().length) {
            throw new IllegalArgumentException("Ender link cover action io is out of range: " + ordinal);
        }
        IO value = IO.values()[ordinal];
        if (value != IO.IN && value != IO.OUT) {
            throw new IllegalArgumentException("Ender link cover action io is unsupported: " + value);
        }
        return value;
    }

    private static ManualIOMode requireManualIOMode(DataComponentMap payload, ResourceLocation field) {
        int ordinal = requireInt(payload, field);
        if (ordinal < 0 || ordinal >= ManualIOMode.values().length) {
            throw new IllegalArgumentException("Ender link cover action manual IO mode is out of range: " + ordinal);
        }
        return ManualIOMode.values()[ordinal];
    }

    private static boolean isValidConfigPayload(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        return fields != null &&
                isValidColorInput(readString(fields, CHANNEL_COLOR_FIELD)) &&
                isValidOrdinal(fields, PERMISSION_FIELD, Permissions.values().length) &&
                isValidIoOrdinal(fields, IO_FIELD) &&
                isValidOrdinal(fields, MANUAL_IO_FIELD, ManualIOMode.values().length) &&
                readBoolean(fields, WORKING_ENABLED_FIELD) != null;
    }

    private static boolean isValidDescriptionPayload(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        return fields != null &&
                readString(fields, CHANNEL_COLOR_FIELD) != null &&
                readString(fields, DESCRIPTION_FIELD) != null;
    }

    private static boolean isValidRequestPayload(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        Boolean request = fields == null ? null : readBoolean(fields, REQUEST_CHANNELS_FIELD);
        return request != null && request;
    }

    private static boolean isValidClearDescriptionPayload(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        String channelName = fields == null ? null : readString(fields, CHANNEL_COLOR_FIELD);
        return channelName != null && !channelName.isBlank();
    }

    private static boolean isValidOrdinal(SyncFieldData fields, ResourceLocation field, int size) {
        Integer ordinal = readInt(fields, field);
        return ordinal != null && ordinal >= 0 && ordinal < size;
    }

    private static boolean isValidIoOrdinal(SyncFieldData fields, ResourceLocation field) {
        Integer ordinal = readInt(fields, field);
        if (ordinal == null || ordinal < 0 || ordinal >= IO.values().length) {
            return false;
        }
        IO value = IO.values()[ordinal];
        return value == IO.IN || value == IO.OUT;
    }

    private static @Nullable String readString(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isString()) {
            return primitive.getAsString();
        }
        return null;
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

    protected enum Permissions implements SelectableEnum {

        PUBLIC("cover.ender_fluid_link.private.tooltip.disabled",
                GuiTextures.BUTTON_PUBLIC_PRIVATE.getSubTexture(0, 0, 1, 0.5)),

        PRIVATE("cover.ender_fluid_link.private.tooltip.enabled",
                GuiTextures.BUTTON_PUBLIC_PRIVATE.getSubTexture(0, 0.5, 1, 0.5));

        @Getter
        private final String tooltip;
        @Getter
        private final IGuiTexture icon;

        Permissions(String tooltip, IGuiTexture icon) {
            this.tooltip = tooltip;
            this.icon = icon;
        }
    }

    private static final class EnderLinkRootElement extends UIElement implements EnderLinkChannelListReceiver {

        private final Player player;
        private final UICoverHolder holder;
        private final AbstractEnderLinkCover<?> cover;
        private final UIElement mainGroup;
        private final UIElement mainChannelGroup;
        private final GTScrollerViewElement channelsGroup;
        private boolean showChannels;

        EnderLinkRootElement(Player player, UICoverHolder holder, AbstractEnderLinkCover<?> cover) {
            this.player = player;
            this.holder = holder;
            this.cover = cover;
            this.mainGroup = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, GROUP_WIDTH, 137);
            this.channelsGroup = new GTScrollerViewElement(0, 20, 170, 110);
            this.mainChannelGroup = UITemplate.setLDLib2Bounds(new UIElement(), 10, 20, 156, 20);
            setId("ender_link_root");
            UITemplate.setLDLib2Bounds(this, 0, 0, GROUP_WIDTH, 137 + 82);
            style(style -> style.backgroundTexture(GuiTextures.BACKGROUND));
            initWidgets();
        }

        @Override
        public boolean acceptsEnderLinkChannelList(BlockPos pos, Direction side, ResourceLocation coverDefinitionId) {
            return holder.getPos().equals(pos) &&
                    holder.getSide() == side &&
                    holder.getCoverDefinitionId().equals(coverDefinitionId);
        }

        @Override
        public void receiveEnderLinkChannelList(List<JsonElement> entries, HolderLookup.Provider registries) {
            List<VirtualEntry> deserialized = new ArrayList<>(entries.size());
            for (JsonElement data : entries) {
                VirtualEntry entry = cover.getEntryType().createInstance();
                deserializeEntry(entry, data, registries);
                deserialized.add(entry);
            }
            addChannelWidgets(deserialized);
        }

        private void initWidgets() {
            int currentX = 0;
            UIElement titleGroup = UITemplate.setLDLib2Bounds(new UIElement(), 10, 5, GROUP_WIDTH, 20);

            addChild(titleGroup);
            addChild(mainGroup);
            addChild(channelsGroup);
            addChild(UITemplate.bindPlayerInventoryLDLib2(player.getInventory(), GuiTextures.SLOT, 7, 137, true));

            channelsGroup.setVisible(false);
            titleGroup.addChild(createToggleButton());
            titleGroup.addChild(createLDLib2Label(15, 3, GROUP_WIDTH - 15, 10, cover.getUITitle(), true));

            mainChannelGroup.addChild(createToggleButtonForPrivacy(currentX));
            currentX += WIDGET_BOARD + 2;
            mainChannelGroup.addChild(createColorBlockElement(currentX, cover::getColor));
            currentX += WIDGET_BOARD + 2;
            mainChannelGroup.addChild(createChannelColorInput(currentX));

            mainChannelGroup.addChild(new ConfirmTextInputElement(0, WIDGET_BOARD + 2, GROUP_WIDTH - WIDGET_BOARD,
                    WIDGET_BOARD, getEntryDescription(), description -> description,
                    description -> description,
                    value -> cover.setLDLib2Description(player, holder, value))
                    .setTooltip("cover.ender_fluid_link.tooltip.channel_description"));

            mainGroup.addChild(mainChannelGroup);
            mainGroup.addChild(createWorkingEnabledButton());
            addEnumSelectorElements();
            mainGroup.addChild(
                    cover.addVirtualEntryLDLib2Element(cover.getEntry(), 146, WIDGET_BOARD, WIDGET_BOARD,
                            WIDGET_BOARD, true));

            FilterHandler<?, ?> filterHandler = cover.getFilterHandler();
            if (filterHandler != null) {
                mainGroup.addChild(filterHandler.createFilterSlotLDLib2UI(117, 108));
                mainGroup.addChild(filterHandler.createFilterConfigLDLib2UI(10, 72, 156, 60));
            }
        }

        @Override
        public void screenTick() {
            updateVisibility();
            super.screenTick();
        }

        private GTToggleButtonElement createToggleButton() {
            GTToggleButtonElement button = new GTToggleButtonElement(0, 0, 12, 12, GuiTextures.BUTTON_LIST,
                    () -> showChannels, this::setShowChannels)
                    .setShouldUseBaseBackground();
            button.style(style -> style.tooltips("cover.ender_fluid_link.tooltip.list_button"));
            return button;
        }

        @Contract("_ -> new")
        private @NotNull UIElement createToggleButtonForPrivacy(int currentX) {
            return GTEnumSelectorElement.selectable(currentX, 0, WIDGET_BOARD, WIDGET_BOARD, Permissions.values(),
                    cover::getPermission, value -> cover.setLDLib2Permission(player, holder, value));
        }

        private ColorBlockElement createColorBlockElement(int currentX, IntSupplier colorSupplier) {
            return new ColorBlockElement(currentX, 0, WIDGET_BOARD, WIDGET_BOARD, colorSupplier);
        }

        private ConfirmTextInputElement createChannelColorInput(int currentX) {
            int groupX = 10;
            int textInputWidth = (GROUP_WIDTH - groupX * 2) - currentX - WIDGET_BOARD - 2;
            return new ConfirmTextInputElement(currentX, 0, textInputWidth, WIDGET_BOARD, cover.colorStr,
                    value -> isValidColorInput(value) ? value : VirtualEntry.DEFAULT_COLOR,
                    AbstractEnderLinkCover::normalizeColorInput,
                    value -> cover.setLDLib2ChannelColor(player, holder, value))
                    .setTooltip("cover.ender_fluid_link.tooltip.channel_name");
        }

        @Contract(" -> new")
        private @NotNull GTToggleButtonElement createWorkingEnabledButton() {
            return new GTToggleButtonElement(116, 82, WIDGET_BOARD, WIDGET_BOARD, GuiTextures.BUTTON_POWER,
                    cover::isWorkingEnabled, value -> cover.setLDLib2WorkingEnabled(player, holder, value));
        }

        private void addEnumSelectorElements() {
            mainGroup.addChild(GTEnumSelectorElement.selectable(146, 82, WIDGET_BOARD, WIDGET_BOARD,
                    List.of(IO.IN, IO.OUT), cover::getIo, value -> cover.setLDLib2Io(player, holder, value)));
            mainGroup.addChild(GTEnumSelectorElement.selectable(146, 107, WIDGET_BOARD, WIDGET_BOARD,
                    ManualIOMode.VALUES, cover::getManualIOMode,
                    value -> cover.setLDLib2ManualIOMode(player, holder, value)));
        }

        private void addChannelWidgets(List<? extends VirtualEntry> entries) {
            channelsGroup.clearAllScrollViewChildren();
            int y = 1;
            for (var entry : entries.stream().sorted(Comparator.comparing(VirtualEntry::getColorStr)).toList()) {
                channelsGroup.addScrollViewChild(createChannelElement(entry, 10, y));
                y += 22;
            }
        }

        private @NotNull UIElement createChannelElement(@NotNull VirtualEntry entry, int x, int y) {
            int currentX = 0;
            int margin = 2;
            int availableWidth = TOTAL_WIDTH - (BUTTON_SIZE + margin) * 3;
            String description = entry.getDescription();
            ChannelElement channel = new ChannelElement(x, y, TOTAL_WIDTH, BUTTON_SIZE,
                    () -> cover.getChannelName().equals(cover.getChannelName(entry)),
                    () -> cover.setLDLib2ChannelColor(player, holder, entry.getColorStr()));

            channel.addChild(new ColorBlockElement(currentX, 0, BUTTON_SIZE, BUTTON_SIZE,
                    () -> VirtualEntry.parseColor(entry.getColorStr())));
            currentX += BUTTON_SIZE + margin;

            GTLabelElement channelName = createLDLib2Label(BUTTON_SIZE + margin, !description.isEmpty() ? 0 : 4,
                    availableWidth, 8, Component.literal(entry.getColorStr()));
            channelName.textStyle(style -> style.textAlignHorizontal(Horizontal.CENTER));
            channel.addChild(channelName);
            currentX += availableWidth + margin;

            if (!description.isEmpty()) {
                GTLabelElement descriptionLabel = createLDLib2Label(BUTTON_SIZE + margin, 10, availableWidth, 8,
                        Component.literal(ChatFormatting.DARK_GRAY + description));
                descriptionLabel.setFontSize(6.0f);
                channel.addChild(descriptionLabel);
            }

            channel.addChild(cover.addVirtualEntryLDLib2Element(entry, currentX, 0, BUTTON_SIZE, BUTTON_SIZE, false));
            currentX += BUTTON_SIZE + margin;

            GTButtonElement clearButton = new GTButtonElement(currentX, 0, BUTTON_SIZE, BUTTON_SIZE,
                    GuiTextures.BUTTON_CLEAR_GRID, event -> cover.clearLDLib2Description(player, holder, entry));
            clearButton.noText();
            clearButton.style(style -> style.tooltips("cover.ender_fluid_link.tooltip.clear_button"));
            channel.addChild(clearButton);

            return channel;
        }

        private void setShowChannels(boolean showChannels) {
            this.showChannels = showChannels;
            updateVisibility();
            if (showChannels) {
                cover.requestLDLib2Channels(player, holder);
            }
        }

        private void updateVisibility() {
            mainGroup.setVisible(!showChannels);
            channelsGroup.setVisible(showChannels);
        }

        private String getEntryDescription() {
            VirtualEntry entry = cover.getEntry();
            return entry == null ? "" : entry.getDescription();
        }
    }

    private static final class ConfirmTextInputElement extends UIElement {

        private final GTTextFieldElement textField;
        private final Function<String, String> validator;
        private final Function<String, String> returnValidator;
        private final Consumer<String> textResponder;
        private String inputText;

        ConfirmTextInputElement(int x, int y, int width, int height, String text,
                                Function<String, String> validator,
                                Function<String, String> returnValidator,
                                Consumer<String> textResponder) {
            this.validator = validator;
            this.returnValidator = returnValidator;
            this.textResponder = textResponder;
            this.inputText = text == null ? "" : text;
            UITemplate.setLDLib2Bounds(this, x, y, width, height);

            GTButtonElement button = new GTButtonElement(width - height, 0, height, height,
                    GuiTextures.group(GuiTextures.VANILLA_BUTTON, GuiTextures.BUTTON_CHECK), event -> confirm());
            button.noText();
            addChild(button);

            textField = new GTTextFieldElement(1, 1, width - height - 4, height - 2);
            textField.setAnyString();
            textField.setText(inputText, false);
            textField.setTextResponder(value -> inputText = validator.apply(value));
            textField.textFieldStyle(style -> style
                    .textColor(0x404040)
                    .textShadow(false));
            addChild(textField);
        }

        ConfirmTextInputElement setTooltip(String tooltip) {
            textField.style(style -> style.tooltips(tooltip));
            return this;
        }

        private void confirm() {
            inputText = returnValidator.apply(inputText);
            textField.setText(inputText, false);
            textResponder.accept(inputText);
        }
    }

    private static class ColorBlockElement extends UIElement {

        private static boolean showAlpha;

        private final IntSupplier colorSupplier;

        ColorBlockElement(int x, int y, int width, int height, IntSupplier colorSupplier) {
            this.colorSupplier = colorSupplier;
            UITemplate.setLDLib2Bounds(this, x, y, width, height);
            addEventListener(UIEvents.MOUSE_DOWN, event -> {
                showAlpha = !showAlpha;
                event.stopPropagation();
            });
        }

        @Override
        public void drawBackgroundAdditional(GUIContext guiContext) {
            super.drawBackgroundAdditional(guiContext);
            int x = Math.round(getPositionX()) + 1;
            int y = Math.round(getPositionY()) + 1;
            int width = Math.round(getSizeWidth()) - 2;
            int height = Math.round(getSizeHeight()) - 2;
            int color = colorSupplier.getAsInt();
            int displayedColor = showAlpha ? color : color | 0xFF000000;
            guiContext.graphics.fill(x, y, x + width, y + height, displayedColor);
            DrawerHelper.drawBorder(guiContext.graphics, x, y, width, height, 0xFF000000, 1);
        }
    }

    private static final class ChannelElement extends UIElement {

        private final BooleanSupplier selectedSupplier;
        private final Runnable onSelected;

        ChannelElement(int x, int y, int width, int height, BooleanSupplier selectedSupplier,
                       Runnable onSelected) {
            this.selectedSupplier = selectedSupplier;
            this.onSelected = onSelected;
            UITemplate.setLDLib2Bounds(this, x, y, width, height);
            addEventListener(UIEvents.MOUSE_DOWN, event -> {
                this.onSelected.run();
                event.stopPropagation();
            });
        }

        @Override
        public void drawBackgroundAdditional(GUIContext guiContext) {
            super.drawBackgroundAdditional(guiContext);
            if (selectedSupplier.getAsBoolean()) {
                DrawerHelper.drawBorder(guiContext.graphics, Math.round(getPositionX()) - 1,
                        Math.round(getPositionY()) - 1, Math.round(getSizeWidth()) + 2,
                        Math.round(getSizeHeight()) + 2, 0xFFFFFFFF, 1);
            }
        }
    }

    private static GTLabelElement createLDLib2Label(int x, int y, int width, int height, String text,
                                                    boolean translate) {
        GTLabelElement label = new GTLabelElement(x, y, width, height, text, translate);
        styleLDLib2Label(label);
        return label;
    }

    private static GTLabelElement createLDLib2Label(int x, int y, int width, int height, Component text) {
        GTLabelElement label = new GTLabelElement(x, y, width, height, text);
        styleLDLib2Label(label);
        return label;
    }

    private static void styleLDLib2Label(GTLabelElement label) {
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
    }

    private static final class EnderLinkCoverConfigActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_ENDER_LINK_COVER_CONFIG_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof AbstractEnderLinkCover<?>;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            return isValidConfigPayload(payload);
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator() && context.holder() instanceof AbstractEnderLinkCover<?>;
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof AbstractEnderLinkCover<?> cover)) {
                throw new IllegalStateException("Ender link cover config action received a non-ender-link cover.");
            }
            applyEnderLinkCoverConfig(cover, context.payload());
        }
    }

    private static final class EnderLinkChannelListActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return REQUEST_ENDER_LINK_CHANNELS_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof AbstractEnderLinkCover<?>;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            return isValidRequestPayload(payload);
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator() && context.holder() instanceof AbstractEnderLinkCover<?>;
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof AbstractEnderLinkCover<?> cover)) {
                throw new IllegalStateException("Ender link channel list action received a non-ender-link cover.");
            }
            sendChannelList(context.player(), cover, context.pos(), context.side());
        }
    }

    private static final class EnderLinkDescriptionActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_ENDER_LINK_CHANNEL_DESCRIPTION_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof AbstractEnderLinkCover<?>;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            return isValidDescriptionPayload(payload);
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator() && context.holder() instanceof AbstractEnderLinkCover<?>;
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof AbstractEnderLinkCover<?> cover)) {
                throw new IllegalStateException(
                        "Ender link description action received a non-ender-link cover.");
            }
            String channelName = requireString(context.payload(), CHANNEL_COLOR_FIELD);
            String description = requireString(context.payload(), DESCRIPTION_FIELD);
            requireRegistryEntry(cover, channelName).setDescription(description);
            cover.markEnderLinkUIChanged();
        }
    }

    private static final class EnderLinkClearDescriptionActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return CLEAR_ENDER_LINK_CHANNEL_DESCRIPTION_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof AbstractEnderLinkCover<?>;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            return isValidClearDescriptionPayload(payload);
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator() && context.holder() instanceof AbstractEnderLinkCover<?>;
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof AbstractEnderLinkCover<?> cover)) {
                throw new IllegalStateException(
                        "Ender link clear description action received a non-ender-link cover.");
            }
            String channelName = requireString(context.payload(), CHANNEL_COLOR_FIELD);
            requireRegistryEntry(cover, channelName).setDescription("");
            sendChannelList(context.player(), cover, context.pos(), context.side());
        }
    }
}

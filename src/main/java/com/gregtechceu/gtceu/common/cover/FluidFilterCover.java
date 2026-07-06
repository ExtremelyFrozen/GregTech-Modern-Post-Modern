package com.gregtechceu.gtceu.common.cover;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.ConfigCopyHelper;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.cover.IUICover;
import com.gregtechceu.gtceu.api.cover.filter.FluidFilter;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTEnumSelectorElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.factory.CoverUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2CoverUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.UICoverHolder;
import com.gregtechceu.gtceu.api.gui.widget.EnumSelectorWidget;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.fluid.FluidHandlerDelegate;
import com.gregtechceu.gtceu.api.transfer.fluid.IFluidHandlerModifiable;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.cover.data.FilterMode;
import com.gregtechceu.gtceu.common.cover.data.ManualIOMode;

import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.fluids.FluidStack;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

public class FluidFilterCover extends CoverBehavior implements IUICover, LDLib2CoverUIProvider {

    private static final ResourceLocation SET_FLUID_FILTER_COVER_CONFIG_ACTION = GTCEu
            .id("set_fluid_filter_cover_config");
    private static final ResourceLocation FILTER_MODE_FIELD = SyncFieldData.key("filterMode");
    private static final ResourceLocation MANUAL_IO_FIELD = SyncFieldData.key("manualIO");

    static {
        SyncActionDispatchers.server().register(new FluidFilterCoverConfigActionHandler());
    }

    protected FluidFilter fluidFilter;
    @SaveField
    @SyncToClient
    @Getter
    protected FilterMode filterMode = FilterMode.FILTER_INSERT;
    private FilteredFluidHandlerWrapper fluidFilterWrapper;
    @SaveField
    @Setter
    @Getter
    protected ManualIOMode allowFlow = ManualIOMode.DISABLED;

    public FluidFilterCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
    }

    public void setFilterMode(FilterMode filterMode) {
        this.filterMode = filterMode;
        syncDataHolder.markClientSyncFieldDirty("filterMode");
    }

    @Override
    public boolean canAttach() {
        return super.canAttach() && coverHolder.getFluidHandlerCap(attachedSide, false) != null;
    }

    public FluidFilter getFluidFilter() {
        if (fluidFilter == null) {
            fluidFilter = FluidFilter.loadFilter(attachItem);
        }
        return fluidFilter;
    }

    @Override
    public @Nullable IFluidHandlerModifiable getFluidHandlerCap(@Nullable IFluidHandlerModifiable defaultValue) {
        if (defaultValue == null) {
            return null;
        }

        if (fluidFilterWrapper == null || fluidFilterWrapper.delegate != defaultValue) {
            this.fluidFilterWrapper = new FilteredFluidHandlerWrapper(defaultValue);
        }

        return fluidFilterWrapper;
    }

    @Override
    public Widget createUIWidget() {
        final var group = new WidgetGroup(0, 0, 178, 85);
        group.addWidget(new LabelWidget(60, 5, attachItem.getDescriptionId()));
        group.addWidget(new EnumSelectorWidget<>(35, 25, 18, 18,
                FilterMode.VALUES, filterMode, this::setFilterMode));
        group.addWidget(new EnumSelectorWidget<>(35, 45, 18, 18, ManualIOMode.VALUES, allowFlow, this::setAllowFlow));
        group.addWidget(getFluidFilter().openConfigurator(62, 25));
        return group;
    }

    @Override
    public boolean canCreateLDLib2UI(Player player, UICoverHolder holder) {
        return holder.getCover() == this && getFluidFilter().supportsLDLib2Configurator();
    }

    @Override
    public UI createLDLib2UI(Player player, UICoverHolder holder) {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, 176, 167);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND));

        root.addChild(createLDLib2Label());
        root.addChild(new GTEnumSelectorElement<>(35, 25, 18, 18, FilterMode.VALUES, this::getFilterMode,
                mode -> setLDLib2FilterMode(player, holder, mode), FilterMode::getIcon, FilterMode::getTooltip));
        root.addChild(new GTEnumSelectorElement<>(35, 45, 18, 18, ManualIOMode.VALUES, this::getAllowFlow,
                mode -> setLDLib2ManualIO(player, holder, mode), ManualIOMode::getIcon, ManualIOMode::getTooltip));
        root.addChild(getFluidFilter().openLDLib2Configurator(62, 25));
        root.addChild(UITemplate.bindPlayerInventoryLDLib2(player.getInventory(), GuiTextures.SLOT, 7, 85, true));
        return UI.of(root);
    }

    private GTLabelElement createLDLib2Label() {
        GTLabelElement label = new GTLabelElement(60, 5, 111, 10, attachItem.getDescriptionId(), true);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private void setLDLib2FilterMode(Player player, UICoverHolder holder, FilterMode mode) {
        setFilterMode(mode);
        sendLDLib2ConfigAction(player, holder);
    }

    private void setLDLib2ManualIO(Player player, UICoverHolder holder, ManualIOMode mode) {
        setAllowFlow(mode);
        sendLDLib2ConfigAction(player, holder);
    }

    private void sendLDLib2ConfigAction(Player player, UICoverHolder holder) {
        if (player.level().isClientSide()) {
            CoverUIHelper.sendAction(holder, createSetFluidFilterCoverConfigAction(getFilterMode(), getAllowFlow()));
        }
    }

    private static SyncActionData createSetFluidFilterCoverConfigAction(FilterMode filterMode,
                                                                        ManualIOMode manualIOMode) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(FILTER_MODE_FIELD, new JsonPrimitive(filterMode.ordinal()))
                        .put(MANUAL_IO_FIELD, new JsonPrimitive(manualIOMode.ordinal()))
                        .build())
                .build();
        return new SyncActionData(SET_FLUID_FILTER_COVER_CONFIG_ACTION, 0, payload);
    }

    private class FilteredFluidHandlerWrapper extends FluidHandlerDelegate {

        public FilteredFluidHandlerWrapper(IFluidHandlerModifiable delegate) {
            super(delegate);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (filterMode == FilterMode.FILTER_EXTRACT) {
                if (allowFlow == ManualIOMode.DISABLED) {
                    return 0;
                }
                if (allowFlow == ManualIOMode.UNFILTERED) {
                    return super.fill(resource, action);
                }
            }
            if (!getFluidFilter().test(resource)) {
                return 0;
            }
            return super.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (filterMode == FilterMode.FILTER_INSERT) {
                if (allowFlow == ManualIOMode.DISABLED) {
                    return FluidStack.EMPTY;
                }
                if (allowFlow == ManualIOMode.UNFILTERED) {
                    return super.drain(resource, action);
                }
            }
            if (!getFluidFilter().test(resource)) {
                return FluidStack.EMPTY;
            }
            return super.drain(resource, action);
        }
    }

    @Override
    public DataComponentMap copyConfig(HolderLookup.Provider registries) {
        return ConfigCopyHelper.withFields(super.copyConfig(registries), fields -> fields
                .put(SyncFieldData.key("manualIO"),
                        ConfigCopyHelper.intValue(getAllowFlow().ordinal()))
                .put(SyncFieldData.key("filterMode"),
                        ConfigCopyHelper.intValue(getFilterMode().ordinal()))
                .put(SyncFieldData.key("filter"),
                        ConfigCopyHelper.encodeItem(registries, attachItem)));
    }

    @Override
    public void pasteConfig(ServerPlayer player, HolderLookup.Provider registries, DataComponentMap config) {
        setAllowFlow(ManualIOMode.values()[ConfigCopyHelper.getInt(config, "manualIO")]);
        setFilterMode(FilterMode.values()[ConfigCopyHelper.getInt(config, "filterMode")]);
        fluidFilter = FluidFilter.loadFilter(ConfigCopyHelper.decodeItem(registries, ConfigCopyHelper.getField(config,
                "filter")));
        super.pasteConfig(player, registries, config);
    }

    private static final class FluidFilterCoverConfigActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_FLUID_FILTER_COVER_CONFIG_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof FluidFilterCover cover &&
                    cover.getFluidFilter().supportsLDLib2Configurator();
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null &&
                    isValidOrdinal(fields, FILTER_MODE_FIELD, FilterMode.VALUES.length) &&
                    isValidOrdinal(fields, MANUAL_IO_FIELD, ManualIOMode.VALUES.length);
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof FluidFilterCover cover)) {
                throw new IllegalStateException("Fluid filter cover config action received a non-fluid-filter cover.");
            }
            int filterModeOrdinal = requireOrdinal(context.payload(), FILTER_MODE_FIELD, FilterMode.VALUES.length);
            int manualIOOrdinal = requireOrdinal(context.payload(), MANUAL_IO_FIELD, ManualIOMode.VALUES.length);
            cover.setFilterMode(FilterMode.VALUES[filterModeOrdinal]);
            cover.setAllowFlow(ManualIOMode.VALUES[manualIOOrdinal]);
        }
    }

    private static boolean isValidOrdinal(SyncFieldData fields, ResourceLocation field, int valueCount) {
        Integer ordinal = readOrdinal(fields, field);
        return ordinal != null && ordinal >= 0 && ordinal < valueCount;
    }

    private static int requireOrdinal(DataComponentMap payload, ResourceLocation field, int valueCount) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Fluid filter cover config action payload is missing field data.");
        }
        Integer ordinal = readOrdinal(fields, field);
        if (ordinal == null) {
            throw new IllegalStateException("Fluid filter cover config action payload is missing " + field + ".");
        }
        if (ordinal < 0 || ordinal >= valueCount) {
            throw new IllegalArgumentException("Fluid filter cover config action ordinal is out of range: " + ordinal);
        }
        return ordinal;
    }

    private static @Nullable Integer readOrdinal(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            return primitive.getAsInt();
        }
        return null;
    }
}

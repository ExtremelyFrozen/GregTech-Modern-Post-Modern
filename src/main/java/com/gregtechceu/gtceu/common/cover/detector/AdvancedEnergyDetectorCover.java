package com.gregtechceu.gtceu.common.cover.detector;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.ConfigCopyHelper;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.capability.IEnergyInfoProvider;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTLongInputElement;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
import com.gregtechceu.gtceu.api.gui.factory.CoverUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2CoverUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.UICoverHolder;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.utils.GTMath;

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

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.math.BigInteger;

import static com.gregtechceu.gtceu.utils.RedstoneUtil.computeLatchedRedstoneBetweenValues;

public class AdvancedEnergyDetectorCover extends EnergyDetectorCover implements LDLib2CoverUIProvider {

    private static final int DEFAULT_MIN_PERCENT = 33;
    private static final int DEFAULT_MAX_PERCENT = 66;
    private static final BigInteger LONG_MAX_VALUE = BigInteger.valueOf(Long.MAX_VALUE);
    private static final ResourceLocation SET_ADVANCED_ENERGY_DETECTOR_CONFIG_ACTION = GTCEu
            .id("set_advanced_energy_detector_config");
    private static final ResourceLocation MIN_FIELD = SyncFieldData.key("min");
    private static final ResourceLocation MAX_FIELD = SyncFieldData.key("max");
    private static final ResourceLocation USE_PERCENT_FIELD = SyncFieldData.key("usePercent");
    private static final ResourceLocation INVERTED_FIELD = SyncFieldData.key("inverted");

    static {
        SyncActionDispatchers.server().register(new AdvancedEnergyDetectorConfigActionHandler());
    }

    @SaveField
    @SyncToClient
    @Getter
    public long minValue, maxValue;

    @SaveField
    @SyncToClient
    @Getter
    private boolean usePercent;

    private @Nullable GTLongInputElement minValueLDLib2Input;
    private @Nullable GTLongInputElement maxValueLDLib2Input;

    public AdvancedEnergyDetectorCover(CoverDefinition definition, ICoverable coverHolder, Direction attachedSide) {
        super(definition, coverHolder, attachedSide);
        this.minValue = DEFAULT_MIN_PERCENT;
        this.maxValue = DEFAULT_MAX_PERCENT;
        this.usePercent = true;
    }

    @Override
    protected void update() {
        if (coverHolder.getOffsetTimer() % 20 != 0) return;

        IEnergyInfoProvider energyInfoProvider = getEnergyInfoProvider();
        if (energyInfoProvider == null) return;

        IEnergyInfoProvider.EnergyInfo energyInfo = energyInfoProvider.getEnergyInfo();
        boolean isBigInt = energyInfoProvider.supportsBigIntEnergyValues();

        if (isBigInt) {
            if (usePercent) {
                if (energyInfo.capacity().compareTo(BigInteger.ZERO) > 0) {
                    float ratio = GTMath.ratio(energyInfo.stored(), energyInfo.capacity());
                    setRedstoneSignalOutput(computeLatchedRedstoneBetweenValues(ratio * 100, maxValue,
                            minValue, isInverted(), redstoneSignalOutput));
                } else {
                    setRedstoneSignalOutput(isInverted() ? 15 : 0);
                }
            } else {
                setRedstoneSignalOutput(computeLatchedRedstoneBetweenValues(energyInfo.stored(),
                        BigInteger.valueOf(this.maxValue), BigInteger.valueOf(this.minValue),
                        isInverted(), redstoneSignalOutput));
            }
        } else {
            if (usePercent) {
                if (energyInfo.capacity().longValue() > 0) {
                    float ratio = energyInfo.stored().floatValue() / energyInfo.capacity().floatValue();
                    setRedstoneSignalOutput(computeLatchedRedstoneBetweenValues(ratio * 100, maxValue,
                            minValue, isInverted(), redstoneSignalOutput));
                } else {
                    setRedstoneSignalOutput(isInverted() ? 15 : 0);
                }
            } else {
                setRedstoneSignalOutput(computeLatchedRedstoneBetweenValues(energyInfo.stored().longValue(),
                        this.maxValue, this.minValue,
                        isInverted(), redstoneSignalOutput));
            }
        }
    }

    public void setMinValue(long minValue) {
        long clamped = Math.max(minValue, 0L);
        if (this.minValue != clamped) {
            this.minValue = clamped;
            syncDataHolder.markClientSyncFieldDirty("minValue");
        }
    }

    public void setMaxValue(long maxValue) {
        long clamped = Math.max(maxValue, 0L);
        if (this.maxValue != clamped) {
            this.maxValue = clamped;
            syncDataHolder.markClientSyncFieldDirty("maxValue");
        }
    }

    public void setUsePercent(boolean usePercent) {
        setUsePercent(usePercent, false);
    }

    private void setUsePercentFromUI(boolean usePercent) {
        setUsePercent(usePercent, true);
    }

    private void setUsePercent(boolean usePercent, boolean convertValues) {
        var wasPercent = this.usePercent;
        if (this.usePercent != usePercent) {
            this.usePercent = usePercent;
            if (convertValues) {
                convertMinMaxValues(wasPercent);
            }
            syncDataHolder.markClientSyncFieldDirty("usePercent");
        }

        initializeLDLib2MinMaxInputs();
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
        UITemplate.setLDLib2Bounds(root, 0, 0, 176, 187);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND));

        root.addChild(createLDLib2Label(10, 5, 156, 10, "cover.advanced_energy_detector.label"));
        root.addChild(createLDLib2Label(10, 55, 25, 10, "cover.advanced_energy_detector.min"));
        root.addChild(createLDLib2Label(10, 80, 25, 10, "cover.advanced_energy_detector.max"));
        minValueLDLib2Input = new GTLongInputElement(40, 50, 126, 20, this::getMinValue,
                value -> setLDLib2MinValue(player, holder, value));
        maxValueLDLib2Input = new GTLongInputElement(40, 75, 126, 20, this::getMaxValue,
                value -> setLDLib2MaxValue(player, holder, value));
        initializeLDLib2MinMaxInputs();
        root.addChild(minValueLDLib2Input);
        root.addChild(maxValueLDLib2Input);
        root.addChild(new GTToggleButtonElement(9, 20, 20, 20, GuiTextures.INVERT_REDSTONE_BUTTON,
                this::isInverted, inverted -> setLDLib2Inverted(player, holder, inverted))
                .isMultiLang()
                .setTooltipText("cover.advanced_energy_detector.invert"));
        root.addChild(new GTToggleButtonElement(147, 20, 20, 20, GuiTextures.ENERGY_DETECTOR_COVER_MODE_BUTTON,
                this::isUsePercent, usePercent -> setLDLib2UsePercent(player, holder, usePercent))
                .isMultiLang()
                .setTooltipText("cover.advanced_energy_detector.use_percent"));
        root.addChild(UITemplate.bindPlayerInventoryLDLib2(player.getInventory(), GuiTextures.SLOT, 7, 105, true));
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

    private void setLDLib2MinValue(Player player, UICoverHolder holder, long value) {
        setMinValue(value);
        sendLDLib2ConfigAction(player, holder);
    }

    private void setLDLib2MaxValue(Player player, UICoverHolder holder, long value) {
        setMaxValue(value);
        sendLDLib2ConfigAction(player, holder);
    }

    private void setLDLib2UsePercent(Player player, UICoverHolder holder, boolean usePercent) {
        setUsePercentFromUI(usePercent);
        sendLDLib2ConfigAction(player, holder);
    }

    private void setLDLib2Inverted(Player player, UICoverHolder holder, boolean inverted) {
        setInverted(inverted);
        sendLDLib2ConfigAction(player, holder);
    }

    private void sendLDLib2ConfigAction(Player player, UICoverHolder holder) {
        if (player.level().isClientSide()) {
            CoverUIHelper.sendAction(holder, createSetAdvancedEnergyDetectorConfigAction(
                    getMinValue(), getMaxValue(), isUsePercent(), isInverted()));
        }
    }

    private static SyncActionData createSetAdvancedEnergyDetectorConfigAction(long min, long max, boolean usePercent,
                                                                              boolean inverted) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(MIN_FIELD, new JsonPrimitive(min))
                        .put(MAX_FIELD, new JsonPrimitive(max))
                        .put(USE_PERCENT_FIELD, new JsonPrimitive(usePercent))
                        .put(INVERTED_FIELD, new JsonPrimitive(inverted))
                        .build())
                .build();
        return new SyncActionData(SET_ADVANCED_ENERGY_DETECTOR_CONFIG_ACTION, 0, payload);
    }

    private void initializeLDLib2MinMaxInputs() {
        if (minValueLDLib2Input == null || maxValueLDLib2Input == null)
            return;

        long max = usePercent ? 100L : getEnergyCapacity();
        minValueLDLib2Input.setMin(0L).setMax(max);
        maxValueLDLib2Input.setMin(0L).setMax(max);
    }

    private void convertMinMaxValues(boolean wasPercent) {
        long energyCapacity = getEnergyCapacity();
        if (usePercent && !wasPercent) {
            setMinValue(energyToPercent(minValue, energyCapacity));
            setMaxValue(energyToPercent(maxValue, energyCapacity));
        } else if (!usePercent && wasPercent) {
            setMinValue(percentToEnergy(minValue, energyCapacity));
            setMaxValue(percentToEnergy(maxValue, energyCapacity));
        }
    }

    private long getEnergyCapacity() {
        IEnergyInfoProvider energyInfoProvider = getEnergyInfoProvider();
        if (energyInfoProvider == null) {
            GTCEu.LOGGER.error("Advanced energy detector cover has no energy info provider at {}.",
                    coverHolder.getBlockPos());
            throw new IllegalStateException("Advanced energy detector cover has no energy info provider.");
        }

        BigInteger energyCapacity = energyInfoProvider.getEnergyInfo().capacity();
        if (energyCapacity.compareTo(LONG_MAX_VALUE) > 0) {
            return Long.MAX_VALUE;
        }
        return energyCapacity.longValue();
    }

    private static long energyToPercent(long value, long energyCapacity) {
        if (energyCapacity <= 0) {
            return 0;
        }
        return GTMath.clamp((long) (((double) value / energyCapacity) * 100), 0, 100);
    }

    private static long percentToEnergy(long value, long energyCapacity) {
        if (energyCapacity <= 0) {
            return 0;
        }
        return GTMath.clamp((long) Math.ceil((value / 100.0) * energyCapacity), 0, energyCapacity);
    }

    @Override
    public DataComponentMap copyConfig(HolderLookup.Provider registries) {
        return ConfigCopyHelper.withFields(super.copyConfig(registries), fields -> fields
                .put(SyncFieldData.key("min"),
                        ConfigCopyHelper.longValue(minValue))
                .put(SyncFieldData.key("max"),
                        ConfigCopyHelper.longValue(maxValue))
                .put(SyncFieldData.key("percent"),
                        ConfigCopyHelper.booleanValue(usePercent)));
    }

    @Override
    public void pasteConfig(ServerPlayer player, HolderLookup.Provider registries, DataComponentMap config) {
        setMinValue(ConfigCopyHelper.getLong(config, "min"));
        setMaxValue(ConfigCopyHelper.getLong(config, "max"));
        setUsePercent(ConfigCopyHelper.getBoolean(config, "percent"));
        super.pasteConfig(player, registries, config);
    }

    private static final class AdvancedEnergyDetectorConfigActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_ADVANCED_ENERGY_DETECTOR_CONFIG_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof AdvancedEnergyDetectorCover;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null &&
                    isValidNonNegativeLong(fields, MIN_FIELD) &&
                    isValidNonNegativeLong(fields, MAX_FIELD) &&
                    readBoolean(fields, USE_PERCENT_FIELD) != null &&
                    readBoolean(fields, INVERTED_FIELD) != null;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof AdvancedEnergyDetectorCover cover)) {
                throw new IllegalStateException("Advanced energy detector config action received a non-energy detector.");
            }
            cover.setUsePercent(requireBoolean(context.payload(), USE_PERCENT_FIELD));
            cover.setMinValue(requireNonNegativeLong(context.payload(), MIN_FIELD));
            cover.setMaxValue(requireNonNegativeLong(context.payload(), MAX_FIELD));
            cover.setInverted(requireBoolean(context.payload(), INVERTED_FIELD));
        }
    }

    private static boolean isValidNonNegativeLong(SyncFieldData fields, ResourceLocation field) {
        Long value = readLong(fields, field);
        return value != null && value >= 0;
    }

    private static long requireNonNegativeLong(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Advanced energy detector config action payload is missing field data.");
        }
        Long value = readLong(fields, field);
        if (value == null) {
            throw new IllegalStateException("Advanced energy detector config action payload is missing " + field + ".");
        }
        if (value < 0) {
            throw new IllegalArgumentException("Advanced energy detector config action value is negative: " + value);
        }
        return value;
    }

    private static boolean requireBoolean(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Advanced energy detector config action payload is missing field data.");
        }
        Boolean value = readBoolean(fields, field);
        if (value == null) {
            throw new IllegalStateException("Advanced energy detector config action payload is missing " + field + ".");
        }
        return value;
    }

    private static @Nullable Long readLong(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            return primitive.getAsLong();
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

package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.ILaserContainer;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTStringSelectorElement;
import com.gregtechceu.gtceu.api.gui.element.GTTextFieldElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.TieredMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class CreativeEnergyContainerMachine extends TieredMachine implements ILaserContainer, LDLib2MachineUIProvider {

    private static final ResourceLocation SET_CREATIVE_ENERGY_VOLTAGE_ACTION = GTCEu
            .id("set_creative_energy_voltage");
    private static final ResourceLocation SET_CREATIVE_ENERGY_AMPS_ACTION = GTCEu
            .id("set_creative_energy_amps");
    private static final ResourceLocation SET_CREATIVE_ENERGY_TIER_ACTION = GTCEu
            .id("set_creative_energy_tier");
    private static final ResourceLocation SET_CREATIVE_ENERGY_SOURCE_ACTION = GTCEu
            .id("set_creative_energy_source");
    private static final ResourceLocation VOLTAGE_FIELD = SyncFieldData.key("voltage");
    private static final ResourceLocation AMPS_FIELD = SyncFieldData.key("amps");
    private static final ResourceLocation TIER_FIELD = SyncFieldData.key("setTier");
    private static final ResourceLocation SOURCE_FIELD = SyncFieldData.key("source");

    static {
        SyncActionDispatchers.server().register(new CreativeEnergyVoltageActionHandler());
        SyncActionDispatchers.server().register(new CreativeEnergyAmpsActionHandler());
        SyncActionDispatchers.server().register(new CreativeEnergyTierActionHandler());
        SyncActionDispatchers.server().register(new CreativeEnergySourceActionHandler());
    }

    @SaveField
    @SyncToClient
    private long voltage = 0;
    @SaveField
    @SyncToClient
    private int amps = 1;
    @SaveField
    @SyncToClient
    private int setTier = 0;
    @SaveField
    @SyncBoth
    private boolean active = false;
    @SaveField
    @SyncToClient
    private boolean source = true;
    @SaveField
    private long energyIOPerSec = 0;
    @SyncToClient
    private long lastAverageEnergyIOPerTick = 0;
    private long ampsReceived = 0;
    private boolean doExplosion = false;

    public CreativeEnergyContainerMachine(BlockEntityCreationInfo info) {
        super(info, GTValues.MAX);
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Override
    public void onLoad() {
        super.onLoad();
        subscribeServerTick(this::updateEnergyTick);
    }

    //////////////////////////////////////
    // ********** MISC ***********//
    //////////////////////////////////////

    protected void updateEnergyTick() {
        if (getOffsetTimer() % 20 == 0) {
            this.setIOSpeed(energyIOPerSec / 20);
            energyIOPerSec = 0;
            if (doExplosion) {
                getLevel().explode(null, getBlockPos().getX() + 0.5, getBlockPos().getY() + 0.5,
                        getBlockPos().getZ() + 0.5,
                        1, Level.ExplosionInteraction.NONE);
                doExplosion = false;
            }
        }
        ampsReceived = 0;
        if (!active || !source || voltage <= 0 || amps <= 0) return;
        int ampsUsed = 0;
        for (var facing : GTUtil.DIRECTIONS) {
            var opposite = facing.getOpposite();
            IEnergyContainer container = GTCapabilityHelper.getEnergyContainer(getLevel(),
                    getBlockPos().relative(facing),
                    opposite);
            // Try to get laser capability
            if (container == null)
                container = GTCapabilityHelper.getLaser(getLevel(), getBlockPos().relative(facing), opposite);

            if (container != null && container.inputsEnergy(opposite) && container.getEnergyCanBeInserted() > 0) {
                ampsUsed += container.acceptEnergyFromNetwork(opposite, voltage, amps - ampsUsed);
                if (ampsUsed >= amps) {
                    break;
                }
            }
        }
        energyIOPerSec += ampsUsed * voltage;
    }

    @Override
    public long acceptEnergyFromNetwork(Direction side, long voltage, long amperage) {
        if (source || !active || ampsReceived >= amps) {
            return 0;
        }
        if (voltage > this.voltage) {
            if (doExplosion)
                return 0;
            doExplosion = true;
            return Math.min(amperage, getInputAmperage() - ampsReceived);
        }
        long amperesAccepted = Math.min(amperage, getInputAmperage() - ampsReceived);
        if (amperesAccepted > 0) {
            ampsReceived += amperesAccepted;
            energyIOPerSec += amperesAccepted * voltage;
            return amperesAccepted;
        }
        return 0;
    }

    @Override
    public boolean inputsEnergy(Direction side) {
        return !source;
    }

    @Override
    public boolean outputsEnergy(Direction side) {
        return source;
    }

    @Override
    public long changeEnergy(long differenceAmount) {
        if (source || !active) {
            return 0;
        }
        energyIOPerSec += differenceAmount;
        return differenceAmount;
    }

    @Override
    public long getEnergyStored() {
        return 69;
    }

    @Override
    public long getEnergyCapacity() {
        return 420;
    }

    @Override
    public long getInputAmperage() {
        return source ? 0 : amps;
    }

    @Override
    public long getInputVoltage() {
        return source ? 0 : voltage;
    }

    @Override
    public long getOutputVoltage() {
        return source ? voltage : 0;
    }

    @Override
    public long getOutputAmperage() {
        return source ? amps : 0;
    }

    public void setIOSpeed(long energyIOPerSec) {
        if (this.lastAverageEnergyIOPerTick != energyIOPerSec) {
            this.lastAverageEnergyIOPerTick = energyIOPerSec;
            syncDataHolder.markClientSyncFieldDirty("lastAverageEnergyIOPerTick");
        }
    }

    private void setVoltage(long voltage) {
        this.voltage = voltage;
        this.setTier = GTUtil.getTierByVoltage(voltage);
        syncDataHolder.markClientSyncFieldDirty("voltage");
        syncDataHolder.markClientSyncFieldDirty("setTier");
    }

    private void setAmps(int amps) {
        this.amps = Math.max(0, amps);
        syncDataHolder.markClientSyncFieldDirty("amps");
    }

    private void setActive(boolean active) {
        if (this.active == active) {
            return;
        }
        this.active = active;
    }

    private void setSource(boolean source) {
        this.source = source;
        if (source) {
            this.voltage = 0;
            this.amps = 0;
            this.setTier = 0;
        } else {
            this.voltage = GTValues.V[14];
            this.amps = Integer.MAX_VALUE;
            this.setTier = 14;
        }
        syncDataHolder.markClientSyncFieldDirty("source");
        syncDataHolder.markClientSyncFieldDirty("voltage");
        syncDataHolder.markClientSyncFieldDirty("amps");
        syncDataHolder.markClientSyncFieldDirty("setTier");
    }

    private void setTier(String tierName) {
        setTier = getTierIndex(tierName);
        voltage = GTValues.VEX[setTier];
        syncDataHolder.markClientSyncFieldDirty("setTier");
        syncDataHolder.markClientSyncFieldDirty("voltage");
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, 176, 166);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND));
        root.addChild(createLDLib2TierSelector(player, holder));
        root.addChild(createLDLib2Label(7, 32, 162, 10, Component.translatable("gtpm.creative.energy.voltage")));
        root.addChild(createLDLib2VoltageField(player, holder));
        root.addChild(createLDLib2Label(7, 74, 162, 10, Component.translatable("gtpm.creative.energy.amperage")));
        root.addChild(createLDLib2AmpsDecreaseButton(player, holder));
        root.addChild(createLDLib2AmpsField(player, holder));
        root.addChild(createLDLib2AmpsIncreaseButton(player, holder));
        root.addChild(createLDLib2AverageIOLabel());
        root.addChild(createLDLib2ActiveButton());
        root.addChild(createLDLib2SourceButton(player, holder));
        return UI.of(root);
    }

    private GTStringSelectorElement createLDLib2TierSelector(Player player, MachineUIHolder holder) {
        return new GTStringSelectorElement(7, 7, 50, 20, Arrays.asList(GTValues.VNF),
                () -> GTValues.VNF[setTier], tierName -> setLDLib2Tier(player, holder, tierName));
    }

    private GTLabelElement createLDLib2Label(int x, int y, int width, int height, Component text) {
        GTLabelElement label = new GTLabelElement(x, y, width, height, text);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private GTTextFieldElement createLDLib2VoltageField(Player player, MachineUIHolder holder) {
        GTTextFieldElement field = new GTTextFieldElement(9, 47, 152, 16) {

            @Override
            public void screenTick() {
                if (!isFocused()) {
                    setText(Long.toString(voltage), false);
                }
                super.screenTick();
            }
        };
        field.setNumbersOnlyLong(0L, Long.MAX_VALUE);
        field.setText(Long.toString(voltage), false);
        field.textFieldStyle(style -> style
                .textColor(0x404040)
                .textShadow(false));
        field.setTextResponder(value -> setLDLib2Voltage(player, holder, value));
        return field;
    }

    private GTTextFieldElement createLDLib2AmpsField(Player player, MachineUIHolder holder) {
        GTTextFieldElement field = new GTTextFieldElement(31, 89, 114, 16) {

            @Override
            public void screenTick() {
                if (!isFocused()) {
                    setText(Integer.toString(amps), false);
                }
                super.screenTick();
            }
        };
        field.setNumbersOnlyInt(0, Integer.MAX_VALUE);
        field.setText(Integer.toString(amps), false);
        field.textFieldStyle(style -> style
                .textColor(0x404040)
                .textShadow(false));
        field.setTextResponder(value -> setLDLib2Amps(player, holder, value));
        return field;
    }

    private GTButtonElement createLDLib2AmpsDecreaseButton(Player player, MachineUIHolder holder) {
        return new GTButtonElement(7, 87, 20, 20,
                GuiTextures.group(GuiTextures.BUTTON, GuiTextures.text("-")),
                event -> setLDLib2Amps(player, holder, Math.max(0, amps - 1)));
    }

    private GTButtonElement createLDLib2AmpsIncreaseButton(Player player, MachineUIHolder holder) {
        return new GTButtonElement(149, 87, 20, 20,
                GuiTextures.group(GuiTextures.BUTTON, GuiTextures.text("+")),
                event -> {
                    if (amps < Integer.MAX_VALUE) {
                        setLDLib2Amps(player, holder, amps + 1);
                    }
                });
    }

    private GTLabelElement createLDLib2AverageIOLabel() {
        GTLabelElement label = new GTLabelElement(7, 110, 162, 10) {

            @Override
            public void screenTick() {
                setValue(createAverageIOText());
                super.screenTick();
            }
        };
        label.setValue(createAverageIOText());
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private Component createAverageIOText() {
        return Component.literal("Average Energy I/O per tick: " + lastAverageEnergyIOPerTick);
    }

    private GTButtonElement createLDLib2ActiveButton() {
        return new GTButtonElement(7, 139, 77, 20, createLDLib2ActiveButtonTexture(),
                event -> setLDLib2Active(!active)) {

            @Override
            public void screenTick() {
                setButtonTexture(createLDLib2ActiveButtonTexture());
                super.screenTick();
            }
        };
    }

    private GTButtonElement createLDLib2SourceButton(Player player, MachineUIHolder holder) {
        return new GTButtonElement(85, 139, 77, 20, createLDLib2SourceButtonTexture(),
                event -> setLDLib2Source(player, holder, !source)) {

            @Override
            public void screenTick() {
                setButtonTexture(createLDLib2SourceButtonTexture());
                super.screenTick();
            }
        };
    }

    private IGuiTexture createLDLib2ActiveButtonTexture() {
        return GuiTextures.group(GuiTextures.BUTTON,
                GuiTextures.text(active ? "gtpm.creative.activity.on" : "gtpm.creative.activity.off"));
    }

    private IGuiTexture createLDLib2SourceButtonTexture() {
        return GuiTextures.group(GuiTextures.BUTTON,
                GuiTextures.text(source ? "gtpm.creative.energy.source" : "gtpm.creative.energy.sink"));
    }

    private void setLDLib2Voltage(Player player, MachineUIHolder holder, String value) {
        if (value.isEmpty()) {
            return;
        }
        long parsedValue;
        try {
            parsedValue = Long.parseLong(value);
        } catch (NumberFormatException e) {
            GTCEu.LOGGER.error("Invalid creative energy voltage input: {}", value, e);
            throw e;
        }
        setVoltage(parsedValue);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetCreativeEnergyVoltageAction(parsedValue));
        }
    }

    private void setLDLib2Amps(Player player, MachineUIHolder holder, String value) {
        if (value.isEmpty()) {
            return;
        }
        int parsedValue;
        try {
            parsedValue = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            GTCEu.LOGGER.error("Invalid creative energy amperage input: {}", value, e);
            throw e;
        }
        setLDLib2Amps(player, holder, parsedValue);
    }

    private void setLDLib2Amps(Player player, MachineUIHolder holder, int value) {
        setAmps(value);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetCreativeEnergyAmpsAction(amps));
        }
    }

    private void setLDLib2Tier(Player player, MachineUIHolder holder, String tierName) {
        int tierIndex = getTierIndex(tierName);
        setTier(tierName);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetCreativeEnergyTierAction(tierIndex));
        }
    }

    private void setLDLib2Active(boolean active) {
        setActive(active);
        sendServerSyncChanges();
    }

    private void setLDLib2Source(Player player, MachineUIHolder holder, boolean source) {
        setSource(source);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetCreativeEnergySourceAction(source));
        }
    }

    private static int getTierIndex(String tierName) {
        for (int index = 0; index < GTValues.VNF.length; index++) {
            if (GTValues.VNF[index].equals(tierName)) {
                return index;
            }
        }
        throw new IllegalArgumentException("Unknown creative energy voltage tier: " + tierName);
    }

    private static SyncActionData createSetCreativeEnergyVoltageAction(long voltage) {
        return createSetCreativeEnergyAction(SET_CREATIVE_ENERGY_VOLTAGE_ACTION, VOLTAGE_FIELD,
                new JsonPrimitive(voltage), Long.hashCode(voltage));
    }

    private static SyncActionData createSetCreativeEnergyAmpsAction(int amps) {
        return createSetCreativeEnergyAction(SET_CREATIVE_ENERGY_AMPS_ACTION, AMPS_FIELD, new JsonPrimitive(amps),
                amps);
    }

    private static SyncActionData createSetCreativeEnergyTierAction(int tier) {
        return createSetCreativeEnergyAction(SET_CREATIVE_ENERGY_TIER_ACTION, TIER_FIELD, new JsonPrimitive(tier),
                tier);
    }

    private static SyncActionData createSetCreativeEnergySourceAction(boolean source) {
        return createSetCreativeEnergyAction(SET_CREATIVE_ENERGY_SOURCE_ACTION, SOURCE_FIELD,
                new JsonPrimitive(source), source ? 1 : 0);
    }

    private static SyncActionData createSetCreativeEnergyAction(ResourceLocation actionId, ResourceLocation field,
                                                                JsonPrimitive value, int sequence) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(field, value)
                        .build())
                .build();
        return new SyncActionData(actionId, sequence, payload);
    }

    private abstract static class CreativeEnergyActionHandler implements SyncActionHandler {

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof CreativeEnergyContainerMachine;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        protected CreativeEnergyContainerMachine getMachine(SyncActionContext context) {
            if (!(context.holder() instanceof CreativeEnergyContainerMachine machine)) {
                throw new IllegalStateException("Creative energy action received a non-creative-energy machine.");
            }
            return machine;
        }
    }

    private static final class CreativeEnergyVoltageActionHandler extends CreativeEnergyActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_CREATIVE_ENERGY_VOLTAGE_ACTION;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readNonNegativeLong(fields, VOLTAGE_FIELD) != null;
        }

        @Override
        public void execute(SyncActionContext context) {
            getMachine(context).setVoltage(requireNonNegativeLong(context.payload(), VOLTAGE_FIELD));
        }
    }

    private static final class CreativeEnergyAmpsActionHandler extends CreativeEnergyActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_CREATIVE_ENERGY_AMPS_ACTION;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readNonNegativeInteger(fields, AMPS_FIELD) != null;
        }

        @Override
        public void execute(SyncActionContext context) {
            getMachine(context).setAmps(requireNonNegativeInteger(context.payload(), AMPS_FIELD));
        }
    }

    private static final class CreativeEnergyTierActionHandler extends CreativeEnergyActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_CREATIVE_ENERGY_TIER_ACTION;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readTierIndex(fields, TIER_FIELD) != null;
        }

        @Override
        public void execute(SyncActionContext context) {
            int tier = requireTierIndex(context.payload(), TIER_FIELD);
            getMachine(context).setTier(GTValues.VNF[tier]);
        }
    }

    private static final class CreativeEnergySourceActionHandler extends CreativeEnergyActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_CREATIVE_ENERGY_SOURCE_ACTION;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readBoolean(fields, SOURCE_FIELD) != null;
        }

        @Override
        public void execute(SyncActionContext context) {
            getMachine(context).setSource(requireBoolean(context.payload(), SOURCE_FIELD));
        }
    }

    private static SyncFieldData requireFieldData(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Creative energy action payload is missing field data.");
        }
        return fields;
    }

    private static long requireNonNegativeLong(DataComponentMap payload, ResourceLocation field) {
        Long value = readNonNegativeLong(requireFieldData(payload), field);
        if (value == null) {
            throw new IllegalStateException("Creative energy action payload is missing " + field + ".");
        }
        return value;
    }

    private static int requireNonNegativeInteger(DataComponentMap payload, ResourceLocation field) {
        Integer value = readNonNegativeInteger(requireFieldData(payload), field);
        if (value == null) {
            throw new IllegalStateException("Creative energy action payload is missing " + field + ".");
        }
        return value;
    }

    private static int requireTierIndex(DataComponentMap payload, ResourceLocation field) {
        Integer value = readTierIndex(requireFieldData(payload), field);
        if (value == null) {
            throw new IllegalStateException("Creative energy action payload is missing " + field + ".");
        }
        return value;
    }

    private static boolean requireBoolean(DataComponentMap payload, ResourceLocation field) {
        Boolean value = readBoolean(requireFieldData(payload), field);
        if (value == null) {
            throw new IllegalStateException("Creative energy action payload is missing " + field + ".");
        }
        return value;
    }

    private static @Nullable Long readNonNegativeLong(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            long value = primitive.getAsLong();
            if (value >= 0L) {
                return value;
            }
        }
        return null;
    }

    private static @Nullable Integer readNonNegativeInteger(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            long value = primitive.getAsLong();
            if (value >= 0L && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
        }
        return null;
    }

    private static @Nullable Integer readTierIndex(SyncFieldData fields, ResourceLocation field) {
        Integer value = readNonNegativeInteger(fields, field);
        if (value != null && value < GTValues.VNF.length) {
            return value;
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

package com.gregtechceu.gtceu.common.machine.storage;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.computation.ComputationProducer;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTTextFieldElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.trait.DirectComputationPortTrait;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTDataComponents;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class CreativeComputationProviderMachine extends MetaMachine
                                                implements LDLib2MachineUIProvider, IOpticalComputationProvider,
                                                ComputationProducer {

    private static final ResourceLocation SET_CREATIVE_COMPUTATION_MAX_CWUT_ACTION = GTCEu
            .id("set_creative_computation_max_cwut");
    private static final ResourceLocation SET_CREATIVE_COMPUTATION_ACTIVE_ACTION = GTCEu
            .id("set_creative_computation_active");
    private static final ResourceLocation MAX_CWUT_FIELD = SyncFieldData.key("maxCWUt");
    private static final ResourceLocation ACTIVE_FIELD = SyncFieldData.key("active");

    static {
        SyncActionDispatchers.server().register(new CreativeComputationMaxCWUtActionHandler());
        SyncActionDispatchers.server().register(new CreativeComputationActiveActionHandler());
    }

    @SaveField
    @SyncToClient
    private int maxCWUt;
    @SyncToClient
    private int lastRequestedCWUt;
    private int requestedCWUPerSec;
    @SaveField
    @SyncToClient
    @Getter
    private boolean active;
    @Nullable
    private TickableSubscription computationSubs;

    public CreativeComputationProviderMachine(BlockEntityCreationInfo info) {
        super(info);
        new DirectComputationPortTrait(this, true, this, null);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateComputationSubscription();
    }

    protected void updateComputationSubscription() {
        if (active) {
            this.computationSubs = subscribeServerTick(this::updateComputationTick);
        } else if (computationSubs != null) {
            computationSubs.unsubscribe();
            this.computationSubs = null;
            setLastRequestedCWUt(0);
            this.requestedCWUPerSec = 0;
        }
    }

    protected void updateComputationTick() {
        if (getOffsetTimer() % 20 == 0) {
            setLastRequestedCWUt(requestedCWUPerSec / 20);
            this.requestedCWUPerSec = 0;
        }
    }

    @Override
    public int requestCWUt(
                           int cwut, boolean simulate, @NotNull Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        int requestedCWUt = active ? Math.min(cwut, maxCWUt) : 0;
        if (!simulate) {
            this.requestedCWUPerSec += requestedCWUt;
        }
        return requestedCWUt;
    }

    @Override
    public int getMaxCWUt(@NotNull Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        return active ? maxCWUt : 0;
    }

    @Override
    public boolean canBridge(@NotNull Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        return true;
    }

    @Override
    public int getOfferedCWUt() {
        return active ? maxCWUt : 0;
    }

    @Override
    public void applyProducedCWUt(int allocatedCWUt) {
        this.requestedCWUPerSec += allocatedCWUt;
    }

    public void setActive(boolean active) {
        this.active = active;
        syncDataHolder.markClientSyncFieldDirty("active");
        updateComputationSubscription();
    }

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, 140, 95);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND));
        root.addChild(createLDLib2Label(7, 7, 126, 10, Component.literal("CWUt")));
        root.addChild(createLDLib2MaxCWUtField(player, holder));
        root.addChild(createLDLib2Label(7, 42, 126, 10,
                Component.translatable("gtpm.creative.computation.average")));
        root.addChild(createLDLib2LastRequestedCWUtLabel());
        root.addChild(createLDLib2ActivityButton(player, holder));
        return UI.of(root);
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

    private GTTextFieldElement createLDLib2MaxCWUtField(Player player, MachineUIHolder holder) {
        GTTextFieldElement field = new GTTextFieldElement(9, 20, 122, 16) {

            @Override
            public void screenTick() {
                if (!isFocused()) {
                    setText(Integer.toString(maxCWUt), false);
                }
                super.screenTick();
            }
        };
        field.setNumbersOnlyInt(0, Integer.MAX_VALUE);
        field.setText(Integer.toString(maxCWUt), false);
        field.textFieldStyle(style -> style
                .textColor(0x404040)
                .textShadow(false));
        field.setTextResponder(value -> setLDLib2MaxCWUt(player, holder, value));
        return field;
    }

    private GTLabelElement createLDLib2LastRequestedCWUtLabel() {
        GTLabelElement label = new GTLabelElement(7, 54, 126, 10) {

            @Override
            public void screenTick() {
                setValue(Component.literal(Integer.toString(lastRequestedCWUt)));
                super.screenTick();
            }
        };
        label.setValue(Component.literal(Integer.toString(lastRequestedCWUt)));
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private GTButtonElement createLDLib2ActivityButton(Player player, MachineUIHolder holder) {
        return new GTButtonElement(9, 66, 122, 20, createLDLib2ActivityButtonTexture(),
                event -> setLDLib2Active(player, holder, !isActive())) {

            @Override
            public void screenTick() {
                setButtonTexture(createLDLib2ActivityButtonTexture());
                super.screenTick();
            }
        };
    }

    private IGuiTexture createLDLib2ActivityButtonTexture() {
        return GuiTextures.group(GuiTextures.BUTTON,
                GuiTextures.text(active ? "gtpm.creative.activity.on" : "gtpm.creative.activity.off"));
    }

    private void setLDLib2MaxCWUt(Player player, MachineUIHolder holder, String value) {
        if (value.isEmpty()) {
            return;
        }
        int parsedValue;
        try {
            parsedValue = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            GTCEu.LOGGER.error("Invalid creative computation max CWUt input: {}", value, e);
            throw e;
        }
        setMaxCWUt(parsedValue);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetCreativeComputationMaxCWUtAction(parsedValue));
        }
    }

    private void setLDLib2Active(Player player, MachineUIHolder holder, boolean active) {
        setActive(active);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetCreativeComputationActiveAction(active));
        }
    }

    private void setMaxCWUt(int maxCWUt) {
        this.maxCWUt = Math.max(0, maxCWUt);
        syncDataHolder.markClientSyncFieldDirty("maxCWUt");
    }

    private void setLastRequestedCWUt(int lastRequestedCWUt) {
        this.lastRequestedCWUt = lastRequestedCWUt;
        syncDataHolder.markClientSyncFieldDirty("lastRequestedCWUt");
    }

    private static SyncActionData createSetCreativeComputationMaxCWUtAction(int maxCWUt) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(MAX_CWUT_FIELD, new JsonPrimitive(maxCWUt))
                        .build())
                .build();
        return new SyncActionData(SET_CREATIVE_COMPUTATION_MAX_CWUT_ACTION, maxCWUt, payload);
    }

    private static SyncActionData createSetCreativeComputationActiveAction(boolean active) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(ACTIVE_FIELD, new JsonPrimitive(active))
                        .build())
                .build();
        return new SyncActionData(SET_CREATIVE_COMPUTATION_ACTIVE_ACTION, active ? 1 : 0, payload);
    }

    private static final class CreativeComputationMaxCWUtActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_CREATIVE_COMPUTATION_MAX_CWUT_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof CreativeComputationProviderMachine;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readNonNegativeInteger(fields, MAX_CWUT_FIELD) != null;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof CreativeComputationProviderMachine machine)) {
                throw new IllegalStateException(
                        "Creative computation max CWUt action received a non-computation-provider machine.");
            }
            machine.setMaxCWUt(requireNonNegativeInteger(context.payload(), MAX_CWUT_FIELD));
        }
    }

    private static final class CreativeComputationActiveActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_CREATIVE_COMPUTATION_ACTIVE_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof CreativeComputationProviderMachine;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readBoolean(fields, ACTIVE_FIELD) != null;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof CreativeComputationProviderMachine machine)) {
                throw new IllegalStateException(
                        "Creative computation active action received a non-computation-provider machine.");
            }
            machine.setActive(requireBoolean(context.payload(), ACTIVE_FIELD));
        }
    }

    private static int requireNonNegativeInteger(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Creative computation action payload is missing field data.");
        }
        Integer value = readNonNegativeInteger(fields, field);
        if (value == null) {
            throw new IllegalStateException("Creative computation action payload is missing " + field + ".");
        }
        return value;
    }

    private static boolean requireBoolean(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Creative computation action payload is missing field data.");
        }
        Boolean value = readBoolean(fields, field);
        if (value == null) {
            throw new IllegalStateException("Creative computation action payload is missing " + field + ".");
        }
        return value;
    }

    private static @Nullable Integer readNonNegativeInteger(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            int value = primitive.getAsInt();
            if (value >= 0) {
                return value;
            }
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

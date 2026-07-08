package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.SteamItemBus;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

public class SteamItemBusPartMachine extends ItemBusPartMachine implements LDLib2MachineUIProvider, SteamItemBus {

    private static final ResourceLocation SET_STEAM_ITEM_BUS_CONFIG_ACTION = GTCEu
            .id("set_steam_item_bus_config");
    private static final ResourceLocation WORKING_ENABLED_FIELD = SyncFieldData.key("workingEnabled");

    static {
        SyncActionDispatchers.server().register(new SteamItemBusConfigActionHandler());
    }

    private final String autoTooltipKey;

    public SteamItemBusPartMachine(BlockEntityCreationInfo info, IO io) {
        super(info, 1, io);
        autoTooltipKey = io == IO.IN ? "gtpm.gui.item_auto_input.tooltip" : "gtpm.gui.item_auto_output.tooltip";
    }

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        int rowSize = (int) Math.sqrt(getInventorySize());
        int xOffset = rowSize == 10 ? 9 : 0;
        int rootWidth = 176 + xOffset * 2;
        int rootHeight = 18 + 18 * rowSize + 105;
        boolean steelSteamMultiblocks = ConfigHolder.INSTANCE.machines.steelSteamMultiblocks;

        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, rootWidth, rootHeight);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_STEAM.get(steelSteamMultiblocks)));
        root.addChild(createLDLib2TitleLabel(rootWidth));
        root.addChild(new GTToggleButtonElement(7 + xOffset, 18 + 18 * rowSize, 18, 18,
                GuiTextures.BUTTON_ITEM_OUTPUT, this::isWorkingEnabled,
                enabled -> setLDLib2WorkingEnabled(player, holder, enabled))
                .setShouldUseBaseBackground()
                .setTooltipText(autoTooltipKey));
        root.addChild(UITemplate.bindPlayerInventoryLDLib2(player.getInventory(),
                GuiTextures.SLOT_STEAM.get(steelSteamMultiblocks),
                7 + xOffset, 18 + 18 * rowSize + 24, true));

        for (int y = 0; y < rowSize; y++) {
            for (int x = 0; x < rowSize; x++) {
                int index = y * rowSize + x;
                root.addChild(createLDLib2InventorySlot(index,
                        (88 - rowSize * 9 + x * 18) + xOffset, 18 + y * 18 + 6, steelSteamMultiblocks));
            }
        }

        return UI.of(root);
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

    private GTItemSlotElement createLDLib2InventorySlot(int index, int x, int y, boolean steelSteamMultiblocks) {
        GTItemSlotElement slot = new GTItemSlotElement(getInventory().storage, index)
                .setBackgroundTexture(GuiTextures.SLOT_STEAM.get(steelSteamMultiblocks))
                .setCanTakeItems(true)
                .setCanPutItems(io.support(IO.IN));
        UITemplate.setLDLib2Bounds(slot, x, y, 18, 18);
        return slot;
    }

    private void setLDLib2WorkingEnabled(Player player, MachineUIHolder holder, boolean enabled) {
        setWorkingEnabled(enabled);
        if (player.level().isClientSide()) {
            MachineUIHelper.sendAction(holder, createSetSteamItemBusConfigAction(enabled));
        }
    }

    private static SyncActionData createSetSteamItemBusConfigAction(boolean enabled) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(WORKING_ENABLED_FIELD, new JsonPrimitive(enabled))
                        .build())
                .build();
        return new SyncActionData(SET_STEAM_ITEM_BUS_CONFIG_ACTION, enabled ? 1 : 0, payload);
    }

    @Override
    public boolean swapIO() {
        BlockPos blockPos = getBlockPos();
        MachineDefinition newDefinition = null;
        if (io == IO.IN) {
            newDefinition = GTMachines.STEAM_EXPORT_BUS;
        } else if (io == IO.OUT) {
            newDefinition = GTMachines.STEAM_IMPORT_BUS;
        }

        if (newDefinition == null) return false;
        BlockState newBlockState = newDefinition.getBlock().defaultBlockState();

        getLevel().setBlockAndUpdate(blockPos, newBlockState);

        if (getLevel().getBlockEntity(blockPos) instanceof SteamItemBusPartMachine newMachine) {
            // We don't set the circuit or distinct busses, since
            // that doesn't make sense on an output bus.
            // Furthermore, existing inventory items
            // and conveyors will drop to the floor on block override.
            newMachine.setFrontFacing(this.getFrontFacing());
            newMachine.setUpwardsFacing(this.getUpwardsFacing());
        }
        return true;
    }

    private static final class SteamItemBusConfigActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_STEAM_ITEM_BUS_CONFIG_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof SteamItemBus;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readBoolean(fields, WORKING_ENABLED_FIELD) != null;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof SteamItemBus steamItemBus)) {
                throw new IllegalStateException("Steam item bus config action received an invalid holder.");
            }
            steamItemBus.setWorkingEnabled(requireBoolean(context.payload(), WORKING_ENABLED_FIELD));
        }
    }

    private static boolean requireBoolean(DataComponentMap payload, ResourceLocation field) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            throw new IllegalStateException("Steam item bus config action payload is missing field data.");
        }
        Boolean value = readBoolean(fields, field);
        if (value == null) {
            throw new IllegalStateException("Steam item bus config action payload is missing " + field + ".");
        }
        return value;
    }

    private static @Nullable Boolean readBoolean(SyncFieldData fields, ResourceLocation field) {
        JsonElement element = fields.get(field);
        if (element instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        return null;
    }
}

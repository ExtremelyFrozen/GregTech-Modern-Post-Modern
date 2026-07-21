package com.gregtechceu.gtceu.common.item.behavior;

import com.gregtechceu.gtceu.api.blockentity.ICopyable;
import com.gregtechceu.gtceu.api.item.component.IAddInformation;
import com.gregtechceu.gtceu.api.item.component.IInteractionItem;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.*;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.item.datacomponents.MachineConfigCopyData;
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner;
import com.gregtechceu.gtceu.utils.GTTransferUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import joptsimple.internal.Strings;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class MachineConfigCopyBehaviour implements IInteractionItem, IAddInformation {

    private static final String NONE_DIRECTION = "null";
    private static final Component ENABLED = Component.translatable("behaviour.memory_card.enabled");
    private static final Component DISABLED = Component.translatable("behaviour.memory_card.disabled");
    private static final String[] DIRECTION_STRINGS = { "§eDown§r", "§eUp§r", "§eNorth§r", "§eSouth§r", "§eWest§r",
            "§eEast§r" };

    public static String directionToString(@Nullable Direction direction) {
        if (direction == null) return NONE_DIRECTION;
        return direction.getName();
    }

    public static @Nullable Direction stringToDirection(@Nullable String str) {
        if (Strings.isNullOrEmpty(str) || NONE_DIRECTION.equalsIgnoreCase(str)) return null;
        return Direction.byName(str);
    }

    public static Component directionListComponent(int directions) {
        List<String> dirStrings = new ArrayList<>();
        if ((directions & (1)) > 0) dirStrings.add(DIRECTION_STRINGS[0]);
        if ((directions & (1 << 1)) > 0) dirStrings.add(DIRECTION_STRINGS[1]);
        if ((directions & (1 << 2)) > 0) dirStrings.add(DIRECTION_STRINGS[2]);
        if ((directions & (1 << 3)) > 0) dirStrings.add(DIRECTION_STRINGS[3]);
        if ((directions & (1 << 4)) > 0) dirStrings.add(DIRECTION_STRINGS[4]);
        if ((directions & (1 << 5)) > 0) dirStrings.add(DIRECTION_STRINGS[5]);
        return Component.literal(String.join(", ", dirStrings));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        var blockEntity = context.getLevel().getBlockEntity(context.getClickedPos());
        var player = context.getPlayer();

        if (!(player instanceof ServerPlayer)) return InteractionResult.PASS;
        if (blockEntity instanceof MetaMachine mm &&
                !MachineOwner.canOpenOwnerMachine(context.getPlayer(), mm))
            return InteractionResult.FAIL;

        if (context.isSecondaryUseActive()) {
            if (blockEntity instanceof ICopyable copyable) {
                var source = new ItemStack(blockEntity.getBlockState().getBlock().asItem()).getDisplayName()
                        .getString();
                stack.set(GTDataComponents.DATA_COPY_TAG,
                        new MachineConfigCopyData(source, copyable.getItemsRequiredToPaste(),
                                copyable.copyConfig(context.getLevel().registryAccess())));
            } else {
                stack.remove(GTDataComponents.DATA_COPY_TAG);
                player.displayClientMessage(Component.translatable("behaviour.memory_card.client_msg.cleared"), true);
                return InteractionResult.SUCCESS;
            }

            player.displayClientMessage(Component.translatable("behaviour.memory_card.client_msg.copied"), true);

        } else {
            MachineConfigCopyData copiedData = stack.get(GTDataComponents.DATA_COPY_TAG);
            if (copiedData == null) return InteractionResult.PASS;

            if (!player.isCreative() && !GTTransferUtils.extractItemsFromPlayerInv(player, copiedData.itemsToPaste(),
                    true)) {
                player.displayClientMessage(Component.translatable("behaviour.memory_card.client_msg.missing_items"),
                        true);
                return InteractionResult.FAIL;
            }
            if (!player.isCreative()) GTTransferUtils.extractItemsFromPlayerInv(player, copiedData.itemsToPaste(),
                    false);

            if (blockEntity instanceof ICopyable copyable) {
                copyable.pasteConfig((ServerPlayer) player, context.getLevel().registryAccess(), copiedData.config());
            }

            player.displayClientMessage(Component.translatable("behaviour.memory_card.client_msg.pasted"), true);

        }

        return InteractionResult.SUCCESS;
    }

    //// Logic for actual config options

    // NBT keys for machine config values
    private static final String PIPE_CONNECTIONS = "pipe_connections";
    private static final String PIPE_BLOCKED_CONNECTIONS = "pipe_blocked_connections";

    private static final String COVER = "cover";
    private static final String FACING_DIR = "front_facing";

    private static final String ITEM_OUTPUT_SIDE = "output_direction_item";
    private static final String ITEM_AUTO_OUTPUT = "item_auto_output";
    private static final String ALLOW_ITEM_IN_FROM_OUT = "allow_input_from_output_item";

    private static final String FLUID_OUTPUT_SIDE = "output_direction_fluid";
    private static final String FLUID_AUTO_OUTPUT = "fluid_auto_output";
    private static final String ALLOW_FLUID_IN_FROM_OUT = "allow_input_from_output_fluid";

    private static final String MUFFLED = "muffled";
    private static final String CIRCUIT = "circuit_config";

    private static void addConfigTooltips(List<Component> tooltip, MachineConfigCopyData data,
                                          Item.TooltipContext context) {
        if (context.level() == null) return;

        tooltip.add(Component.translatable("behaviour.memory_card.copy_target", data.source()));
        tooltip.add(Component.empty());

        Integer pipeConnections = getInt(data.config(), PIPE_CONNECTIONS);
        if (pipeConnections != null && pipeConnections != 0)
            tooltip.add(Component.translatable("behaviour.setting.tooltip.pipe_connections",
                    directionListComponent(pipeConnections)));
        Integer pipeBlockedConnections = getInt(data.config(), PIPE_BLOCKED_CONNECTIONS);
        if (pipeBlockedConnections != null && pipeBlockedConnections != 0)
            tooltip.add(Component.translatable("behaviour.setting.tooltip.pipe_blocked_connections",
                    directionListComponent(pipeBlockedConnections)));

        String itemOutputSide = getString(data.config(), ITEM_OUTPUT_SIDE);
        Boolean itemAutoOutput = getBoolean(data.config(), ITEM_AUTO_OUTPUT);
        Boolean allowItemInputFromOut = getBoolean(data.config(), ALLOW_ITEM_IN_FROM_OUT);
        if (itemOutputSide != null && itemAutoOutput != null && allowItemInputFromOut != null) {
            Component outputMode;
            if (itemAutoOutput && allowItemInputFromOut)
                outputMode = Component.translatable("behaviour.setting.tooltip.auto_output_allow_input");
            else if (itemAutoOutput)
                outputMode = Component.translatable("behaviour.setting.tooltip.auto_output");
            else if (allowItemInputFromOut)
                outputMode = Component.translatable("behaviour.setting.tooltip.allow_input");
            else outputMode = Component.empty();

            Direction dir = stringToDirection(itemOutputSide);
            if (dir == null) return;

            tooltip.add(Component.translatable("behaviour.setting.tooltip.item_io",
                    Component.literal(DIRECTION_STRINGS[dir.ordinal()]), outputMode));
        }

        String fluidOutputSide = getString(data.config(), FLUID_OUTPUT_SIDE);
        Boolean fluidAutoOutput = getBoolean(data.config(), FLUID_AUTO_OUTPUT);
        Boolean allowFluidInputFromOut = getBoolean(data.config(), ALLOW_FLUID_IN_FROM_OUT);
        if (fluidOutputSide != null && fluidAutoOutput != null && allowFluidInputFromOut != null) {
            Component outputMode;
            if (fluidAutoOutput && allowFluidInputFromOut)
                outputMode = Component.translatable("behaviour.setting.tooltip.auto_output_allow_input");
            else if (fluidAutoOutput)
                outputMode = Component.translatable("behaviour.setting.tooltip.auto_output");
            else if (allowFluidInputFromOut)
                outputMode = Component.translatable("behaviour.setting.tooltip.allow_input");
            else outputMode = Component.empty();

            Direction dir = stringToDirection(fluidOutputSide);
            if (dir == null) return;

            tooltip.add(Component.translatable("behaviour.setting.tooltip.fluid_io",
                    Component.literal(DIRECTION_STRINGS[dir.ordinal()]), outputMode));
        }

        Boolean muffled = getBoolean(data.config(), MUFFLED);
        if (muffled != null) tooltip.add(Component.translatable("behaviour.setting.tooltip.muffled",
                muffled ? ENABLED : DISABLED));
        Integer circuit = getInt(data.config(), CIRCUIT);
        if (circuit != null) tooltip.add(Component.translatable("behaviour.setting.tooltip.circuit_config")
                .append(Component.literal(Integer.toString(circuit)).withStyle(ChatFormatting.YELLOW)));

        if (!data.itemsToPaste().isEmpty()) {

            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("behaviour.memory_card.tooltip.items_to_paste"));

            for (var item : data.itemsToPaste()) {
                tooltip.add(Component.literal("- " + item.getCount() + "x ").append(item.getDisplayName())
                        .withStyle(ChatFormatting.DARK_GREEN));
            }
        }
    }

    private static @Nullable JsonElement getField(DataComponentMap config, String key) {
        SyncFieldData fields = config.get(GTDataComponents.SYNC_FIELD_DATA.get());
        return fields == null ? null : fields.get(SyncFieldData.key(key));
    }

    private static @Nullable Integer getInt(DataComponentMap config, String key) {
        JsonElement field = getField(config, key);
        return field instanceof JsonPrimitive primitive && primitive.isNumber() ? primitive.getAsInt() : null;
    }

    private static @Nullable Boolean getBoolean(DataComponentMap config, String key) {
        JsonElement field = getField(config, key);
        if (!(field instanceof JsonPrimitive primitive)) {
            return null;
        }
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        return primitive.isNumber() ? primitive.getAsByte() != 0 : null;
    }

    private static @Nullable String getString(DataComponentMap config, String key) {
        JsonElement field = getField(config, key);
        return field instanceof JsonPrimitive primitive ? primitive.getAsString() : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents,
                                TooltipFlag isAdvanced) {
        tooltipComponents.add(Component.translatable("behaviour.memory_card.tooltip.copy"));
        tooltipComponents.add(Component.translatable("behaviour.memory_card.tooltip.paste"));
        MachineConfigCopyData data = stack.get(GTDataComponents.DATA_COPY_TAG);
        if (data == null) return;
        if (Screen.hasShiftDown()) {
            tooltipComponents.add(CommonComponents.EMPTY);
            addConfigTooltips(tooltipComponents, data, context);
        } else {
            tooltipComponents.add(Component.translatable("behaviour.memory_card.tooltip.view_stored"));
        }
    }
}

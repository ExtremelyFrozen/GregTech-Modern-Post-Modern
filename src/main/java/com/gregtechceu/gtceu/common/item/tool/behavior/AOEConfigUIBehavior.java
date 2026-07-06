package com.gregtechceu.gtceu.common.item.tool.behavior;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.factory.HeldItemUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.HeldItemUIHolder;
import com.gregtechceu.gtceu.api.item.IGTTool;
import com.gregtechceu.gtceu.api.item.datacomponents.AoESymmetrical;
import com.gregtechceu.gtceu.api.item.tool.behavior.IToolUIBehavior;
import com.gregtechceu.gtceu.api.item.tool.behavior.ToolBehaviorType;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTToolBehaviors;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.NotNull;

import static com.gregtechceu.gtceu.api.gui.UITemplate.setLDLib2Bounds;
import static com.gregtechceu.gtceu.api.item.tool.ToolHelper.*;

public class AOEConfigUIBehavior implements IToolUIBehavior<AOEConfigUIBehavior> {

    private static final ResourceLocation SET_TOOL_AOE_ACTION = GTCEu.id("set_tool_aoe");

    public static final AOEConfigUIBehavior INSTANCE = new AOEConfigUIBehavior();
    public static final Codec<AOEConfigUIBehavior> CODEC = Codec.unit(INSTANCE);
    public static final StreamCodec<ByteBuf, AOEConfigUIBehavior> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    static {
        SyncActionDispatchers.server().register(new AOEConfigurationActionHandler());
    }

    @Override
    public boolean openUI(@NotNull Player player, @NotNull InteractionHand hand) {
        return player.isShiftKeyDown() && !player.getItemInHand(hand)
                .getOrDefault(GTDataComponents.AOE, AoESymmetrical.ZERO).isZero();
    }

    @Override
    public boolean openLDLib2UI(@NotNull Player player, @NotNull InteractionHand hand) {
        return openUI(player, hand);
    }

    @Override
    public boolean hasLDLib2UI(Player player, HeldItemUIHolder holder) {
        return ItemStack.isSameItem(holder.getHeld(), holder.getOpenedStack()) && hasConfigurableAOE(holder.getHeld());
    }

    @Override
    public boolean isLDLib2UIStillValid(Player player, HeldItemUIHolder holder) {
        return hasLDLib2UI(player, holder);
    }

    @Override
    public UI createLDLib2UI(Player player, HeldItemUIHolder holder) {
        ItemStack held = holder.getHeld();
        AoESymmetrical.Mutable definition = getAoEDefinition(held).toMutable();

        UIElement root = new UIElement();
        setLDLib2Bounds(root, 0, 0, 120, 80);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND));

        root.addChild(createLDLib2Label("item.gtpm.tool.aoe.columns", 6, 10, 38, 10, true));
        root.addChild(createLDLib2Label("item.gtpm.tool.aoe.rows", 49, 10, 28, 10, true));
        root.addChild(createLDLib2Label("item.gtpm.tool.aoe.layers", 79, 10, 34, 10, true));

        GTLabelElement columnValue = createLDLib2ValueLabel(columnText(definition.toImmutable()), 23, 65);
        GTLabelElement rowValue = createLDLib2ValueLabel(rowText(definition.toImmutable()), 58, 65);
        GTLabelElement layerValue = createLDLib2ValueLabel(layerText(definition.toImmutable()), 93, 65);

        root.addChild(createLDLib2AOEButton("+", 15, 24, () -> {
            AoESymmetrical next = definition.increaseColumn().toImmutable();
            setAOEDefinition(holder, next, columnValue, rowValue, layerValue);
        }));
        root.addChild(createLDLib2AOEButton("-", 15, 44, () -> {
            AoESymmetrical next = definition.decreaseColumn().toImmutable();
            setAOEDefinition(holder, next, columnValue, rowValue, layerValue);
        }));
        root.addChild(createLDLib2AOEButton("+", 50, 24, () -> {
            AoESymmetrical next = definition.increaseRow().toImmutable();
            setAOEDefinition(holder, next, columnValue, rowValue, layerValue);
        }));
        root.addChild(createLDLib2AOEButton("-", 50, 44, () -> {
            AoESymmetrical next = definition.decreaseRow().toImmutable();
            setAOEDefinition(holder, next, columnValue, rowValue, layerValue);
        }));
        root.addChild(createLDLib2AOEButton("+", 85, 24, () -> {
            AoESymmetrical next = definition.increaseLayer().toImmutable();
            setAOEDefinition(holder, next, columnValue, rowValue, layerValue);
        }));
        root.addChild(createLDLib2AOEButton("-", 85, 44, () -> {
            AoESymmetrical next = definition.decreaseLayer().toImmutable();
            setAOEDefinition(holder, next, columnValue, rowValue, layerValue);
        }));

        root.addChildren(columnValue, rowValue, layerValue);
        return UI.of(root);
    }

    @Override
    public ToolBehaviorType<AOEConfigUIBehavior> getType() {
        return GTToolBehaviors.AOE_CONFIG_UI;
    }

    private static GTLabelElement createLDLib2Label(String text, int x, int y, int width, int height,
                                                    boolean translate) {
        GTLabelElement label = new GTLabelElement(x, y, width, height, text, translate);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private static GTLabelElement createLDLib2ValueLabel(String text, int x, int y) {
        return createLDLib2Label(text, x, y, 18, 10, false);
    }

    private static GTButtonElement createLDLib2AOEButton(String text, int x, int y, Runnable action) {
        GTButtonElement button = new GTButtonElement(x, y, 20, 20, GuiTextures.BUTTON, event -> action.run());
        button.setText(text, false);
        button.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER));
        return button;
    }

    private static void setAOEDefinition(HeldItemUIHolder holder, AoESymmetrical definition,
                                         GTLabelElement columnValue, GTLabelElement rowValue,
                                         GTLabelElement layerValue) {
        if (holder.getPlayer().level().isClientSide()) {
            HeldItemUIHelper.sendAction(holder, createSetToolAOEAction(definition));
        }
        updateValueLabels(definition, columnValue, rowValue, layerValue);
    }

    private static SyncActionData createSetToolAOEAction(AoESymmetrical definition) {
        if (!isValidAOEDefinition(definition)) {
            throw new IllegalArgumentException("Given AOE definition is out of range.");
        }
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.AOE.get(), definition)
                .build();
        return new SyncActionData(SET_TOOL_AOE_ACTION, 0, payload);
    }

    private static void updateValueLabels(AoESymmetrical definition, GTLabelElement columnValue,
                                          GTLabelElement rowValue, GTLabelElement layerValue) {
        columnValue.setText(columnText(definition), false);
        rowValue.setText(rowText(definition), false);
        layerValue.setText(layerText(definition), false);
    }

    private static String columnText(AoESymmetrical definition) {
        return Integer.toString(1 + 2 * definition.column());
    }

    private static String rowText(AoESymmetrical definition) {
        return Integer.toString(1 + 2 * definition.row());
    }

    private static String layerText(AoESymmetrical definition) {
        return Integer.toString(1 + definition.layer());
    }

    private static boolean hasConfigurableAOE(ItemStack stack) {
        return !getAoEDefinition(stack).isZero();
    }

    private static boolean isAOEConfigTool(ItemStack stack) {
        if (!(stack.getItem() instanceof IGTTool tool)) {
            return false;
        }
        return tool.getToolStats().getBehaviors().stream().anyMatch(AOEConfigUIBehavior.class::isInstance);
    }

    private static boolean isValidAOEDefinition(AoESymmetrical definition) {
        return definition.maxColumn() >= 0 && definition.maxRow() >= 0 && definition.maxLayer() >= 0 &&
                definition.column() >= 0 && definition.column() <= definition.maxColumn() &&
                definition.row() >= 0 && definition.row() <= definition.maxRow() &&
                definition.layer() >= 0 && definition.layer() <= definition.maxLayer() &&
                !definition.isZero();
    }

    private static boolean canApplyAOEDefinition(AoESymmetrical current, AoESymmetrical requested) {
        return isValidAOEDefinition(current) && isValidAOEDefinition(requested) &&
                current.maxColumn() == requested.maxColumn() &&
                current.maxRow() == requested.maxRow() &&
                current.maxLayer() == requested.maxLayer();
    }

    private static final class AOEConfigurationActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_TOOL_AOE_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            if (!(context.holder() instanceof ItemStack stack) || context.openedStack() == null) {
                return false;
            }
            return ItemStack.isSameItem(stack, context.openedStack()) &&
                    isAOEConfigTool(stack) &&
                    hasConfigurableAOE(stack) &&
                    hasConfigurableAOE(context.openedStack());
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            AoESymmetrical definition = payload.get(GTDataComponents.AOE.get());
            return definition != null && isValidAOEDefinition(definition);
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof ItemStack stack)) {
                throw new IllegalStateException("AOE configuration action received a non-item holder.");
            }
            AoESymmetrical requested = context.payload().get(GTDataComponents.AOE.get());
            if (requested == null) {
                throw new IllegalStateException("AOE configuration action payload is missing.");
            }
            AoESymmetrical current = getAoEDefinition(stack);
            if (!canApplyAOEDefinition(current, requested)) {
                throw new IllegalArgumentException("AOE configuration action payload does not match the current tool.");
            }
            stack.set(GTDataComponents.AOE, requested);
        }
    }
}

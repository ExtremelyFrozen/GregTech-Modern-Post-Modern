package com.gregtechceu.gtceu.common.item.behavior;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.factory.HeldItemUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.HeldItemUIHolder;
import com.gregtechceu.gtceu.api.item.component.IAddInformation;
import com.gregtechceu.gtceu.api.item.component.IItemUIFactory;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IHasCircuitSlot;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

import static com.gregtechceu.gtceu.api.gui.UITemplate.setLDLib2Bounds;

public class IntCircuitBehaviour implements IItemUIFactory, IAddInformation {

    public static final int CIRCUIT_MAX = 32;
    private static final ResourceLocation SET_CIRCUIT_CONFIGURATION_ACTION = GTCEu.id("set_circuit_configuration");

    static {
        SyncActionDispatchers.server().register(new CircuitConfigurationActionHandler());
    }

    public static ItemStack stack(int configuration) {
        var stack = GTItems.PROGRAMMED_CIRCUIT.asStack();
        setCircuitConfiguration(stack, configuration);
        return stack;
    }

    public static void setCircuitConfiguration(HeldItemUIHolder holder, int configuration) {
        setCircuitConfiguration(holder.getHeld(), configuration);
    }

    public static void setCircuitConfiguration(ItemStack itemStack, int configuration) {
        if (!isValidCircuitConfiguration(configuration))
            throw new IllegalArgumentException("Given configuration number is out of range!");
        itemStack.set(GTDataComponents.CIRCUIT_CONFIG, configuration);
    }

    public static int getCircuitConfiguration(ItemStack itemStack) {
        return itemStack.getOrDefault(GTDataComponents.CIRCUIT_CONFIG, 0);
    }

    public static boolean isIntegratedCircuit(ItemStack itemStack) {
        return GTItems.PROGRAMMED_CIRCUIT.isIn(itemStack);
    }

    private static boolean isValidCircuitConfiguration(int configuration) {
        return configuration >= 0 && configuration <= CIRCUIT_MAX;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltipComponents,
                                TooltipFlag isAdvanced) {
        int configuration = getCircuitConfiguration(stack);
        tooltipComponents.add(Component.translatable("metaitem.int_circuit.configuration", configuration));
    }

    @Override
    public boolean canCreateLDLib2UI(HeldItemUIHolder holder, Player entityPlayer) {
        return isIntegratedCircuit(holder.getHeld());
    }

    @Override
    public boolean isLDLib2UIStillValid(HeldItemUIHolder holder, Player entityPlayer) {
        return isIntegratedCircuit(holder.getHeld()) && ItemStack.isSameItem(holder.getHeld(), holder.getOpenedStack());
    }

    @Override
    public UI createLDLib2UI(HeldItemUIHolder holder, Player entityPlayer) {
        UIElement root = new UIElement();
        setLDLib2Bounds(root, 0, 0, 184, 132);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND));

        GTLabelElement label = new GTLabelElement(9, 8, 166, 10,
                "Programmed Circuit Configuration", false);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        root.addChild(label);

        GTItemSlotElement selectedSlot = new GTItemSlotElement(
                new CustomItemStackHandler(stack(getCircuitConfiguration(holder.getHeld()))), 0);
        selectedSlot.setBackgroundTexture(GuiTextures.SLOT)
                .setCanPutItems(false)
                .setCanTakeItems(false);
        setLDLib2Bounds(selectedSlot, 82, 20, 18, 18);
        root.addChild(selectedSlot);

        int idx = 0;
        for (int x = 0; x <= 2; x++) {
            for (int y = 0; y <= 8; y++) {
                root.addChild(createLDLib2CircuitButton(holder, selectedSlot, idx, 10 + 18 * y, 48 + 18 * x));
                idx++;
            }
        }
        for (int x = 0; x <= 5; x++) {
            int configuration = x + 27;
            root.addChild(createLDLib2CircuitButton(holder, selectedSlot, configuration, 10 + 18 * x, 102));
        }
        return UI.of(root);
    }

    private static GTButtonElement createLDLib2CircuitButton(HeldItemUIHolder holder, GTItemSlotElement selectedSlot,
                                                             int configuration, int x, int y) {
        var texture = GuiTextures.group(GuiTextures.SLOT,
                GuiTextures.itemStack(stack(configuration)).scale(16f / 18));
        GTButtonElement button = new GTButtonElement(x, y, 18, 18, texture,
                event -> setCircuitConfiguration(holder, selectedSlot, configuration));
        button.noText();
        return button;
    }

    private static void setCircuitConfiguration(HeldItemUIHolder holder, GTItemSlotElement selectedSlot,
                                                int configuration) {
        if (holder.getPlayer().level().isClientSide()) {
            HeldItemUIHelper.sendAction(holder, createSetCircuitConfigurationAction(configuration));
        }
        selectedSlot.setHandlerSlot(new CustomItemStackHandler(stack(configuration)), 0);
    }

    private static SyncActionData createSetCircuitConfigurationAction(int configuration) {
        if (!isValidCircuitConfiguration(configuration)) {
            throw new IllegalArgumentException("Given configuration number is out of range!");
        }
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.CIRCUIT_CONFIG.get(), configuration)
                .build();
        return new SyncActionData(SET_CIRCUIT_CONFIGURATION_ACTION, configuration, payload);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var stack = context.getItemInHand();
        int circuitSetting = getCircuitConfiguration(stack);
        BlockEntity entity = context.getLevel().getBlockEntity(context.getClickedPos());
        if (entity instanceof MetaMachine machine && context.isSecondaryUseActive()) {
            if (machine instanceof IHasCircuitSlot circuitMachine &&
                    circuitMachine.getCircuitInventory().getSlots() > 0) {
                setCircuitConfig(circuitMachine.getCircuitInventory(), circuitSetting);
            }
            if (!ConfigHolder.INSTANCE.machines.ghostCircuit)
                stack.shrink(1);
            return InteractionResult.SUCCESS;
        }
        return IItemUIFactory.super.useOn(context);
    }

    void setCircuitConfig(NotifiableItemStackHandler circuit, int value) {
        circuit.setStackInSlot(0, IntCircuitBehaviour.stack(value));
    }

    private static final class CircuitConfigurationActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_CIRCUIT_CONFIGURATION_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            if (!(context.holder() instanceof ItemStack stack) || context.openedStack() == null) {
                return false;
            }
            return isIntegratedCircuit(stack) && isIntegratedCircuit(context.openedStack()) &&
                    ItemStack.isSameItem(stack, context.openedStack());
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            Integer configuration = payload.get(GTDataComponents.CIRCUIT_CONFIG.get());
            return configuration != null && isValidCircuitConfiguration(configuration);
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            return !player.isSpectator();
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof ItemStack stack)) {
                throw new IllegalStateException("Circuit configuration action received a non-item holder.");
            }
            Integer configuration = context.payload().get(GTDataComponents.CIRCUIT_CONFIG.get());
            if (configuration == null) {
                throw new IllegalStateException("Circuit configuration action payload is missing.");
            }
            setCircuitConfiguration(stack, configuration);
        }
    }
}

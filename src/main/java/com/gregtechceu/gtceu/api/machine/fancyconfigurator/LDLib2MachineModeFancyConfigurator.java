package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.ColorPattern;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.gui.texture.TextTexture;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyActionMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionContext;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.api.sync_system.SyncActionDispatchers;
import com.gregtechceu.gtceu.api.sync_system.SyncActionHandler;
import com.gregtechceu.gtceu.api.sync_system.SyncFieldData;
import com.gregtechceu.gtceu.common.data.GTDataComponents;
import com.gregtechceu.gtceu.common.data.GTItems;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * LDLib2 Fancy page for selecting the active recipe type of a recipe logic machine.
 *
 * <p>
 * The page sends a GTM machine action for server-side mutation instead of LDLib2-owned business state channels.
 */
public class LDLib2MachineModeFancyConfigurator implements LDLib2FancyUIProvider {

    private static final int PAGE_WIDTH = 140;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_X = 2;
    private static final int BUTTON_Y = 2;
    private static final int BUTTON_WIDTH = 136;
    private static final ResourceLocation SET_MACHINE_MODE_ACTION = GTCEu.id("set_machine_mode");
    private static final ResourceLocation ACTIVE_RECIPE_TYPE_FIELD = SyncFieldData.key("activeRecipeType");

    private final IRecipeLogicMachine machine;

    static {
        SyncActionDispatchers.server().register(new MachineModeActionHandler());
    }

    /**
     * Creates a mode selector page for one recipe logic machine.
     */
    public LDLib2MachineModeFancyConfigurator(IRecipeLogicMachine machine) {
        this.machine = machine;
    }

    /**
     * Builds fixed-height LDLib2 buttons that request a recipe type switch through GTM action sync.
     */
    @Override
    public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, getLDLib2PageWidth(), getLDLib2PageHeight());
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));

        for (int index = 0; index < machine.getRecipeTypes().length; index++) {
            int activeRecipeType = index;
            int y = BUTTON_Y + index * BUTTON_HEIGHT;
            GTButtonElement button = new GTButtonElement(BUTTON_X, y, BUTTON_WIDTH, BUTTON_HEIGHT, IGuiTexture.EMPTY,
                    event -> onModeClicked(shell, activeRecipeType, event));
            button.noText();
            root.addChild(button);

            GTImageElement label = new GTImageElement(BUTTON_X, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                    createModeTexture(activeRecipeType));
            label.setAllowHitTest(false);
            root.addChild(label);
        }
        return root;
    }

    /**
     * Returns the fixed content width used by the legacy machine mode selector.
     */
    @Override
    public int getLDLib2PageWidth() {
        return PAGE_WIDTH;
    }

    /**
     * Returns the height required for all machine recipe type buttons.
     */
    @Override
    public int getLDLib2PageHeight() {
        return BUTTON_HEIGHT * machine.getRecipeTypes().length + BUTTON_Y * 2;
    }

    /**
     * Returns the translated page title shown in the Fancy title bar.
     */
    @Override
    public Component getTitle() {
        return Component.translatable("gtpm.gui.machinemode.title");
    }

    /**
     * Uses the robot arm icon from the legacy machine mode tab.
     */
    @Override
    public IGuiTexture getTabIcon() {
        return GuiTextures.itemStack(GTItems.ROBOT_ARM_LV.get());
    }

    /**
     * Returns the legacy machine mode tab tooltip.
     */
    @Override
    public List<Component> getTabTooltips() {
        return List.of(Component.translatable("gtpm.gui.machinemode.tab_tooltip"));
    }

    private IGuiTexture createModeTexture(int activeRecipeType) {
        return GuiTextures.group(
                GuiTextures.VANILLA_BUTTON.copy()
                        .setDynamicColor(() -> machine.getActiveRecipeType() == activeRecipeType ?
                                ColorPattern.CYAN.color : -1),
                GuiTextures.text(machine.getRecipeTypes()[activeRecipeType].getTranslationKey())
                        .setWidth(BUTTON_WIDTH)
                        .setType(TextTexture.TextType.ROLL));
    }

    private void onModeClicked(LDLib2FancyMachineUIElement shell, int activeRecipeType, UIEvent event) {
        if (machine.self().isRemote()) {
            MachineUIHelper.sendAction(shell.getHolder(), createSetMachineModeAction(activeRecipeType));
            event.stopImmediatePropagation();
            event.hasHandler = true;
        }
    }

    private static SyncActionData createSetMachineModeAction(int activeRecipeType) {
        DataComponentMap payload = DataComponentMap.builder()
                .set(GTDataComponents.SYNC_FIELD_DATA.get(), SyncFieldData.builder()
                        .put(ACTIVE_RECIPE_TYPE_FIELD, new JsonPrimitive(activeRecipeType))
                        .build())
                .build();
        return new SyncActionData(SET_MACHINE_MODE_ACTION, activeRecipeType, payload);
    }

    private static final class MachineModeActionHandler implements SyncActionHandler {

        @Override
        public ResourceLocation actionId() {
            return SET_MACHINE_MODE_ACTION;
        }

        @Override
        public boolean acceptsHolder(SyncActionContext context) {
            return context.holder() instanceof IRecipeLogicMachine &&
                    context.holder() instanceof LDLib2FancyActionMachine;
        }

        @Override
        public boolean acceptsPayload(DataComponentMap payload) {
            SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
            return fields != null && readActiveRecipeType(fields) != null;
        }

        @Override
        public boolean mayExecute(ServerPlayer player, SyncActionContext context) {
            if (player.isSpectator() || !(context.holder() instanceof IRecipeLogicMachine machine)) {
                return false;
            }
            Integer activeRecipeType = readActiveRecipeType(context.payload());
            return activeRecipeType != null && activeRecipeType >= 0 &&
                    activeRecipeType < machine.getRecipeTypes().length;
        }

        @Override
        public void execute(SyncActionContext context) {
            if (!(context.holder() instanceof IRecipeLogicMachine machine)) {
                throw new IllegalStateException("Machine mode action received a non-recipe machine.");
            }
            int activeRecipeType = requireActiveRecipeType(context.payload());
            boolean needUpdateTickSubs = !machine.keepSubscribing() &&
                    activeRecipeType != machine.getActiveRecipeType();
            machine.setActiveRecipeType(activeRecipeType);
            if (needUpdateTickSubs) {
                machine.getRecipeLogic().updateTickSubscription();
            }
        }
    }

    private static int requireActiveRecipeType(DataComponentMap payload) {
        Integer activeRecipeType = readActiveRecipeType(payload);
        if (activeRecipeType == null) {
            throw new IllegalStateException("Machine mode action payload is missing active recipe type.");
        }
        return activeRecipeType;
    }

    private static @Nullable Integer readActiveRecipeType(DataComponentMap payload) {
        SyncFieldData fields = payload.get(GTDataComponents.SYNC_FIELD_DATA.get());
        if (fields == null) {
            return null;
        }
        return readActiveRecipeType(fields);
    }

    private static @Nullable Integer readActiveRecipeType(SyncFieldData fields) {
        JsonElement element = fields.get(ACTIVE_RECIPE_TYPE_FIELD);
        if (element instanceof JsonPrimitive primitive && primitive.isNumber()) {
            return readExactInt(primitive);
        }
        return null;
    }

    private static @Nullable Integer readExactInt(JsonPrimitive primitive) {
        try {
            long value = primitive.getAsBigDecimal().longValueExact();
            if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
        } catch (ArithmeticException | NumberFormatException e) {
            GTCEu.LOGGER.warn("Invalid machine-mode integer action payload.", e);
        }
        return null;
    }
}

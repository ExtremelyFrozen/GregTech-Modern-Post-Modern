package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.gui.ColorPattern;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.DirectionalAutoOutputMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.client.gui.fancy.LDLib2DirectionalSceneElement;
import com.gregtechceu.gtceu.data.lang.LangHandler;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;

import com.tterrag.registrate.util.RegistrateDistExecutor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * LDLib2 Fancy side page that composes directional machine controls around one shared scene.
 *
 * <p>
 * The provider owns its page-specific machine holder so direct machine pages and future nested part pages cannot
 * accidentally send actions through the Fancy shell's root holder.
 */
public final class LDLib2DirectionalFancyConfigurator implements LDLib2FancyUIProvider {

    public static final int PAGE_WIDTH = 168;
    public static final int PAGE_HEIGHT = 80;

    private static final int SCENE_MARGIN = 4;
    private static final int SCENE_WIDTH = PAGE_WIDTH - SCENE_MARGIN * 2;
    private static final int SCENE_HEIGHT = PAGE_HEIGHT - SCENE_MARGIN * 2;
    private static final int CONTROL_SIZE = 18;
    private static final int CONTROL_GAP = 1;
    private static final int CONTROL_GROUP_WIDTH = CONTROL_SIZE * 2 + CONTROL_GAP;
    private static final int ITEM_COLOR = 0xffff6e0f;

    private static final IGuiTexture ITEM_MODE_OFF = GuiTextures.group(
            GuiTextures.VANILLA_BUTTON,
            GuiTextures.IO_CONFIG_ITEM_MODES_BUTTON.getSubTexture(0, 0, 1, 1 / 3f));
    private static final IGuiTexture ITEM_MODE_OUTPUT = GuiTextures.group(
            GuiTextures.VANILLA_BUTTON,
            GuiTextures.IO_CONFIG_ITEM_MODES_BUTTON.getSubTexture(0, 1 / 3f, 1, 1 / 3f));
    private static final IGuiTexture ITEM_MODE_AUTO = GuiTextures.group(
            GuiTextures.VANILLA_BUTTON,
            GuiTextures.IO_CONFIG_ITEM_MODES_BUTTON.getSubTexture(0, 2 / 3f, 1, 1 / 3f));

    private final DirectionalAutoOutputMachine output;
    private final MachineUIHolder pageHolder;
    private final BiConsumer<MachineUIHolder, SyncActionData> actionSender;

    @Nullable
    private Direction selectedSide;

    enum ItemOutputMode {
        OFF,
        OUTPUT,
        AUTO,
    }

    public LDLib2DirectionalFancyConfigurator(DirectionalAutoOutputMachine output,
                                              MachineUIHolder pageHolder) {
        this(output, pageHolder, MachineUIHelper::sendAction);
    }

    LDLib2DirectionalFancyConfigurator(DirectionalAutoOutputMachine output, MachineUIHolder pageHolder,
                                       BiConsumer<MachineUIHolder, SyncActionData> actionSender) {
        if (!output.supportsAutoOutputItems()) {
            throw new IllegalArgumentException("Item directional page requires item auto-output support.");
        }
        this.output = output;
        this.pageHolder = pageHolder;
        this.actionSender = actionSender;
        LDLib2DirectionalAutoOutputActions.initialize();
    }

    @Override
    public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
        MetaMachine machine = pageHolder.getMachine();
        if (machine == null) {
            throw new IllegalStateException("Item directional page holder no longer resolves a machine.");
        }

        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, PAGE_WIDTH, PAGE_HEIGHT);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));

        UIElement sceneContainer = UITemplate.setLDLib2Bounds(new UIElement(), SCENE_MARGIN, SCENE_MARGIN,
                SCENE_WIDTH, SCENE_HEIGHT);
        sceneContainer.style(style -> style.backgroundTexture(ColorPattern.BLACK.rectTexture()));
        root.addChild(sceneContainer);

        root.addChild(new ItemAutoOutputLabel());
        root.addChild(createItemControls());

        if (machine.isRemote()) {
            RegistrateDistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> LDLib2DirectionalSceneElement.attachItemScene(sceneContainer, machine, output,
                            SCENE_WIDTH, SCENE_HEIGHT, this::handleSceneFaceClick));
        }
        return root;
    }

    @Override
    public IGuiTexture getTabIcon() {
        return GuiTextures.TOOL_COVER_SETTINGS;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gtpm.gui.directional_setting.title");
    }

    @Override
    public List<Component> getTabTooltips() {
        return List.of(Component.translatable("gtpm.gui.directional_setting.tab_tooltip"));
    }

    @Override
    public int getLDLib2PageWidth() {
        return PAGE_WIDTH;
    }

    @Override
    public int getLDLib2PageHeight() {
        return PAGE_HEIGHT;
    }

    boolean handleSceneFaceClick(Direction side, int button) {
        if (selectedSide != side) {
            selectedSide = side;
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            sendAction(LDLib2DirectionalAutoOutputActions.createConfigureItemOutputSideAction(side));
        }
        return true;
    }

    boolean configureSelectedOutputSide() {
        Direction side = selectedSide;
        if (side == null) {
            return false;
        }
        sendAction(LDLib2DirectionalAutoOutputActions.createConfigureItemOutputSideAction(side));
        return true;
    }

    void setAllowInputFromOutputSide(boolean allow) {
        sendAction(LDLib2DirectionalAutoOutputActions.createSetItemInputFromOutputSideAction(allow));
    }

    ItemOutputMode getItemOutputMode() {
        Direction side = selectedSide;
        if (side == null || output.getItemOutputDirection() != side) {
            return ItemOutputMode.OFF;
        }
        return output.isAutoOutputItems() ? ItemOutputMode.AUTO : ItemOutputMode.OUTPUT;
    }

    private UIElement createItemControls() {
        UIElement controls = UITemplate.setLDLib2Bounds(new UIElement(), 6,
                PAGE_HEIGHT - SCENE_MARGIN - CONTROL_SIZE, CONTROL_GROUP_WIDTH, CONTROL_SIZE);
        controls.addChild(new ItemOutputModeButton());
        controls.addChild(new GTToggleButtonElement(CONTROL_SIZE + CONTROL_GAP, 0, CONTROL_SIZE, CONTROL_SIZE,
                GuiTextures.BUTTON_ITEM_OUTPUT,
                output::allowsItemInputFromOutputSide, this::setAllowInputFromOutputSide)
                .setShouldUseBaseBackground()
                .setTooltipText("gtpm.gui.item_auto_output.allow_input"));
        return controls;
    }

    private void sendAction(SyncActionData action) {
        actionSender.accept(pageHolder, action);
    }

    private List<Component> getItemModeTooltips() {
        Direction side = selectedSide;
        if (side == null) {
            return List.copyOf(LangHandler.getMultiLang("gtpm.gui.item_auto_output.unselected"));
        }
        if (output.getItemOutputDirection() != side) {
            return List.copyOf(LangHandler.getMultiLang("gtpm.gui.item_auto_output.other_direction"));
        }
        return List.of(Component.translatable(output.isAutoOutputItems() ?
                "gtpm.gui.item_auto_output.enabled" : "gtpm.gui.item_auto_output.disabled"));
    }

    private IGuiTexture getItemModeTexture() {
        return switch (getItemOutputMode()) {
            case OFF -> ITEM_MODE_OFF;
            case OUTPUT -> ITEM_MODE_OUTPUT;
            case AUTO -> ITEM_MODE_AUTO;
        };
    }

    private final class ItemOutputModeButton extends GTButtonElement {

        private ItemOutputModeButton() {
            super(0, 0, CONTROL_SIZE, CONTROL_SIZE, ITEM_MODE_OFF,
                    LDLib2DirectionalFancyConfigurator.this::onItemOutputModeClick);
            noText();
            refreshState();
        }

        @Override
        public void screenTick() {
            refreshState();
            super.screenTick();
        }

        private void refreshState() {
            setButtonTexture(getItemModeTexture());
            List<Component> tooltips = getItemModeTooltips();
            style(style -> style.tooltips(tooltips.toArray(Component[]::new)));
        }
    }

    private final class ItemAutoOutputLabel extends GTLabelElement {

        private ItemAutoOutputLabel() {
            super(SCENE_MARGIN, SCENE_MARGIN, SCENE_WIDTH, 9,
                    Component.translatable("gtpm.gui.auto_output.name"));
            setTextColor(ITEM_COLOR);
            setTextShadow(false);
            setAllowHitTest(false);
            refreshVisibility();
        }

        @Override
        public void screenTick() {
            refreshVisibility();
            super.screenTick();
        }

        private void refreshVisibility() {
            setVisible(output.isAutoOutputItems() && output.getItemOutputDirection() != null);
        }
    }

    private void onItemOutputModeClick(UIEvent event) {
        if (configureSelectedOutputSide()) {
            event.stopImmediatePropagation();
            event.hasHandler = true;
        }
    }
}

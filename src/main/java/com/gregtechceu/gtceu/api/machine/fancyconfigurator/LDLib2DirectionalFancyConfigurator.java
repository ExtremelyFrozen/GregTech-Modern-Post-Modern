package com.gregtechceu.gtceu.api.machine.fancyconfigurator;

import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.gui.ColorPattern;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTButtonElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.element.GTToggleButtonElement;
import com.gregtechceu.gtceu.api.gui.factory.CoverUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHelper;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.MachineCoverContainer;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.DirectionalAutoOutputMachine;
import com.gregtechceu.gtceu.api.sync_system.SyncActionData;
import com.gregtechceu.gtceu.client.gui.fancy.LDLib2DirectionalSceneElement;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;
import com.gregtechceu.gtceu.data.lang.LangHandler;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
    private static final int CONTROL_ROW_GAP = 3;
    private static final int CONTROL_GROUP_WIDTH = CONTROL_SIZE * 2 + CONTROL_GAP;
    private static final int ITEM_COLOR = 0xffff6e0f;
    private static final int FLUID_COLOR = 0xff00b4ff;

    private static final IGuiTexture ITEM_MODE_OFF = GuiTextures.group(
            GuiTextures.VANILLA_BUTTON,
            GuiTextures.IO_CONFIG_ITEM_MODES_BUTTON.getSubTexture(0, 0, 1, 1 / 3f));
    private static final IGuiTexture ITEM_MODE_OUTPUT = GuiTextures.group(
            GuiTextures.VANILLA_BUTTON,
            GuiTextures.IO_CONFIG_ITEM_MODES_BUTTON.getSubTexture(0, 1 / 3f, 1, 1 / 3f));
    private static final IGuiTexture ITEM_MODE_AUTO = GuiTextures.group(
            GuiTextures.VANILLA_BUTTON,
            GuiTextures.IO_CONFIG_ITEM_MODES_BUTTON.getSubTexture(0, 2 / 3f, 1, 1 / 3f));
    private static final IGuiTexture FLUID_MODE_OFF = GuiTextures.group(
            GuiTextures.VANILLA_BUTTON,
            GuiTextures.IO_CONFIG_FLUID_MODES_BUTTON.getSubTexture(0, 0, 1, 1 / 3f));
    private static final IGuiTexture FLUID_MODE_OUTPUT = GuiTextures.group(
            GuiTextures.VANILLA_BUTTON,
            GuiTextures.IO_CONFIG_FLUID_MODES_BUTTON.getSubTexture(0, 1 / 3f, 1, 1 / 3f));
    private static final IGuiTexture FLUID_MODE_AUTO = GuiTextures.group(
            GuiTextures.VANILLA_BUTTON,
            GuiTextures.IO_CONFIG_FLUID_MODES_BUTTON.getSubTexture(0, 2 / 3f, 1, 1 / 3f));
    private static final IGuiTexture COVER_CONFIG_TEXTURE = GuiTextures.group(GuiTextures.IO_CONFIG_COVER_SETTINGS);

    private final MetaMachine machine;
    private final Player player;
    private final MachineCoverContainer coverContainer;
    @Nullable
    private final DirectionalAutoOutputMachine output;
    private final MachineUIHolder pageHolder;
    private final BiConsumer<MachineUIHolder, SyncActionData> actionSender;

    @Nullable
    private Direction selectedSide;

    enum OutputMode {
        OFF,
        OUTPUT,
        AUTO,
    }

    /**
     * Creates a cover-capable directional page and discovers optional auto-output controls from the machine.
     *
     * @param machine    machine rendered and configured by this page
     * @param player     player whose cursor and cover permissions drive the page controls
     * @param pageHolder stable holder used for every machine action sent by the page
     */
    public LDLib2DirectionalFancyConfigurator(MetaMachine machine, Player player, MachineUIHolder pageHolder) {
        this(machine, player, pageHolder, MachineUIHelper::sendAction);
    }

    LDLib2DirectionalFancyConfigurator(MetaMachine machine, Player player, MachineUIHolder pageHolder,
                                       BiConsumer<MachineUIHolder, SyncActionData> actionSender) {
        requireMatchingMachine(machine, pageHolder);
        this.machine = machine;
        this.player = player;
        this.coverContainer = machine.getCoverContainer();
        this.output = findDirectionalOutput(machine);
        this.pageHolder = pageHolder;
        this.actionSender = actionSender;
        LDLib2DirectionalCoverActions.initialize();
        if (output != null) {
            LDLib2DirectionalAutoOutputActions.initialize();
        }
    }

    private static void requireMatchingMachine(MetaMachine machine, MachineUIHolder pageHolder) {
        MetaMachine heldMachine = pageHolder.getMachine();
        if (heldMachine != machine) {
            throw new IllegalArgumentException("Directional page holder must resolve the configured machine.");
        }
    }

    private static @Nullable DirectionalAutoOutputMachine findDirectionalOutput(MetaMachine machine) {
        DirectionalAutoOutputMachine output = machine instanceof DirectionalAutoOutputMachine directOutput ?
                directOutput : machine.getTrait(AutoOutputTrait.TYPE);
        if (output == null || (!output.supportsAutoOutputItems() && !output.supportsAutoOutputFluids())) {
            return null;
        }
        return output;
    }

    @Override
    public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
        if (pageHolder.getMachine() != machine) {
            throw new IllegalStateException("Directional page holder no longer resolves its opened machine.");
        }

        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, PAGE_WIDTH, PAGE_HEIGHT);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));

        UIElement sceneContainer = UITemplate.setLDLib2Bounds(new UIElement(), SCENE_MARGIN, SCENE_MARGIN,
                SCENE_WIDTH, SCENE_HEIGHT);
        sceneContainer.style(style -> style.backgroundTexture(ColorPattern.BLACK.rectTexture()));
        root.addChild(sceneContainer);

        if (supportsItemOutput()) {
            root.addChild(new ItemAutoOutputLabel());
        }
        if (supportsFluidOutput()) {
            root.addChild(new FluidAutoOutputLabel());
        }
        if (supportsItemOutput()) {
            root.addChild(createItemControls());
        }
        if (supportsFluidOutput()) {
            root.addChild(createFluidControls());
        }
        root.addChild(createCoverControls());

        if (machine.isRemote()) {
            RegistrateDistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> LDLib2DirectionalSceneElement.attachScene(sceneContainer, machine, output,
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
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && supportsItemOutput()) {
            sendAction(LDLib2DirectionalAutoOutputActions.createConfigureItemOutputSideAction(side));
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && supportsFluidOutput()) {
            sendAction(LDLib2DirectionalAutoOutputActions.createConfigureFluidOutputSideAction(side));
        }
        return true;
    }

    boolean configureSelectedItemOutputSide() {
        Direction side = selectedSide;
        if (side == null || !supportsItemOutput()) {
            return false;
        }
        sendAction(LDLib2DirectionalAutoOutputActions.createConfigureItemOutputSideAction(side));
        return true;
    }

    boolean configureSelectedFluidOutputSide() {
        Direction side = selectedSide;
        if (side == null || !supportsFluidOutput()) {
            return false;
        }
        sendAction(LDLib2DirectionalAutoOutputActions.createConfigureFluidOutputSideAction(side));
        return true;
    }

    void setAllowItemInputFromOutputSide(boolean allow) {
        if (machine.isRemote()) {
            requireOutput().setAllowItemInputFromOutputSide(allow);
            machine.sendServerSyncChanges();
        }
    }

    void setAllowFluidInputFromOutputSide(boolean allow) {
        if (machine.isRemote()) {
            requireOutput().setAllowFluidInputFromOutputSide(allow);
            machine.sendServerSyncChanges();
        }
    }

    OutputMode getItemOutputMode() {
        DirectionalAutoOutputMachine output = this.output;
        Direction side = selectedSide;
        if (output == null || side == null || output.getItemOutputDirection() != side) {
            return OutputMode.OFF;
        }
        return output.isAutoOutputItems() ? OutputMode.AUTO : OutputMode.OUTPUT;
    }

    OutputMode getFluidOutputMode() {
        DirectionalAutoOutputMachine output = this.output;
        Direction side = selectedSide;
        if (output == null || side == null || output.getFluidOutputDirection() != side) {
            return OutputMode.OFF;
        }
        return output.isAutoOutputFluids() ? OutputMode.AUTO : OutputMode.OUTPUT;
    }

    private UIElement createItemControls() {
        DirectionalAutoOutputMachine output = requireOutput();
        UIElement controls = UITemplate.setLDLib2Bounds(new UIElement(), 6,
                PAGE_HEIGHT - SCENE_MARGIN - CONTROL_SIZE, CONTROL_GROUP_WIDTH, CONTROL_SIZE);
        controls.addChild(new ItemOutputModeButton());
        controls.addChild(new GTToggleButtonElement(CONTROL_SIZE + CONTROL_GAP, 0, CONTROL_SIZE, CONTROL_SIZE,
                GuiTextures.BUTTON_ITEM_OUTPUT,
                output::allowsItemInputFromOutputSide, this::setAllowItemInputFromOutputSide)
                .setShouldUseBaseBackground()
                .setTooltipText("gtpm.gui.item_auto_output.allow_input"));
        return controls;
    }

    private UIElement createFluidControls() {
        DirectionalAutoOutputMachine output = requireOutput();
        int y = PAGE_HEIGHT - SCENE_MARGIN - CONTROL_SIZE;
        if (output.supportsAutoOutputItems()) {
            y -= CONTROL_SIZE + CONTROL_ROW_GAP;
        }
        UIElement controls = UITemplate.setLDLib2Bounds(new UIElement(), 6, y,
                CONTROL_GROUP_WIDTH, CONTROL_SIZE);
        controls.addChild(new FluidOutputModeButton());
        controls.addChild(new GTToggleButtonElement(CONTROL_SIZE + CONTROL_GAP, 0, CONTROL_SIZE, CONTROL_SIZE,
                GuiTextures.BUTTON_FLUID_OUTPUT,
                output::allowsFluidInputFromOutputSide, this::setAllowFluidInputFromOutputSide)
                .setShouldUseBaseBackground()
                .setTooltipText("gtpm.gui.fluid_auto_output.allow_input"));
        return controls;
    }

    private UIElement createCoverControls() {
        UIElement controls = UITemplate.setLDLib2Bounds(new UIElement(),
                PAGE_WIDTH - SCENE_MARGIN - CONTROL_GROUP_WIDTH,
                PAGE_HEIGHT - SCENE_MARGIN - CONTROL_SIZE,
                CONTROL_GROUP_WIDTH, CONTROL_SIZE);
        controls.addChild(new CoverConfigButton());
        CoverSlotElement coverSlot = new CoverSlotElement();
        UITemplate.setLDLib2Bounds(coverSlot, CONTROL_SIZE + CONTROL_GAP, 0, CONTROL_SIZE, CONTROL_SIZE);
        controls.addChild(coverSlot);
        return controls;
    }

    private void sendAction(SyncActionData action) {
        actionSender.accept(pageHolder, action);
    }

    private boolean supportsItemOutput() {
        DirectionalAutoOutputMachine output = this.output;
        return output != null && output.supportsAutoOutputItems();
    }

    private boolean supportsFluidOutput() {
        DirectionalAutoOutputMachine output = this.output;
        return output != null && output.supportsAutoOutputFluids();
    }

    private DirectionalAutoOutputMachine requireOutput() {
        DirectionalAutoOutputMachine output = this.output;
        if (output == null) {
            throw new IllegalStateException("Cover-only directional page has no auto-output controls.");
        }
        return output;
    }

    private @Nullable CoverBehavior getSelectedCover() {
        Direction side = selectedSide;
        return side == null ? null : coverContainer.getCoverAtSide(side);
    }

    private List<Component> getItemModeTooltips() {
        DirectionalAutoOutputMachine output = requireOutput();
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

    private List<Component> getFluidModeTooltips() {
        DirectionalAutoOutputMachine output = requireOutput();
        Direction side = selectedSide;
        if (side == null) {
            return List.copyOf(LangHandler.getMultiLang("gtpm.gui.fluid_auto_output.unselected"));
        }
        if (output.getFluidOutputDirection() != side) {
            return List.copyOf(LangHandler.getMultiLang("gtpm.gui.fluid_auto_output.other_direction"));
        }
        return List.of(Component.translatable(output.isAutoOutputFluids() ?
                "gtpm.gui.fluid_auto_output.enabled" : "gtpm.gui.fluid_auto_output.disabled"));
    }

    private IGuiTexture getFluidModeTexture() {
        return switch (getFluidOutputMode()) {
            case OFF -> FLUID_MODE_OFF;
            case OUTPUT -> FLUID_MODE_OUTPUT;
            case AUTO -> FLUID_MODE_AUTO;
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

    private final class FluidOutputModeButton extends GTButtonElement {

        private FluidOutputModeButton() {
            super(0, 0, CONTROL_SIZE, CONTROL_SIZE, FLUID_MODE_OFF,
                    LDLib2DirectionalFancyConfigurator.this::onFluidOutputModeClick);
            noText();
            refreshState();
        }

        @Override
        public void screenTick() {
            refreshState();
            super.screenTick();
        }

        private void refreshState() {
            setButtonTexture(getFluidModeTexture());
            List<Component> tooltips = getFluidModeTooltips();
            style(style -> style.tooltips(tooltips.toArray(Component[]::new)));
        }
    }

    private final class ItemAutoOutputLabel extends GTLabelElement {

        private ItemAutoOutputLabel() {
            super(SCENE_MARGIN, SCENE_MARGIN, SCENE_WIDTH / 2, 9,
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
            DirectionalAutoOutputMachine output = requireOutput();
            setVisible(output.isAutoOutputItems() && output.getItemOutputDirection() != null);
        }
    }

    private final class FluidAutoOutputLabel extends GTLabelElement {

        private FluidAutoOutputLabel() {
            super(SCENE_MARGIN + SCENE_WIDTH / 2, SCENE_MARGIN, SCENE_WIDTH / 2, 9,
                    Component.translatable("gtpm.gui.auto_output.name"));
            setTextColor(FLUID_COLOR);
            setTextShadow(false);
            setTextAlignHorizontal(Horizontal.RIGHT);
            setAllowHitTest(false);
            refreshVisibility();
        }

        @Override
        public void screenTick() {
            refreshVisibility();
            super.screenTick();
        }

        private void refreshVisibility() {
            DirectionalAutoOutputMachine output = requireOutput();
            setVisible(output.isAutoOutputFluids() && output.getFluidOutputDirection() != null);
        }
    }

    private final class CoverConfigButton extends GTButtonElement {

        private CoverConfigButton() {
            super(0, 0, CONTROL_SIZE, CONTROL_SIZE, COVER_CONFIG_TEXTURE,
                    LDLib2DirectionalFancyConfigurator.this::onCoverConfigClick);
            noText();
            refreshState();
        }

        @Override
        public void screenTick() {
            refreshState();
            super.screenTick();
        }

        private void refreshState() {
            CoverBehavior cover = getSelectedCover();
            boolean configurable = cover != null && CoverUIHelper.canOpenLDLib2(cover, player);
            setVisible(configurable);
            setActive(configurable);
        }
    }

    private final class CoverSlotElement extends GTItemSlotElement {

        private CoverSlotElement() {
            setCanPutItems(false);
            setCanTakeItems(false);
            setBackgroundTexture(GuiTextures.group(GuiTextures.SLOT, GuiTextures.IO_CONFIG_COVER_SLOT_OVERLAY));
            addEventListener(UIEvents.MOUSE_DOWN, LDLib2DirectionalFancyConfigurator.this::onCoverSlotClick);
            refreshState();
        }

        @Override
        public void screenTick() {
            refreshState();
            super.screenTick();
        }

        private void refreshState() {
            Direction side = selectedSide;
            setVisible(side != null);
            setActive(side != null);
            CoverBehavior cover = getSelectedCover();
            setItem(cover == null ? ItemStack.EMPTY : cover.getAttachItem().copy(), false);
        }
    }

    private void onItemOutputModeClick(UIEvent event) {
        if (configureSelectedItemOutputSide()) {
            event.stopImmediatePropagation();
            event.hasHandler = true;
        }
    }

    private void onFluidOutputModeClick(UIEvent event) {
        if (configureSelectedFluidOutputSide()) {
            event.stopImmediatePropagation();
            event.hasHandler = true;
        }
    }

    private void onCoverSlotClick(UIEvent event) {
        Direction side = selectedSide;
        if (side == null || (event.button != GLFW.GLFW_MOUSE_BUTTON_LEFT &&
                event.button != GLFW.GLFW_MOUSE_BUTTON_RIGHT)) {
            return;
        }
        SyncActionData action = player.containerMenu.getCarried().isEmpty() && getSelectedCover() != null ?
                LDLib2DirectionalCoverActions.createRemoveCoverAction(side) :
                LDLib2DirectionalCoverActions.createPlaceCoverAction(side);
        sendAction(action);
        event.stopImmediatePropagation();
        event.hasHandler = true;
    }

    private void onCoverConfigClick(UIEvent event) {
        Direction side = selectedSide;
        CoverBehavior cover = getSelectedCover();
        if (side == null || cover == null || !CoverUIHelper.canOpenLDLib2(cover, player)) {
            return;
        }
        sendAction(LDLib2DirectionalCoverActions.createOpenCoverAction(side));
        event.stopImmediatePropagation();
        event.hasHandler = true;
    }
}

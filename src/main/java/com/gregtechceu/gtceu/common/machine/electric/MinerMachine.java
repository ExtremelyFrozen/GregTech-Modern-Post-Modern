package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.IMiner;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTComponentPanelElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTScrollerViewElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.WorkableTieredMachine;
import com.gregtechceu.gtceu.api.machine.feature.IDataInfoProvider;
import com.gregtechceu.gtceu.api.machine.feature.LDLib2FancyUIMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.item.behavior.PortableScannerBehavior;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;
import com.gregtechceu.gtceu.common.machine.trait.miner.MinerLogic;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.data.lang.LangHandler;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;
import com.gregtechceu.gtceu.utils.ISubscription;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionResult;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MinerMachine extends WorkableTieredMachine
                          implements IControllable, LDLib2FancyUIMachine, IDataInfoProvider, IMiner {

    private static final int SLOT_SIZE = 18;
    private static final int PAGE_MIN_WIDTH = 172;
    private static final int PAGE_TEMPLATE_EXTRA_WIDTH = 12;
    private static final int PAGE_TEMPLATE_EXTRA_HEIGHT = 8;
    private static final int PAGE_TEMPLATE_X_BIAS = 4;
    private static final int OUTPUT_SLOTS_X = 120;
    private static final int DISPLAY_CONTAINER_WIDTH = 117;
    private static final int DISPLAY_MIN_HEIGHT = 80;
    private static final int DISPLAY_SCROLLER_PADDING = 4;
    private static final int DISPLAY_PANEL_X = 4;
    private static final int DISPLAY_PANEL_Y = 5;
    private static final int DISPLAY_PANEL_MAX_WIDTH = 110;
    private static final int BATTERY_SLOT_X = 100;
    private static final int BATTERY_SLOT_Y = 10;

    @Getter
    @SaveField
    protected final CustomItemStackHandler chargerInventory;
    private final long energyPerTick;
    @Nullable
    protected TickableSubscription batterySubs;
    @Nullable
    protected ISubscription energySubs;

    @SaveField
    @SyncToClient
    public final AutoOutputTrait autoOutput;

    public MinerMachine(BlockEntityCreationInfo info, int tier, int speed, int maximumRadius, int fortune) {
        super(info, tier,
                new MinerLogic(fortune, speed, maximumRadius),
                0, (tier + 1) * (tier + 1), 0, 0, ($) -> 0);
        this.energyPerTick = GTValues.V[tier - 1];
        this.chargerInventory = createChargerItemHandler();
        this.autoOutput = attachTrait(AutoOutputTrait.ofItems(exportItems));
        autoOutput.setItemOutputDirectionValidator(d -> d != Direction.DOWN);
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    protected CustomItemStackHandler createChargerItemHandler() {
        var handler = new CustomItemStackHandler();
        handler.setFilter(item -> GTCapabilityHelper.getElectricItem(item) != null ||
                (ConfigHolder.INSTANCE.compat.energy.nativeEUToFE &&
                        GTCapabilityHelper.getForgeEnergyItem(item) != null));
        return handler;
    }

    @Override
    public void onMachineDestroyed() {
        super.onMachineDestroyed();
        // Remove the miner pipes below this miner
        chargerInventory.dropInventoryInWorld(getLevel(), getBlockPos());
    }

    @Override
    public MinerLogic getRecipeLogic() {
        return (MinerLogic) super.getRecipeLogic();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (!isRemote()) {
            updateBatterySubscription();
            energySubs = energyContainer.addChangedListener(this::updateBatterySubscription);
            chargerInventory.setOnContentsChanged(this::updateBatterySubscription);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (energySubs != null) {
            energySubs.unsubscribe();
            energySubs = null;
        }
    }

    //////////////////////////////////////
    // ********** LOGIC **********//
    //////////////////////////////////////
    protected void updateBatterySubscription() {
        if (energyContainer.dischargeOrRechargeEnergyContainers(chargerInventory, 0, true)) {
            batterySubs = subscribeServerTick(batterySubs, this::chargeBattery);
        } else if (batterySubs != null) {
            batterySubs.unsubscribe();
            batterySubs = null;
        }
    }

    protected void chargeBattery() {
        if (!energyContainer.dischargeOrRechargeEnergyContainers(chargerInventory, 0, false)) {
            updateBatterySubscription();
        }
    }

    //////////////////////////////////////
    // *********** GUI ***********//
    //////////////////////////////////////

    @Override
    public UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0,
                getLDLib2PageWidth(), getLDLib2PageHeight());

        UIElement template = UITemplate.setLDLib2Bounds(new UIElement(), getLDLib2TemplateX(), getLDLib2TemplateY(),
                getLDLib2TemplateWidth(), getLDLib2TemplateHeight());
        template.addChild(createLDLib2DisplayContainer());

        int rowSize = getLDLib2OutputRowSize();
        for (int y = 0; y < rowSize; y++) {
            for (int x = 0; x < rowSize; x++) {
                int index = y * rowSize + x;
                template.addChild(createLDLib2OutputSlot(index, OUTPUT_SLOTS_X + x * SLOT_SIZE,
                        getLDLib2OutputSlotsY() + y * SLOT_SIZE));
            }
        }

        root.addChild(template);
        root.addChild(createLDLib2BatterySlot(BATTERY_SLOT_X, BATTERY_SLOT_Y));
        return root;
    }

    @Override
    public int getLDLib2PageWidth() {
        return Math.max(getLDLib2TemplateWidth() + PAGE_TEMPLATE_EXTRA_WIDTH, PAGE_MIN_WIDTH);
    }

    @Override
    public int getLDLib2PageHeight() {
        return getLDLib2TemplateHeight() + PAGE_TEMPLATE_EXTRA_HEIGHT;
    }

    private UIElement createLDLib2DisplayContainer() {
        UIElement container = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0,
                DISPLAY_CONTAINER_WIDTH, getLDLib2TemplateHeight());
        container.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_INVERSE));

        GTScrollerViewElement scroller = new GTScrollerViewElement(DISPLAY_SCROLLER_PADDING, DISPLAY_SCROLLER_PADDING,
                DISPLAY_CONTAINER_WIDTH - DISPLAY_SCROLLER_PADDING * 2,
                getLDLib2TemplateHeight() - DISPLAY_SCROLLER_PADDING * 2);
        scroller.style(style -> style.backgroundTexture(GuiTextures.DISPLAY));
        scroller.viewPort(viewPort -> viewPort
                .layout(layout -> layout.paddingAll(0))
                .style(style -> style.backgroundTexture(GuiTextures.DISPLAY)));
        scroller.scrollerStyle(style -> style
                .mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.AUTO)
                .horizontalScrollDisplay(ScrollDisplay.NEVER));
        scroller.addScrollViewChild(createLDLib2DisplayPanel());
        container.addChild(scroller);
        return container;
    }

    private GTComponentPanelElement createLDLib2DisplayPanel() {
        return new GTComponentPanelElement(DISPLAY_PANEL_X, DISPLAY_PANEL_Y, this::addDisplayText)
                .setMaxWidthLimit(DISPLAY_PANEL_MAX_WIDTH);
    }

    private GTItemSlotElement createLDLib2OutputSlot(int index, int x, int y) {
        GTItemSlotElement slot = new GTItemSlotElement(exportItems.storage, index)
                .setCanTakeItems(true)
                .setCanPutItems(false)
                .setBackgroundTexture(GuiTextures.SLOT);
        return UITemplate.setLDLib2Bounds(slot, x, y, SLOT_SIZE, SLOT_SIZE);
    }

    private GTItemSlotElement createLDLib2BatterySlot(int x, int y) {
        GTItemSlotElement slot = new GTItemSlotElement(chargerInventory, 0)
                .setCanPutItems(true)
                .setCanTakeItems(true)
                .setBackgroundTexture(GuiTextures.group(GuiTextures.SLOT, GuiTextures.CHARGER_OVERLAY))
                .setOnAddedTooltips((slotElement, tooltips) -> tooltips.addAll(
                        LangHandler.getMultiLang("gtpm.gui.charger_slot.tooltip",
                                GTValues.VNF[getTier()], GTValues.VNF[getTier()])));
        return UITemplate.setLDLib2Bounds(slot, x, y, SLOT_SIZE, SLOT_SIZE);
    }

    private int getLDLib2TemplateX() {
        return (getLDLib2PageWidth() - PAGE_TEMPLATE_X_BIAS - getLDLib2TemplateWidth()) / 2 +
                PAGE_TEMPLATE_X_BIAS;
    }

    private int getLDLib2TemplateY() {
        return (getLDLib2PageHeight() - getLDLib2TemplateHeight()) / 2;
    }

    private int getLDLib2TemplateWidth() {
        return getLDLib2OutputRowSize() * SLOT_SIZE + OUTPUT_SLOTS_X;
    }

    private int getLDLib2TemplateHeight() {
        return Math.max(getLDLib2OutputRowSize() * SLOT_SIZE, DISPLAY_MIN_HEIGHT);
    }

    private int getLDLib2OutputSlotsY() {
        return (getLDLib2TemplateHeight() - getLDLib2OutputRowSize() * SLOT_SIZE) / 2;
    }

    private int getLDLib2OutputRowSize() {
        return (int) Math.sqrt(exportItems.getSlots());
    }

    private void addDisplayText(List<Component> textList) {
        int workingArea = IMiner.getWorkingArea(getRecipeLogic().getCurrentRadius());
        textList.add(recipeLogic.getCustomProgressLine());
        textList.add(Component.translatable("gtpm.machine.miner.startx", getRecipeLogic().getX()).append(" ")
                .append(Component.translatable("gtpm.machine.miner.minex", getRecipeLogic().getMineX())));
        textList.add(Component.translatable("gtpm.machine.miner.starty", getRecipeLogic().getY()).append(" ")
                .append(Component.translatable("gtpm.machine.miner.miney", getRecipeLogic().getMineY())));
        textList.add(Component.translatable("gtpm.machine.miner.startz", getRecipeLogic().getZ()).append(" ")
                .append(Component.translatable("gtpm.machine.miner.minez", getRecipeLogic().getMineZ())));
        textList.add(Component.translatable("gtpm.universal.tooltip.working_area", workingArea, workingArea));
        if (getRecipeLogic().isDone())
            textList.add(Component.translatable("gtpm.multiblock.large_miner.done")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.GREEN)));
        else if (getWorkLogic().isWorking())
            textList.add(Component.translatable("gtpm.multiblock.large_miner.working")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.GOLD)));
        else if (!this.isWorkingEnabled())
            textList.add(Component.translatable("gtpm.multiblock.work_paused"));
        if (getRecipeLogic().isInventoryFull())
            textList.add(Component.translatable("gtpm.multiblock.large_miner.invfull")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
        if (!drainInput(true))
            textList.add(Component.translatable("gtpm.multiblock.large_miner.needspower")
                    .setStyle(Style.EMPTY.withColor(ChatFormatting.RED)));
    }

    @Override
    public boolean drainInput(boolean simulate) {
        long resultEnergy = energyContainer.getEnergyStored() - energyPerTick;
        if (resultEnergy >= 0L && resultEnergy <= energyContainer.getEnergyCapacity()) {
            if (!simulate)
                energyContainer.removeEnergy(energyPerTick);
            return true;
        }
        return false;
    }

    //////////////////////////////////////
    // ******* Interaction *******//
    //////////////////////////////////////
    @Override
    protected InteractionResult onScrewdriverClick(ExtendedUseOnContext context) {
        if (isRemote()) return InteractionResult.SUCCESS;

        if (!this.isActive()) {
            int currentRadius = getRecipeLogic().getCurrentRadius();
            if (currentRadius == 1)
                getRecipeLogic().setCurrentRadius(getRecipeLogic().getMaximumRadius());
            else if (context.getPlayer().isShiftKeyDown())
                getRecipeLogic().setCurrentRadius(Math.max(1, Math.round(currentRadius / 2.0f)));
            else
                getRecipeLogic().setCurrentRadius(Math.max(1, currentRadius - 1));

            getRecipeLogic().resetArea(true);

            int workingArea = IMiner.getWorkingArea(getRecipeLogic().getCurrentRadius());
            context.getPlayer().sendSystemMessage(
                    Component.translatable("gtpm.universal.tooltip.working_area", workingArea, workingArea));
        } else {
            context.getPlayer().sendSystemMessage(Component.translatable("gtpm.multiblock.large_miner.errorradius"));
        }
        return InteractionResult.SUCCESS;
    }

    @NotNull
    @Override
    public List<Component> getDataInfo(PortableScannerBehavior.DisplayMode mode) {
        if (mode == PortableScannerBehavior.DisplayMode.SHOW_ALL ||
                mode == PortableScannerBehavior.DisplayMode.SHOW_MACHINE_INFO) {
            int workingArea = IMiner.getWorkingArea(getRecipeLogic().getCurrentRadius());
            return Collections.singletonList(
                    Component.translatable("gtpm.universal.tooltip.working_area", workingArea, workingArea));
        }
        return new ArrayList<>();
    }
}

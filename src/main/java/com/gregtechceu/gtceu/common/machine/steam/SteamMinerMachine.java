package com.gregtechceu.gtceu.common.machine.steam;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.IMiner;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.element.GTItemSlotElement;
import com.gregtechceu.gtceu.api.gui.element.GTLabelElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IDataInfoProvider;
import com.gregtechceu.gtceu.api.machine.steam.SteamWorkableMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.item.behavior.PortableScannerBehavior;
import com.gregtechceu.gtceu.common.machine.trait.ExhaustVentMachineTrait;
import com.gregtechceu.gtceu.common.machine.trait.miner.SteamMinerLogic;
import com.gregtechceu.gtceu.utils.GTTransferUtils;
import com.gregtechceu.gtceu.utils.ISubscription;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SteamMinerMachine extends SteamWorkableMachine implements IControllable,
                               LDLib2MachineUIProvider, IDataInfoProvider, IMiner {

    @SaveField
    public final NotifiableItemStackHandler importItems;
    @SaveField
    public final NotifiableItemStackHandler exportItems;
    private final int inventorySize;
    private final int energyPerTick;
    @Nullable
    protected TickableSubscription autoOutputSubs;
    @Nullable
    protected ISubscription exportItemSubs;

    @Getter
    private final ExhaustVentMachineTrait exhaustVentTrait;

    public SteamMinerMachine(BlockEntityCreationInfo info, boolean isHighPressure, int speed, int maximumRadius,
                             int fortune, int energyPerTick) {
        super(info, isHighPressure, new SteamMinerLogic(fortune, speed, maximumRadius));

        this.inventorySize = 4;
        this.energyPerTick = energyPerTick;
        this.importItems = attachTrait(createImportItemHandler());
        this.exportItems = attachTrait(createExportItemHandler());
        this.exhaustVentTrait = attachTrait(new ExhaustVentMachineTrait());
        exhaustVentTrait.setVentingDirection(Direction.UP);
        exhaustVentTrait.setVentingDamageAmount(isHighPressure() ? 12F : 6F);
    }

    @Override
    public SteamMinerLogic getRecipeLogic() {
        return (SteamMinerLogic) super.getRecipeLogic();
    }

    protected NotifiableItemStackHandler createImportItemHandler() {
        return new NotifiableItemStackHandler(0, IO.IN);
    }

    protected NotifiableItemStackHandler createExportItemHandler() {
        return new NotifiableItemStackHandler(inventorySize, IO.OUT);
    }

    @Override
    public void onNeighborChanged(Block block, BlockPos fromPos, boolean isMoving) {
        super.onNeighborChanged(block, fromPos, isMoving);
        updateAutoOutputSubscription();
        getRecipeLogic().updateTickSubscription();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        scheduleForNextServerTick(this::updateAutoOutputSubscription);
        if (!isRemote()) {
            exportItemSubs = exportItems.addChangedListener(this::updateAutoOutputSubscription);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (exportItemSubs != null) {
            exportItemSubs.unsubscribe();
            exportItemSubs = null;
        }
    }

    //////////////////////////////////////
    // ********** LOGIC **********//
    //////////////////////////////////////
    protected void updateAutoOutputSubscription() {
        var outputFacingItems = getFrontFacing();
        if (!exportItems.isEmpty() &&
                GTTransferUtils.hasAdjacentItemHandler(getLevel(), getBlockPos(), outputFacingItems)) {
            autoOutputSubs = subscribeServerTick(autoOutputSubs, this::autoOutput);
        } else if (autoOutputSubs != null) {
            autoOutputSubs.unsubscribe();
            autoOutputSubs = null;
        }
    }

    protected void autoOutput() {
        if (getOffsetTimer() % 5 == 0) {
            exportItems.exportToNearby(getFrontFacing());
        }
        updateAutoOutputSubscription();
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
        int rowSize = (int) Math.sqrt(inventorySize);

        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, 175, 176);
        root.style(style -> style.backgroundTexture(GuiTextures.BACKGROUND_STEAM.get(isHighPressure())));
        root.addChild(UITemplate.bindPlayerInventoryLDLib2(player.getInventory(),
                GuiTextures.SLOT_STEAM.get(isHighPressure()), 7, 94, true));

        for (int y = 0; y < rowSize; y++) {
            for (int x = 0; x < rowSize; x++) {
                int index = y * rowSize + x;
                GTItemSlotElement slot = new GTItemSlotElement(exportItems, index)
                        .setBackgroundTexture(GuiTextures.SLOT_STEAM.get(isHighPressure()))
                        .setCanTakeItems(true)
                        .setCanPutItems(false);
                UITemplate.setLDLib2Bounds(slot, 142 - rowSize * 9 + x * 18, 18 + y * 18, 18, 18);
                root.addChild(slot);
            }
        }

        root.addChild(createLDLib2TitleLabel());
        root.addChild(new GTImageElement(7, 16, 105, 75, GuiTextures.DISPLAY_STEAM.get(isHighPressure())));
        root.addChild(new GTImageElement(79, 42, 18, 18,
                GuiTextures.INDICATOR_NO_STEAM.get(isHighPressure())).setVisibleSupplier(() -> !drainInput(true)));
        root.addChild(createDisplayTextPanel(10, 19, this::addDisplayText));
        root.addChild(createDisplayTextPanel(70, 19, this::addDisplayText2));

        return UI.of(root);
    }

    private GTLabelElement createLDLib2TitleLabel() {
        GTLabelElement label = new GTLabelElement(5, 5, 166, 10,
                getBlockState().getBlock().getDescriptionId(), true);
        label.textStyle(style -> style
                .textColor(0x404040)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.CENTER));
        return label;
    }

    private GTLabelElement createDisplayTextPanel(int x, int y, Consumer<List<Component>> displayTextAppender) {
        GTLabelElement label = new GTLabelElement(x, y, 84, 70) {

            @Override
            public void screenTick() {
                setText(buildDisplayText(displayTextAppender));
                super.screenTick();
            }
        };
        label.setText(buildDisplayText(displayTextAppender));
        label.textStyle(style -> style
                .textColor(0xFFFFFF)
                .textShadow(false)
                .textAlignHorizontal(Horizontal.LEFT)
                .textAlignVertical(Vertical.TOP)
                .textWrap(TextWrap.WRAP)
                .fontSize(9f)
                .lineSpacing(0f));
        return label;
    }

    private MutableComponent buildDisplayText(Consumer<List<Component>> displayTextAppender) {
        List<Component> displayText = new ArrayList<>();
        displayTextAppender.accept(displayText);
        MutableComponent text = Component.empty();
        for (int index = 0; index < displayText.size(); index++) {
            if (index > 0) {
                text.append("\n");
            }
            text.append(displayText.get(index));
        }
        return text;
    }

    void addDisplayText(List<Component> textList) {
        int workingArea = IMiner.getWorkingArea(getRecipeLogic().getCurrentRadius());
        textList.add(Component.translatable("gtpm.machine.miner.startx", this.getRecipeLogic().getX()));
        textList.add(Component.translatable("gtpm.machine.miner.starty", this.getRecipeLogic().getY()));
        textList.add(Component.translatable("gtpm.machine.miner.startz", this.getRecipeLogic().getZ()));
        textList.add(Component.translatable("gtpm.universal.tooltip.working_area", workingArea, workingArea));
        if (this.getRecipeLogic().isDone())
            textList.add(Component.translatable("gtpm.multiblock.large_miner.done")
                    .withStyle(ChatFormatting.GREEN));
        else if (getWorkLogic().isWorking())
            textList.add(Component.translatable("gtpm.multiblock.large_miner.working")
                    .withStyle(ChatFormatting.GOLD));
        else if (!this.isWorkingEnabled())
            textList.add(Component.translatable("gtpm.multiblock.work_paused"));
        if (getRecipeLogic().isInventoryFull())
            textList.add(Component.translatable("gtpm.multiblock.large_miner.invfull")
                    .withStyle(ChatFormatting.RED));
        if (exhaustVentTrait.isVentingBlocked())
            textList.add(Component.translatable("gtpm.multiblock.large_miner.vent")
                    .withStyle(ChatFormatting.RED));
        else if (!drainInput(true))
            textList.add(Component.translatable("gtpm.multiblock.large_miner.steam")
                    .withStyle(ChatFormatting.RED));
    }

    void addDisplayText2(List<Component> textList) {
        textList.add(Component.translatable("gtpm.machine.miner.minex", this.getRecipeLogic().getMineX()));
        textList.add(Component.translatable("gtpm.machine.miner.miney", this.getRecipeLogic().getMineY()));
        textList.add(Component.translatable("gtpm.machine.miner.minez", this.getRecipeLogic().getMineZ()));
    }

    @Override
    public boolean drainInput(boolean simulate) {
        long resultSteam = steamTank.getFluidInTank(0).getAmount() - energyPerTick;
        if (!exhaustVentTrait.isVentingBlocked() && resultSteam >= 0L && resultSteam <= steamTank.getTankCapacity(0)) {
            if (!simulate)
                steamTank.drainInternal(energyPerTick, IFluidHandler.FluidAction.EXECUTE);
            return true;
        }
        return false;
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

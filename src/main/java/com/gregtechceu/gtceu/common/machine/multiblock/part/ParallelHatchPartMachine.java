package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTIntInputElement;
import com.gregtechceu.gtceu.api.gui.factory.LDLib2MachineUIProvider;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldChangeListener;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldNormalizer;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;

import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import lombok.Getter;

public class ParallelHatchPartMachine extends TieredPartMachine implements LDLib2MachineUIProvider {

    private static final int MIN_PARALLEL = 1;

    private final int maxParallel;

    @SaveField
    @SyncBoth
    @Getter
    private int currentParallel = 1;

    public ParallelHatchPartMachine(BlockEntityCreationInfo info, int tier) {
        super(info, tier);
        this.maxParallel = (int) Math.pow(4, tier - GTValues.EV);
        this.currentParallel = maxParallel;
    }

    public void setCurrentParallel(int parallelAmount) {
        int normalizedParallel = normalizeCurrentParallel(parallelAmount);
        if (this.currentParallel == normalizedParallel) {
            return;
        }
        this.currentParallel = normalizedParallel;
        markControllerRecipesDirty();
    }

    @ServerFieldNormalizer(fieldName = "currentParallel")
    private int normalizeCurrentParallel(int parallelAmount) {
        return Mth.clamp(parallelAmount, MIN_PARALLEL, this.maxParallel);
    }

    @ServerFieldChangeListener(fieldName = "currentParallel")
    private void onCurrentParallelChanged(int oldParallel, int newParallel) {
        markControllerRecipesDirty();
    }

    protected void markControllerRecipesDirty() {
        for (MultiblockControllerMachine controller : this.getControllers()) {
            if (controller instanceof IRecipeLogicMachine rlm) {
                rlm.getRecipeLogic().markLastRecipeDirty();
            }
        }
    }

    @Override
    public boolean canCreateLDLib2UI(Player player, MachineUIHolder holder) {
        return holder.getMachine() == this;
    }

    @Override
    public UI createLDLib2UI(Player player, MachineUIHolder holder) {
        UIElement root = new UIElement();
        UITemplate.setLDLib2Bounds(root, 0, 0, 100, 20);
        root.addChild(new GTIntInputElement(0, 0, 100, 20, this::getCurrentParallel,
                this::setLDLib2CurrentParallel)
                .setMin(MIN_PARALLEL)
                .setMax(maxParallel));
        return UI.of(root);
    }

    private void setLDLib2CurrentParallel(int value) {
        setCurrentParallel(value);
        sendServerSyncChanges();
    }

    @Override
    public boolean canShared(MultiblockControllerMachine controller, String structureName) {
        return false;
    }
}

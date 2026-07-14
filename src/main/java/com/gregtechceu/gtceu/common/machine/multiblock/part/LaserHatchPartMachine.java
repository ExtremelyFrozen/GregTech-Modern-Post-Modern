package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyPreviewPage;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.feature.IDataInfoProvider;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableLaserContainer;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.item.behavior.PortableScannerBehavior;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LaserHatchPartMachine extends TieredIOPartMachine
                                   implements IDataInfoProvider, LDLib2FancyPartUIProvider {

    @SaveField
    private NotifiableLaserContainer buffer;

    public LaserHatchPartMachine(BlockEntityCreationInfo info, IO io, int tier, int amperage) {
        super(info, tier, io);
        if (io == IO.OUT) {
            this.buffer = attachTrait(NotifiableLaserContainer.emitterContainer(GTValues.V[tier] * 64L * amperage,
                    GTValues.V[tier], amperage));
            this.buffer.setSideOutputCondition(s -> s == getFrontFacing());
        } else {
            this.buffer = attachTrait(NotifiableLaserContainer.receiverContainer(GTValues.V[tier] * 64L * amperage,
                    GTValues.V[tier], amperage));
            this.buffer.setSideInputCondition(s -> s == getFrontFacing());
        }
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return false;
    }

    @Override
    public LDLib2FancyUIProvider createLDLib2FancyPage(Player player, MachineUIHolder holder) {
        return new LDLib2FancyPreviewPage(this, player, holder, switch (io) {
            case IN -> new LDLib2FancyUIProvider.PageGroupingData(
                    "gtpm.multiblock.page_switcher.io.import", 1);
            case OUT -> new LDLib2FancyUIProvider.PageGroupingData(
                    "gtpm.multiblock.page_switcher.io.export", 2);
            case BOTH -> new LDLib2FancyUIProvider.PageGroupingData(
                    "gtpm.multiblock.page_switcher.io.both", 3);
            case NONE -> null;
        });
    }

    @Override
    public boolean canShared(MultiblockControllerMachine controller, String structureName) {
        return false;
    }

    @NotNull
    @Override
    public List<Component> getDataInfo(PortableScannerBehavior.DisplayMode mode) {
        if (mode == PortableScannerBehavior.DisplayMode.SHOW_ALL ||
                mode == PortableScannerBehavior.DisplayMode.SHOW_ELECTRICAL_INFO) {
            return Collections.singletonList(Component.translatable(
                    String.format("%d/%d EU", buffer.getEnergyStored(), buffer.getEnergyCapacity())));
        }
        return new ArrayList<>();
    }

    //////////////////////////////////////
    // ********** Misc **********//
    //////////////////////////////////////

    @Override
    public int tintColor(int index) {
        if (index == 2) {
            return GTValues.VC[getTier()];
        }
        return super.tintColor(index);
    }
}

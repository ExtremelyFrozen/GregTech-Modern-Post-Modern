package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyPreviewPage;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.MultiblockComputationPortTrait;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableComputationContainer;
import com.gregtechceu.gtceu.common.computation.ComputationNetworkManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;

import lombok.Getter;
import org.jetbrains.annotations.MustBeInvokedByOverriders;

public class OpticalComputationHatchMachine extends MultiblockPartMachine implements LDLib2FancyPartUIProvider {

    @Getter
    private final boolean transmitter;

    protected NotifiableComputationContainer computationContainer;
    @Getter
    protected final MultiblockComputationPortTrait computationPort;

    public OpticalComputationHatchMachine(BlockEntityCreationInfo info, boolean transmitter) {
        super(info);
        this.transmitter = transmitter;
        this.computationContainer = attachTrait(new NotifiableComputationContainer(IO.IN, transmitter));
        this.computationPort = new MultiblockComputationPortTrait(this, transmitter, !transmitter);
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return false;
    }

    /** Creates the holder-scoped default preview used by a surrounding multiblock controller. */
    @Override
    public LDLib2FancyUIProvider createLDLib2FancyPage(Player player, MachineUIHolder holder) {
        return new LDLib2FancyPreviewPage(this, player, holder, null);
    }

    @Override
    public boolean canShared(MultiblockControllerMachine controller, String structureName) {
        return false;
    }

    @Override
    public void addedToController(MultiblockControllerMachine controller, String structureName) {
        super.addedToController(controller, structureName);
        markComputationTopologyDirty();
    }

    @MustBeInvokedByOverriders
    @Override
    public void removedFromController(MultiblockControllerMachine controller, String structureName) {
        super.removedFromController(controller, structureName);
        markComputationTopologyDirty();
    }

    private void markComputationTopologyDirty() {
        if (getLevel() instanceof ServerLevel serverLevel) {
            ComputationNetworkManager.get(serverLevel).markTopologyDirty();
        }
    }
}

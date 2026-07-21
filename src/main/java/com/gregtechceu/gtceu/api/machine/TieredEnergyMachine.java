package com.gregtechceu.gtceu.api.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTProgressBarElement;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.machine.trait.EnvironmentalExplosionTrait;

import com.lowdragmc.lowdraglib2.gui.ui.data.FillDirection;

import net.minecraft.util.Mth;

import lombok.Getter;

import java.util.function.Function;

public class TieredEnergyMachine extends TieredMachine implements ITieredMachine {

    @SaveField
    @SyncToClient
    public final NotifiableEnergyContainer energyContainer;
    @Getter
    protected final EnvironmentalExplosionTrait environmentalExplosionTrait;

    public TieredEnergyMachine(BlockEntityCreationInfo info, int tier,
                               NotifiableEnergyContainer energyContainer) {
        super(info, tier);
        this.energyContainer = attachTrait(energyContainer);
        environmentalExplosionTrait = attachTrait(new EnvironmentalExplosionTrait(tier, tier * 10,
                () -> energyContainer.getEnergyStored() > 0));
    }

    public TieredEnergyMachine(BlockEntityCreationInfo info, int tier,
                               Function<TieredEnergyMachine, NotifiableEnergyContainer> energyContainer) {
        super(info, tier);
        this.energyContainer = attachTrait(energyContainer.apply(this));
        environmentalExplosionTrait = attachTrait(new EnvironmentalExplosionTrait(tier, tier * 10,
                () -> this.energyContainer.getEnergyStored() > 0));
    }

    public TieredEnergyMachine(BlockEntityCreationInfo info, int tier) {
        super(info, tier);

        long tierVoltage = GTValues.V[tier];
        if (isEnergyEmitter()) {
            energyContainer = attachTrait(NotifiableEnergyContainer.emitterContainer(tierVoltage * 64L, tierVoltage,
                    getMaxInputOutputAmperage()));
        } else {
            energyContainer = attachTrait(NotifiableEnergyContainer.receiverContainer(tierVoltage * 64L, tierVoltage,
                    getMaxInputOutputAmperage()));
        }
        environmentalExplosionTrait = attachTrait(new EnvironmentalExplosionTrait(tier, tier * 10,
                () -> energyContainer.getEnergyStored() > 0));
    }

    //////////////////////////////////////
    // ********** MISC ***********//
    //////////////////////////////////////
    @Override
    public int getAnalogOutputSignal() {
        long energyStored = energyContainer.getEnergyStored();
        long energyCapacity = energyContainer.getEnergyCapacity();
        float f = energyCapacity == 0L ? 0.0f : energyStored / (energyCapacity * 1.0f);
        return Mth.floor(f * 14.0f) + (energyStored > 0 ? 1 : 0);
    }

    /**
     * Determines max input or output amperage used by this meta tile entity
     * if emitter, it determines size of energy packets it will emit at once
     * if receiver, it determines max input energy per request
     *
     * @return max amperage received or emitted by this machine
     */
    protected long getMaxInputOutputAmperage() {
        return 1L;
    }

    /**
     * Determines if this meta tile entity is in energy receiver or emitter mode
     *
     * @return true if machine emits energy to network, false it it accepts energy from network
     */
    protected boolean isEnergyEmitter() {
        return false;
    }

    /**
     * Create an LDLib2 energy bar element.
     */
    protected GTProgressBarElement createLDLib2EnergyBar() {
        var progressBar = bindLDLib2EnergyBar(new GTProgressBarElement())
                .setProgressTexture(IGuiTexture.EMPTY, GuiTextures.ENERGY_BAR_BASE)
                .setFillDirection(FillDirection.DOWN_TO_UP);
        progressBar.style(style -> style.backgroundTexture(GuiTextures.ENERGY_BAR_BACKGROUND));
        return UITemplate.setLDLib2Bounds(progressBar, 0, 0, 18, 60);
    }

    /**
     * Binds a parsed LDLib2 energy bar to this machine's energy container.
     */
    protected GTProgressBarElement bindLDLib2EnergyBar(GTProgressBarElement progressBar) {
        return progressBar.setProgressSupplier(this::getLDLib2EnergyProgress);
    }

    private double getLDLib2EnergyProgress() {
        long energyCapacity = energyContainer.getEnergyCapacity();
        if (energyCapacity == 0L) {
            return 0.0;
        }
        return energyContainer.getEnergyStored() * 1.0 / energyCapacity;
    }
}

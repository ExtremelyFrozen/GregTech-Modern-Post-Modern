package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyPreviewPage;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.machine.trait.EnvironmentalExplosionTrait;

import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.world.entity.player.Player;

import lombok.Getter;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class EnergyHatchPartMachine extends TieredIOPartMachine implements LDLib2FancyPartUIProvider {

    @SaveField
    public final NotifiableEnergyContainer energyContainer;
    @Getter
    protected int amperage;

    public EnergyHatchPartMachine(BlockEntityCreationInfo info, int tier, IO io, int amperage) {
        super(info, tier, io);
        this.amperage = amperage;
        this.energyContainer = attachTrait(createEnergyContainer());
        attachTrait(new EnvironmentalExplosionTrait(tier, tier * 10, () -> energyContainer.getEnergyStored() > 0));
    }

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    protected NotifiableEnergyContainer createEnergyContainer() {
        NotifiableEnergyContainer container;
        if (io == IO.OUT) {
            container = NotifiableEnergyContainer.emitterContainer(GTValues.V[tier] * 64L * amperage,
                    GTValues.V[tier], amperage);
            container.setSideOutputCondition(s -> s == getFrontFacing() && isWorkingEnabled());
            container.setCapabilityValidator(s -> s == null || s == getFrontFacing());
        } else {
            container = NotifiableEnergyContainer.receiverContainer(GTValues.V[tier] * 16L * amperage,
                    GTValues.V[tier], amperage);
            container.setSideInputCondition(s -> s == getFrontFacing() && isWorkingEnabled());
            container.setCapabilityValidator(s -> s == null || s == getFrontFacing());
        }
        return container;
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
    public void onLoad() {
        super.onLoad();
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

    public static long getHatchEnergyCapacity(int tier, int amperage) {
        return GTValues.V[tier] * 64L * amperage;
    }
}

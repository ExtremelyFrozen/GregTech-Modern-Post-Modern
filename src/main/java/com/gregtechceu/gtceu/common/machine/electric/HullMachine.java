package com.gregtechceu.gtceu.common.machine.electric;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IMonitorComponent;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyPreviewPage;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.sync_system.ClassSyncData;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.codecs.GridNodeHostCodec;
import com.gregtechceu.gtceu.integration.ae2.machine.trait.GridNodeHostTrait;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;

public class HullMachine extends TieredPartMachine implements IMonitorComponent, LDLib2FancyPartUIProvider {

    @SaveField(nbtKey = "grid_node")
    private final Object gridNodeHost;

    @SaveField
    protected NotifiableEnergyContainer energyContainer;

    public HullMachine(BlockEntityCreationInfo info, int tier) {
        super(info, tier);
        if (GTCEu.Mods.isAE2Loaded()) {
            this.gridNodeHost = GridNodeHostFactory.attachToMachine(this);
        } else {
            this.gridNodeHost = null;
        }

        long tierVoltage = GTValues.V[getTier()];
        this.energyContainer = attachTrait(
                new NotifiableEnergyContainer(tierVoltage * 16L, tierVoltage, 1L, tierVoltage, 1L));
        this.energyContainer.setSideOutputCondition(s -> s == getFrontFacing());
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (GTCEu.Mods.isAE2Loaded() && gridNodeHost instanceof GridNodeHostTrait connectedBlockEntity) {
            scheduleForNextServerTick(connectedBlockEntity::init);
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (GTCEu.Mods.isAE2Loaded() && gridNodeHost instanceof GridNodeHostTrait connectedBlockEntity) {
            connectedBlockEntity.getMainNode().destroy();
        }
    }

    @Override
    public void setFrontFacing(Direction facing) {
        super.setFrontFacing(facing);
        if (isFacingValid(facing)) {
            if (GTCEu.Mods.isAE2Loaded() && gridNodeHost instanceof GridNodeHostTrait connectedBlockEntity) {
                connectedBlockEntity.init();
            }
        }
    }

    @Override
    public LDLib2FancyUIProvider createLDLib2FancyPage(Player player, MachineUIHolder holder) {
        return new LDLib2FancyPreviewPage(this, player, holder, null);
    }

    //////////////////////////////////////
    // ********** Misc **********//
    //////////////////////////////////////

    private static class GridNodeHostFactory {

        private static Object attachToMachine(HullMachine machine) {
            return machine.attachTrait(new GridNodeHostTrait(machine));
        }
    }

    static {
        if (GTCEu.Mods.isAE2Loaded()) {
            ClassSyncData.getClassData(HullMachine.class).setCustomContextualCodecForField("gridNodeHost",
                    GridNodeHostCodec.INSTANCE);
        }
    }

    @Override
    public int tintColor(int index) {
        if (index == 2) {
            return GTValues.VC[getTier()];
        }
        return super.tintColor(index);
    }

    @Override
    public IGuiTexture getComponentIcon() {
        return GuiTextures.BUTTON_CHECK; // temporary (until there's a texture that is not fully 16x16 for this)
    }
}

package com.gregtechceu.gtceu.common.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.gui.factory.MachineUIHolder;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyUIProvider;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.LDLib2FancyPartUIProvider;

import net.minecraft.world.entity.player.Player;

/**
 * Ordinary Item Bus whose existing LDLib2 page can also be opened from a multiblock controller context.
 *
 * <p>
 * The provider marker lives on this concrete type so specialized Item Buses cannot accidentally inherit a page
 * contract that their distinct standalone interfaces do not implement.
 */
public final class StandardItemBusPartMachine extends ItemBusPartMachine
                                              implements LDLib2FancyPartUIProvider {

    /**
     * Creates an ordinary Item Bus with the configured tier and direction.
     */
    public StandardItemBusPartMachine(BlockEntityCreationInfo info, int tier, IO io) {
        super(info, tier, io);
    }

    /**
     * Ordinary definitions always use the generic Item Bus page, including addon registrations.
     */
    @Override
    protected boolean supportsGenericLDLib2Page() {
        return true;
    }

    /**
     * Reuses the holder-validated ordinary Item Bus page for one controller UI opening.
     */
    @Override
    public LDLib2FancyUIProvider createLDLib2FancyPage(Player player, MachineUIHolder holder) {
        return createLDLib2Page(player, holder);
    }
}

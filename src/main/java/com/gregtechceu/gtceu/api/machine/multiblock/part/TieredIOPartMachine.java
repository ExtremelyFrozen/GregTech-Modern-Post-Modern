package com.gregtechceu.gtceu.api.machine.multiblock.part;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.sync_system.annotations.RerenderOnChanged;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.ServerFieldChangeListener;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncBoth;

import lombok.Getter;
import org.jetbrains.annotations.Nullable;

public class TieredIOPartMachine extends TieredPartMachine implements IControllable {

    protected final IO io;

    /**
     * AUTO IO working?
     */
    @Getter
    @SaveField
    @SyncBoth
    @RerenderOnChanged
    protected boolean workingEnabled;

    public TieredIOPartMachine(BlockEntityCreationInfo info, int tier, IO io) {
        super(info, tier);
        this.io = io;
        this.workingEnabled = true;
    }

    @Override
    public void setWorkingEnabled(boolean workingEnabled) {
        this.workingEnabled = workingEnabled;
        onWorkingEnabledChanged();
    }

    @ServerFieldChangeListener(fieldName = "workingEnabled")
    private void onWorkingEnabledUpdatedByClient(boolean oldValue, boolean newValue) {
        onWorkingEnabledChanged();
    }

    /**
     * Rebuilds subclass runtime state after either a direct setter call or an accepted client update.
     */
    protected void onWorkingEnabledChanged() {}

    //////////////////////////////////////
    // ***** Initialization ******//
    //////////////////////////////////////

    @Nullable
    @Override
    public PageGroupingData getPageGroupingData() {
        return switch (this.io) {
            case IN -> new PageGroupingData("gtpm.multiblock.page_switcher.io.import", 1);
            case OUT -> new PageGroupingData("gtpm.multiblock.page_switcher.io.export", 2);
            case BOTH -> new PageGroupingData("gtpm.multiblock.page_switcher.io.both", 3);
            case NONE -> null;
        };
    }
}

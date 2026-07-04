package com.gregtechceu.gtceu.integration.ae2.autobuild;

import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildMaterialSources;

public final class MEAutoBuildSources {

    private MEAutoBuildSources() {}

    public static void init() {
        AutoBuildMaterialSources.registerMESource(MEAutoBuildSource::create);
    }
}

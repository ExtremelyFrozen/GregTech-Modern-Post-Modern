package com.gregtechceu.gtceu.common.item.datacomponents;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.autobuild.AutoBuildOptions;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Stores the backend-only automatic multiblock build options carried by a Terminal item.
 *
 * @param structureName selected multiblock substructure name; defaults to the controller main structure
 * @param options       selected automatic build behavior used when the Terminal calls a controller
 */
public record TerminalAutoBuildConfig(String structureName, AutoBuildOptions options) {

    public static final TerminalAutoBuildConfig DEFAULT = new TerminalAutoBuildConfig(
            MultiblockControllerMachine.DEFAULT_STRUCTURE, AutoBuildOptions.DEFAULT);

    public static final Codec<TerminalAutoBuildConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("structure_name", DEFAULT.structureName())
                    .forGetter(TerminalAutoBuildConfig::structureName),
            AutoBuildOptions.CODEC.optionalFieldOf("options", DEFAULT.options())
                    .forGetter(TerminalAutoBuildConfig::options))
            .apply(instance, TerminalAutoBuildConfig::new));
}

package com.gregtechceu.gtceu.api.multiblock.autobuild;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

/**
 * Describes one automatic multiblock build request's user-selected behavior.
 *
 * @param repeatCount    repeat count requested for repeatable pattern units; zero means pattern minimum
 * @param replaceMode    whether occupied target positions may be cleared and replaced during building
 * @param demolitionMode whether the request removes matching structure blocks instead of placing them
 * @param useME          whether optional ME material access should be used when available
 * @param flipMode       whether to use the flipped structure transform for this request
 * @param noHatchMode    whether non-single predicates should avoid choosing hatch-like machine blocks
 * @param tierSelections selected one-based tier index by block category
 */
public record AutoBuildOptions(int repeatCount, boolean replaceMode, boolean demolitionMode, boolean useME,
                               boolean flipMode, boolean noHatchMode, Map<String, Integer> tierSelections) {

    public AutoBuildOptions {
        tierSelections = Map.copyOf(tierSelections);
    }

    public static final AutoBuildOptions DEFAULT = new AutoBuildOptions(0, false, false, false, false, true,
            Map.of());

    public static final Codec<AutoBuildOptions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("repeat_count", DEFAULT.repeatCount()).forGetter(AutoBuildOptions::repeatCount),
            Codec.BOOL.optionalFieldOf("replace_mode", DEFAULT.replaceMode()).forGetter(AutoBuildOptions::replaceMode),
            Codec.BOOL.optionalFieldOf("demolition_mode", DEFAULT.demolitionMode())
                    .forGetter(AutoBuildOptions::demolitionMode),
            Codec.BOOL.optionalFieldOf("use_me", DEFAULT.useME()).forGetter(AutoBuildOptions::useME),
            Codec.BOOL.optionalFieldOf("flip_mode", DEFAULT.flipMode()).forGetter(AutoBuildOptions::flipMode),
            Codec.BOOL.optionalFieldOf("no_hatch_mode", DEFAULT.noHatchMode())
                    .forGetter(AutoBuildOptions::noHatchMode),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("tier_selections", Map.of())
                    .forGetter(AutoBuildOptions::tierSelections))
            .apply(instance, AutoBuildOptions::new));
}

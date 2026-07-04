package com.gregtechceu.gtceu.api.multiblock.autobuild;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import org.jetbrains.annotations.Nullable;

/**
 * A concrete reason an automatic multiblock build request could not continue.
 *
 * @param type    machine-readable problem category
 * @param pos     optional world position related to the problem
 * @param message user-facing diagnostic text
 */
public record AutoBuildProblem(Type type, @Nullable BlockPos pos, Component message) {

    public enum Type {
        UNKNOWN_STRUCTURE,
        INVALID_OPTIONS,
        PATTERN_UNAVAILABLE,
        PERMISSION_DENIED,
        UNLOADED,
        BLOCKED,
        UNSUPPORTED,
        MISSING_MATERIAL,
        PLACE_FAILED,
        DEMOLITION_FAILED,
        ME_UNAVAILABLE,
        STRUCTURE_CHECK_FAILED
    }
}

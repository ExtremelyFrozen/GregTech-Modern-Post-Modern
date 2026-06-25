package com.gregtechceu.gtceu.data.pattern;

import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import net.minecraft.resources.ResourceLocation;

import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Identifies one named structure pattern belonging to a multiblock machine.
 *
 * @param machineId     the registry id of the multiblock machine that owns the structure
 * @param structureName the non-empty structure variant name registered by the machine definition
 */
public record StructurePatternKey(ResourceLocation machineId, String structureName) {

    public static final String DEFAULT_STRUCTURE_NAME = MultiblockControllerMachine.DEFAULT_STRUCTURE;

    public StructurePatternKey {
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(structureName, "structureName");
        if (structureName.isBlank()) {
            throw new IllegalArgumentException("Structure pattern name must not be blank for " + machineId);
        }
        if (structureName.indexOf('/') >= 0 || structureName.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Structure pattern name must be one path segment: " + structureName);
        }
    }

    public static StructurePatternKey main(ResourceLocation machineId) {
        return new StructurePatternKey(machineId, DEFAULT_STRUCTURE_NAME);
    }

    public boolean isDefaultStructure() {
        return DEFAULT_STRUCTURE_NAME.equals(structureName);
    }

    public ResourceLocation resourceId() {
        if (isDefaultStructure()) {
            return machineId;
        }
        return machineId.withPath(path -> path + "/" + structureName);
    }

    @Override
    public @NonNull String toString() {
        return machineId + "#" + structureName;
    }
}

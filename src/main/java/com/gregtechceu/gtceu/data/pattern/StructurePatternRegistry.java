package com.gregtechceu.gtceu.data.pattern;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.data.pattern.event.StructurePatternsReloadedEvent;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class StructurePatternRegistry {

    private static final Map<ResourceLocation, MultiblockMachineDefinition> DEFINITIONS = new ConcurrentHashMap<>();

    private StructurePatternRegistry() {}

    public static void register(MultiblockMachineDefinition definition) {
        DEFINITIONS.put(definition.getId(), definition);
    }

    public static void onStructurePatternsReloaded(StructurePatternsReloadedEvent event) {
        int refreshed = 0;
        if (event.getId() != null) {
            MultiblockMachineDefinition definition = DEFINITIONS.get(event.getId());
            if (definition != null && reloadPattern(definition)) {
                refreshed = 1;
            }
        } else {
            for (MultiblockMachineDefinition definition : DEFINITIONS.values()) {
                if (reloadPattern(definition)) {
                    refreshed++;
                }
            }
        }
        event.addRefreshedPatterns(refreshed);
    }

    private static boolean reloadPattern(MultiblockMachineDefinition definition) {
        try {
            definition.reloadPattern();
            return true;
        } catch (Exception e) {
            GTCEu.LOGGER.error("Failed to reload structure pattern for {}", definition.getId(), e);
            return false;
        }
    }
}

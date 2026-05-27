package com.gregtechceu.gtceu.data.pattern;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;

import org.jetbrains.annotations.ApiStatus;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class StructurePatternRegistry {

    private static final Map<ResourceLocation, MultiblockMachineDefinition> DEFINITIONS = new ConcurrentHashMap<>();
    private static final Set<Runnable> RELOAD_LISTENERS = ConcurrentHashMap.newKeySet();

    private StructurePatternRegistry() {}

    @ApiStatus.Internal
    public static void init() {
        NeoForge.EVENT_BUS.addListener(StructurePatternRegistry::onStructurePatternsReloaded);
    }

    public static void register(MultiblockMachineDefinition definition) {
        DEFINITIONS.put(definition.getId(), definition);
    }

    @ApiStatus.Internal
    public static void addReloadListener(Runnable listener) {
        RELOAD_LISTENERS.add(listener);
    }

    @ApiStatus.Internal
    public static int reloadAllPatterns() {
        return postReloadEvent(null);
    }

    @ApiStatus.Internal
    public static int reloadTypePatterns(StructureDefinitionType type) {
        Objects.requireNonNull(type);
        return postReloadEvent(null);
    }

    @ApiStatus.Internal
    public static int reloadPattern(ResourceLocation id) {
        return postReloadEvent(id);
    }

    @ApiStatus.Internal
    public static int reloadPattern(StructureDefinitionType type, ResourceLocation id) {
        Objects.requireNonNull(type);
        return postReloadEvent(id);
    }

    private static int postReloadEvent(ResourceLocation id) {
        StructurePatternsReloadedEvent event = NeoForge.EVENT_BUS.post(new StructurePatternsReloadedEvent(id));
        return event.getRefreshedPatterns();
    }

    private static void onStructurePatternsReloaded(StructurePatternsReloadedEvent event) {
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
        if (refreshed > 0) {
            RELOAD_LISTENERS.forEach(Runnable::run);
        }
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

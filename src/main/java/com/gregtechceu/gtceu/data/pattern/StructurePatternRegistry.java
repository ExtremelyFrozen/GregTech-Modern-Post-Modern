package com.gregtechceu.gtceu.data.pattern;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.multiblock.BlockPattern;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.ApiStatus;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StructurePatternRegistry {

    private static final Map<StructurePatternKey, JavaDefinition> JAVA_DEFINITIONS = new ConcurrentHashMap<>();
    private static final Set<Runnable> RELOAD_LISTENERS = ConcurrentHashMap.newKeySet();
    private static final ExecutorService RELOAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private StructurePatternRegistry() {}

    public static void registerJavaDefinition(MultiblockMachineDefinition definition, String structureName) {
        StructurePatternKey key = new StructurePatternKey(definition.getId(), structureName);
        JAVA_DEFINITIONS.put(key, new JavaDefinition(key, definition, structureName));
    }

    public static BlockPattern resolvePattern(MultiblockMachineDefinition definition, String structureName) {
        StructurePatternKey key = new StructurePatternKey(definition.getId(), structureName);
        BlockPattern javaPattern = definition.createJavaPattern(structureName);
        return resolvePattern(key, definition, javaPattern);
    }

    public static BlockPattern resolvePattern(StructurePatternKey key, MultiblockMachineDefinition definition,
                                              BlockPattern javaPattern) {
        return StructureCache.resolvePattern(key, definition, javaPattern);
    }

    @ApiStatus.Internal
    public static void addReloadListener(Runnable listener) {
        RELOAD_LISTENERS.add(listener);
    }

    @ApiStatus.Internal
    public static CompletableFuture<Integer> reloadAllPatternsAsync() {
        return runReloadTasksAsync((StructureDefinitionSource) null, (ResourceLocation) null);
    }

    @ApiStatus.Internal
    public static CompletableFuture<Integer> reloadTypePatternsAsync(StructureDefinitionType type) {
        return runReloadTasksAsync(StructureDefinitionSource.fromDefinitionType(type), (ResourceLocation) null);
    }

    @ApiStatus.Internal
    public static CompletableFuture<Integer> reloadPatternAsync(ResourceLocation machineId) {
        return runReloadTasksAsync(machineId);
    }

    @ApiStatus.Internal
    public static CompletableFuture<Integer> reloadPatternAsync(StructurePatternKey key) {
        return runReloadTasksAsync(null, key);
    }

    @ApiStatus.Internal
    public static CompletableFuture<Integer> reloadPatternAsync(StructureDefinitionType type,
                                                                ResourceLocation machineId) {
        return runReloadTasksAsync(StructureDefinitionSource.fromDefinitionType(type), machineId);
    }

    @ApiStatus.Internal
    public static CompletableFuture<Integer> reloadPatternAsync(StructureDefinitionType type, StructurePatternKey key) {
        return runReloadTasksAsync(StructureDefinitionSource.fromDefinitionType(type), key);
    }

    private static CompletableFuture<Integer> runReloadTasksAsync(StructureDefinitionSource source,
                                                                  ResourceLocation machineId) {
        CompletableFuture<?>[] tasks = JAVA_DEFINITIONS.values().stream()
                .filter(definition -> machineId == null || definition.key().machineId().equals(machineId))
                .filter(definition -> matchesSource(definition, source))
                .map(definition -> CompletableFuture.supplyAsync(() -> runTask(definition) ? 1 : 0, RELOAD_EXECUTOR))
                .toArray(CompletableFuture[]::new);
        return joinReloadTasks(tasks)
                .thenApply(StructurePatternRegistry::notifyReloadListeners);
    }

    private static CompletableFuture<Integer> runReloadTasksAsync(StructureDefinitionSource source,
                                                                  StructurePatternKey key) {
        JavaDefinition definition = JAVA_DEFINITIONS.get(key);
        if (definition == null || !matchesSource(definition, source)) {
            return CompletableFuture.completedFuture(0);
        }
        return CompletableFuture.supplyAsync(() -> runTask(definition) ? 1 : 0, RELOAD_EXECUTOR)
                .thenApply(StructurePatternRegistry::notifyReloadListeners);
    }

    private static CompletableFuture<Integer> runReloadTasksAsync(ResourceLocation machineId) {
        CompletableFuture<?>[] tasks = JAVA_DEFINITIONS.values().stream()
                .filter(definition -> machineId == null || definition.key().machineId().equals(machineId))
                .map(definition -> CompletableFuture.supplyAsync(() -> runTask(definition) ? 1 : 0, RELOAD_EXECUTOR))
                .toArray(CompletableFuture[]::new);
        return joinReloadTasks(tasks)
                .thenApply(StructurePatternRegistry::notifyReloadListeners);
    }

    private static CompletableFuture<Integer> joinReloadTasks(CompletableFuture<?>[] tasks) {
        return CompletableFuture.allOf(tasks)
                .thenApply(unused -> {
                    int refreshed = 0;
                    for (CompletableFuture<?> task : tasks) {
                        refreshed += (Integer) task.join();
                    }
                    return refreshed;
                });
    }

    private static int notifyReloadListeners(int refreshed) {
        if (refreshed > 0) {
            RELOAD_LISTENERS.forEach(Runnable::run);
        }
        return refreshed;
    }

    private static boolean matchesSource(JavaDefinition definition, StructureDefinitionSource source) {
        return source == null || StructureCache.getActiveSource(definition.key()) == source;
    }

    private static boolean runTask(JavaDefinition definition) {
        try {
            definition.definition().reloadPattern(definition.structureName());
            return true;
        } catch (Exception e) {
            GTCEu.LOGGER.error("Failed to reload structure pattern for {}", definition.key(), e);
            return false;
        }
    }

    private record JavaDefinition(StructurePatternKey key, MultiblockMachineDefinition definition,
                                  String structureName) {}
}

package com.gregtechceu.gtceu.data.pattern;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.multiblock.BlockPattern;

import net.minecraft.resources.ResourceLocation;

import org.jetbrains.annotations.ApiStatus;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StructurePatternRegistry {

    private static final Map<ResourceLocation, JavaDefinition> JAVA_DEFINITIONS = new ConcurrentHashMap<>();
    private static final Set<Runnable> RELOAD_LISTENERS = ConcurrentHashMap.newKeySet();
    private static final ExecutorService RELOAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private StructurePatternRegistry() {}

    public static void registerJavaDefinition(MultiblockMachineDefinition definition) {
        JAVA_DEFINITIONS.put(definition.getId(), new JavaDefinition(definition.getId(), definition));
    }

    public static BlockPattern resolvePattern(MultiblockMachineDefinition definition) {
        BlockPattern javaPattern = definition.createJavaPattern();
        return StructureCache.resolvePattern(definition.getId(), definition, javaPattern);
    }

    @ApiStatus.Internal
    public static void addReloadListener(Runnable listener) {
        RELOAD_LISTENERS.add(listener);
    }

    @ApiStatus.Internal
    public static CompletableFuture<Integer> reloadAllPatternsAsync() {
        return runReloadTasksAsync((StructureDefinitionSource) null, null);
    }

    @ApiStatus.Internal
    public static CompletableFuture<Integer> reloadTypePatternsAsync(StructureDefinitionType type) {
        Objects.requireNonNull(type);
        return runReloadTasksAsync(StructureDefinitionSource.fromDefinitionType(type), null);
    }

    @ApiStatus.Internal
    public static CompletableFuture<Integer> reloadPatternAsync(ResourceLocation id) {
        return runReloadTasksAsync(id);
    }

    @ApiStatus.Internal
    public static CompletableFuture<Integer> reloadPatternAsync(StructureDefinitionType type, ResourceLocation id) {
        Objects.requireNonNull(type);
        return runReloadTasksAsync(StructureDefinitionSource.fromDefinitionType(type), id);
    }

    private static CompletableFuture<Integer> runReloadTasksAsync(StructureDefinitionSource source,
                                                                  ResourceLocation id) {
        if (id != null) {
            JavaDefinition definition = JAVA_DEFINITIONS.get(id);
            if (definition == null || !matchesSource(definition, source)) {
                return CompletableFuture.completedFuture(0);
            }
            return CompletableFuture.supplyAsync(() -> runTask(definition) ? 1 : 0, RELOAD_EXECUTOR)
                    .thenApply(StructurePatternRegistry::notifyReloadListeners);
        }

        CompletableFuture<?>[] tasks = JAVA_DEFINITIONS.values().stream()
                .filter(definition -> matchesSource(definition, source))
                .map(definition -> CompletableFuture.supplyAsync(() -> runTask(definition) ? 1 : 0, RELOAD_EXECUTOR))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(tasks)
                .thenApply(unused -> {
                    int refreshed = 0;
                    for (CompletableFuture<?> task : tasks) {
                        refreshed += (Integer) task.join();
                    }
                    return refreshed;
                })
                .thenApply(StructurePatternRegistry::notifyReloadListeners);
    }

    private static CompletableFuture<Integer> runReloadTasksAsync(ResourceLocation id) {
        if (id != null) {
            JavaDefinition definition = JAVA_DEFINITIONS.get(id);
            if (definition == null ||
                    StructureCache.getActiveSource(definition.id()) == StructureDefinitionSource.JAVA) {
                return CompletableFuture.completedFuture(0);
            }
            return CompletableFuture.supplyAsync(() -> runTask(definition) ? 1 : 0, RELOAD_EXECUTOR)
                    .thenApply(StructurePatternRegistry::notifyReloadListeners);
        }

        CompletableFuture<?>[] tasks = JAVA_DEFINITIONS.values().stream()
                .filter(definition -> StructureCache.getActiveSource(definition.id()) != StructureDefinitionSource.JAVA)
                .map(definition -> CompletableFuture.supplyAsync(() -> runTask(definition) ? 1 : 0, RELOAD_EXECUTOR))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(tasks)
                .thenApply(unused -> {
                    int refreshed = 0;
                    for (CompletableFuture<?> task : tasks) {
                        refreshed += (Integer) task.join();
                    }
                    return refreshed;
                })
                .thenApply(StructurePatternRegistry::notifyReloadListeners);
    }

    private static int notifyReloadListeners(int refreshed) {
        if (refreshed > 0) {
            RELOAD_LISTENERS.forEach(Runnable::run);
        }
        return refreshed;
    }

    private static boolean matchesSource(JavaDefinition definition, StructureDefinitionSource source) {
        return source == null || StructureCache.getActiveSource(definition.id()) == source;
    }

    private static boolean runTask(JavaDefinition definition) {
        try {
            definition.definition().reloadPattern();
            return true;
        } catch (Exception e) {
            GTCEu.LOGGER.error("Failed to reload structure pattern for {}", definition.id(), e);
            return false;
        }
    }

    private record JavaDefinition(ResourceLocation id, MultiblockMachineDefinition definition) {}
}

package com.gregtechceu.gtceu.data.pattern;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

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

    private static final Map<ResourceLocation, ReloadTask> RELOAD_TASKS = new ConcurrentHashMap<>();
    private static final Set<Runnable> RELOAD_LISTENERS = ConcurrentHashMap.newKeySet();
    private static final ExecutorService RELOAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

    private StructurePatternRegistry() {}

    public static void register(MultiblockMachineDefinition definition) {
        RELOAD_TASKS.put(definition.getId(), new ReloadTask(definition.getId(), definition::reloadPattern));
    }

    @ApiStatus.Internal
    public static void addReloadListener(Runnable listener) {
        RELOAD_LISTENERS.add(listener);
    }

    @ApiStatus.Internal
    public static CompletableFuture<Integer> reloadAllPatternsAsync() {
        return runReloadTasksAsync(null);
    }

    @ApiStatus.Internal
    public static CompletableFuture<Integer> reloadTypePatternsAsync(StructureDefinitionType type) {
        Objects.requireNonNull(type);
        return runReloadTasksAsync(null);
    }

    @ApiStatus.Internal
    public static CompletableFuture<Integer> reloadPatternAsync(ResourceLocation id) {
        return runReloadTasksAsync(id);
    }

    @ApiStatus.Internal
    public static CompletableFuture<Integer> reloadPatternAsync(StructureDefinitionType type, ResourceLocation id) {
        Objects.requireNonNull(type);
        return runReloadTasksAsync(id);
    }

    private static CompletableFuture<Integer> runReloadTasksAsync(ResourceLocation id) {
        if (id != null) {
            ReloadTask task = RELOAD_TASKS.get(id);
            if (task == null) {
                return CompletableFuture.completedFuture(0);
            }
            return CompletableFuture.supplyAsync(() -> runTask(task) ? 1 : 0, RELOAD_EXECUTOR)
                    .thenApply(StructurePatternRegistry::notifyReloadListeners);
        }

        CompletableFuture<?>[] tasks = RELOAD_TASKS.values().stream()
                .map(task -> CompletableFuture.supplyAsync(() -> runTask(task) ? 1 : 0, RELOAD_EXECUTOR))
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

    private static boolean runTask(ReloadTask task) {
        try {
            task.task().run();
            return true;
        } catch (Exception e) {
            GTCEu.LOGGER.error("Failed to reload structure pattern for {}", task.id(), e);
            return false;
        }
    }

    private record ReloadTask(ResourceLocation id, Runnable task) {}
}

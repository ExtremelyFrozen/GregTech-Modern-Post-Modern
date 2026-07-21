package com.gregtechceu.gtceu.api.gui.editor;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Path;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;

public record GTUIXmlTarget(Kind kind, ResourceLocation location) {

    public enum Kind {

        MACHINE("machine"),
        RECIPE_TYPE("recipe_type");

        private final String directory;

        Kind(String directory) {
            this.directory = directory;
        }

        public String directory() {
            return directory;
        }
    }

    public static GTUIXmlTarget machine(ResourceLocation location) {
        return new GTUIXmlTarget(Kind.MACHINE, location);
    }

    public static GTUIXmlTarget recipeType(ResourceLocation location) {
        return new GTUIXmlTarget(Kind.RECIPE_TYPE, location);
    }

    public Path resolve(Path assetsRoot) {
        Path baseDirectory = assetsRoot.toAbsolutePath().normalize()
                .resolve(location.getNamespace())
                .resolve("ui")
                .resolve(kind.directory())
                .normalize();
        Path target = baseDirectory.resolve(location.getPath() + ".xml").normalize();
        if (!target.startsWith(baseDirectory)) {
            GTCEu.LOGGER.error("Refusing to resolve UI target outside its resource directory: {}", location);
            throw new IllegalArgumentException("UI target escapes its resource directory: " + location);
        }
        return target;
    }

    public static Optional<GTUIXmlTarget> infer(Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        int nameCount = normalized.getNameCount();
        for (int assetsIndex = nameCount - 5; assetsIndex >= 0; assetsIndex--) {
            if (!"assets".equals(normalized.getName(assetsIndex).toString()) ||
                    !"ui".equals(normalized.getName(assetsIndex + 2).toString())) {
                continue;
            }

            Kind kind = kindForDirectory(normalized.getName(assetsIndex + 3).toString());
            if (kind == null) {
                continue;
            }

            String fileName = normalized.getFileName().toString();
            if (!fileName.endsWith(".xml") || fileName.length() == ".xml".length()) {
                continue;
            }

            StringBuilder resourcePath = new StringBuilder();
            for (int pathIndex = assetsIndex + 4; pathIndex < nameCount; pathIndex++) {
                if (!resourcePath.isEmpty()) {
                    resourcePath.append('/');
                }
                String segment = normalized.getName(pathIndex).toString();
                if (pathIndex == nameCount - 1) {
                    segment = segment.substring(0, segment.length() - ".xml".length());
                }
                resourcePath.append(segment);
            }

            ResourceLocation location = ResourceLocation.tryBuild(
                    normalized.getName(assetsIndex + 1).toString(), resourcePath.toString());
            if (location != null) {
                return Optional.of(new GTUIXmlTarget(kind, location));
            }
        }
        return Optional.empty();
    }

    private static Kind kindForDirectory(String directory) {
        for (Kind kind : Kind.values()) {
            if (kind.directory().equals(directory)) {
                return kind;
            }
        }
        return null;
    }

    public void reloadRuntime() {
        switch (kind) {
            case MACHINE -> reloadMachineRuntime();
            case RECIPE_TYPE -> reloadRecipeTypeRuntime();
        }
    }

    private void reloadMachineRuntime() {
        Set<EditableMachineUI> reloaded = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var definition : GTRegistries.MACHINES) {
            var editableUI = definition.getEditableUI();
            if (editableUI != null && location.equals(editableUI.getUiPath()) && reloaded.add(editableUI)) {
                editableUI.reloadCustomUI();
            }
        }
        if (reloaded.isEmpty()) {
            GTCEu.LOGGER.warn("Saved machine UI XML has no registered runtime target: {}", location);
        }
    }

    private void reloadRecipeTypeRuntime() {
        var recipeType = BuiltInRegistries.RECIPE_TYPE.get(location);
        if (recipeType instanceof GTRecipeType gtRecipeType) {
            gtRecipeType.getRecipeUI().reloadCustomUI();
        } else {
            GTCEu.LOGGER.warn("Saved recipe type UI XML has no registered GT recipe type target: {}", location);
        }
    }
}

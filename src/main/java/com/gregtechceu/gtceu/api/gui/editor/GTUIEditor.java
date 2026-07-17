package com.gregtechceu.gtceu.api.gui.editor;

import com.gregtechceu.gtceu.GTCEu;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.editor.UIEditor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nonnull;

public class GTUIEditor extends UIEditor {

    public GTUIEditor() {}

    @Override
    protected @Nonnull Editor createNewEditorInstance() {
        return new GTUIEditor();
    }

    @Override
    protected void initMenus() {
        fileMenu.addProjectProvider(GTUIXmlProjectType.TYPE);
        menuContainer.addChildren(
                fileMenu.createMenuTab(),
                viewMenu.createMenuTab(),
                new TemplateTab(this).createMenuTab());
    }

    public static Path getWorkspacePath() {
        return LDLib2.getAssetsDir().toPath();
    }

    public static Path createWorkspaceDirectory(String relativePath) {
        return createWorkspaceDirectory(getWorkspacePath().resolve(relativePath));
    }

    static Path createWorkspaceDirectory(Path requestedDirectory) {
        Path workspace = getWorkspacePath().toAbsolutePath().normalize();
        Path directory = requestedDirectory.toAbsolutePath().normalize();
        if (!directory.startsWith(workspace)) {
            GTCEu.LOGGER.error("Refusing to create GT UI editor directory outside active assets root: {}", directory);
            throw new IllegalArgumentException("GT UI editor directory escapes workspace: " + directory);
        }
        try {
            return Files.createDirectories(directory);
        } catch (IOException e) {
            GTCEu.LOGGER.error("Failed to create GT UI editor workspace directory {}", directory, e);
            throw new IllegalStateException("Failed to create GT UI editor workspace directory " + directory, e);
        }
    }
}

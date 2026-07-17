package com.gregtechceu.gtceu.api.gui.editor;

import com.gregtechceu.gtceu.GTCEu;

import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.project.ProjectType;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GTUIXmlProjectType extends ProjectType {

    public static final GTUIXmlProjectType TYPE = new GTUIXmlProjectType();

    private GTUIXmlProjectType() {
        super(Icons.XML, "gtpm.gui.editor.project.xml", ".xml", GTUIXmlProject::new);
    }

    @Override
    public File getRootSavePath(IProject project, File projectRoot) {
        Path workspace = GTUIEditor.getWorkspacePath().toAbsolutePath().normalize();
        Path directory = project instanceof GTUIXmlProject xmlProject ?
                xmlProject.getTarget().map(target -> target.resolve(workspace).getParent()).orElse(workspace) :
                workspace;
        return GTUIEditor.createWorkspaceDirectory(directory).toFile();
    }

    @Override
    public File getDefaultSaveFile(IProject project, File projectRoot) {
        Path defaultFile = resolveDefaultSaveFile(project, GTUIEditor.getWorkspacePath());
        GTUIEditor.createWorkspaceDirectory(defaultFile.getParent());
        return defaultFile.toFile();
    }

    Path resolveDefaultSaveFile(IProject project, Path workspace) {
        if (project instanceof GTUIXmlProject xmlProject) {
            return xmlProject.getTarget()
                    .map(target -> target.resolve(workspace))
                    .orElseGet(() -> resolveUntargetedSaveFile(workspace));
        }

        return resolveUntargetedSaveFile(workspace);
    }

    private static Path resolveUntargetedSaveFile(Path workspace) {
        Path defaultFile = workspace.toAbsolutePath().normalize().resolve("new.xml");
        int index = 1;
        while (Files.exists(defaultFile)) {
            defaultFile = workspace.toAbsolutePath().normalize().resolve("new_" + index++ + ".xml");
        }
        return defaultFile;
    }

    @Override
    public void saveProjectToFile(IProject project, File file) throws IOException {
        GTUIXmlProject xmlProject = requireXmlProject(project);
        Path path = file.toPath().toAbsolutePath().normalize();
        String xml = requireBomFree(xmlProject.getXml(), path);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, xml, StandardCharsets.UTF_8);
        } catch (IOException e) {
            GTCEu.LOGGER.error("Failed to save GT UI XML project to {}", path, e);
            throw e;
        }

        xmlProject.updateTarget(path);
        Path activeAssetsRoot = GTUIEditor.getWorkspacePath();
        if (!isWithinAssetsRoot(path, activeAssetsRoot)) {
            GTCEu.LOGGER.warn(
                    "Saved GT UI XML to {}, but it is outside the active assets root {}; runtime UI was not reloaded",
                    path, activeAssetsRoot);
        } else {
            xmlProject.getTarget().ifPresentOrElse(
                    GTUIXmlTarget::reloadRuntime,
                    () -> GTCEu.LOGGER.warn("Saved GT UI XML has no machine or recipe runtime target: {}", path));
        }
    }

    @Override
    public boolean isProjectDirty(IProject project, File file) throws IOException {
        GTUIXmlProject xmlProject = requireXmlProject(project);
        Path path = file.toPath();
        try {
            return !Files.exists(path) || !Files.readString(path, StandardCharsets.UTF_8).equals(xmlProject.getXml());
        } catch (IOException e) {
            GTCEu.LOGGER.error("Failed to compare GT UI XML project with {}", path, e);
            throw e;
        }
    }

    @Override
    public GTUIXmlProject loadProjectFromFile(File file) throws IOException {
        Path path = file.toPath().toAbsolutePath().normalize();
        try {
            String xml = requireBomFree(Files.readString(path, StandardCharsets.UTF_8), path);
            GTUIXmlProject project = new GTUIXmlProject()
                    .setXml(xml, false);
            project.updateTarget(path);
            return project;
        } catch (IOException e) {
            GTCEu.LOGGER.error("Failed to load GT UI XML project from {}", path, e);
            throw e;
        }
    }

    private static GTUIXmlProject requireXmlProject(IProject project) {
        if (project instanceof GTUIXmlProject xmlProject) {
            return xmlProject;
        }
        throw new IllegalArgumentException("GT XML project type cannot handle " + project.getClass().getName());
    }

    private static String requireBomFree(String xml, Path path) {
        if (!xml.isEmpty() && xml.charAt(0) == '\uFEFF') {
            GTCEu.LOGGER.error("GT UI XML must be UTF-8 without BOM: {}", path);
            throw new IllegalArgumentException("GT UI XML must be UTF-8 without BOM: " + path);
        }
        return xml;
    }

    static boolean isWithinAssetsRoot(Path path, Path assetsRoot) {
        return path.toAbsolutePath().normalize().startsWith(assetsRoot.toAbsolutePath().normalize());
    }
}

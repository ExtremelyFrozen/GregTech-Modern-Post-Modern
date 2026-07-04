package com.gregtechceu.gtceu.api.gui.editor;

import com.gregtechceu.gtceu.GTCEu;

import com.lowdragmc.lowdraglib.gui.editor.Icons;
import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.ui.UIEditor;
import com.lowdragmc.lowdraglib.gui.editor.ui.tool.WidgetToolBox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.lowdragmc.lowdraglib.gui.editor.ui.tool.WidgetToolBox.Default.registerTab;

@LDLRegister(name = "editor.gtpm", group = "editor")
public class GTUIEditor extends UIEditor {

    public static final WidgetToolBox.Default GT_CONTAINER = registerTab("widget.gtm_container",
            Icons.WIDGET_CONTAINER);

    public GTUIEditor() {
        super(getWorkspace());
    }

    @Override
    public String getTranslateKey() {
        return GTCEu.MOD_ID + ".gui.editor.register.editor.gtpm";
    }

    public static String getToolBoxTranslateKey(WidgetToolBox.Default tab) {
        if (tab == GT_CONTAINER) {
            return GTCEu.MOD_ID + ".gui.editor.group.widget.gtm_container";
        }
        return GTCEu.MOD_ID + ".gui.editor.group." + tab.groupName;
    }

    public static File getWorkspace() {
        return createWorkspaceDirectory("").toFile();
    }

    public static Path createWorkspaceDirectory(String relativePath) {
        Path workspace = GTCEu.GTCEU_FOLDER.resolve(relativePath);
        try {
            Files.createDirectories(workspace);
        } catch (IOException e) {
            GTCEu.LOGGER.error("Failed to create GTM UI editor workspace {}", workspace, e);
            throw new IllegalStateException("Failed to create GTM UI editor workspace", e);
        }
        return workspace;
    }
}

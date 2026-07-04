package com.gregtechceu.gtceu.api.gui.editor;

import com.gregtechceu.gtceu.GTCEu;

import com.lowdragmc.lowdraglib.gui.editor.annotation.LDLRegister;
import com.lowdragmc.lowdraglib.gui.editor.ui.menu.MenuTab;
import com.lowdragmc.lowdraglib.gui.util.TreeBuilder;

@LDLRegister(name = "template_tab", group = "editor.gtpm")
public class TemplateTab extends MenuTab {

    @Override
    public String getTranslateKey() {
        return GTCEu.MOD_ID + ".gui.editor.register.editor.gtpm.template_tab";
    }

    protected TreeBuilder.Menu createMenu() {
        return TreeBuilder.Menu.start();
    }
}

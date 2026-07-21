package com.gregtechceu.gtceu.api.gui.factory;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.gui.editor.GTUIEditor;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public final class GTUIEditorMenu {

    public static final ResourceLocation WINDOW_ID = GTCEu.id("gt_ui_editor");

    private GTUIEditorMenu() {}

    public static void register() {
        PlayerUIMenuType.register(WINDOW_ID, GTUIEditorMenu::createHolder);
    }

    public static boolean open(ServerPlayer player) {
        return PlayerUIMenuType.openUI(player, WINDOW_ID);
    }

    static PlayerUIMenuType.PlayerUIHolder createHolder(Player ignored) {
        return player -> player.level().isClientSide ? createClientUI() : createServerUI();
    }

    static ModularUI createClientUI() {
        return new ModularUI(UI.of(EditorWindow.open(WINDOW_ID, GTUIEditor::new)))
                .shouldCloseOnEsc(false)
                .shouldCloseOnKeyInventory(false);
    }

    static ModularUI createServerUI() {
        return new ModularUI(UI.empty());
    }
}

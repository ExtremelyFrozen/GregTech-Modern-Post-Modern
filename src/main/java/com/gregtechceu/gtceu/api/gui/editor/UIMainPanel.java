package com.gregtechceu.gtceu.api.gui.editor;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.texture.IGuiTexture;

import com.lowdragmc.lowdraglib.gui.editor.ui.Editor;
import com.lowdragmc.lowdraglib.gui.editor.ui.MainPanel;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class UIMainPanel extends MainPanel {

    final String description;

    public UIMainPanel(Editor editor, WidgetGroup root, String description) {
        super(editor, root);
        this.setBackground(new IGuiTexture() {

            @Override
            @OnlyIn(Dist.CLIENT)
            public void draw(GuiGraphics graphics, float mouseX, float mouseY, float x, float y, float width,
                             float height, float partialTicks) {
                if (description != null) {
                    GuiTextures.text(description).scale(2.0f).draw(graphics, mouseX, mouseY, x, y,
                            width - editor.getConfigPanel().getSize().getWidth(), height, partialTicks);
                }
                var border = 4;
                var background = GuiTextures.BACKGROUND;
                var position = root.getPosition();
                var size = root.getSize();
                var w = Math.max(size.width + border * 2, 172);
                var h = Math.max(size.height + border * 2, 86);
                background.draw(graphics, mouseX, mouseY,
                        position.x - (w - size.width) / 2f,
                        position.y - (h - size.height) / 2f,
                        w, h, partialTicks);
            }
        });
        this.description = description;
    }
}

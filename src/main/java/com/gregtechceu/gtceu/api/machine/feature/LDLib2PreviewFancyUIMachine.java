package com.gregtechceu.gtceu.api.machine.feature;

import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.element.GTImageElement;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2FancyMachineUIElement;
import com.gregtechceu.gtceu.api.multiblock.MultiblockBlockInfo;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;

import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Supplies the LDLib2 default preview page for Fancy machines without a dedicated page body.
 *
 * <p>
 * This opt-in contract mirrors the legacy fallback preview while keeping {@link LDLib2FancyUIMachine} explicit:
 * concrete machines must choose this fallback instead of silently receiving a placeholder page.
 */
public interface LDLib2PreviewFancyUIMachine extends LDLib2FancyUIMachine {

    /**
     * Width of the legacy fallback preview content.
     */
    int PREVIEW_PAGE_WIDTH = 100;

    /**
     * Height of the legacy fallback preview content.
     */
    int PREVIEW_PAGE_HEIGHT = 100;

    /**
     * Builds a fixed-size LDLib2 preview page for this machine.
     *
     * @param shell Fancy shell that owns this page; the preview does not need shell state.
     * @return page root containing a client-side LDLib2 scene when the machine is remote.
     */
    @Override
    default UIElement createLDLib2MainPage(LDLib2FancyMachineUIElement shell) {
        UIElement root = UITemplate.setLDLib2Bounds(new UIElement(), 0, 0, PREVIEW_PAGE_WIDTH, PREVIEW_PAGE_HEIGHT);
        if (self().isRemote()) {
            root.addChild(new GTImageElement(26, 60, 48, 16, GuiTextures.SCENE));
            root.addChild(createLDLib2PreviewScene());
        }
        return root;
    }

    /**
     * Returns the fixed preview content width used by the Fancy shell.
     *
     * @return preview page width in pixels.
     */
    @Override
    default int getLDLib2PageWidth() {
        return PREVIEW_PAGE_WIDTH;
    }

    /**
     * Returns the fixed preview content height used by the Fancy shell.
     *
     * @return preview page height in pixels.
     */
    @Override
    default int getLDLib2PageHeight() {
        return PREVIEW_PAGE_HEIGHT;
    }

    /**
     * Creates the client-side LDLib2 scene that renders this machine's current block state.
     *
     * @return configured scene element sized to the preview page.
     */
    default Scene createLDLib2PreviewScene() {
        TrackedDummyWorld level = new TrackedDummyWorld();
        level.addBlock(BlockPos.ZERO, MultiblockBlockInfo.fromBlockState(self().getBlockState()));

        Scene scene = new Scene();
        scene.createScene(level)
                .useOrtho(true)
                .setOrthoRange(0.5f)
                .setScalable(false)
                .setDraggable(false)
                .setRenderFacing(false)
                .setRenderSelect(false)
                .setRenderedCore(List.of(BlockPos.ZERO), null);
        return UITemplate.setLDLib2Bounds(scene, 0, 0, PREVIEW_PAGE_WIDTH, PREVIEW_PAGE_HEIGHT);
    }
}

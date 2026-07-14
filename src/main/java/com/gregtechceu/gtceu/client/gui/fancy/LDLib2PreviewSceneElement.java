package com.gregtechceu.gtceu.client.gui.fancy;

import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.multiblock.MultiblockBlockInfo;

import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;

import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Client-only LDLib2 scene that preserves the rotating single-machine preview used by legacy Fancy pages.
 */
@OnlyIn(Dist.CLIENT)
public final class LDLib2PreviewSceneElement extends Scene {

    private static final float PREVIEW_FOV = 30;
    private static final float ROTATION_DEGREES_PER_TICK = 2;

    private LDLib2PreviewSceneElement() {}

    /**
     * Creates a fixed, non-interactive preview scene for the supplied machine block state.
     */
    public static Scene createScene(MetaMachine machine, int width, int height) {
        TrackedDummyWorld level = new TrackedDummyWorld();
        level.addBlock(BlockPos.ZERO, MultiblockBlockInfo.fromBlockState(machine.getBlockState()));

        LDLib2PreviewSceneElement scene = new LDLib2PreviewSceneElement();
        scene.createScene(level)
                .useOrtho(true)
                .setOrthoRange(0.5f)
                .setScalable(false)
                .setDraggable(false)
                .setRenderFacing(false)
                .setRenderSelect(false)
                .setRenderedCore(List.of(BlockPos.ZERO), null);
        var renderer = scene.getRenderer();
        if (renderer == null) {
            throw new IllegalStateException("LDLib2 preview scene did not create a renderer.");
        }
        renderer.setFov(PREVIEW_FOV);
        return UITemplate.setLDLib2Bounds(scene, 0, 0, width, height);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        var modularUI = getModularUI();
        if (modularUI == null) {
            throw new IllegalStateException("LDLib2 preview scene must be attached before rendering.");
        }
        float rotationYaw = (modularUI.getTickCounter() + guiContext.partialTick) *
                ROTATION_DEGREES_PER_TICK;
        setCameraYawAndPitch(rotationYaw, getRotationPitch());
        super.drawBackgroundAdditional(guiContext);
    }
}

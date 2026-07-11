package com.gregtechceu.gtceu.client.gui.fancy;

import com.gregtechceu.gtceu.api.gui.UITemplate;
import com.gregtechceu.gtceu.api.gui.fancy.LDLib2DirectionalFaceClickTracker;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.DirectionalAutoOutputMachine;

import com.lowdragmc.lowdraglib2.client.scene.ISceneBlockRenderHook;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.utils.data.BlockPosFace;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * Client-only construction and interaction adapter for LDLib2 directional scenes.
 */
@OnlyIn(Dist.CLIENT)
public final class LDLib2DirectionalSceneElement {

    private static final int ITEM_AUTO_OUTPUT_COLOR = 0xffff6e0f;
    private static final int ITEM_OUTPUT_COLOR = 0x8fff6e0f;
    private static final int FLUID_AUTO_OUTPUT_COLOR = 0xff00b4ff;
    private static final int FLUID_OUTPUT_COLOR = 0x8f00b4ff;

    private LDLib2DirectionalSceneElement() {}

    /**
     * Attaches a directional output scene to a common-side placeholder element.
     */
    public static void attachScene(UIElement sceneContainer, MetaMachine machine,
                                   DirectionalAutoOutputMachine output, int width, int height,
                                   BiPredicate<Direction, Integer> faceClickHandler) {
        BlockPos machinePos = machine.getBlockPos();
        DirectionalScene scene = new DirectionalScene(machinePos, faceClickHandler);
        scene.createScene(machine.getLevel())
                .setRenderSelect(false)
                .setRenderedCore(List.of(machinePos), null);

        var renderer = scene.getRenderer();
        if (renderer == null) {
            throw new IllegalStateException("LDLib2 directional scene did not create a renderer.");
        }
        List<BlockPos> adjacentPositions = getAdjacentPositions(machinePos);
        TrackedDummyWorld dummyWorld = scene.getDummyWorld();
        if (dummyWorld == null) {
            throw new IllegalStateException("LDLib2 directional scene did not create a tracked world.");
        }
        dummyWorld.setBlockFilter(pos -> pos.equals(machinePos) || adjacentPositions.contains(pos));
        renderer.addRenderedBlocks(adjacentPositions, new AdditiveRenderHook());
        scene.setAfterWorldRender(ignored -> renderOutputs(scene, machinePos, output));

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            throw new IllegalStateException("LDLib2 directional scene requires the client player.");
        }
        var playerRotation = player.getRotationVector();
        scene.setCameraYawAndPitch(playerRotation.y - 90, playerRotation.x);
        sceneContainer.addChild(UITemplate.setLDLib2Bounds(scene, 0, 0, width, height));
    }

    private static List<BlockPos> getAdjacentPositions(BlockPos pos) {
        return List.of(pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west());
    }

    private static void renderOutputs(Scene scene, BlockPos machinePos, DirectionalAutoOutputMachine output) {
        renderItemOutput(scene, machinePos, output);
        renderFluidOutput(scene, machinePos, output);
    }

    private static void renderItemOutput(Scene scene, BlockPos machinePos, DirectionalAutoOutputMachine output) {
        Direction outputDirection = output.getItemOutputDirection();
        if (outputDirection == null) {
            return;
        }
        int color = output.isAutoOutputItems() ? ITEM_AUTO_OUTPUT_COLOR : ITEM_OUTPUT_COLOR;
        scene.drawFacingBorder(new PoseStack(), new BlockPosFace(machinePos, outputDirection), color, 1);
    }

    private static void renderFluidOutput(Scene scene, BlockPos machinePos, DirectionalAutoOutputMachine output) {
        Direction outputDirection = output.getFluidOutputDirection();
        if (outputDirection == null) {
            return;
        }
        int color = output.isAutoOutputFluids() ? FLUID_AUTO_OUTPUT_COLOR : FLUID_OUTPUT_COLOR;
        scene.drawFacingBorder(new PoseStack(), new BlockPosFace(machinePos, outputDirection), color, 2);
    }

    private static final class AdditiveRenderHook implements ISceneBlockRenderHook {

        @Override
        public void apply(RenderType layer) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        }
    }

    private static final class DirectionalScene extends Scene {

        private final BlockPos machinePos;
        private final BiPredicate<Direction, Integer> faceClickHandler;
        private final LDLib2DirectionalFaceClickTracker clickTracker = new LDLib2DirectionalFaceClickTracker();

        private DirectionalScene(BlockPos machinePos, BiPredicate<Direction, Integer> faceClickHandler) {
            this.machinePos = machinePos;
            this.faceClickHandler = faceClickHandler;
            addEventListener(UIEvents.MOUSE_LEAVE, this::cancelFaceClickOnMouseLeave);
            addEventListener(UIEvents.DRAG_END, this::resetInteractionAfterExternalDrag);
        }

        @Override
        protected void onMouseDown(UIEvent event) {
            super.onMouseDown(event);
            clickTracker.press(event.button, lastHoverPosFace);
            if (event.button == 1 && isHover()) {
                lastClickPosFace = lastHoverPosFace;
            }
        }

        @Override
        protected void onMouseUp(UIEvent event) {
            dragging = false;
            var modularUI = getModularUI();
            boolean samePressTarget = modularUI != null && modularUI.getLastMouseDownElement() == this;
            LDLib2DirectionalFaceClickTracker.FaceClick click = clickTracker.release(event.button,
                    lastHoverPosFace, samePressTarget);
            lastClickPosFace = null;
            if (click == null || !click.face().pos().equals(machinePos)) {
                return;
            }

            lastSelectedPosFace = click.face();
            if (faceClickHandler.test(click.face().facing(), click.button())) {
                event.stopImmediatePropagation();
                event.hasHandler = true;
            }
        }

        private void cancelFaceClickOnMouseLeave(UIEvent event) {
            clickTracker.cancel();
            lastClickPosFace = null;
        }

        private void resetInteractionAfterExternalDrag(UIEvent event) {
            if (event.relatedTarget != this) {
                clickTracker.cancel();
                lastClickPosFace = null;
                dragging = false;
            }
        }
    }
}

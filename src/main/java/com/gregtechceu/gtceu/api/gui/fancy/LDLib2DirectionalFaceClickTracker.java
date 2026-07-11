package com.gregtechceu.gtceu.api.gui.fancy;

import com.lowdragmc.lowdraglib2.utils.data.BlockPosFace;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Tracks a complete left- or right-button click on one LDLib2 scene face.
 *
 * <p>
 * LDLib2's base {@code Scene} only records left-button face presses and does not match the release button. The
 * directional pages need the legacy behavior where both buttons may select a face, while a release on another face
 * or with another button must not execute an action.
 */
public final class LDLib2DirectionalFaceClickTracker {

    private int pressedButton = -1;
    @Nullable
    private BlockPosFace pressedFace;

    /**
     * Records a supported button press and its hovered face, replacing any unfinished press.
     */
    public void press(int button, @Nullable BlockPosFace face) {
        if (!isSupportedButton(button) || face == null) {
            clear();
            return;
        }
        pressedButton = button;
        pressedFace = face;
    }

    /**
     * Completes the current click only when target, button, and face still match the original press.
     */
    @Nullable
    public FaceClick release(int button, @Nullable BlockPosFace face, boolean samePressTarget) {
        BlockPosFace originalFace = pressedFace;
        int originalButton = pressedButton;
        clear();
        if (!samePressTarget || originalFace == null || originalButton != button || !originalFace.equals(face)) {
            return null;
        }
        return new FaceClick(originalFace, originalButton);
    }

    /**
     * Cancels an unfinished face click when the pointer or drag release leaves the scene.
     */
    public void cancel() {
        clear();
    }

    private static boolean isSupportedButton(int button) {
        return button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT;
    }

    private void clear() {
        pressedButton = -1;
        pressedFace = null;
    }

    /**
     * Identifies the face and mouse button of one validated scene click.
     */
    public record FaceClick(BlockPosFace face, int button) {}
}
